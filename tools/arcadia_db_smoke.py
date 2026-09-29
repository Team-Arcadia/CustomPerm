# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""The Arcadia server pack on a real database, through Arcadia Lib, as a production server would run it.

The pack's own connection is never used: every member's config/arcadia/lib/database.toml is rewritten to a throwaway
database on the local MariaDB (XAMPP, started when it is not running), and a member refuses to boot if any of its
config files still names the host the pack ships. Two variants:

  A. The pack as shipped, LuckPerms included, one server. Arcadia Lib and the Arcadia mods connect for real;
     CustomPerm, with cluster mode on, must say LuckPerms decides and run alone, with no error.
  B. The pack without LuckPerms, two servers joined through Arcadia Lib (cluster.connection = arcadia): the parts
     propagate both ways, the shared log shows the other member's actions, a change is refused while the database
     is down and accepted again once it is back, and the logs hold no CustomPerm error.

Verdict in run/arcadia-db/arcadia-db-report.txt, last line RESULT PASS or RESULT FAIL; exit code 0 or 1.
Usage: python tools/arcadia_db_smoke.py [--only a|b] [--xmx 6G] [--mysql-bin DIR] [--server-pack ZIP]
"""
import argparse
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

from arcadia_smoke import DEFAULT_PACK, SERVER, Report, build_jar, extract, neoforge_args
from mc_harness import (ROOT, Database, connect, kill, log_text, our_problems, wait_for, wait_for_line,
                        write_properties)

BASE = ROOT / "run" / "arcadia-db"
DATABASE = "arcadia_smoke"
DB_USER = "arcadia_smoke"
CUSTOMPERM_TABLES = ["customperm_log", "customperm_rows", "customperm_seq", "customperm_servers", "customperm_uses"]
# Folders of the pack a server reads at boot; the rest (worlds, logs, caches) is per member.
COPIED = ("config", "defaultconfigs", "kubejs", "emotes", "easy_npc", "local", "moonlight-global-datapacks",
          "patchouli_books")


def production_host(pack):
    """The database host the pack ships, read to be refused, never printed."""
    with zipfile.ZipFile(pack) as zf:
        text = zf.read("config/arcadia/lib/database.toml").decode("utf-8", "replace")
    found = re.search(r'(?m)^\s*host\s*=\s*"([^"]*)"', text)
    host = found.group(1).strip() if found else ""
    return "" if host in ("", "localhost", "127.0.0.1") else host


class Member:
    def __init__(self, key, server_id, port, keep_luckperms):
        self.key, self.server_id, self.port, self.rcon_port = key, server_id, port, port + 10
        self.keep_luckperms = keep_luckperms
        self.folder = BASE / key
        self.password = secrets.token_hex(12)
        self.process = None
        self.rcon = None

    @property
    def log(self):
        return self.folder / "logs" / "latest.log"

    def build(self, jar, db_password, forbidden_host):
        libraries = self.folder / "libraries"
        if libraries.exists():
            os.rmdir(libraries)  # the junction only
        if self.folder.exists():
            shutil.rmtree(self.folder)
        self.folder.mkdir(parents=True)
        subprocess.run(["cmd", "/c", "mklink", "/J", str(libraries), str(SERVER / "libraries")], check=True, capture_output=True)
        mods = self.folder / "mods"
        mods.mkdir()
        self.dropped = []
        for mod in (SERVER / "mods").glob("*.jar"):
            if mod.name.startswith("customperm-"):
                continue
            if not self.keep_luckperms and (mod.name.lower().startswith("luckperms") or needs_luckperms(mod)):
                if not mod.name.lower().startswith("luckperms"):
                    self.dropped.append(mod.name)
                continue
            try:
                os.link(mod, mods / mod.name)
            except OSError:
                shutil.copy2(mod, mods / mod.name)
        shutil.copy2(jar, mods / jar.name)
        for name in COPIED:
            if (SERVER / name).is_dir():
                shutil.copytree(SERVER / name, self.folder / name)
        for stale in ("config/arcadia/customperm", "config/luckperms"):
            path = self.folder / stale
            if path.is_dir():
                shutil.rmtree(path)
        lib = self.folder / "config" / "arcadia" / "lib"
        (lib / "database.toml").write_text("\n".join([
            "[database]", "\tenabled = true", '\thost = "127.0.0.1"', "\tport = 3306", f'\tname = "{DATABASE}"',
            f'\tuser = "{DB_USER}"', f'\tpassword = "{db_password}"', "\tmax_pool_size = 6", ""]), encoding="utf-8")
        (lib / "server.toml").write_text(f'[server]\n\tserver_id = "{self.server_id}"\n', encoding="utf-8")
        customperm = self.folder / "config" / "arcadia" / "customperm"
        customperm.mkdir(parents=True)
        (customperm / "settings.json").write_text(json.dumps(
            {"configVersion": 1, "cluster": {"enabled": True, "connection": "arcadia", "pollSeconds": 2}}, indent=2),
            encoding="utf-8")
        # Voice chat binds its own UDP port: -1 takes the game port, so two members on one machine do not collide.
        voice = self.folder / "config" / "voicechat" / "voicechat-server.properties"
        if voice.is_file():
            voice.write_text(re.sub(r"(?m)^port=.*$", "port=-1", voice.read_text(encoding="utf-8")), encoding="utf-8")
        (self.folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        write_properties(self.folder, {
            "server-port": self.port, "server-ip": "127.0.0.1", "online-mode": "false", "enable-rcon": "true",
            "rcon.port": self.rcon_port, "rcon.password": self.password, "view-distance": 4, "simulation-distance": 4,
            "spawn-protection": 0, "max-tick-time": -1, "gamemode": "creative", "difficulty": "peaceful", "level-seed": "customperm",
            "motd": f"CustomPerm Arcadia DB smoke {self.key}"})
        if forbidden_host:
            for path in list((self.folder / "config").rglob("*")) + list((self.folder / "defaultconfigs").rglob("*")):
                if path.is_file() and path.suffix in (".toml", ".json", ".json5", ".properties", ".cfg", ".yml", ".yaml"):
                    if forbidden_host in path.read_text(encoding="utf-8", errors="ignore"):
                        sys.exit(f"{path} still names the pack's production database host; refusing to boot.")

    def start(self, xmx):
        console = open(self.folder / "console.log", "w", encoding="utf-8")
        self.process = subprocess.Popen(["java", f"-Xms{xmx}", f"-Xmx{xmx}", "-XX:+UseG1GC", f"@{neoforge_args()}", "nogui"],
                                        cwd=self.folder, stdout=console, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)

    def run(self, command):
        if self.rcon is None:
            self.rcon = connect(self.rcon_port, self.password, 180)
        return self.rcon.run(command)

    def stop(self):
        if self.process is not None and self.process.poll() is None:
            try:
                self.run("stop")
            except (OSError, IOError):
                pass
            wait_for(lambda: self.process.poll() is not None, 240, 2)
        if self.rcon is not None:
            self.rcon.close()
            self.rcon = None
        kill(self.process)


def needs_luckperms(jar):
    """Whether a mod refuses to load without LuckPerms: a required dependency on it in its neoforge.mods.toml."""
    try:
        with zipfile.ZipFile(jar) as zf:
            toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
    except (KeyError, zipfile.BadZipFile, OSError):
        return False
    for block in re.split(r"\[\[dependencies\.", toml)[1:]:
        if re.search(r'modId\s*=\s*"luckperms"', block) and (re.search(r'type\s*=\s*"required"', block)
                                                            or re.search(r"mandatory\s*=\s*true", block)):
            return True
    return False


def one_line(text, limit=300):
    return " | ".join(line.strip() for line in text.splitlines() if line.strip())[:limit]


def log_lines(member, needle):
    return [l.split("]: ", 1)[-1] for l in log_text(member.log).splitlines() if needle in l]


def exists(member, grade):
    return "does not exist" not in member.run(f"customperm grade list {grade}")


def arcadia_connections(db):
    out = db.sql(f"SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER = '{DB_USER}'")
    return int(out.split()[0]) if out.split() else 0


# ---------------------------------------------------------------- variants

def variant_a(report, db, jar, db_password, forbidden, xmx):
    report.add("Variant A: the pack as shipped, LuckPerms included, on the local database")
    member = Member("a-luckperms", "arc_lp", 25680, keep_luckperms=True)
    member.build(jar, db_password, forbidden)
    member.start(xmx)
    try:
        booted = wait_for_line(member.log, "Done (", 1800, member.process)
        report.check("A the pack boots on the local database", booted)
        if not booted:
            return
        wait_for_line(member.log, "backend=LuckPerms", 60, member.process)
        loaded = log_lines(member, "[ArcadiaLib] Database config loaded")
        report.check("A Arcadia Lib reads the local database, not the pack's", any(f"db: {DATABASE}" in l and "127.0.0.1" in l for l in loaded),
                     "; ".join(loaded)[:250])
        disabled = log_lines(member, "Database disabled in config")
        report.check("A no Arcadia mod falls back to memory", not disabled, "; ".join(disabled)[:250] or "none")
        report.check("A Arcadia Lib holds connections to the database", arcadia_connections(db) > 0,
                     f"{arcadia_connections(db)} connection(s) of {DB_USER}")
        said = wait_for_line(member.log, "LuckPerms decides permissions and already shares its storage", 60, member.process)
        report.check("A CustomPerm's cluster mode stands down and says LuckPerms decides", said,
                     next(iter(log_lines(member, "LuckPerms decides permissions and already shares")), "")[:250])
        status = member.run("customperm status")
        report.check("A customperm status answers on the LuckPerms backend", "LuckPerms" in status, one_line(status))
        for command, expect in (("customperm command add weather", "weather"), ("customperm alias add arc_alias say arc", "arc_alias"),
                                ("customperm ratelimit set weather 3 3600", "weather"), ("customperm reload", "reloaded")):
            out = member.run(command)
            report.check(f"A /{command}", expect in out, one_line(out, 200))
        tables = db.sql("SHOW TABLES", DATABASE).split()
        others = [t for t in tables if not t.startswith("customperm_")]
        report.check("A no CustomPerm table while LuckPerms decides", not [t for t in tables if t.startswith("customperm_")],
                     ", ".join(t for t in tables if t.startswith("customperm_")) or "none")
        report.add(f"INFO tables the Arcadia mods created: {len(others)} ({', '.join(others[:12])}{'...' if len(others) > 12 else ''})")
    finally:
        member.stop()
        judge_log(report, member, "A")


def variant_b(report, db, jar, db_password, forbidden, xmx):
    report.add("Variant B: the pack without LuckPerms, two members joined through Arcadia Lib")
    db.sql("DROP TABLE IF EXISTS " + ", ".join(CUSTOMPERM_TABLES), DATABASE)
    alpha = Member("b-alpha", "arc_alpha", 25681, keep_luckperms=False)
    beta = Member("b-beta", "arc_beta", 25682, keep_luckperms=False)
    members = [alpha, beta]
    for m in members:
        m.build(jar, db_password, forbidden)
    report.add("INFO also left out, as they require LuckPerms: " + (", ".join(alpha.dropped) or "none"))
    outage_at = None
    try:
        # One after the other: two packs of 400 mods loading at once take the machine's memory at its peak.
        booted = True
        for m in members:
            m.start(xmx)
            booted = booted and wait_for_line(m.log, "Done (", 1800, m.process)
            time.sleep(20)
        report.check("B both pack servers boot without LuckPerms", booted)
        if not booted:
            return
        for m in members:
            ok = wait_for_line(m.log, "Cluster mode:", 120, m.process) and any("connected" in l for l in log_lines(m, "Cluster mode:"))
            report.check(f"B {m.server_id} joins the cluster through Arcadia Lib", ok,
                         (log_lines(m, "Cluster mode") or log_lines(m, "CLUSTER_UNAVAILABLE") or [""])[0][:250])
            if not ok:
                return
        tables = sorted(t for t in db.sql("SHOW TABLES", DATABASE).split() if t.startswith("customperm_"))
        report.check("B the five CustomPerm tables live beside the Arcadia mods' tables", tables == CUSTOMPERM_TABLES, ", ".join(tables))

        alpha.run("customperm grade create arc_probe")
        alpha.run("customperm grade addperm arc_probe arc.probe.node")
        alpha.run("customperm command add weather")
        alpha.run("customperm alias add arc_alias say arc")
        alpha.run("customperm ratelimit set weather 3 3600")
        started = time.time()
        grade = [""]

        def on_beta():
            grade[0] = beta.run("customperm grade list arc_probe")
            return "arc.probe.node" in grade[0]
        seen = wait_for(on_beta, 30, 1)
        report.check("B a grade made on alpha reaches beta", seen, f"after {time.time() - started:.0f} s: {one_line(grade[0], 150)}")
        report.check("B an alias made on alpha reaches beta", "arc_alias" in beta.run("customperm alias list"))
        report.check("B an exposed command and its limit reach beta", "3" in beta.run("customperm ratelimit show weather"),
                     one_line(beta.run("customperm ratelimit show weather"), 150))
        beta.run("customperm grade create arc_from_beta")
        back = wait_for(lambda: exists(alpha, "arc_from_beta"), 30, 1)
        report.check("B a grade made on beta reaches alpha", back)
        time.sleep(6)
        shared = beta.run("customperm log admin 100")
        report.check("B beta's shared log lists alpha's actions", "arc_probe" in shared, one_line(shared, 200))

        report.add("INFO stopping the database")
        outage_at = time.time()
        db.stop()
        time.sleep(15)
        refused = alpha.run("customperm grade create arc_during")
        report.check("B with the database down a change is refused and says so", not exists(alpha, "arc_during"),
                     one_line(refused, 200))
        report.check("B beta keeps serving what it last read", "arc.probe.node" in beta.run("customperm grade list arc_probe"))
        db.start(open(BASE / "mariadb.log", "a", encoding="utf-8"))
        report.add("INFO database back")
        after = [""]

        def retry():
            after[0] = alpha.run("customperm grade create arc_after")
            return exists(alpha, "arc_after")
        accepted = wait_for(retry, 120, 5)
        report.check("B once the database is back a change is accepted again", accepted, one_line(after[0], 200))
        reached = wait_for(lambda: exists(beta, "arc_after"), 30, 1)
        report.check("B and it reaches beta", reached)
        cleared = wait_for(lambda: any("resolved" in l for l in log_lines(alpha, "Admin alert")[-6:]), 60, 2)
        report.check("B the alert clears on its own", cleared, "; ".join(log_lines(alpha, "Admin alert")[-3:])[:250])
    finally:
        if not db.up():
            db.start(open(BASE / "mariadb.log", "a", encoding="utf-8"))
        for m in members:
            m.stop()
        for m in members:
            judge_log(report, m, "B", outage_at)


def judge_log(report, member, variant, outage_at=None):
    # While the database is down, saying so is the job: those lines are the expected alert, not a fault.
    allowed = ("database", "Database", "store", "unreachable", "Communications link", "Connection")
    problems = [p for p in our_problems(member.log) if outage_at is None or not any(a in p for a in allowed)]
    report.check(f"{variant} no ERROR line or exception naming CustomPerm on {member.server_id}", not problems,
                 " || ".join(problems[:3]) or "none")
    report.check(f"{variant} no OutOfMemoryError on {member.server_id}", "OutOfMemoryError" not in log_text(member.log))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=["a", "b"])
    parser.add_argument("--xmx", default="6G")
    parser.add_argument("--mysql-bin", default=r"C:\xampp\mysql\bin")
    parser.add_argument("--server-pack", type=Path, default=DEFAULT_PACK)
    args = parser.parse_args()
    if not args.server_pack.is_file():
        sys.exit(f"Server pack not found: {args.server_pack}")
    BASE.mkdir(parents=True, exist_ok=True)
    report = Report()
    report.add("CustomPerm in the Arcadia server pack, on a local database through Arcadia Lib")
    extract(args.server_pack)
    forbidden = production_host(args.server_pack)
    jar = build_jar()
    db = Database(args.mysql_bin)
    started_here = False
    db_password = secrets.token_hex(12)
    try:
        if not db.up():
            db.start(open(BASE / "mariadb.log", "a", encoding="utf-8"))
            started_here = True
        db.sql(f"DROP DATABASE IF EXISTS {DATABASE}")
        db.sql(f"CREATE DATABASE {DATABASE}")
        for host in ("localhost", "127.0.0.1"):
            db.sql(f"CREATE USER IF NOT EXISTS '{DB_USER}'@'{host}' IDENTIFIED BY '{db_password}'")
            db.sql(f"ALTER USER '{DB_USER}'@'{host}' IDENTIFIED BY '{db_password}'")
            # The Arcadia mods create and change their own tables: all rights, on this throwaway database only.
            db.sql(f"GRANT ALL PRIVILEGES ON {DATABASE}.* TO '{DB_USER}'@'{host}'")
        if args.only in (None, "a"):
            variant_a(report, db, jar, db_password, forbidden, args.xmx)
        if args.only in (None, "b"):
            variant_b(report, db, jar, db_password, forbidden, args.xmx)
    except Exception as e:  # the verdict must still be written
        report.check("the scenario ran to the end", False, repr(e))
    finally:
        try:
            if started_here and db.up():
                db.stop()
        except Exception as e:
            report.add(f"INFO database left as it was: {e!r}")
    ok = report.write(BASE / "arcadia-db-report.txt")
    print("ARCADIA DB SMOKE " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
