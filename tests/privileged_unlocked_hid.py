#!/usr/bin/env python3
"""Verify unlocked privileged raw keys and automatic accessibility recovery."""
import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import re
import select
import subprocess
import time

from android_device import Device, HOST, PACKAGE, normalized_components

ACCESSIBILITY = PACKAGE + "/" + PACKAGE + ".FineVolumeAccessibilityService"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--keys", required=True, type=Path)
    parser.add_argument("--preset-dex", type=Path,
                        help="Exact shell-UID preset; omit to use cmd media_session with readback")
    parser.add_argument("--backend", choices=("shizuku", "root"), required=True)
    parser.add_argument("--root-hid", action="store_true",
                        help="Use already-authorized su only for the external HID generator")
    parser.add_argument("--existing-setup", action="store_true",
                        help="Verify already-configured keys without settings instrumentation or service removal")
    parser.add_argument("--steps", required=True, type=int)
    parser.add_argument("--baseline", required=True, type=int)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("Store phone captures outside the repository")
    output.mkdir(parents=True, exist_ok=True)

    remote_keys = "/data/local/tmp/pvs-unlocked-hid"
    remote_preset = "/data/local/tmp/pvs-unlocked-preset.dex"
    device = Device(args.serial, remote_preset if args.preset_dex else None)
    original_volume = device.volume()
    original_services = device.shell(
        "settings", "get", "secure", "enabled_accessibility_services").strip()
    original_accessibility = device.shell(
        "settings", "get", "secure", "accessibility_enabled").strip()
    original_service = device.service()
    checks = []
    traces = []
    errors = []
    hid = None
    saved_settings = None
    preset_ready = False

    def check(name, condition):
        checks.append({"test": name, "pass": bool(condition)})
        print(("PASS " if condition else "FAIL ") + name, flush=True)
        if not condition:
            raise RuntimeError(name)

    def send(key):
        hid.stdin.write(key)
        hid.stdin.flush()
        if not select.select([hid.stdout], [], [], 3)[0]:
            raise RuntimeError("HID acknowledgement timed out")
        check("HID acknowledges " + key, hid.stdout.readline().strip() == "OK " + key)

    def sample_hold(key):
        samples = []
        hid.stdin.write(key)
        hid.stdin.flush()
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if select.select([hid.stdout], [], [], 0)[0]:
                check("HID acknowledges " + key,
                      hid.stdout.readline().strip() == "OK " + key)
                settled_deadline = time.monotonic() + 1.4
                while time.monotonic() < settled_deadline:
                    samples.append({"time": time.monotonic(), "volume": device.volume()})
                traces.append({"label": key + " live hold", "samples": samples})
                return samples
            samples.append({"time": time.monotonic(), "volume": device.volume()})
        raise RuntimeError("HID hold acknowledgement timed out")

    def stop_hid():
        nonlocal hid
        if hid is None:
            return
        try:
            hid.stdin.write("q")
            hid.stdin.flush()
            hid.communicate(timeout=5)
        except Exception:
            hid.kill()
            hid.communicate()
        hid = None

    def wait_for_volume(expected):
        deadline = time.monotonic() + 2
        stable_since = None
        while time.monotonic() < deadline:
            if device.volume() == expected:
                if stable_since is None:
                    stable_since = time.monotonic()
                elif time.monotonic() - stable_since >= .35:
                    return True
            else:
                stable_since = None
            time.sleep(.04)
        return False

    def expected(origin, position, maximum):
        intervals = min(args.steps, maximum)
        return max(0, min(maximum, origin + math.floor(
            position * maximum / intervals + .5)))

    def check_hold(key, origin, direction, maximum, hold_presses):
        samples = sample_hold(key)
        settled = device.volume()
        state = device.service()
        values = [sample["volume"] for sample in samples]
        traces.append({"label": key + " settled", "volume": settled,
                       "presses": hold_presses, "origin": origin, "service": state})
        check(key + " hold advances at least the Android repeat count",
              direction * (settled - expected(origin, direction * hold_presses, maximum)) >= 0)
        check(key + " hold keeps every sampled readback monotonic through settling",
              bool(values) and all(direction * (b - a) >= 0 for a, b in zip(values, values[1:])))
        check(key + " hold settles beyond its live native progress",
              bool(values) and direction * (settled - (min(values) if direction < 0
                                                        else max(values))) >= 0)
        check(key + " hold stays on the original rounded path",
              any(settled == expected(origin, direction * n, maximum)
                  for n in range(1, min(args.steps, maximum) + 1)))
        check(key + " hold finishes with no pending correction",
              "pending correction=none" in state
              and "key write unconfirmed" not in state)

    def settings_probe(saved=None):
        command = ["am", "instrument", "-w"]
        if saved is not None:
            encoded = base64.b64encode(json.dumps(saved).encode()).decode()
            command += ["-e", "restore", encoded]
        result = device.shell(*command, HOST + "/.PhoneProbeInstrumentation")
        if "INSTRUMENTATION_CODE: 0" not in result:
            raise RuntimeError("Settings fixture failed")
        return json.loads(re.search(
            r"INSTRUMENTATION_RESULT: settings=(.*)", result).group(1))

    def reader_ready(state):
        if args.backend == "shizuku":
            return ("Shizuku=enabled=true, ready=true" in state
                    and "raw volume keys=ready=true" in state)
        return re.search(r"\n\s*Root=.*ready=true, devices=[1-9]\d*, input error=null", state) is not None

    def backend_ready(state):
        if args.backend == "shizuku":
            return "Shizuku=enabled=true, ready=true" in state
        # The writer can be connected before the fixture creates a readable
        # input node. Reader readiness is checked separately after HID startup.
        return re.search(r"\n\s*Root=.*ready=true,", state) is not None

    def components(value):
        return set() if value in ("", "null") else normalized_components(value)

    def remove_own_accessibility():
        remaining = [
            item for item in original_services.split(":")
            if item and item != "null"
            and ACCESSIBILITY not in components(item)
        ]
        value = ":".join(remaining) if remaining else "null"
        device.shell("settings", "put", "secure",
                     "enabled_accessibility_services", value)
        if not remaining:
            device.shell("settings", "put", "secure", "accessibility_enabled", "0")
        return components(value)

    try:
        check("phone starts awake and unlocked",
              "screen interactive=true, keyguard locked=false" in original_service)
        if args.existing_setup:
            other_services = components(original_services) - {ACCESSIBILITY}
            check("existing selected backend and steps match requested setup",
                  reader_ready(original_service)
                  and f"enabled=true, steps={args.steps}" in original_service)
        else:
            other_services = remove_own_accessibility()
            check("fixture removes only this accessibility service",
                  ACCESSIBILITY not in components(device.shell(
                      "settings", "get", "secure",
                      "enabled_accessibility_services").strip()))
            saved_settings = settings_probe()
            configured = dict(
                saved_settings,
                operating_mode=args.backend.upper(),
                shizuku_enabled=args.backend == "shizuku",
                media_only=True,
                enabled=True,
                steps=args.steps)
            settings_probe(configured)
        device.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline and not backend_ready(device.service()):
            time.sleep(.2)
        traces.append({"label": "backend startup", "service": device.service()})
        check(args.backend + " volume backend is ready",
              backend_ready(device.service()))

        if args.preset_dex:
            device.adb("push", str(args.preset_dex.resolve()), remote_preset)
        preset_ready = True
        device.adb("push", str(args.keys.resolve()), remote_keys)
        device.shell("chmod", "755", remote_keys)
        hid_command = ["adb", "-s", args.serial, "shell"]
        hid_command += ["su", "-c", remote_keys] if args.root_hid else [remote_keys]
        hid = subprocess.Popen(
            hid_command,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True)
        check("external HID starts",
              bool(select.select([hid.stdout], [], [], 5)[0])
              and hid.stdout.readline().strip() == "READY")
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline and not reader_ready(device.service()):
            time.sleep(.2)
        check(args.backend + " raw input becomes ready",
              reader_ready(device.service()))

        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            state = device.service()
            enabled = components(device.shell(
                "settings", "get", "secure",
                "enabled_accessibility_services").strip())
            if ACCESSIBILITY in enabled and "accessibility connected=true" in state:
                break
            time.sleep(.2)
        check("existing accessibility remains connected" if args.existing_setup
              else "privileged mode restores and connects accessibility",
              ACCESSIBILITY in enabled and "accessibility connected=true" in state)
        check("other accessibility services remain enabled" if args.existing_setup
              else "automatic merge preserves other accessibility services",
              other_services.issubset(enabled))

        maximum = int(device.shell(
            "cmd", "media_session", "volume", "--stream", 3, "--get"
        ).split("range [0..", 1)[1].split("]", 1)[0])
        if not 0 <= args.baseline <= maximum:
            raise RuntimeError("Baseline outside native range")
        device.preset(args.baseline)
        time.sleep(1)
        check(args.backend + " raw input remains ready after HID creation",
              reader_ready(device.service()))

        up = expected(args.baseline, 1, maximum)
        send("+")
        check("unlocked raw up reaches one configured step", wait_for_volume(up))
        up_state = device.service()
        traces.append({"label": "up", "volume": device.volume(), "service": up_state})
        check("unlocked up uses privileged raw ownership",
              "raw privileged screen-on correction at release" in up_state)
        check("accessibility callback does not duplicate privileged raw input",
              "accessibility connected=true" in up_state
              and device.volume() == up)

        send("-")
        check("unlocked raw reversal returns exactly to origin",
              wait_for_volume(args.baseline))
        down_state = device.service()
        traces.append({"label": "reversal", "volume": device.volume(), "service": down_state})
        check("one external HID pair creates two corrections",
              down_state.count("raw privileged screen-on correction at release") >= 2)

        for _ in range(5):
            send("-")
        rapid_down = expected(args.baseline, -5, maximum)
        rapid_down_reached = wait_for_volume(rapid_down)
        rapid_down_state = device.service()
        traces.append({"label": "rapid downs", "volume": device.volume(),
                       "service": rapid_down_state})
        check("rapid unlocked downs reach five configured steps",
              rapid_down_reached)
        time.sleep(.4)
        check("rapid unlocked downs do not rebound after settling",
              device.volume() == rapid_down)
        for _ in range(5):
            send("+")
        rapid_reversed = wait_for_volume(args.baseline)
        rapid_up_state = device.service()
        traces.append({"label": "rapid reversal", "volume": device.volume(),
                       "service": rapid_up_state})
        check("rapid unlocked reversal restores the origin", rapid_reversed)

        before_hold = device.service()
        timing = re.search(r"key repeat timeout/delay ms=(\d+)/(\d+)", before_hold)
        check("controller reports Android repeat timing", timing is not None)
        timeout_ms, delay_ms = map(int, timing.groups())
        hold_presses = (1 if 700 < timeout_ms else
                        2 + (700 - timeout_ms) // max(1, delay_ms))
        check_hold("D", args.baseline, -1, maximum, hold_presses)
        # Faster native repeats may add different progress in opposite holds.
        # Exact reversal belongs to the short-press checks above; reset to a
        # fresh observed origin to verify the UP hold without clipping early.
        up_origin = maximum - args.baseline
        device.preset(up_origin)
        time.sleep(1)
        check_hold("U", up_origin, 1, maximum, hold_presses)
    except Exception as exception:
        errors.append(str(exception))
        try:
            traces.append({"label": "failure before restoration",
                           "volume": device.volume(), "service": device.service()})
        except Exception as capture_error:
            errors.append("failure capture: " + str(capture_error))
    finally:
        try:
            if saved_settings is not None:
                settings_probe(saved_settings)
                device.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
            stop_hid()
            try:
                if preset_ready:
                    device.preset(original_volume)
                elif device.volume() != original_volume:
                    raise RuntimeError("Preset helper unavailable for volume restoration")
            except Exception as exception:
                errors.append("restore volume: " + str(exception))
            device.shell("rm", "-f", remote_keys, remote_preset)
            device.shell("settings", "put", "secure",
                         "enabled_accessibility_services", original_services or "null")
            device.shell("settings", "put", "secure",
                         "accessibility_enabled", original_accessibility)
            check("native volume restored", device.volume() == original_volume)
            final_accessibility = components(device.shell(
                "settings", "get", "secure",
                "enabled_accessibility_services").strip())
            check("original accessibility settings restored",
                  components(original_services) == final_accessibility
                  and device.shell("settings", "get", "secure",
                                   "accessibility_enabled").strip() == original_accessibility)
            check("other accessibility services remain preserved",
                  components(original_services) - {ACCESSIBILITY}
                  <= final_accessibility)
            deadline = time.monotonic() + 8
            original_reader = reader_ready(original_service)
            while (reader_ready(device.service()) != original_reader
                   and time.monotonic() < deadline):
                time.sleep(.2)
            final = device.service()
            check("original key backend is restored",
                  backend_ready(final) == backend_ready(original_service)
                  and reader_ready(final) == reader_ready(original_service)
                  and "pending correction=none" in final)
        except Exception as exception:
            errors.append("restore: " + str(exception))
        stop_hid()

    apk_path = device.shell("pm", "path", PACKAGE).strip().splitlines()[0]
    apk_path = apk_path.removeprefix("package:")
    report = {
        "serial": args.serial,
        "apk_sha256": hashlib.sha256(
            device.adb("exec-out", "cat", apk_path, binary=True)).hexdigest(),
        "fixture_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "hid_sha256": hashlib.sha256(args.keys.read_bytes()).hexdigest(),
        "preset": "shell UID dex" if args.preset_dex else "cmd media_session; verified readback",
        "input": "external UHID consumer keys; built-in physical buttons not pressed",
        "backend": args.backend,
        "existing_setup": args.existing_setup,
        "before": {
            "volume": original_volume,
            "accessibility_services": original_services,
            "accessibility_enabled": original_accessibility,
            "service": original_service,
        },
        "checks": checks,
        "traces": traces,
        "errors": errors,
        "success": not errors and all(item["pass"] for item in checks),
    }
    path = output / ("privileged-unlocked-" + str(time.time_ns()) + ".json")
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(path)
    raise SystemExit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
