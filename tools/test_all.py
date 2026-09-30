# CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
# SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
"""Every automated layer in one command, and one page saying which step of the test procedure each one proved.

  gametests  ./gradlew testAll: JUnit, the package check, the three GameTest modes judged from their logs
  client     ./gradlew runClientSmoke: every page on a real client, layout at four sizes (V02-V05, V08, V10, V11)
  server     tools/server_smoke.py: dedicated server with LuckPerms, a client without the mod, V01 and V09
  cluster    tools/cluster_smoke.py: release jar on real NeoForge and MariaDB, D01-D10, V06 and V07
  arcadia    tools/arcadia_smoke.py: the release jar in the Arcadia server pack
  arcadia-db tools/arcadia_db_smoke.py: the pack on a local database through Arcadia Lib, with and without LuckPerms
  arcadia-client tools/arcadia_client_smoke.py: the admin interface in the Arcadia client pack, on the server pack
  spark      ./gradlew runSparkScenario and tools/spark_cluster.py (S01-S04), only with --spark (over an hour)

Each layer writes its own report; this script reads them, never exit codes alone, and writes
run/automation/automation-report.txt and automation-report.html. Exit code 0 only when every layer ran and passed.
Usage: python tools/test_all.py [--only client,server] [--skip arcadia] [--spark] [--spark-minutes 60]
"""
import argparse
import datetime
import glob
import html
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from mc_harness import GRADLEW, ROOT

OUT = ROOT / "run" / "automation"
PYTHON = sys.executable

LAYERS = {
    "gametests": ([GRADLEW, "testAll", "--console=plain"], None),
    "client": ([GRADLEW, "runClientSmoke", "--console=plain"], ROOT / "run" / "clientsmoke" / "smoke-report.txt"),
    "server": ([PYTHON, "tools/server_smoke.py"], ROOT / "run" / "serversmoke" / "server-smoke-report.txt"),
    "cluster": ([PYTHON, "tools/cluster_smoke.py"], ROOT / "run" / "clustersmoke" / "cluster-smoke-report.txt"),
    "arcadia": ([PYTHON, "tools/arcadia_smoke.py"], ROOT / "run" / "arcadia" / "arcadia-smoke-report.txt"),
    "arcadia-db": ([PYTHON, "tools/arcadia_db_smoke.py"], ROOT / "run" / "arcadia-db" / "arcadia-db-report.txt"),
    "arcadia-client": ([PYTHON, "tools/arcadia_client_smoke.py"], ROOT / "run" / "arcadia" / "arcadia-client-report.txt"),
}

# What each step of the test procedure asks, to title the page.
STEPS = {
    "V01": "A client without the mod joins", "V02": "The interface opens and draws", "V03": "A narrow window",
    "V04": "Decoration as a player sees it", "V05": "Completion changes under the player's eyes",
    "V06": "The cluster members of an element", "V07": "A node server by server", "V08": "Completion in every field",
    "V09": "What an import or export carries", "V10": "The levels of a rate limit", "V11": "Who may use a command where",
    "D01": "No JDBC driver on the server", "D02": "Arcadia Lib provides the driver", "D03": "Encryption against a database",
    "D04": "First boot of a cluster on a virgin database", "D05": "A sharing plan", "D06": "An element limited to some members",
    "D07": "A grade or a player has the last word", "D08": "A limit per member, one for a shared counter",
    "D09": "A grade's and a player's value across the cluster", "D10": "An alias opened on one member by a grade",
    "S01": "Open and close the interface 50 times", "S02": "Frame time with the interface open",
    "S03": "Rate limit history under load", "S04": "A cluster member over an hour",
}
ID = re.compile(r"^(PASS|FAIL) ([VDS]\d\d)\b")


def run_layer(name, command):
    log = OUT / f"{name}.log"
    started = time.time()
    with open(log, "w", encoding="utf-8") as out:
        code = subprocess.run(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT).returncode
    return code, time.time() - started


