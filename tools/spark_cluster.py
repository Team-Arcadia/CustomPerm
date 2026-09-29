# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""Spark scenario S04 of the test procedure, played by itself: a cluster member over an hour.

Two dedicated servers (gradle runSparkClusterA / runSparkClusterB) share one MariaDB through Arcadia Lib. The
script changes grades, aliases, rate limits and exposed commands on both in turn, stops the database for a while
in the middle (D06), and judges the result from spark's saved files and the ClusterProbe log lines:
GapReader pending back to zero, SqlStore and connection objects stable, no thread growth after the outage, and no
rising allocation from the poller. Nothing is uploaded; the verdict goes to run/spark-cluster/spark-cluster-report.txt.

Needs: spark in run/spark/mods (copied into both members), and MariaDB or MySQL binaries (XAMPP by default,
started here when not running). Usage: python tools/spark_cluster.py [--minutes 60] [--mysql-bin DIR]
"""
import argparse
import os
import secrets
import shutil
import subprocess
import sys
import time
from pathlib import Path

from mc_harness import (Database, connect, heap, kill, profile_ms_per_tick, wait_for)

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "run" / "spark-cluster"
GRADLEW = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
MEMBERS = {"a": {"port": 25601, "rcon": 25611, "id": "spark_alpha"},
           "b": {"port": 25602, "rcon": 25612, "id": "spark_beta"}}
DATABASE = "customperm_spark"
DB_USER = "customperm_spark"
PROBE = "[spark-cluster]"


# ---------------------------------------------------------------- members

def prepare(member, settings, password, db_password):
    folder = BASE / member
    for stale in ("world", "logs", "config", "crash-reports", "usercache.json", "usernamecache.json"):
        path = folder / stale
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()
    (folder / "mods").mkdir(parents=True, exist_ok=True)
    sparks = sorted((ROOT / "run" / "spark" / "mods").glob("spark-*.jar"))
    if not sparks:
        sys.exit("Put the spark mod jar in run/spark/mods first; it is never part of the build.")
    for old in (folder / "mods").glob("spark-*.jar"):
        old.unlink()
    shutil.copy2(sparks[-1], folder / "mods")
    (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (folder / "server.properties").write_text("\n".join([
        f"server-port={settings['port']}", "server-ip=127.0.0.1", "online-mode=false", "enable-rcon=true",
        f"rcon.port={settings['rcon']}", f"rcon.password={password}", "level-type=minecraft\\:flat",
        "view-distance=4", "simulation-distance=4", "spawn-protection=0", "motd=CustomPerm spark S04", ""]),
        encoding="utf-8")
    config = folder / "config" / "arcadia" / "customperm"
    config.mkdir(parents=True)
    (config / "settings.json").write_text(
        '{\n  "cluster": { "enabled": true, "connection": "arcadia", "pollSeconds": 2 }\n}\n', encoding="utf-8")
    arcadia = folder / "world" / "serverconfig" / "arcadia" / "lib"
    arcadia.mkdir(parents=True)
    (arcadia / "database.toml").write_text("\n".join([
        "[database]", "\tenabled = true", '\thost = "127.0.0.1"', "\tport = 3306", f'\tname = "{DATABASE}"',
        f'\tuser = "{DB_USER}"', f'\tpassword = "{db_password}"', "\tmax_pool_size = 4", ""]), encoding="utf-8")
    (arcadia / "server.toml").write_text(f'[server]\n\tserver_id = "{settings["id"]}"\n', encoding="utf-8")


def spark_files(member, extension):
    folder = BASE / member / "config" / "spark"
    return set(folder.glob(f"*{extension}")) if folder.is_dir() else set()


def spark_save(rcon, member, command, extension):
    before = spark_files(member, extension)
    rcon.run(command)
    last = -1

    def saved():
        nonlocal last
        new = spark_files(member, extension) - before
        if not new:
            return False
        size = next(iter(new)).stat().st_size
        stable = size > 0 and size == last
        last = size
        return stable
    if not wait_for(saved, 300, step=2):
        raise RuntimeError(f"spark did not save {command} on {member}")
    return next(iter(spark_files(member, extension) - before))


def probes(member):
    log = BASE / member / "logs" / "latest.log"
    lines = []
    if log.is_file():
        for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
            if PROBE in line:
                values = dict(part.split("=", 1) for part in line.split(PROBE, 1)[1].split())
                lines.append({k: int(v) for k, v in values.items()})
    return lines


# ---------------------------------------------------------------- use

def use(step, consoles, notes):
    """One administrative change, alternating members; every object made is removed a few steps later."""
    member = "a" if step % 2 == 0 else "b"
    n = step // 8
    commands = [
        f"customperm grade create spark_g{n}",
        f"customperm grade addperm spark_g{n} spark.node.{n}",
        f"customperm alias add spark_a{n} say spark {n}",
        f"customperm ratelimit set seed 3 60",
        f"customperm command add seed",
        f"customperm alias remove spark_a{n}",
        f"customperm grade delete spark_g{n}",
        f"customperm ratelimit remove seed",
    ]
    command = commands[step % len(commands)]
    try:
        reply = consoles[member].run(command)
    except (OSError, IOError) as e:
        reply = f"rcon error {e}"
    if step % 40 == 0:
        notes.append(f"  {member}: {command} -> {reply.splitlines()[0][:120] if reply else ''}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--minutes", type=int, default=60)
    parser.add_argument("--outage-at", type=float, default=0.4, help="fraction of the run when the database stops")
    parser.add_argument("--outage-seconds", type=int, default=120)
    parser.add_argument("--mysql-bin", default="C:/xampp/mysql/bin")
    parser.add_argument("--root-password", default=os.environ.get("CUSTOMPERM_SPARK_DB_ROOT_PASSWORD", ""))
    args = parser.parse_args()

    BASE.mkdir(parents=True, exist_ok=True)
    report, notes, failures = [], [], []
    password, db_password = secrets.token_hex(12), secrets.token_hex(12)
    logs = {name: open(BASE / f"{name}.log", "w", encoding="utf-8") for name in ("mariadb", "gradle-a", "gradle-b")}
    db = Database(args.mysql_bin, args.root_password)
    processes, consoles = [], {}
    try:
        if subprocess.run([GRADLEW, "gameTestClasses", "--console=plain", "-q"], cwd=ROOT).returncode != 0:
            sys.exit("compilation failed")
        # A database that was already running stays running at the end; one started here is stopped again.
        if not db.up():
            db.start(logs["mariadb"])
            db.started_here = True
        # Both host spellings: an anonymous account of a default install can otherwise shadow one of them.
        users = "".join(f"DROP USER IF EXISTS '{DB_USER}'@'{h}'; CREATE USER '{DB_USER}'@'{h}' IDENTIFIED BY '{db_password}'; "
                        f"GRANT ALL ON {DATABASE}.* TO '{DB_USER}'@'{h}'; " for h in ("localhost", "127.0.0.1"))
        db.sql(f"DROP DATABASE IF EXISTS {DATABASE}; CREATE DATABASE {DATABASE}; {users}FLUSH PRIVILEGES;")
        for member, settings in MEMBERS.items():
            prepare(member, settings, password, db_password)
        for member, settings in MEMBERS.items():
            processes.append(subprocess.Popen([GRADLEW, f"runSparkCluster{member.upper()}", "--console=plain"], cwd=ROOT,
                                              stdout=logs[f"gradle-{member}"], stderr=subprocess.STDOUT))
            consoles[member] = connect(settings["rcon"], password, 600)
        time.sleep(30)

        heaps, profiles = {}, {}
        for member, console in consoles.items():
            heaps[(member, "start")] = spark_save(console, member, "spark heapsummary --save-to-file", ".sparkheap")
            console.run("spark profiler start")
        start_probes = {m: len(probes(m)) for m in MEMBERS}

        steps = args.minutes * 60 // 5
        outage_step = int(steps * args.outage_at)
        outage_until = None
        outage_probes = {}
        for step in range(steps):
            if step == outage_step:
                notes.append(f"  database stopped at step {step}")
                outage_probes = {m: len(probes(m)) for m in MEMBERS}
                db.stop()
                outage_until = time.time() + args.outage_seconds
            if outage_until and time.time() >= outage_until:
                db.start(logs["mariadb"])
                notes.append(f"  database back at step {step}")
                outage_probes = {m: (outage_probes[m], len(probes(m))) for m in MEMBERS}
                outage_until = None
            use(step, consoles, notes)
            time.sleep(5)
        time.sleep(90)  # let both members settle and the probe report twice

        for member, console in consoles.items():
            profiles[member] = spark_save(console, member, "spark profiler stop --save-to-file", ".sparkprofile")
            heaps[(member, "end")] = spark_save(console, member, "spark heapsummary --save-to-file", ".sparkheap")

        report.append(f"CustomPerm spark scenario S04, two members over {args.minutes} min with a "
                      f"{args.outage_seconds} s database outage")
        report.append("")
        for member in MEMBERS:
            judge(member, heaps, profiles, probes(member), start_probes[member], outage_probes.get(member), report, failures)
        report.append("Sample of the changes made:")
        report.extend(notes)
    except Exception as e:  # the report must still be written
        failures.append(f"run failed: {e!r}")
    finally:
        for console in consoles.values():
            try:
                console.run("stop")
            except (OSError, IOError):
                pass
            console.close()
        wait_for(lambda: all(p.poll() is not None for p in processes), 120)
        for p in processes:
            kill(p)
        try:
            db.sql(f"DROP DATABASE IF EXISTS {DATABASE}; DROP USER IF EXISTS '{DB_USER}'@'127.0.0.1'; "
                   f"DROP USER IF EXISTS '{DB_USER}'@'localhost';")
        except Exception as e:
            failures.append(f"cleanup: {e}")
        if db.started_here:
            db.stop()
        elif not db.up():
            db.start(logs["mariadb"])  # it was running before the outage: leave it running
        for log in logs.values():
            log.close()

    report.append("")
    report.append("Run problems:" if failures else "Run: every step completed.")
    report.extend(f"  {f}" for f in failures)
    report.append("S04 " + ("PASS" if not failures else "FAIL"))
    (BASE / "spark-cluster-report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))
    return 0 if not failures else 1


def judge(member, heaps, profiles, lines, first, outage, report, failures):
    def check(what, ok, evidence):
        report.append(f"  {'PASS' if ok else 'FAIL'} {what}: {evidence}")
        if not ok:
            failures.append(f"{member}: {what}")

    name = MEMBERS[member]["id"]
    report.append(f"Member {name}")
    start, end = heap(heaps[(member, "start")]), heap(heaps[(member, "end")])

    def count(predicate, h):
        return sum(v for k, v in h.items() if predicate(k))
    store = lambda k: k == "com.arcadia.customperm.cluster.SqlStore"
    connections = lambda k: ("mariadb" in k.lower() or "hikari" in k.lower())         and k.rsplit(".", 1)[-1] in ("Connection", "HikariProxyConnection", "PoolEntry")
    threads = lambda k: k == "java.lang.Thread"
    check("SqlStore stable", count(store, end) <= max(1, count(store, start)), f"{count(store, start)} -> {count(store, end)}")
    found = sorted(k for k in set(start) | set(end) if connections(k))
    check("connection objects stable", bool(found) and count(connections, end) <= count(connections, start) + 1,
          f"{count(connections, start)} -> {count(connections, end)} ({', '.join(k.rsplit('.', 1)[-1] for k in found) or 'no connection class found'})")

    if not lines:
        check("probe lines present", False, "no [spark-cluster] line in the log")
        return
    check("GapReader pending back to zero", lines[-1]["pending"] == 0,
          f"last {lines[-1]['pending']}, highest {max(l['pending'] for l in lines)}")
    before_outage = lines[first:outage[0]] if isinstance(outage, tuple) else lines[first:]
    after_outage = lines[outage[1] + 2:] if isinstance(outage, tuple) else []
    if before_outage and after_outage:
        before_threads = max(l["threads"] for l in before_outage)
        after_threads = after_outage[-1]["threads"]
        check("no thread growth after the outage", after_threads <= before_threads + 1,
              f"{before_threads} before, {after_threads} at the end (heap Thread {count(threads, start)} -> {count(threads, end)})")
    else:
        check("outage observed", False, "no probe lines around the outage")

    rates = [b["pollerBytes"] - a["pollerBytes"] for a, b in zip(lines[first:], lines[first + 1:])
             if a["pollerBytes"] >= 0 and b["pollerBytes"] >= 0]
    if len(rates) >= 8:
        quarter = len(rates) // 4
        early, late = sorted(rates[:quarter])[quarter // 2], sorted(rates[-quarter:])[quarter // 2]
        check("poller allocation not rising", late <= early * 1.5 + 65536,
              f"median {early // 1024} KiB per 30 s early, {late // 1024} KiB late")
    else:
        check("poller allocation measured", False, f"only {len(rates)} intervals")
    gc = lines[-1]["gcCount"] - lines[first]["gcCount"] if len(lines) > first else 0
    report.append(f"  info: {gc} collections over the run, {lines[-1]['gcMs'] - lines[first]['gcMs']} ms in GC")

    ms, ticks = profile_ms_per_tick(profiles[member], "com.arcadia.customperm")
    if ms is not None:
        report.append(f"  info: CustomPerm on the server thread {ms:.4f} ms per tick over {ticks} ticks")
    report.append("")


if __name__ == "__main__":
    sys.exit(main())
