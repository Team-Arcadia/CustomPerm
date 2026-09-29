# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""What only a dedicated server and real clients show, played by itself.

A dedicated dev server with LuckPerms (gradle runSmokeServer) is joined in turn by:
  - a client without CustomPerm (runSmokeBare, V01): it must connect and play, not be refused for a missing mod;
  - an admin client with CustomPerm (runSmokeAdmin): once granted over RCON it draws the pages only LuckPerms opens
    at every window size and plays V09, the import selection (gametest/client/ServerSmokeClient).
The server log must hold no ERROR line or exception naming CustomPerm.

Verdict in run/serversmoke/server-smoke-report.txt (last line RESULT PASS or RESULT FAIL), exit code 0 or 1.
Usage: python tools/server_smoke.py [--skip-bare] [--skip-admin]
"""
import argparse
import secrets
import shutil
import subprocess
import sys
import time

from mc_harness import GRADLEW, ROOT, connect, kill, log_text, our_problems, wait_for, wait_for_line, write_properties

BASE = ROOT / "run" / "serversmoke"
SERVER = BASE / "server"
PORT, RCON_PORT = 25630, 25631


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


def prepare(password):
    for stale in ("world", "logs", "crash-reports", "usercache.json", "usernamecache.json", "ops.json", "config/arcadia/customperm",
                  "config/luckperms"):
        path = SERVER / stale
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
    SERVER.mkdir(parents=True, exist_ok=True)
    (SERVER / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    write_properties(SERVER, {
        "server-port": PORT, "server-ip": "127.0.0.1", "online-mode": "false", "enable-rcon": "true",
        "rcon.port": RCON_PORT, "rcon.password": password, "level-type": "minecraft\\:flat", "view-distance": 4,
        "simulation-distance": 4, "spawn-protection": 0, "motd": "CustomPerm server smoke",
        # A driven client cannot defend itself: the first run lost its admin to a slime.
        "max-tick-time": -1, "gamemode": "creative", "difficulty": "peaceful", "spawn-monsters": "false"})


def gradle(task, log_name):
    log = open(BASE / log_name, "w", encoding="utf-8")
    return subprocess.Popen([GRADLEW, task, "--console=plain"], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                            stdin=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-bare", action="store_true")
    parser.add_argument("--skip-admin", action="store_true")
    args = parser.parse_args()

    BASE.mkdir(parents=True, exist_ok=True)
    report = Report()
    report.add("CustomPerm on a dedicated server with LuckPerms, joined by real clients")
    subprocess.run([GRADLEW, "classes", "gameTestClasses", "-q"], cwd=ROOT, check=True)
    password = secrets.token_hex(16)
    prepare(password)

    log = SERVER / "logs" / "latest.log"
    server = gradle("runSmokeServer", "gradle-server.log")
    clients = []
    rcon = None
    try:
        booted = wait_for_line(log, "Done (", 600, server)
        report.check("the dedicated server starts with LuckPerms and CustomPerm", booted)
        if not booted:
            return finish(report, server, clients, rcon)
        report.check("CustomPerm runs on the LuckPerms backend", wait_for_line(log, "backend=LuckPerms", 60, server))
        rcon = connect(RCON_PORT, password, 120)
        for command in ("lp creategroup smoke_a", "lp creategroup smoke_b",
                        "lp group smoke_a permission set smoke.a.node true",
                        "lp group smoke_b permission set smoke.b.node true"):
            rcon.run(command)
            time.sleep(1)

        if not args.skip_bare:
            bare(report, rcon, log, clients)
        if not args.skip_admin:
            admin(report, rcon, log, clients)

        rcon.run("stop")
        report.check("the server stops cleanly", wait_for(lambda: "Stopping server" in log_text(log), 120, 2))
    except Exception as e:  # the verdict must still be written
        report.check("the scenario ran to the end", False, repr(e))
    return finish(report, server, clients, rcon)


def bare(report, rcon, log, clients):
    """V01: a client with no CustomPerm connects and plays."""
    client = gradle("runSmokeBare", "gradle-bare.log")
    clients.append(client)
    joined = wait_for_line(log, "BareClient joined the game", 900, client)
    report.check("V01 a client without CustomPerm joins the server", joined,
                 "" if joined else "see run/serversmoke/bare/logs/latest.log and gradle-bare.log")
    if joined:
        time.sleep(10)
        text = log_text(log)
        still = "BareClient" in rcon.run("list")
        report.check("V01 it stays connected and plays", still and "BareClient lost connection" not in text,
                     "listed by /list" if still else "gone from /list")
        rcon.run("kick BareClient smoke done")
    kill(client)


def admin(report, rcon, log, clients):
    """The admin client plays the LuckPerms pages and V09 once granted."""
    client = gradle("runSmokeAdmin", "gradle-admin.log")
    clients.append(client)
    joined = wait_for_line(log, "SmokeAdmin joined the game", 900, client)
    report.check("the admin client joins the server", joined)
    if not joined:
        kill(client)
        return
    # An op given before the first join does not stick: give it now, then the nodes through LuckPerms.
    for command in ("op SmokeAdmin", "lp user SmokeAdmin permission set customperm.* true"):
        rcon.run(command)
        time.sleep(1)
    result = BASE / "admin" / "smoke-report.txt"
    wait_for(lambda: result.is_file() or client.poll() is not None, 1800, 5)
    lines = result.read_text(encoding="utf-8").splitlines() if result.is_file() else []
    for line in lines:
        if line.startswith(("PASS ", "FAIL ")):
            report.check(line[5:], line.startswith("PASS "))
        elif line.startswith("INFO "):
            report.add(line)
    if not lines or not lines[-1].startswith("RESULT"):
        report.check("the admin client wrote its report", False, "see run/serversmoke/admin/logs/latest.log")
    wait_for(lambda: client.poll() is not None, 60, 2)
    kill(client)


def finish(report, server, clients, rcon):
    if rcon is not None:
        rcon.close()
    for client in clients:
        kill(client)
    wait_for(lambda: server.poll() is not None, 60, 2)
    kill(server)
    problems = our_problems(SERVER / "logs" / "latest.log")
    report.check("no ERROR line or exception naming CustomPerm in the server log", not problems,
                 " || ".join(problems[:5]) or "none")
    ok = report.write(BASE / "server-smoke-report.txt")
    print("SERVER SMOKE " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
