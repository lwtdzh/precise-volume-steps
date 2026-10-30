#!/usr/bin/env python3
"""Test an already-playing app while asleep, using simulated keys and real readback.

This does not launch/stop the player or change its settings. It temporarily
changes media volume and restores it. Built-in phone buttons remain unverified.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import select
import subprocess
import time
from android_device import Device, PACKAGE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--player-package", required=True)
    parser.add_argument("--keys", type=Path, required=True)
    parser.add_argument("--input-source", choices=("usb", "kernel"), default="usb")
    parser.add_argument("--retain-volume-failures", action="store_true",
                        help="Finish the sequence after wrong readbacks; state failures still stop it")
    parser.add_argument("--preset-dex", type=Path, required=True)
    parser.add_argument("--presses", type=int, default=10)
    parser.add_argument("--backend", choices=("shizuku", "ordinary"), default="shizuku")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("Store phone captures outside the repository")
    if not 1 <= args.presses <= 20:
        parser.error("presses must be in 1..20")
    if re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.player_package) is None:
        parser.error("player-package must be an Android package name")
    output.mkdir(parents=True, exist_ok=True)
    d = Device(args.serial, "/data/local/tmp/pvs-player-test-preset.dex")
    before, origin = d.service(), d.volume()
    limits = re.search(r"native media volume=\d+ / (\d+), min=(\d+)", before)
    steps = re.search(r"enabled=true, steps=(\d+)", before)
    if limits is None or steps is None:
        raise SystemExit("Enabled keeper and real native range required")
    maximum, minimum = map(int, limits.groups())
    configured = int(steps.group(1))
    intervals = min(configured, maximum - minimum)
    if intervals < 1:
        raise SystemExit("Native range has no adjustable intervals")

    def expected(position):
        return origin + math.floor(position * (maximum - minimum) / intervals + .5)

    if expected(args.presses) > maximum:
        raise SystemExit("Choose fewer presses or a lower safe baseline before this reversal test")
    installed = d.shell("pm", "list", "packages", "--user", 0, "-U", args.player_package)
    match = re.search(r"^package:" + re.escape(args.player_package) + r"\s+uid:(\d+)$",
                      installed, re.M)
    if match is None:
        raise SystemExit("Exact player package is not installed")
    uid = int(match.group(1))
    pid = d.shell("pidof", PACKAGE).strip()
    player_pid = d.shell("pidof", args.player_package).strip()
    apk = d.shell("pm", "path", PACKAGE).strip().splitlines()[0].removeprefix("package:")
    digest = hashlib.sha256(d.adb("exec-out", "cat", apk, binary=True)).hexdigest()
    prefix = f"playing-app-{args.input_source}-{time.time_ns()}"
    checks, traces, errors = [], [], []
    hid = None
    preset_ready = False

    def check(name, value, retain_failure=False):
        checks.append({"test": name, "pass": bool(value)})
        print(("PASS " if value else "FAIL ") + name, flush=True)
        if not value and not retain_failure:
            raise RuntimeError(name)

    def active():
        audio = d.shell("dumpsys", "audio")
        return any(f"u/pid:{uid}/" in line and "state:started" in line
                   and "AudioPlaybackConfiguration" in line and "USAGE_MEDIA" in line
                   for line in audio.splitlines())

    try:
        check("actual player owns active media while display is off",
              active() and "screen interactive=false" in before)
        if args.backend == "shizuku":
            check("both selected Shizuku connections are ready",
                  "Shizuku=enabled=true, ready=true" in before and "raw volume keys=ready=true" in before)
        else:
            check("ordinary helper and accessibility ready with raw input disabled",
                  "Shizuku=enabled=false, ready=false" in before
                  and "raw volume keys=ready=false" in before
                  and "companion=supported=true, ready=true" in before
                  and "accessibility connected=true" in before)
        d.adb("push", str(args.preset_dex.resolve()), d.preset_dex)
        preset_ready = True
        d.adb("push", str(args.keys.resolve()), "/data/local/tmp/pvs-player-test-hid")
        d.shell("chmod", 755, "/data/local/tmp/pvs-player-test-hid")
        key_command = ["adb", "-s", args.serial, "shell", "/data/local/tmp/pvs-player-test-hid"]
        if args.input_source == "kernel":
            key_command.append("--kernel")
        hid = subprocess.Popen(key_command,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True)
        check(args.input_source + " key simulation created",
              bool(select.select([hid.stdout], [], [], 5)[0])
              and hid.stdout.readline().strip() == "READY")
        time.sleep(2.5)
        for direction in (1, -1):
            key = "+" if direction == 1 else "-"
            for n in range(1, args.presses + 1):
                hid.stdin.write(key)
                hid.stdin.flush()
                if not select.select([hid.stdout], [], [], 3)[0]:
                    raise RuntimeError("HID acknowledgement timeout")
                if hid.stdout.readline().strip() != "OK " + key:
                    raise RuntimeError("HID failed")
                target = expected(n if direction == 1 else args.presses - n)
                deadline = time.monotonic() + 2
                while time.monotonic() < deadline and d.volume() != target:
                    time.sleep(.05)
                matched = d.volume() == target
                traces.append({"direction": direction, "press": n, "stage": "initial",
                               "target": target, "volume": d.volume(), "service": d.service()})
                time.sleep(1.05)
                state = d.service()
                traces.append({"direction": direction, "press": n, "stage": "settled",
                               "target": target, "volume": d.volume(), "service": state})
                check(f"{key} press {n} native readback", matched,
                      retain_failure=args.retain_volume_failures)
                check(f"{key} press {n} remains settled after one second", d.volume() == target,
                      retain_failure=args.retain_volume_failures)
                check(f"{key} press {n} preserves screen, panel and actual player",
                      "screen interactive=false" in state and "overlay=attached=false" in state
                      and "playback active=true" in state and active())
        check("reversal restores native origin", d.volume() == origin)
        check("player and main processes survive input",
              d.shell("pidof", PACKAGE).strip() == pid
              and d.shell("pidof", args.player_package).strip() == player_pid)
    except Exception as error:
        errors.append(str(error))
    finally:
        if hid is not None:
            try:
                hid.stdin.write("q")
                hid.stdin.flush()
                hid.communicate(timeout=5)
            except Exception as error:
                hid.terminate()
                errors.append("HID cleanup: " + str(error))
        try:
            if preset_ready and d.volume() != origin:
                d.preset(origin)
            d.shell("rm", "-f", "/data/local/tmp/pvs-player-test-hid", d.preset_dex)
        except Exception as error:
            errors.append("Restoration: " + str(error))
        report = {"serial": args.serial, "api": d.shell("getprop", "ro.build.version.sdk").strip(),
                  "apk_sha256": digest, "fixture_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  "success": bool(checks) and all(c["pass"] for c in checks) and not errors,
                  "suite": "playing-app-hid", "player": args.player_package, "steps": configured,
                  "backend": args.backend, "input_source": args.input_source,
                  "keys_sha256": hashlib.sha256(args.keys.read_bytes()).hexdigest(),
                  "retain_volume_failures": args.retain_volume_failures,
                  "input": ("BUS_HOST uinput kernel simulation"
                            if args.input_source == "kernel" else "external UHID")
                           + "; actual player; built-in buttons not pressed",
                  "results": checks, "errors": errors, "before": before, "origin": origin,
                  "traces": traces, "after": d.service()}
        path = output / (prefix + ".json")
        path.write_text(json.dumps(report, indent=2))
        print(path, flush=True)
    raise SystemExit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
