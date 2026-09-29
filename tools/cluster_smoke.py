# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""The real-infrastructure steps of the test procedure (D01 to D10, V06, V07), played by themselves on this machine.

Two benches share one MariaDB (XAMPP's, started here when it is not running, as tools/spark_cluster.py does):

  Release jar on a real NeoForge server: the Arcadia server pack's own NeoForge install (run/arcadia/server,
  extracted by tools/arcadia_smoke.py) linked into bare server folders that carry only CustomPerm, and Arcadia Lib
  when asked. Nothing from the dev runs, so the driver lookup meets a real classpath.
    D01 no JDBC driver: the server starts, cluster mode does not and says which mod to install.
    D02 Arcadia Lib brings the driver: the direct connection comes up and creates the five tables.
    D03 TLS: trust connects and the database counts the TLS handshake; verify is refused on the certificate,
        never on a driver property; verify connects once the authority is in the JVM truststore.
    D04 first boot of a three-member cluster on a virgin database: every member lists every probe.

  Two dev members, alpha and beta, joined directly (runClusterSmokeA/B), with smoke players online on both
  (gametest/cluster/SmokePlayers, over RCON):
    D06 an element limited to some members; D07 command, grade, player, each with the last word on one member;
    D08 a limit per member and one for a shared counter; D09 grade and player values across the cluster, kept over
    a restart; D10 an alias opened on one member for one grade; D05 a part a member does not share, and the
    per-server settings; then the admin client (runClusterSmokeAdmin) plays V06 and V07 on alpha.

