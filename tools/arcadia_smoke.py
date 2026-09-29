# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""The release jar inside the real Arcadia server pack: about 400 mods, LuckPerms, Arcadia Lib, spark.

The pack zip is extracted once into run/arcadia/server (its bundled JRE left out) and reused. Every run resets the
world, the logs and LuckPerms' data, cuts the pack's own database connection so nothing leaves the machine, drops in
the jar the build just produced, boots the server headless and drives it over RCON:

  - CustomPerm starts on the LuckPerms backend and leaves other mods' permission checks to LuckPerms;
  - status, modcheck, exposing a command, an alias and a rate limit answer normally among 400 mods;
  - CustomPerm stays under 0.5 ms per tick over a minute of play (spark profiler);
  - ten reloads grow no CustomPerm class (spark heap summaries) and each holds the server thread under 250 ms;
  - no ERROR line or exception in the log names CustomPerm, no OutOfMemoryError.

Verdict in run/arcadia/arcadia-smoke-report.txt, last line RESULT PASS or RESULT FAIL; exit code 0 or 1.
Usage: python tools/arcadia_smoke.py [--server-pack ZIP] [--xmx 8G] [--keep]
"""
import argparse
import re
import secrets
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

from mc_harness import (GRADLEW, ROOT, connect, heap, kill, log_text, our_problems, profile_ms_per_tick, spark_save,
                        wait_for, wait_for_line, write_properties)

BASE = ROOT / "run" / "arcadia"
SERVER = BASE / "server"
DEFAULT_PACK = Path.home() / "Downloads" / "arcadia-echoes-of-power-2032-2.0.32-2.0.32.zip"
PORT, RCON_PORT = 25620, 25621
RELOADS = 10
REQUIRED_LINES = [
    "[CustomPerm] LuckPerms detected",
    "backend=LuckPerms",
    "Not answering the permission checks of other mods",
]
# A reload is an admin's command, not a tick: judged per reload, on the time it holds the server thread.
RELOAD_BUDGET_MS = 250


class Report:
    def __init__(self):
        self.lines, self.checks, self.failures = [], 0, 0

    def check(self, what, passed, detail=""):
        self.checks += 1
        self.failures += 0 if passed else 1
        self.add(("PASS " if passed else "FAIL ") + what + (f" - {detail}" if detail else ""))

    def add(self, line):
        self.lines.append(line)
        print(line, flush=True)

    def write(self, path):
        result = f"RESULT PASS {self.checks} checks" if self.failures == 0 else f"RESULT FAIL {self.failures} of {self.checks} checks"
        self.add(result)
        path.write_text("\n".join(self.lines) + "\n", encoding="utf-8")
        return self.failures == 0


# ---------------------------------------------------------------- preparing the pack

def extract(pack):
    marker = SERVER / ".extracted-from"
    stamp = f"{pack.name} {pack.stat().st_size}"
    if marker.is_file() and marker.read_text(encoding="utf-8").strip() == stamp:
        return False
    if SERVER.exists():
        shutil.rmtree(SERVER)
    SERVER.mkdir(parents=True)
    with zipfile.ZipFile(pack) as zf:
        for member in zf.infolist():
            if member.filename.startswith("jre21/"):
                continue
            zf.extract(member, SERVER)
    marker.write_text(stamp + "\n", encoding="utf-8")
    return True


def cut_database():
    """The pack ships a connection to a real database: disable it and point it nowhere, or refuse to boot."""
    for path in [SERVER / "config" / "arcadia" / "lib" / "database.toml",
                 *SERVER.glob("defaultconfigs/**/database.toml"),
                 *SERVER.glob("world/serverconfig/**/database.toml")]:
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        text, enabled = re.subn(r"(?m)^(\s*enabled\s*=\s*)\w+", r"\1false", text)
        text, host = re.subn(r'(?m)^(\s*host\s*=\s*)".*"', r'\1"127.0.0.1"', text)
        text, port = re.subn(r"(?m)^(\s*port\s*=\s*)\d+", r"\g<1>1", text)
        text = re.sub(r'(?m)^(\s*(user|password)\s*=\s*)".*"', r'\1""', text)
        if not (enabled and host and port):
            sys.exit(f"Could not disable the database in {path}; refusing to boot a server that may reach it.")
        path.write_text(text, encoding="utf-8")


def reset(jar, password):
    for stale in ("world", "logs", "crash-reports", "usercache.json", "usernamecache.json", "ops.json",
                  "config/arcadia/customperm", "config/spark/tmp"):
        path = SERVER / stale
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
    for db in SERVER.glob("config/luckperms/*.db"):
        db.unlink()
    for old in (SERVER / "mods").glob("customperm-*.jar"):
        old.unlink()
    shutil.copy2(jar, SERVER / "mods" / jar.name)
    (SERVER / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    # max-tick-time -1: the pack's autosave (FTB Quests rewriting some 3900 files) can hold a tick past the watchdog's
    # minute on a disk scanned on access, and the watchdog would stop the test; slow ticks still show in spark.
    write_properties(SERVER, {
        "server-port": PORT, "server-ip": "127.0.0.1", "online-mode": "false", "enable-rcon": "true",
        "rcon.port": RCON_PORT, "rcon.password": password, "view-distance": 4, "simulation-distance": 4,
        "spawn-protection": 0, "max-tick-time": -1, "motd": "CustomPerm Arcadia smoke", "level-seed": "customperm"})
    cut_database()


def build_jar():
    subprocess.run([GRADLEW, "jar", "-q"], cwd=ROOT, check=True)
    jars = [j for j in (ROOT / "build" / "libs").glob("customperm-*.jar") if not j.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if not jars:
        sys.exit("No customperm jar in build/libs.")
    return max(jars, key=lambda j: j.stat().st_mtime)


def neoforge_args():
    found = sorted((SERVER / "libraries" / "net" / "neoforged" / "neoforge").glob("*/win_args.txt"))
    if not found:
        sys.exit("The pack has no NeoForge win_args.txt under libraries/net/neoforged/neoforge.")
    return found[-1].relative_to(SERVER)


# ---------------------------------------------------------------- run

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-pack", type=Path, default=DEFAULT_PACK)
    parser.add_argument("--xmx", default="8G")
    parser.add_argument("--keep", action="store_true", help="leave the server running for a look, stop it with Enter")
    args = parser.parse_args()
    if not args.server_pack.is_file():
        sys.exit(f"Server pack not found: {args.server_pack} (pass --server-pack).")

    report = Report()
    report.add(f"CustomPerm in the Arcadia server pack {args.server_pack.name}")
    BASE.mkdir(parents=True, exist_ok=True)
    jar = build_jar()
    report.add(f"jar {jar.name}")
    if extract(args.server_pack):
        report.add("pack extracted to run/arcadia/server")
    password = secrets.token_hex(16)
    reset(jar, password)

    log = SERVER / "logs" / "latest.log"
    console = open(BASE / "server-console.log", "w", encoding="utf-8")
    started = time.time()
    process = subprocess.Popen(["java", f"-Xms{args.xmx}", f"-Xmx{args.xmx}", "-XX:+UseG1GC", f"@{neoforge_args()}", "nogui"],
                               cwd=SERVER, stdout=console, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    rcon = None
    try:
        booted = wait_for_line(log, "Done (", 1800, process)
        report.check("the server boots with CustomPerm among the pack's mods", booted,
                     f"{time.time() - started:.0f} s" if booted else "no 'Done (' line, see run/arcadia/server/logs")
        if not booted:
            return finish(report, process, rcon)
        # CustomPerm finishes its start a few seconds after "Done": wait for its lines rather than read too early.
        for line in REQUIRED_LINES:
            report.check(f"log says '{line}'", wait_for_line(log, line, 60, process))
        rcon = connect(RCON_PORT, password, 120)
        scenario(rcon, report)
        rcon.run("stop")
        rcon.close()
        rcon = None
        report.check("the server stops cleanly", wait_for(lambda: process.poll() is not None, 180, 2))
    except Exception as e:  # the verdict must still be written
        report.check("the scenario ran to the end", False, repr(e))
    finally:
        if args.keep and process.poll() is None:
            input("Server left running on 127.0.0.1:%d. Press Enter to stop it." % PORT)
    return finish(report, process, rcon)


def scenario(rcon, report):
    status = rcon.run("customperm status")
    report.check("customperm status answers on the LuckPerms backend", "LuckPerms" in status, status.replace("\n", " | ")[:300])
    modcheck = rcon.run("customperm modcheck")
    report.check("customperm modcheck reads the pack's mods", bool(modcheck.strip()), modcheck.replace("\n", " | ")[:300])
    for command, expect in [("customperm command add weather", "weather"),
                            ("customperm alias add cp_pack_alias say pack", "cp_pack_alias"),
                            ("customperm ratelimit set weather 3 3600", "weather")]:
        out = rcon.run(command)
        report.check(f"/{command}", expect in out and "rror" not in out, out.replace("\n", " | ")[:200])

    spark = SERVER / "config" / "spark"
    rcon.run("spark profiler start")
    time.sleep(60)
    idle = spark_save(rcon, spark, "spark profiler stop --save-to-file", ".sparkprofile")
    ms, ticks = profile_ms_per_tick(idle, "com.arcadia.customperm")
    report.check("CustomPerm under 0.5 ms per tick on the server thread over a minute of play",
                 ms is not None and ms < 0.5, f"{ms:.4f} ms per tick over {ticks} ticks" if ms is not None else "no Server thread")

    before = heap(spark_save(rcon, spark, "spark heapsummary --save-to-file", ".sparkheap"))
    rcon.run("spark profiler start")
    for _ in range(RELOADS):
        out = rcon.run("customperm reload")
        time.sleep(2)
    report.check(f"customperm reload answers ({RELOADS} times)", "rror" not in out, out.replace("\n", " | ")[:200])
    profile = spark_save(rcon, spark, "spark profiler stop --save-to-file", ".sparkprofile")
    after = heap(spark_save(rcon, spark, "spark heapsummary --save-to-file", ".sparkheap"))
    grown = sorted(f"{k.rsplit('.', 1)[-1]} {before.get(k, 0)} -> {v}" for k, v in after.items()
                   if k.startswith("com.arcadia.customperm") and v - before.get(k, 0) >= RELOADS
                   and not k.endswith(".LogEntry"))
    report.check(f"no CustomPerm class grown by {RELOADS}+ instances across the reloads", not grown, ", ".join(grown) or "none")
    ms, ticks = profile_ms_per_tick(profile, "com.arcadia.customperm")
    per_reload = ms * ticks / RELOADS if ms is not None else None
    report.check(f"a reload holds the server thread under {RELOAD_BUDGET_MS} ms",
                 per_reload is not None and per_reload < RELOAD_BUDGET_MS,
                 f"{per_reload:.0f} ms per reload" if per_reload is not None else "no Server thread")


def finish(report, process, rcon):
    if rcon is not None:
        rcon.close()
    kill(process)
    log = SERVER / "logs" / "latest.log"
    problems = our_problems(log)
    report.check("no ERROR line or exception naming CustomPerm in the log", not problems, " || ".join(problems[:5]) or "none")
    report.check("no OutOfMemoryError", "OutOfMemoryError" not in log_text(log) + log_text(BASE / "server-console.log"))
    ok = report.write(BASE / "arcadia-smoke-report.txt")
    print("ARCADIA SMOKE " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
