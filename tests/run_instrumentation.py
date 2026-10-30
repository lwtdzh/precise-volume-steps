#!/usr/bin/env python3
"""Run an explicit Android fixture and preserve its real result, including failure."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "roro.stellar.volumesteps"
HOST = PACKAGE + ".testhost"
SUITES = {
    "backend": "ShizukuRegressionInstrumentation",
    "ordinary": "VolumeRegressionInstrumentation",
    "frames": "FinalPanelInstrumentation",
    "layout": "OverlayStabilityInstrumentation",
    "screen-off": "ScreenOffInstrumentation",
    "modes": "OperatingModesInstrumentation",
}


def parse_result(text):
    matches = re.findall(r"^INSTRUMENTATION_RESULT: tests=(.*)$", text, re.M)
    tests = json.loads(matches[-1]) if matches else []
    codes = re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)$", text, re.M)
    errors = re.findall(r"^INSTRUMENTATION_RESULT: (?:error|restore_error)=(.*)$", text, re.M)
    passed = bool(tests) and all(t.get("pass") is True for t in tests)
    complete = bool(codes) and codes[-1] == "0" and not errors
    return tests, passed and complete, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--suite", choices=SUITES, required=True)
    parser.add_argument("--backend", choices=["companion", "shizuku", "root"], default="shizuku")
    parser.add_argument("--apk", type=Path, help="Require installed main to match this APK")
    parser.add_argument("--host-apk", type=Path, help="Require installed host to match this APK")
    parser.add_argument("--steps", type=int)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.suite in ("frames", "screen-off", "modes") and not args.serial.startswith("emulator-"):
        parser.error("Rejection/frame and screen-off fixtures are disposable-emulator only")
    if args.steps is not None and not 5 <= args.steps <= 150:
        parser.error("steps must be between 5 and 150")
    if args.steps is not None and args.suite != "backend":
        parser.error("--steps is supported by the backend fixture only")
    output = args.output.resolve()
    if output == ROOT or ROOT in output.parents:
        parser.error("Store device captures outside the repository, for example /tmp/pvs-tests")
    output.mkdir(parents=True, exist_ok=True)
    prefix = f"{args.serial}-{args.suite}-{time.time_ns()}"

    def adb(*arguments, binary=False, timeout=30):
        return subprocess.check_output(["adb", "-s", args.serial, *arguments],
                                       timeout=timeout, text=not binary)

    def snapshot():
        result = {}
        commands = {
            "fingerprint": ["getprop", "ro.build.fingerprint"],
            "model": ["getprop", "ro.product.model"],
            "api": ["getprop", "ro.build.version.sdk"],
            "volume": ["cmd", "media_session", "volume", "--stream", "3", "--get"],
            "service": ["dumpsys", "activity", "service", PACKAGE + "/.VolumeKeeperService"],
            "accessibility": ["settings", "get", "secure", "enabled_accessibility_services"],
            "screen_timeout": ["settings", "get", "system", "screen_off_timeout"],
            "usb_stay_awake": ["settings", "get", "global", "stay_on_while_plugged_in"],
        }
        for name, command in commands.items():
            try:
                result[name] = adb("shell", *command).strip()
            except (subprocess.SubprocessError, OSError) as error:
                result[name] = {"error": str(error)}
        return result

    before = snapshot()
    def installed_hash(package):
        installed = adb("shell", "pm", "path", package).strip().splitlines()
        path = next((line.removeprefix("package:") for line in installed
                     if line.endswith("/base.apk")), None)
        if not path:
            raise SystemExit(f"{package} is not installed")
        return hashlib.sha256(adb("exec-out", "cat", path, binary=True)).hexdigest()

    digest = installed_hash(PACKAGE)
    host_digest = installed_hash(HOST)
    mismatches = []
    for name, path, installed in (("main", args.apk, digest),
                                  ("host", args.host_apk, host_digest)):
        if path and hashlib.sha256(path.read_bytes()).hexdigest() != installed:
            mismatches.append(f"Installed {name} APK does not match {path}")
    if mismatches:
        report = {"serial": args.serial, "success": False, "errors": mismatches,
                  "before": before, "apk_sha256": digest, "host_apk_sha256": host_digest}
        (output / (prefix + "-setup-failure.json")).write_text(json.dumps(report, indent=2))
        raise SystemExit("; ".join(mismatches))
    helper_digest = installed_hash("com.vivo.finevolumesteps.helper") if (
        args.suite == "backend" and args.backend == "companion") else None
    fixture = ROOT / "tests/host/src/main/java/roro/stellar/volumesteps/testhost" / (
        SUITES[args.suite] + ".java")
    fixture_digest = hashlib.sha256(fixture.read_bytes()).hexdigest()
    command = ["adb", "-s", args.serial, "shell", "am", "instrument", "-w"]
    if args.suite == "backend":
        command += ["-e", "backend", args.backend]
        if args.steps is not None:
            command += ["-e", "steps", str(args.steps)]
    command += [HOST + "/." + SUITES[args.suite]]
    text = ""
    tests, success, errors = [], False, []
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=180)
        text = completed.stdout + completed.stderr
        tests, success, errors = parse_result(text)
        success = success and completed.returncode == 0
    except (subprocess.SubprocessError, OSError, ValueError) as error:
        errors.append(str(error))
        if isinstance(error, subprocess.TimeoutExpired):
            captured = error.stdout or b""
            text = captured.decode(errors="replace") if isinstance(captured, bytes) else captured
    finally:
        (output / (prefix + ".txt")).write_text(text)
        report = {
            "serial": args.serial, "api": before.get("api"),
            "apk_sha256": digest, "suite": args.suite,
            "host_apk_sha256": host_digest, "helper_apk_sha256": helper_digest,
            "fixture_sha256": fixture_digest,
            "backend": args.backend if args.suite == "backend" else None,
            "steps": args.steps, "input": "synthetic controller callbacks; actual Android audio/IPC",
            "results": tests, "success": success, "errors": errors,
            "before": before, "after": snapshot(),
        }
        (output / (prefix + ".json")).write_text(
            json.dumps(report, ensure_ascii=False, indent=2))
    print(f"{sum(t.get('pass') is True for t in tests)}/{len(tests)} passed; success={success}")
    print(output / (prefix + ".json"))
    raise SystemExit(0 if success else 1)


if __name__ == "__main__":
    main()