def gametests_lines():
    """PASS/FAIL lines for testAll: JUnit from its XML, each GameTest mode from the count its verify task wrote."""
    lines = []
    total = failed = 0
    for path in glob.glob(str(ROOT / "build" / "test-results" / "test" / "*.xml")):
        suite = ET.parse(path).getroot()
        total += int(suite.get("tests", 0))
        failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    lines.append(("PASS " if total and not failed else "FAIL ") + f"JUnit - {total - failed} of {total} passed")
    for mode in ("gametest", "gametest-luckperms", "gametest-arcadia"):
        count = ROOT / "run" / mode / "gametest-count.txt"
        passed = count.read_text(encoding="utf-8").strip() if count.is_file() else ""
        lines.append((f"PASS GameTest {mode} - {passed} required tests passed" if passed
                      else f"FAIL GameTest {mode} - no verified result, see its logs/latest.log"))
    return lines


def report_lines(path):
    if path is None or not path.is_file():
        return None
    return path.read_text(encoding="utf-8").splitlines()


def spark_lines():
    lines = []
    for label, path in (("S01-S03", ROOT / "run" / "spark" / "spark-report.txt"),
                        ("S04", ROOT / "run" / "spark-cluster" / "spark-cluster-report.txt")):
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        # S01-S03 open a section per scenario; the S04 report is S04 as a whole, one block per member.
        section = "S04" if label == "S04" else None
        for line in text.splitlines():
            head = re.match(r"^(S0\d) ", line)
            if head:
                section = head.group(1)
            check = re.match(r"^\s+(PASS|FAIL) (.*)", line)
            if check and section:
                lines.append(f"{check.group(1)} {section} {check.group(2)}")
        if not text:
            lines.append(f"FAIL {label.split('-')[0]} no report at {path.relative_to(ROOT)}")
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--skip", default="")
    parser.add_argument("--spark", action="store_true")
    parser.add_argument("--spark-minutes", type=int, default=60)
    args = parser.parse_args()
    only = {x for x in args.only.split(",") if x}
    skip = {x for x in args.skip.split(",") if x}
    OUT.mkdir(parents=True, exist_ok=True)

    layers = [n for n in LAYERS if (not only or n in only) and n not in skip]
    results = []
    for name in layers:
        command, report = LAYERS[name]
        if report is not None and report.exists():
            report.unlink()
        print(f"== {name}: {' '.join(str(c) for c in command[1:] if c != '--console=plain')}", flush=True)
        code, seconds = run_layer(name, command)
        lines = gametests_lines() if name == "gametests" else report_lines(report)
        if lines is None:
            lines = [f"FAIL {name} wrote no report (exit code {code}), see run/automation/{name}.log"]
        results.append((name, code, seconds, lines))
        print(f"   {sum(l.startswith('FAIL') for l in lines)} failed of {sum(l.startswith(('PASS', 'FAIL')) for l in lines)}"
              f" in {seconds / 60:.1f} min", flush=True)
    if args.spark and "spark" not in skip:
        # A report left from an earlier run must never pass for this one: the S04 script once failed at start and
        # the report of the day before was read as its verdict.
        for stale in (ROOT / "run" / "spark" / "spark-report.txt", ROOT / "run" / "spark-cluster" / "spark-cluster-report.txt"):
            stale.unlink(missing_ok=True)
        started = time.time()
        subprocess.run([GRADLEW, "runSparkScenario", "--console=plain"], cwd=ROOT,
                       stdout=open(OUT / "spark.log", "w", encoding="utf-8"), stderr=subprocess.STDOUT)
        subprocess.run([PYTHON, "tools/spark_cluster.py", "--minutes", str(args.spark_minutes)], cwd=ROOT,
                       stdout=open(OUT / "spark.log", "a", encoding="utf-8"), stderr=subprocess.STDOUT)
        results.append(("spark", 0, time.time() - started, spark_lines()))

    ok = write(results)
    print("AUTOMATION " + ("PASS" if ok else "FAIL") + f" - run/automation/automation-report.html")
    return 0 if ok else 1


