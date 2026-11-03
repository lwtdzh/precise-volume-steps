#!/usr/bin/env python3
"""Disposable-emulator tests of a delayed server and a real silent manager grant.

Compile tests/ShizukuFlags.java with android.jar, then dex it with d8. This
test-only client runs as the manager UID through the emulator's existing su.
Production assistant/Shizuku code never calls su.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import threading
import time
from run_instrumentation import parse_result

PACKAGE = "roro.stellar.volumesteps"
MANAGER = "moe.shizuku.privileged.api"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scenario", choices=["delayed", "silent", "missed", "stalled"], required=True)
    parser.add_argument("--server-uid", type=int, choices=[0, 2000], required=True)
    parser.add_argument("--flags-dex", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--host-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use a disposable emulator; this fixture changes Shizuku permission")
    output = args.output.resolve()
    root = Path(__file__).resolve().parents[1]
    if output == root or root in output.parents:
        parser.error("Keep captures outside the repository")
    output.mkdir(parents=True, exist_ok=True)
    prefix = f"{args.serial}-{args.scenario}-uid{args.server_uid}-{time.time_ns()}"

    def adb(*command, binary=False):
        return subprocess.check_output(["adb", "-s", args.serial, *map(str, command)],
                                       timeout=30, text=not binary)

    def shell(command):
        return adb("shell", command).strip()

    def uid(package):
        return int(re.search(r"userId=(\d+)", shell("dumpsys package " + package)).group(1))

    manager_apk = shell("pm path " + MANAGER).removeprefix("package:")
    native = manager_apk.rsplit("/", 1)[0] + "/lib/arm64/libshizuku.so"
    client = "/data/local/tmp/pvs-shizuku-flags.dex"
    adb("push", args.flags_dex, client)
    package_uid, manager_uid = uid(PACKAGE), uid(MANAGER)

    def flags(value=None):
        invocation = "CLASSPATH=" + shlex.quote(client + ":" + manager_apk)
        invocation += " app_process /system/bin ShizukuFlags " + str(package_uid)
        if value is not None:
            invocation += " " + str(value)
        command = f"su {manager_uid} /system/bin/sh -c " + shlex.quote(invocation)
        return int(re.search(r"flags=(\d+)", shell(command)).group(1))

    def server():
        process = shell("ps -A -o UID,PID,NAME | grep shizuku_server || true").split()
        return (int(process[0]), int(process[1])) if process else None

    def start_server():
        invocation = shlex.quote(native) + " --apk=" + shlex.quote(manager_apk)
        if args.server_uid == 0:
            invocation = "su 0 /system/bin/sh -c " + shlex.quote(invocation)
        log = shell(invocation)
        (output / (prefix + "-starter.txt")).write_text(log)
        until = time.monotonic() + 5
        current = server()
        while current is None and time.monotonic() < until:
            time.sleep(.1)
            current = server()
        if current is None or current[0] != args.server_uid:
            raise RuntimeError("Official starter did not start the requested server UID")

    def stop_server():
        process = server()
        if process:
            shell("su 0 kill " + str(process[1]))

    hashes = {}
    for name, apk in ((PACKAGE, args.apk), (PACKAGE + ".testhost", args.host_apk)):
        installed = shell("pm path " + name).removeprefix("package:")
        digest = hashlib.sha256(adb("exec-out", "cat", installed, binary=True)).hexdigest()
        if digest != hashlib.sha256(apk.read_bytes()).hexdigest():
            raise RuntimeError("Installed APK mismatch: " + name)
        hashes[name] = digest
    original = server()
    if original is None or original[0] != args.server_uid:
        raise RuntimeError("Start the requested official server before running this fixture")
    saved_flags = flags()
    text, errors = [], []
    process = None
    tests, success = [], False
    before = shell("dumpsys activity service " + PACKAGE + "/.VolumeKeeperService")
    try:
        if args.scenario == "silent":
            flags(0)  # Official updateFlagsForUid grants later without a request callback.
        else:
            flags(2)
        if args.scenario == "delayed":
            stop_server()
        command = ["adb", "-s", args.serial, "shell", "am", "instrument", "-w",
                   "-e", "scenario", args.scenario,
                   PACKAGE + ".testhost/.ShizukuReconnectInstrumentation"]
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, bufsize=1)
        waiting = threading.Event()

        def collect():
            for line in process.stdout:
                text.append(line)
                if "INSTRUMENTATION_STATUS: phase=waiting-" in line:
                    waiting.set()

        reader = threading.Thread(target=collect, daemon=True)
        reader.start()
        if not waiting.wait(20):
            raise RuntimeError("Fixture never reached the background waiting state")
        if args.scenario in ("delayed", "silent"):
            time.sleep(5)  # Deliberately simulate a server/grant arriving seconds later.
            start_server() if args.scenario == "delayed" else flags(2)
        code = process.wait(timeout=70)
        reader.join(timeout=5)
        tests, success, parse_errors = parse_result("".join(text))
        errors.extend(parse_errors)
        success = success and code == 0
    except Exception as exception:
        errors.append(str(exception))
    finally:
        if process is not None and process.poll() is None:
            try:
                process.wait(timeout=45)  # Let the fixture restore preferences on failure.
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=10)
                shell("am force-stop " + PACKAGE + ".testhost")
        if process is not None:
            reader.join(timeout=5)
            tests, parsed_success, parse_errors = parse_result("".join(text))
            errors.extend(error for error in parse_errors if error not in errors)
            success = success and parsed_success
        try:
            if server() is None:
                start_server()
            flags(saved_flags)
            if flags() != saved_flags:
                raise RuntimeError("Original Shizuku permission flags were not restored")
        except Exception as exception:
            errors.append("restore: " + str(exception))
        shell("rm -f " + client)
        report = {
            "serial": args.serial, "api": shell("getprop ro.build.version.sdk"),
            "scenario": args.scenario, "server_uid": args.server_uid,
            "apk_sha256": hashes[PACKAGE], "host_apk_sha256": hashes[PACKAGE + ".testhost"],
            "input": "real server startup/manager permission grant and Android Binder; no keys",
            "results": tests, "success": success and not errors, "errors": errors,
            "before": before,
            "after": shell("dumpsys activity service " + PACKAGE + "/.VolumeKeeperService"),
            "saved_flags": saved_flags, "restored_server": server(),
        }
        (output / (prefix + ".txt")).write_text("".join(text))
        (output / (prefix + ".json")).write_text(json.dumps(report, indent=2))
    print(f"{sum(t.get('pass') is True for t in tests)}/{len(tests)} passed; "
          f"success={report['success']}")
    print(output / (prefix + ".json"))
    raise SystemExit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