Verdict in run/clustersmoke/cluster-smoke-report.txt, last line RESULT PASS or RESULT FAIL; exit code 0 or 1.
Usage: python tools/cluster_smoke.py [--only bare|dev] [--skip-tls] [--skip-client] [--mysql-bin DIR]
"""
import argparse
import json
import os
import secrets
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

from mc_harness import (GRADLEW, ROOT, Database, connect, kill, log_text, our_problems, wait_for, wait_for_line,
                        write_properties)

BASE = ROOT / "run" / "clustersmoke"
PACK = ROOT / "run" / "arcadia" / "server"
DATABASE = "customperm_smoke"
DB_USER = "customperm_smoke"
TABLES = ["customperm_log", "customperm_rows", "customperm_seq", "customperm_servers", "customperm_uses"]
DEV = {"a": {"name": "alpha", "port": 25640, "rcon": 25650}, "b": {"name": "beta", "port": 25641, "rcon": 25651}}
CONNECTED = "Cluster mode:"


class Report:
    def __init__(self):
        self.lines, self.checks, self.failures = [], 0, 0

    def check(self, what, passed, detail=""):
        self.checks += 1
        self.failures += 0 if passed else 1
        self.add(("PASS " if passed else "FAIL ") + what + (f" - {detail}" if detail else ""))
        return passed

    def add(self, line):
        self.lines.append(line)
        print(line, flush=True)

    def write(self, path):
        result = f"RESULT PASS {self.checks} checks" if self.failures == 0 else f"RESULT FAIL {self.failures} of {self.checks} checks"
        self.add(result)
        path.write_text("\n".join(self.lines) + "\n", encoding="utf-8")
        return self.failures == 0


def one_line(text, limit=300):
    return " | ".join(line.strip() for line in text.splitlines() if line.strip())[:limit]


# ---------------------------------------------------------------- database

class Bench:
    """The shared database and what every member needs to reach it."""

    def __init__(self, mysql_bin):
        self.db = Database(mysql_bin)
        self.password = secrets.token_hex(12)
        self.started_here = False
        self.tls_args = ()

    def up(self):
        if not self.db.up():
            self.db.start(open(BASE / "mariadb.log", "a", encoding="utf-8"))
            self.started_here = True
        self.db.sql(f"CREATE DATABASE IF NOT EXISTS {DATABASE}")
        self.db.sql(f"CREATE USER IF NOT EXISTS '{DB_USER}'@'localhost' IDENTIFIED BY '{self.password}'")
        self.db.sql(f"CREATE USER IF NOT EXISTS '{DB_USER}'@'127.0.0.1' IDENTIFIED BY '{self.password}'")
        for host in ("localhost", "127.0.0.1"):
            self.db.sql(f"ALTER USER '{DB_USER}'@'{host}' IDENTIFIED BY '{self.password}'")
            # The grants the README asks for, nothing more: CustomPerm issues no DROP and no ALTER.
            self.db.sql(f"GRANT CREATE, SELECT, INSERT, UPDATE, DELETE ON {DATABASE}.* TO '{DB_USER}'@'{host}'")

    def virgin(self):
        self.db.sql("DROP TABLE IF EXISTS " + ", ".join(TABLES), DATABASE)

    def tables(self):
        return sorted(self.db.sql("SHOW TABLES LIKE 'customperm_%'", DATABASE).split())

    def restart(self, extra=()):
        self.db.stop()
        self.db.start(open(BASE / "mariadb.log", "a", encoding="utf-8"), extra)

    def settings(self, name, tls="off", share=None):
        cluster = {"enabled": True, "connection": "direct", "serverName": name, "pollSeconds": 2,
                   "database": {"host": "127.0.0.1", "port": 3306, "name": DATABASE, "user": DB_USER,
                                "password": self.password, "tls": tls}}
        if share:
            cluster["share"] = share
        return json.dumps({"cluster": cluster}, indent=2)

    def close(self):
        if self.tls_args:
            self.restart()
        if self.started_here:
            self.db.stop()


# ---------------------------------------------------------------- members

def clean(folder, keep=()):
    for stale in ("world", "logs", "config", "crash-reports", "usercache.json", "usernamecache.json", "ops.json",
                  "bridge-request.txt", "bridge-response.txt"):
        if stale in keep:
            continue
        path = folder / stale
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()


def server_files(folder, port, rcon_port, password, settings):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    write_properties(folder, {
        "server-port": port, "server-ip": "127.0.0.1", "online-mode": "false", "enable-rcon": "true",
        "rcon.port": rcon_port, "rcon.password": password, "level-type": "minecraft\\:flat", "view-distance": 4,
        "simulation-distance": 4, "spawn-protection": 0, "gamemode": "creative", "difficulty": "peaceful",
        "spawn-monsters": "false", "motd": "CustomPerm cluster smoke"})
    config = folder / "config" / "arcadia" / "customperm"
    config.mkdir(parents=True, exist_ok=True)
    (config / "settings.json").write_text(settings, encoding="utf-8")


class Member:
    """One server, started either from the pack's NeoForge install (bare) or as a Gradle dev run."""

    def __init__(self, key, name, folder, port, rcon_port):
        self.key, self.name, self.folder = key, name, folder
        self.port, self.rcon_port = port, rcon_port
        self.password = secrets.token_hex(12)
        self.process = None
        self.rcon = None

    @property
    def log(self):
        return self.folder / "logs" / "latest.log"

    def started(self, seconds=600):
        return wait_for_line(self.log, "Done (", seconds, self.process)

    def console(self):
        if self.rcon is None:
            self.rcon = connect(self.rcon_port, self.password, 120)
        return self.rcon

    def run(self, command):
        return self.console().run(command)

    def stop(self):
        if self.process is not None and self.process.poll() is None:
            try:
                self.run("stop")
            except (OSError, IOError):
                pass  # the server may close RCON as it stops; what matters is that it saves and exits
            # Killed before it exits, a server loses what it writes on stop, the rate-limit history included.
            wait_for(lambda: "Stopping server" in log_text(self.log) and self.process.poll() is not None, 120, 2)
        if self.rcon is not None:
            self.rcon.close()
            self.rcon = None
        kill(self.process)


