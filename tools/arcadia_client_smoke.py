# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""CustomPerm's admin interface inside the Arcadia client pack, joined to the Arcadia server pack.

The server is the pack as shipped (LuckPerms included) with the release jar, its database connection cut as in
tools/arcadia_smoke.py. The client is the CurseForge instance, read only: its mods are hard-linked into
run/arcadia/client (about 450, Sodium and Iris among them), its configs copied, the release jar added, and it runs on
the pack's NeoForge through tools/arcadia-harness, whose PackSmokeClient opens the interface with /customperm gui and
judges every page and view at four window sizes (V02, V03, the LuckPerms and Import pages included).

Verdict in run/arcadia/arcadia-client-report.txt, last line RESULT PASS or RESULT FAIL; exit code 0 or 1.
Usage: python tools/arcadia_client_smoke.py [--instance DIR] [--server-pack ZIP] [--xmx 8G]
"""
import argparse
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
from pathlib import Path

from arcadia_smoke import (BASE, DEFAULT_PACK, PORT, RCON_PORT, SERVER, Report, build_jar, extract, neoforge_args,
                           reset)
from mc_harness import GRADLEW, ROOT, connect, kill, log_text, our_problems, wait_for, wait_for_line

CLIENT = BASE / "client"
HARNESS = ROOT / "tools" / "arcadia-harness"
DEFAULT_INSTANCE = Path.home() / "curseforge" / "minecraft" / "Instances" / "Arcadia Echoes Of Power V2 (2)"
COPIED = ("kubejs", "config", "defaultconfigs", "datapacks", "resourcepacks", "moonlight-global-datapacks")
OPTIONS = {"onboardAccessibility": "false", "pauseOnLostFocus": "false", "tutorialStep": "none",
           "joinedFirstServer": "true", "skipMultiplayerWarning": "true", "soundCategory_master": "0.0",
           "lang": "en_us", "renderDistance": "4", "fullscreen": "false", "narrator": "0"}


def link_or_copy(source, target):
    try:
        os.link(source, target)
    except OSError:
        shutil.copy2(source, target)


def prepare_client(instance, jar):
    mods = CLIENT / "mods"
    if mods.exists():
        shutil.rmtree(mods)
    mods.mkdir(parents=True)
    kept = 0
    for mod in sorted((instance / "mods").glob("*.jar")):
        if mod.name.startswith("customperm-"):
            continue
        link_or_copy(mod, mods / mod.name)
        kept += 1
    shutil.copy2(jar, mods / jar.name)
    for folder in COPIED:
        if (CLIENT / folder).exists():
            shutil.rmtree(CLIENT / folder)
        if (instance / folder).is_dir():
            shutil.copytree(instance / folder, CLIENT / folder)
    # The client copy of Arcadia Lib's settings carries the same connection as the server's: point it nowhere.
    database = CLIENT / "config" / "arcadia" / "lib" / "database.toml"
    if database.is_file():
        text = database.read_text(encoding="utf-8")
        text = re.sub(r"(?m)^(\s*enabled\s*=\s*)\w+", r"\1false", text)
        text = re.sub(r'(?m)^(\s*host\s*=\s*)".*"', r'\1"127.0.0.1"', text)
        text = re.sub(r"(?m)^(\s*port\s*=\s*)\d+", r"\g<1>1", text)
        text = re.sub(r'(?m)^(\s*(user|password)\s*=\s*)".*"', r'\1""', text)
        database.write_text(text, encoding="utf-8")
    for stale in ("smoke-report.txt", "logs/latest.log", "screenshots"):
        path = CLIENT / stale
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
    source = instance / "options.txt"
    lines = source.read_text(encoding="utf-8", errors="replace").splitlines() if source.is_file() else []
    lines = [line for line in lines if line.split(":", 1)[0] not in OPTIONS]
    lines += [f"{key}:{value}" for key, value in OPTIONS.items()]
    (CLIENT / "options.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return kept


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    parser.add_argument("--server-pack", type=Path, default=DEFAULT_PACK)
    parser.add_argument("--xmx", default="8G")
    args = parser.parse_args()
    if not (args.instance / "mods").is_dir():
        sys.exit(f"Arcadia client instance not found: {args.instance} (pass --instance).")
    if not args.server_pack.is_file():
        sys.exit(f"Server pack not found: {args.server_pack} (pass --server-pack).")

    report = Report()
    report.add("CustomPerm's interface in the Arcadia client pack, on the Arcadia server pack")
    BASE.mkdir(parents=True, exist_ok=True)
    jar = build_jar()
    extract(args.server_pack)
    password = secrets.token_hex(16)
    reset(jar, password)
    kept = prepare_client(args.instance, jar)
    report.add(f"INFO {kept} client mods linked from {args.instance.name}, plus {jar.name}")
    subprocess.run([GRADLEW, "-p", str(HARNESS), "compileJava", "-q"], cwd=ROOT, check=True)

    log = SERVER / "logs" / "latest.log"
    console = open(BASE / "server-console.log", "w", encoding="utf-8")
    server = subprocess.Popen(["java", f"-Xms{args.xmx}", f"-Xmx{args.xmx}", "-XX:+UseG1GC", f"@{neoforge_args()}", "nogui"],
                              cwd=SERVER, stdout=console, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    client = None
    rcon = None
    try:
        booted = wait_for_line(log, "Done (", 1800, server)
        report.check("the Arcadia server pack boots with the release jar", booted)
        if not booted:
            return finish(report, server, client, rcon)
        wait_for_line(log, "backend=LuckPerms", 60, server)
        rcon = connect(RCON_PORT, password, 120)
        for command in ("customperm command add weather", "customperm ratelimit set weather 3 3600",
                        "customperm alias add pack_alias say pack"):
            rcon.run(command)

        client_log = open(BASE / "gradle-client.log", "w", encoding="utf-8")
        client = subprocess.Popen([GRADLEW, "-p", str(HARNESS), "runPackClient", "--console=plain"], cwd=ROOT,
                                  stdout=client_log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        started = time.time()
        # The pack replaces the "joined the game" broadcast; the player list's own line is always there.
        joined = wait_for_line(log, "PackAdmin[/127.0.0.1", 1800, client)
        report.check("the Arcadia client pack joins the server with CustomPerm on both sides", joined,
                     f"{time.time() - started:.0f} s after launch" if joined else "see run/arcadia/client/logs and gradle-client.log")
        if not joined:
            return finish(report, server, client, rcon)
        # An op given before the first join does not stick: give it now, then the nodes through LuckPerms.
        for command in ("op PackAdmin", "lp user PackAdmin permission set customperm.* true"):
            rcon.run(command)
            time.sleep(1)
        result = CLIENT / "smoke-report.txt"
        wait_for(lambda: result.is_file() or client.poll() is not None, 2400, 5)
        lines = result.read_text(encoding="utf-8").splitlines() if result.is_file() else []
        for line in lines:
            if line.startswith(("PASS ", "FAIL ")):
                report.check(line[5:], line.startswith("PASS "))
            elif line.startswith("INFO "):
                report.add(line)
        if not lines or not lines[-1].startswith("RESULT"):
            report.check("the pack client wrote its report", False, "see run/arcadia/client/logs/latest.log")
        wait_for(lambda: client.poll() is not None, 90, 3)
        rcon.run("stop")
        wait_for(lambda: server.poll() is not None, 180, 2)
    except Exception as e:  # the verdict must still be written
        report.check("the scenario ran to the end", False, repr(e))
    return finish(report, server, client, rcon)


def finish(report, server, client, rcon):
    if rcon is not None:
        rcon.close()
    kill(client)
    kill(server)
    # The pack's Crash Assistant starts a watcher beside the client that outlives it and opens a window when it
    # stops: end it too, found by the jar it runs from inside this client's folder.
    if os.name == "nt":
        subprocess.run(["powershell", "-NoProfile", "-Command",
                        "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like "
                        "'*crash_assistant*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"],
                       cwd=CLIENT, capture_output=True)
    for side, path in (("server", SERVER / "logs" / "latest.log"), ("client", CLIENT / "logs" / "latest.log")):
        problems = our_problems(path)
        report.check(f"no ERROR line or exception naming CustomPerm in the {side} log", not problems,
                     " || ".join(problems[:4]) or "none")
    report.check("no OutOfMemoryError on either side", "OutOfMemoryError" not in
                 log_text(SERVER / "logs" / "latest.log") + log_text(CLIENT / "logs" / "latest.log"))
    ok = report.write(BASE / "arcadia-client-report.txt")
    print("ARCADIA CLIENT SMOKE " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