def write(results):
    OUT.mkdir(parents=True, exist_ok=True)
    steps = {}
    for name, _, _, lines in results:
        for line in lines:
            m = ID.match(line)
            if m:
                steps.setdefault(m.group(2), []).append((m.group(1), line[5:], name))
    ok = all(not any(l.startswith("FAIL") for l in lines) for _, _, _, lines in results) and bool(results)

    text = [f"CustomPerm automation {datetime.datetime.now():%Y-%m-%d %H:%M}", ""]
    for name, code, seconds, lines in results:
        failed = [l for l in lines if l.startswith("FAIL")]
        text.append(f"{name}: {'PASS' if not failed else 'FAIL'} ({seconds / 60:.1f} min, exit {code})")
        text += [f"  {l}" for l in failed]
    text += ["", "Procedure steps:"]
    for step in sorted(STEPS):
        checks = steps.get(step, [])
        state = "not run" if not checks else "PASS" if all(s == "PASS" for s, _, _ in checks) else "FAIL"
        text.append(f"  {step} {state:7} {STEPS[step]} ({len(checks)} checks)")
    text.append("")
    text.append("RESULT PASS" if ok else "RESULT FAIL")
    (OUT / "automation-report.txt").write_text("\n".join(text) + "\n", encoding="utf-8")

    rows = []
    for step in sorted(STEPS):
        checks = steps.get(step, [])
        state = "none" if not checks else "pass" if all(s == "PASS" for s, _, _ in checks) else "fail"
        label = {"none": "Not run / Non joue", "pass": "Pass / OK", "fail": "Fail / Echec"}[state]
        detail = "".join(f"<li class='{s.lower()}'><span>{s}</span> {html.escape(d)} <em>{n}</em></li>" for s, d, n in checks)
        rows.append(f"<details class='{state}'><summary><b>{step}</b> {html.escape(STEPS[step])}"
                    f"<i>{label} &middot; {len(checks)}</i></summary><ul>{detail}</ul></details>")
    layers = "".join(
        f"<tr class='{'fail' if any(l.startswith('FAIL') for l in lines) else 'pass'}'><td>{n}</td>"
        f"<td>{sum(l.startswith('PASS') for l in lines)}</td><td>{sum(l.startswith('FAIL') for l in lines)}</td>"
        f"<td>{s / 60:.1f} min</td></tr>" for n, _, s, lines in results)
    others = "".join(
        f"<li class='{l[:4].lower()}'><span>{l[:4]}</span> {html.escape(l[5:])} <em>{n}</em></li>"
        for n, _, _, lines in results for l in lines if l.startswith(("PASS", "FAIL")) and not ID.match(l))
    page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>CustomPerm automation</title><style>
:root{{--bg:#f6f7f9;--fg:#1c2026;--mute:#5d6673;--card:#fff;--line:#dde1e7;--pass:#1f7a4d;--fail:#b3261e;--none:#8a6d00}}
@media (prefers-color-scheme:dark){{:root{{--bg:#15181d;--fg:#e6e9ee;--mute:#9aa3ae;--card:#1d2127;--line:#2c323b;--pass:#5fc28f;--fail:#f08a80;--none:#d9b64a}}}}
body{{margin:0;padding:24px 16px;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,sans-serif}}
main{{max-width:960px;margin:auto}} h1{{margin:0 0 4px}} p{{color:var(--mute);margin:0 0 20px}}
table{{border-collapse:collapse;width:100%;margin-bottom:24px;background:var(--card)}} td,th{{padding:6px 10px;border-bottom:1px solid var(--line);text-align:left}}
tr.pass td:first-child{{border-left:4px solid var(--pass)}} tr.fail td:first-child{{border-left:4px solid var(--fail)}}
details{{background:var(--card);border:1px solid var(--line);border-left:4px solid var(--none);border-radius:6px;margin:6px 0;padding:6px 12px}}
details.pass{{border-left-color:var(--pass)}} details.fail{{border-left-color:var(--fail)}}
summary{{cursor:pointer;display:flex;gap:10px;align-items:baseline}} summary i{{margin-left:auto;color:var(--mute);font-style:normal;font-size:13px}}
ul{{margin:6px 0;padding-left:18px}} li{{margin:2px 0;overflow-wrap:anywhere}} li span{{font-weight:600}}
li.pass span{{color:var(--pass)}} li.fail span{{color:var(--fail)}} em{{color:var(--mute);font-size:12px}}
</style></head><body><main><h1>CustomPerm automation / Automatisation</h1>
<p>{datetime.datetime.now():%Y-%m-%d %H:%M} &middot; {"RESULT PASS" if ok else "RESULT FAIL"}</p>
<table><tr><th>Layer / Etage</th><th>Pass</th><th>Fail</th><th>Time / Duree</th></tr>{layers}</table>
<h2>Procedure steps / Etapes de la procedure</h2>{''.join(rows)}
<h2>Other checks / Autres controles</h2><ul>{others}</ul></main></body></html>"""
    (OUT / "automation-report.html").write_text(page, encoding="utf-8")
    return ok


if __name__ == "__main__":
    sys.exit(main())