def bare_member(key, port, bench, jar, mods, tls="off", jvm=()):
    folder = BASE / f"bare-{key}"
    libraries = folder / "libraries"
    if libraries.exists():
        os.rmdir(libraries)  # the junction only, never what it points at
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True)
    subprocess.run(["cmd", "/c", "mklink", "/J", str(libraries), str(PACK / "libraries")], check=True, capture_output=True)
    (folder / "mods").mkdir()
    for mod in [jar, *mods]:
        shutil.copy2(mod, folder / "mods" / mod.name)
    member = Member(key, key, folder, port, port + 10)
    server_files(folder, port, port + 10, member.password, bench.settings(key, tls))
    args = next((PACK / "libraries" / "net" / "neoforged" / "neoforge").glob("*/win_args.txt")).relative_to(PACK)
    console = open(folder / "console.log", "w", encoding="utf-8")
    member.process = subprocess.Popen(["java", "-Xms1G", "-Xmx2G", *jvm, f"@{args}", "nogui"], cwd=folder,
                                      stdout=console, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    return member


def dev_member(key, bench, share=None, keep_world=False):
    spec = DEV[key]
    folder = BASE / f"dev-{key}"
    clean(folder, keep=("world",) if keep_world else ())
    member = Member(key, spec["name"], folder, spec["port"], spec["rcon"])
    server_files(folder, spec["port"], spec["rcon"], member.password, bench.settings(spec["name"], share=share))
    log = open(BASE / f"gradle-dev-{key}.log", "w", encoding="utf-8")
    member.process = subprocess.Popen([GRADLEW, f"runClusterSmoke{key.upper()}", "--console=plain"], cwd=ROOT,
                                      stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    return member


def connected(member, report, what):
    ok = wait_for_line(member.log, CONNECTED, 120, member.process) and "connected" in next(
        (l for l in log_text(member.log).splitlines() if CONNECTED in l), "")
    line = next((l for l in log_text(member.log).splitlines() if CONNECTED in l or "Admin alert CLUSTER" in l), "")
    report.check(what, ok, line.split("]: ", 1)[-1][:250])
    return ok


# ---------------------------------------------------------------- release jar on real NeoForge

def build_jar():
    subprocess.run([GRADLEW, "jar", "-q"], cwd=ROOT, check=True)
    jars = [j for j in (ROOT / "build" / "libs").glob("customperm-*.jar") if not j.name.endswith(("-sources.jar", "-javadoc.jar"))]
    return max(jars, key=lambda j: j.stat().st_mtime)


def bare_bench(report, bench, skip_tls):
    if not (PACK / "libraries").is_dir():
        report.check("the Arcadia pack's NeoForge install is there (python tools/arcadia_smoke.py extracts it)", False)
        return
    jar = build_jar()
    arcadia_lib = next((PACK / "mods").glob("arcadia-lib-*.jar"))
    report.add(f"INFO release jar {jar.name}, {arcadia_lib.name}, NeoForge from the pack")

    # D01
    member = bare_member("d01", 25660, bench, jar, [])
    try:
        booted = member.started()
        report.check("D01 with no JDBC driver the server still starts", booted)
        said = wait_for_line(member.log, "needs a JDBC driver", 60, member.process)
        line = next((l for l in log_text(member.log).splitlines() if "needs a JDBC driver" in l), "")
        report.check("D01 cluster mode stays off and names the missing driver and Arcadia Lib",
                     said and "Arcadia Lib" in line, line.split("]: ", 1)[-1][:250])
    finally:
        member.stop()

    # D02
    bench.virgin()
    member = bare_member("d02", 25661, bench, jar, [arcadia_lib])
    try:
        member.started()
        connected(member, report, "D02 Arcadia Lib provides the driver and the direct connection comes up")
        tables = bench.tables()
        report.check("D02 the five tables exist", tables == TABLES, ", ".join(tables))
    finally:
        member.stop()

    if not skip_tls:
        tls(report, bench, jar, arcadia_lib)

    # D04
    bench.virgin()
    members = [bare_member(k, 25663 + i, bench, jar, [arcadia_lib]) for i, k in enumerate(("d04a", "d04b", "d04c"))]
    try:
        up = all(m.started() for m in members) and all(wait_for_line(m.log, CONNECTED, 120, m.process) for m in members)
        report.check("D04 three members start together on a virgin database", up)
        if up:
            for m in members:
                m.run(f"customperm grade create probe_from_{m.key}")
            time.sleep(10)
            probes = {f"probe_from_{m.key}" for m in members}
            for m in members:
                seen = m.run("customperm log admin 100")
                missing = sorted(p for p in probes if p not in seen)
                report.check(f"D04 {m.key} lists every member's first action", not missing,
                             "missing " + ", ".join(missing) if missing else "all three")
            count = bench.db.sql("SELECT COUNT(*) FROM customperm_log", DATABASE).strip()
            report.add(f"INFO customperm_log rows after D04: {count}")
    finally:
        for m in members:
            m.stop()


def tls(report, bench, jar, arcadia_lib):
    openssl = shutil.which("openssl") or r"C:\Program Files\Git\usr\bin\openssl.exe"
    keytool = shutil.which("keytool")
    if not Path(openssl).is_file() or not keytool:
        report.check("D03 openssl and keytool are available", False, f"openssl {openssl}, keytool {keytool}")
        return
    folder = BASE / "tls"
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True)

    def ssl(*args):
        subprocess.run([openssl, *args], cwd=folder, check=True, capture_output=True)
    ssl("req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", "ca.key", "-out", "ca.pem", "-days", "2",
        "-subj", "/CN=CustomPerm smoke CA")
    ssl("req", "-newkey", "rsa:2048", "-nodes", "-keyout", "server.key", "-out", "server.csr", "-subj", "/CN=localhost")
    (folder / "san.ext").write_text("subjectAltName=DNS:localhost,IP:127.0.0.1\n", encoding="ascii")
    ssl("x509", "-req", "-in", "server.csr", "-CA", "ca.pem", "-CAkey", "ca.key", "-CAcreateserial", "-out", "server.pem",
        "-days", "2", "-extfile", "san.ext")
    bench.tls_args = (f"--ssl-ca={folder / 'ca.pem'}", f"--ssl-cert={folder / 'server.pem'}", f"--ssl-key={folder / 'server.key'}")
    bench.restart(bench.tls_args)

    def accepts():
        return int(bench.db.sql("SHOW GLOBAL STATUS LIKE 'Ssl_accepts'").split()[-1])

    before = accepts()
    member = bare_member("d03", 25662, bench, jar, [arcadia_lib], tls="trust")
    try:
        member.started()
        connected(member, report, "D03 tls trust connects")
        report.check("D03 the database counts the TLS handshakes", accepts() > before, f"Ssl_accepts {before} -> {accepts()}")
    finally:
        member.stop()

    member = bare_member("d03", 25662, bench, jar, [arcadia_lib], tls="verify")
    try:
        member.started()
        wait_for_line(member.log, "Admin alert CLUSTER", 120, member.process)
        lines = [l for l in log_text(member.log).splitlines() if "CLUSTER_UNAVAILABLE" in l or "certificate" in l.lower()
                 or "PKIX" in l or "sslMode" in l or "sslmode" in l]
        text = " || ".join(l.split("]: ", 1)[-1] for l in lines)[:400]
        refused = CONNECTED not in log_text(member.log) or "connected" not in next(
            (l for l in log_text(member.log).splitlines() if CONNECTED in l), "")
        report.check("D03 tls verify without the authority is refused", refused, text)
        report.check("D03 the refusal is about the certificate, not a driver property",
                     ("certif" in text.lower() or "PKIX" in text) and "sslmode" not in text.lower(), text)
    finally:
        member.stop()

    store, store_pass = folder / "trust.jks", secrets.token_hex(8)
    subprocess.run([keytool, "-importcert", "-noprompt", "-alias", "customperm-smoke", "-file", str(folder / "ca.pem"),
                    "-keystore", str(store), "-storepass", store_pass], check=True, capture_output=True)
    member = bare_member("d03", 25662, bench, jar, [arcadia_lib], tls="verify",
                         jvm=(f"-Djavax.net.ssl.trustStore={store}", f"-Djavax.net.ssl.trustStorePassword={store_pass}"))
    try:
        member.started()
        connected(member, report, "D03 tls verify connects once the authority is in the JVM truststore")
    finally:
        member.stop()
    bench.restart()
    bench.tls_args = ()


# ---------------------------------------------------------------- dev members

def can(member, player, command):
    return member.run(f"cptest can {player} {command}").strip().endswith("true")


def play(member, player, command):
    """What the player is told when typing the command; SmokePlayers runs it on the next tick."""
    member.run(f"cptest run {player} {command}")
    answer = ["(pending)"]
    wait_for(lambda: (answer.__setitem__(0, member.run(f"cptest result {player}")) or answer[0] != "(pending)"), 10, 0.1)
    return answer[0]


def uses(member, player, command, times):
    """How many of {times} runs were accepted, i.e. not refused by a rate limit."""
    return sum("Rate limit reached" not in play(member, player, command) for _ in range(times))


def shared(member, rule):
    """Whether the member's own ratelimits.json gives the rule the network scope."""
    path = member.folder / "config" / "arcadia" / "customperm" / "ratelimits.json"
    text = path.read_text(encoding="utf-8") if path.is_file() else ""
    start = text.find(f'"{rule}"')
    return start >= 0 and '"network"' in text[start:start + 600]


def settle():
    time.sleep(5)  # two polls of two seconds and a margin


def dev_bench(report, bench, skip_client):
    subprocess.run([GRADLEW, "classes", "gameTestClasses", "-q"], cwd=ROOT, check=True)
    bench.virgin()
    a, b = dev_member("a", bench), dev_member("b", bench)
    members = [a, b]
    try:
        up = a.started() and b.started()
        report.check("both dev members start", up)
        if not up:
            return
        if not (connected(a, report, "alpha joins the cluster") and connected(b, report, "beta joins the cluster")):
            return
        for m in members:
            for p in ("cs_p1", "cs_p2", "cs_p3"):
                m.run(f"cptest join {p}")
        a.run("customperm grade create cs_vip")
        a.run("customperm grade addperm cs_vip customperm.cs.node")
        a.run("customperm grade assign cs_p1 cs_vip")
        settle()

        element_on_some_members(report, a, b)
        last_word(report, a, b)
        limits_per_member(report, a, b)
        b = values_across_cluster(report, a, b, bench, members)
        alias_opened_by_grade(report, a, b)
        if not skip_client:
            admin_client(report, a, b, members)
        b = sharing_plan(report, a, b, bench, members)
    finally:
        for m in members:
            m.stop()
        for m in members:
            problems = our_problems(m.log)
            report.check(f"no ERROR line or exception naming CustomPerm on {m.name}", not problems,
                         " || ".join(problems[:3]) or "none")


def element_on_some_members(report, a, b):
    a.run("customperm command add tp")
    a.run("customperm user addperm cs_p1 customperm.command.tp")
    settle()
    report.check("D06 with no list, the node opens /tp on both members", can(a, "cs_p1", "tp") and can(b, "cs_p1", "tp"))
    a.run("customperm command servers tp here")
    settle()
    report.check("D06 limited to alpha: allowed there, refused on beta although the node is held",
                 can(a, "cs_p1", "tp") and not can(b, "cs_p1", "tp"))
    debug = b.run("customperm debug cs_p1 tp")
    report.check("D06 debug on beta names the server list as the reason", "alpha" in debug, one_line(debug))

    a.run("customperm alias add cs_alias say cs")
    a.run("customperm user addperm cs_p1 customperm.alias.cs_alias")
    a.run("customperm alias servers cs_alias here")
    a.run("customperm ratelimit set tp 1 3600")
    a.run("customperm ratelimit servers tp here")
    a.run("customperm command servers tp all")
    settle()
    report.check("D06 an alias limited to alpha is refused on beta", can(a, "cs_p1", "cs_alias") and not can(b, "cs_p1", "cs_alias"))
    report.check("D06 a limit active on alpha only counts there", uses(a, "cs_p1", "tp cs_p1 0 -60 0", 2) == 1
                 and uses(b, "cs_p1", "tp cs_p1 0 -60 0", 2) == 2)
    a.run("customperm alias servers cs_alias all")
    a.run("customperm ratelimit remove tp")
    settle()
    report.check("D06 after all, everything is back on both members", can(b, "cs_p1", "cs_alias") and can(b, "cs_p1", "tp"))


def last_word(report, a, b):
    a.run("customperm user removeperm cs_p1 customperm.command.tp")
    a.run("customperm grade addperm cs_vip customperm.command.tp")
    a.run("customperm grade adddeny cs_vip customperm.command.tp server=beta")
    settle()
    report.check("D07 a grade denied on beta: refused there, allowed on alpha", not can(b, "cs_p1", "tp") and can(a, "cs_p1", "tp"))
    a.run("customperm user addperm cs_p1 customperm.command.tp server=beta")
    settle()
    report.check("D07 the player's own entry on beta has the last word over the grade", can(b, "cs_p1", "tp"))
    a.run("customperm user removeperm cs_p1 customperm.command.tp server=beta")
    a.run("customperm grade removedeny cs_vip customperm.command.tp server=beta")
    a.run("customperm grade removeperm cs_vip customperm.command.tp")
    a.run("customperm command servers tp alpha")
    a.run("customperm grade addperm cs_vip customperm.command.tp server=beta")
    settle()
    report.check("D07 limited to alpha, a grade's entry on beta opens it there for that grade only",
                 can(b, "cs_p1", "tp") and not can(b, "cs_p2", "tp"))
    a.run("customperm grade removeperm cs_vip customperm.command.tp server=beta")
    a.run("customperm command servers tp all")


def limits_per_member(report, a, b):
    a.run("customperm alias add home say home")
    for p in ("cs_p1", "cs_p2", "cs_p3"):
        a.run(f"customperm user addperm {p} customperm.alias.home")
    a.run("customperm ratelimit set home 3 3600")
    a.run("customperm ratelimit server home beta 1 3600")
    settle()
    on_beta, on_alpha = uses(b, "cs_p2", "home", 2), uses(a, "cs_p2", "home", 4)
    report.check("D08 one use on beta, three on alpha", on_beta == 1 and on_alpha == 3, f"beta {on_beta}, alpha {on_alpha}")
    refused = a.run("customperm ratelimit scope home network")
    report.check("D08 sharing the counter is refused while beta has a limit of its own, naming beta",
                 "beta" in refused and not shared(a, "home"), one_line(refused))
    a.run("customperm ratelimit server home beta clear")
    accepted = a.run("customperm ratelimit scope home network")
    settle()
    report.check("D08 once cleared, the counter is shared", shared(a, "home") and shared(b, "home"), one_line(accepted))
    # A shared counter is read from the database at each poll: the other member sees the uses a poll later.
    first = uses(a, "cs_p3", "home", 2)
    settle()
    total = first + uses(b, "cs_p3", "home", 2)
    report.check("D08 one budget of three covers both members", total == 3, f"{total} accepted")


def values_across_cluster(report, a, b, bench, members):
    a.run("customperm ratelimit grade home cs_vip 10/1h")
    a.run("customperm ratelimit player home cs_p3 unlimited 2h")
    settle()
    first = uses(a, "cs_p1", "home", 6)
    settle()
    accepted = first + uses(b, "cs_p1", "home", 6)
    report.check("D09 the grade's ten cover both members, not ten each", accepted == 10, f"{accepted} of 12 accepted")
    first = uses(a, "cs_p3", "home", 6)
    settle()
    free = first + uses(b, "cs_p3", "home", 6)
    report.check("D09 the unlimited player is never refused", free == 12, f"{free} of 12 accepted")
    report.add("INFO before the restart, beta says: " + one_line(b.run("customperm ratelimit show home cs_p1")))
    b.stop()
    p1 = "2d4486b9-c406-32d4-a2a4-7ed5d874585c"  # offline UUID of cs_p1
    saved = b.folder / "world" / "data" / "customperm_ratelimits.json"
    history = json.loads(saved.read_text(encoding="utf-8")).get("history", {}) if saved.is_file() else {}
    report.add(f"INFO beta saved on stop: {{k: len(v.get(p1, [])) for k, v in history.items()}} = "
               + str({k: len(v.get(p1, [])) for k, v in history.items()}))
    report.add("INFO shared uses of cs_p1 by server: " + one_line(bench.db.sql(
        f"SELECT server, command, COUNT(*) FROM customperm_uses WHERE player = '{p1}' GROUP BY server, command", DATABASE)))
    members.remove(b)
    b = dev_member("b", bench, keep_world=True)
    members.append(b)
    b.started()
    connected(b, report, "D09 beta comes back after a restart")
    for p in ("cs_p1", "cs_p2", "cs_p3"):
        b.run(f"cptest join {p}")
    settle()
    shown = one_line(b.run("customperm ratelimit show home cs_p1"))
    report.check("D09 after the restart the grade's count is still there", uses(b, "cs_p1", "home", 1) == 0, shown)
    return b


def alias_opened_by_grade(report, a, b):
    a.run("customperm alias add cs_spawn say spawn")
    a.run("customperm alias servers cs_spawn alpha")
    a.run("customperm grade addperm cs_vip customperm.alias.cs_spawn server=beta")
    a.run("customperm alias add list say aliased list")
    a.run("customperm alias servers list alpha")
    settle()
    report.check("D10 on beta the grade opens the alias to its members only",
                 can(b, "cs_p1", "cs_spawn") and not can(b, "cs_p2", "cs_spawn"))
    out = play(b, "cs_p2", "list")
    report.check("D10 an alias named like a command leaves that command in place on beta", "aliased" not in out, one_line(out))


def sharing_plan(report, a, b, bench, members):
    b.stop()
    members.remove(b)
    b = dev_member("b", bench, share={"grades": True, "commands": True, "aliases": False, "rateLimits": True, "log": True},
                   keep_world=True)
    members.append(b)
    b.started()
    connected(b, report, "D05 beta restarts keeping its aliases local")
    a.run("customperm alias add cs_local say local")
    a.run("customperm names on")
    a.run("customperm command gateall true")
    a.run("customperm grade setdefault cs_vip")
    settle()
    aliases = b.run("customperm alias list")
    report.check("D05 an alias made on alpha does not reach beta, which keeps its aliases local", "cs_local" not in aliases,
                 one_line(aliases, 200))
    settings = json.loads((b.folder / "config" / "arcadia" / "customperm" / "settings.json").read_text(encoding="utf-8"))
    report.check("D05 default grade, gate all and name display stay per server",
                 settings.get("defaultGrade", "") != "cs_vip" and not settings.get("gateAllCommands", False)
                 and not settings.get("decorateNames", False),
                 f"beta: defaultGrade={settings.get('defaultGrade')!r}, gateAllCommands={settings.get('gateAllCommands')}, "
                 f"decorateNames={settings.get('decorateNames')}")
    a.run("customperm command gateall false")
    a.run("customperm grade cleardefault")
    return b


# ---------------------------------------------------------------- V06, V07 with a real client

def admin_client(report, a, b, members):
    a.run("customperm ratelimit set tp 5 3600")
    folder = BASE / "admin"
    for stale in ("bridge-request.txt", "bridge-response.txt", "smoke-report.txt"):
        (folder / stale).unlink(missing_ok=True)
    log = open(BASE / "gradle-admin.log", "w", encoding="utf-8")
    client = subprocess.Popen([GRADLEW, "runClusterSmokeAdmin", "--console=plain"], cwd=ROOT, stdout=log,
                              stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    stop = threading.Event()
    served = threading.Thread(target=serve_bridge, args=(folder, {"a": a, "b": b}, stop), daemon=True)
    served.start()
    try:
        joined = wait_for_line(a.log, "ClusterAdmin joined the game", 900, client)
        report.check("the admin client joins alpha", joined)
        if not joined:
            return
        a.run("op ClusterAdmin")
        a.run("customperm user addperm ClusterAdmin customperm.*")
        result = folder / "smoke-report.txt"
        wait_for(lambda: result.is_file() or client.poll() is not None, 1800, 5)
        lines = result.read_text(encoding="utf-8").splitlines() if result.is_file() else []
        for line in lines:
            if line.startswith(("PASS ", "FAIL ")):
                report.check(line[5:], line.startswith("PASS "))
        if not lines or not lines[-1].startswith("RESULT"):
            report.check("the admin client wrote its report", False, "see run/clustersmoke/admin/logs/latest.log")
    finally:
        stop.set()
        wait_for(lambda: client.poll() is not None, 60, 2)
        kill(client)
        a.run("customperm ratelimit remove tp")


def serve_bridge(folder, members, stop):
    """Answers the client's questions: 'rcon <a|b> <command>' or 'read <a|b> <path under the member>'."""
    request, response = folder / "bridge-request.txt", folder / "bridge-response.txt"
    last = None
    while not stop.is_set():
        try:
            text = request.read_text(encoding="utf-8") if request.is_file() else ""
        except OSError:
            text = ""
        if text and text != last and "\t" in text:
            last = text
            seq, question = text.split("\t", 1)
            kind, key, rest = (question.split(" ", 2) + ["", ""])[:3]
            try:
                member = members[key]
                if kind == "rcon":
                    answer = member.run(rest)
                else:
                    path = member.folder / rest
                    answer = path.read_text(encoding="utf-8") if path.is_file() else ""
            except Exception as e:  # an answer, never a dead bridge
                answer = f"bridge error: {e!r}"
            tmp = response.with_suffix(".tmp")
            tmp.write_text(f"{seq}\t{answer}", encoding="utf-8")
            os.replace(tmp, response)
        time.sleep(0.2)


# ---------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=["bare", "dev"])
    parser.add_argument("--skip-tls", action="store_true")
    parser.add_argument("--skip-client", action="store_true")
    parser.add_argument("--mysql-bin", default=r"C:\xampp\mysql\bin")
    args = parser.parse_args()
    BASE.mkdir(parents=True, exist_ok=True)
    report = Report()
    report.add("CustomPerm cluster mode on real servers and a real database")
    bench = Bench(args.mysql_bin)
    try:
        bench.up()
        if args.only in (None, "bare"):
            bare_bench(report, bench, args.skip_tls)
        if args.only in (None, "dev"):
            dev_bench(report, bench, args.skip_client)
    except Exception as e:  # the verdict must still be written
        report.check("the scenario ran to the end", False, repr(e))
    finally:
        try:
            bench.close()
        except Exception as e:
            report.add(f"INFO database left as it was: {e!r}")
    ok = report.write(BASE / "cluster-smoke-report.txt")
    print("CLUSTER SMOKE " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
