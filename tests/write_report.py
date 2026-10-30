#!/usr/bin/env python3
"""Summarize recorded runs without hiding failures or mixing tested APKs."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--run", action="append", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--unit-variant", choices=["Release", "Debug"], default="Release")
    args = parser.parse_args()
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    unit_dir = ROOT / "build/test-results" / ("test" + args.unit_variant + "UnitTest")
    units = [ET.parse(p).getroot() for p in sorted(unit_dir.glob("TEST-*.xml"))]
    if not units:
        raise SystemExit("Missing unit-test evidence")
    counts = {key: sum(int(s.get(key, "0")) for s in units)
              for key in ("tests", "failures", "errors")}
    lint = ET.parse(ROOT / "build/reports/lint-results-release.xml").getroot()
    issues = list(lint.iter("issue"))
    lint_errors = sum(i.get("severity") in ("Error", "Fatal") for i in issues)
    all_pass = counts["failures"] == 0 and counts["errors"] == 0 and lint_errors == 0
    lines = [
        f"# Precise Volume Steps {args.version} — recorded verification", "",
        f"APK SHA-256: `{digest}`", "",
        f"Unit tests: {counts['tests']} tests, {counts['failures']} failures, "
        f"{counts['errors']} errors.",
        f"Release lint: {lint_errors} errors; {len(issues) - lint_errors} warnings.", "",
        "| Run | Device / API | Input | Passed | Outcome |",
        "|---|---|---|---:|---|",
    ]
    failures = []
    for path in args.run:
        run = json.loads(path.read_text())
        if run.get("apk_sha256") != digest:
            raise SystemExit(f"APK hash missing or different in {path}; do not merge this evidence")
        tests = run.get("results", [])
        passed = sum(t.get("pass") is True for t in tests)
        success = bool(tests) and passed == len(tests)
        success = success and run.get("success") is True and not run.get("errors")
        all_pass = all_pass and success
        def cell(value):
            return str(value).replace("|", "\\|").replace("\n", " ")
        name = str(path.parent.name + "/" + path.name) if path.name == "result.json" else path.name
        lines.append(
            f"| {cell(name)} | {cell(run.get('serial', run.get('device', 'unknown')))} / "
            f"{cell(run.get('api', 'unknown'))} | "
            f"{cell(run.get('input', 'input source not recorded'))} | "
            f"{passed}/{len(tests)} | {'PASS' if success else 'FAIL'} |")
        for test in tests:
            if test.get("pass") is not True:
                failures.append(f"- {path.name}: {test.get('test')} — {test.get('detail', '')}".rstrip())
        for error in run.get("errors", []):
            failures.append(f"- {path.name}: {error}")
    lines += ["", "Original failed runs remain separate from repeats. "
              "Synthetic callbacks and external input do not establish physical-button coverage.",
              "Unit/lint files are the current local evidence; run the build immediately before "
              "this report. A matching device APK hash does not establish a matching source commit.",
              "", "Physical phone matrix: record separately; no pass is inferred here."]
    if failures:
        lines += ["", "Failures:", "", *failures]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n")
    print(args.output)
    raise SystemExit(0 if all_pass else 1)


if __name__ == "__main__":
    main()
