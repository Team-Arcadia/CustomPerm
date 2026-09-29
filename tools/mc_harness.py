# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""Shared pieces of the scripts that drive real servers: RCON, waiting, killing a process tree, reading spark's saved
files, MariaDB from XAMPP, and judging a log. Standard library only, so every script runs on a bare Python 3.
"""
import os
import re
import socket
import struct
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLEW = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))


# ---------------------------------------------------------------- waiting and processes

def wait_for(predicate, seconds, step=1.0):
    end = time.time() + seconds
    while time.time() < end:
        if predicate():
            return True
        time.sleep(step)
    return False


def kill(process):
    if process is not None and process.poll() is None:
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
        else:
            process.kill()


def log_text(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def wait_for_line(log, needle, seconds, process=None):
    """Waits until the log holds the line, or the process died; returns whether the line came."""
    return wait_for(lambda: needle in log_text(log) or (process is not None and process.poll() is not None), seconds, 2) \
        and needle in log_text(log)


# ---------------------------------------------------------------- RCON

def _packet(pid, kind, body):
    payload = struct.pack("<ii", pid, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def _read(sock):
    def exactly(n):
        data = b""
        while len(data) < n:
            chunk = sock.recv(n - len(data))
            if not chunk:
                raise IOError("rcon closed")
            data += chunk
        return data
    size = struct.unpack("<i", exactly(4))[0]
    data = exactly(size)
    return struct.unpack("<i", data[:4])[0], data[8:-2].decode("utf-8", "replace")


class Rcon:
    def __init__(self, port, password):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=60)
        self.sock.sendall(_packet(1, 3, password))
        if _read(self.sock)[0] == -1:
            raise IOError("rcon authentication refused")

    def run(self, command):
        # The server splits a long answer into packets of 4096 bytes and never says which is the last one. A full
        # packet may be followed by more: read on briefly. A leftover fragment would otherwise answer the next command.
        self.sock.sendall(_packet(2, 2, command))
        parts = [_read(self.sock)[1]]
        while len(parts[-1].encode("utf-8")) >= 4096:
            self.sock.settimeout(1.0)
            try:
                parts.append(_read(self.sock)[1])
            except socket.timeout:
                break
            finally:
                self.sock.settimeout(60)
        return "".join(parts).strip()

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def connect(port, password, seconds):
    end = time.time() + seconds
    while time.time() < end:
        try:
            return Rcon(port, password)
        except (OSError, IOError):
            time.sleep(3)
    raise IOError(f"rcon never came up on {port}")


# ---------------------------------------------------------------- spark files

def _varint(b, i):
    result = shift = 0
    while True:
        c = b[i]
        i += 1
        result |= (c & 0x7F) << shift
        shift += 7
        if c < 0x80:
            return result, i


def _fields(b):
    i = 0
    while i < len(b):
        key, i = _varint(b, i)
        field, wire = key >> 3, key & 7
        if wire == 0:
            value, i = _varint(b, i)
        elif wire == 1:
            value, i = b[i:i + 8], i + 8
        elif wire == 5:
            value, i = b[i:i + 4], i + 4
        elif wire == 2:
            n, i = _varint(b, i)
            value, i = b[i:i + n], i + n
        else:
            raise ValueError(f"wire type {wire}")
        yield field, wire, value


def heap(path):
    """Live instances per class, from spark's HeapData (entries: 2 = instances, 4 = type)."""
    counts = {}
    for field, _, value in _fields(path.read_bytes()):
        if field == 2:
            entry = {f: v for f, _, v in _fields(value)}
            name = entry.get(4, b"").decode("utf-8", "replace")
            counts[name] = counts.get(name, 0) + entry.get(2, 0)
    return counts


def _ints(v, wire):
    if wire == 0:
        return [v]
    out, i = [], 0
    while i < len(v):
        n, i = _varint(v, i)
        out.append(n)
    return out


def _frame(b):
    cls, ms, children = "", 0.0, []
    for f, w, v in _fields(b):
        if f == 3:
            cls = v.decode("utf-8", "replace")
        elif f == 8:
            ms = sum(struct.unpack(f"<{len(v) // 8}d", v)) if w == 2 else struct.unpack("<d", v)[0]
        elif f == 9:
            children += _ints(v, w)
    return cls, ms, children


def profile_ms_per_tick(path, prefix, thread="Server thread"):
    """Time per tick of a thread spent in frames of classes starting with prefix, callees included."""
    ticks = 0
    for field, _, value in _fields(path.read_bytes()):
        if field == 1:
            for f, _, v in _fields(value):
                if f == 12:
                    ticks = v
        if field != 2:
            continue
        frames, roots, name, total = [], [], "", 0.0
        for f, w, v in _fields(value):
            if f == 1:
                name = v.decode("utf-8", "replace")
            elif f == 3:
                frames.append(_frame(v))
            elif f == 4:
                total = sum(struct.unpack(f"<{len(v) // 8}d", v)) if w == 2 else struct.unpack("<d", v)[0]
            elif f == 5:
                roots += _ints(v, w)
        if name != thread:
            continue
        spent, stack = 0.0, list(roots)
        while stack:
            cls, ms, children = frames[stack.pop()]
            if cls.startswith(prefix):
                spent += ms
            else:
                stack.extend(children)
        return spent / max(1, ticks or round(total / 50)), ticks
    return None, ticks


def spark_save(rcon, spark_dir, command, extension, seconds=300):
    """Runs a spark command that saves a file, and returns the file once it has stopped growing."""
    def files():
        return set(spark_dir.glob(f"*{extension}")) if spark_dir.is_dir() else set()
    before = files()
    rcon.run(command)
    last = -1

    def saved():
        nonlocal last
        new = files() - before
        if not new:
            return False
        size = next(iter(new)).stat().st_size
        stable = size > 0 and size == last
        last = size
        return stable
    if not wait_for(saved, seconds, step=2):
        raise RuntimeError(f"spark did not save '{command}'")
    return next(iter(files() - before))


# ---------------------------------------------------------------- MariaDB (XAMPP by default)

class Database:
    def __init__(self, bin_dir, root_password=""):
        self.bin = Path(bin_dir)
        self.root = ["-u", "root"] + ([f"-p{root_password}"] if root_password else [])
        self.process = None
        # Whether this script started the server, so it stops it again at the end and leaves a running one alone.
        self.started_here = False

    def up(self):
        return subprocess.run([str(self.bin / "mysqladmin.exe"), *self.root, "ping"],
                              capture_output=True, text=True).returncode == 0

    def start(self, log, extra=()):
        ini = self.bin / "my.ini"
        args = [str(self.bin / "mysqld.exe")] + ([f"--defaults-file={ini}"] if ini.is_file() else []) \
            + ["--standalone", "--console", *extra]
        self.process = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT)
        if not wait_for(self.up, 60):
            raise RuntimeError("MariaDB did not start")

    def stop(self):
        subprocess.run([str(self.bin / "mysqladmin.exe"), *self.root, "shutdown"], capture_output=True)
        wait_for(lambda: not self.up(), 60)
        if self.process:
            self.process.wait(timeout=60)
            self.process = None

    def sql(self, statement, database=None):
        args = [str(self.bin / "mysql.exe"), *self.root, "-N", "-B"] + ([database] if database else []) + ["-e", statement]
        result = subprocess.run(args, capture_output=True, text=True)
        if result.returncode != 0:
            raise RuntimeError(f"SQL failed: {result.stderr.strip()}")
        return result.stdout


# ---------------------------------------------------------------- judging a log

# Lines that mean the mod broke, whoever logged them, as long as they name CustomPerm's code.
# The code or the mod's own tag, never a bare "customperm": the repository folder is named CustomPerm, so every path
# another mod prints from a run folder would match.
OUR_CODE = re.compile(r"com\.arcadia\.customperm|\[CustomPerm\]|\bcustomperm:")
FATAL = re.compile(r"/ERROR\]|Exception|Mixin apply failed|InvalidInjectionException|failed to load correctly|invalid dist")


def our_problems(log_path, allowed=()):
    """ERROR lines and exceptions that name CustomPerm, plus the stack frames under them; the pack's own noise is ignored."""
    problems = []
    lines = log_text(log_path).splitlines()
    for i, line in enumerate(lines):
        if not FATAL.search(line):
            continue
        # A stack trace belongs to us when one of its first frames is ours.
        window = [line] + [l for l in lines[i + 1:i + 12] if l.startswith("\tat ") or l.startswith("Caused by")]
        if any(OUR_CODE.search(l) for l in window) and not any(a in line for a in allowed):
            problems.append(line.strip()[:300])
    return problems


def write_properties(folder, values):
    lines = [f"{k}={v}" for k, v in values.items()]
    (folder / "server.properties").write_text("\n".join(lines) + "\n", encoding="utf-8")
