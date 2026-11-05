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
    parser.add_argument("--preset-dex", required=True, type=Path)
    parser.add_argument("--backend", choices=("shizuku", "root"), required=True)
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
    device = Device(args.serial, remote_preset)
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

    def wait_for_volume(expected):
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            if device.volume() == expected:
                time.sleep(.35)
                return device.volume() == expected
            time.sleep(.04)
        return False

    def expected(origin, position, maximum):
        intervals = min(args.steps, maximum)
        return max(0, min(maximum, origin + math.floor(
            position * maximum / intervals + .5)))

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
        return re.search(r"\nRoot=.*devices=[1-9]\d*, input error=null", state) is not None

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
        while time.monotonic() < deadline and not reader_ready(device.service()):
            time.sleep(.2)
        check(args.backend + " volume and raw input are ready",
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
        check("privileged mode restores and connects accessibility",
              ACCESSIBILITY in enabled and "accessibility connected=true" in state)
        check("automatic merge preserves other accessibility services",
              other_services.issubset(enabled))

        maximum = int(device.shell(
            "cmd", "media_session", "volume", "--stream", 3, "--get"
        ).split("range [0..", 1)[1].split("]", 1)[0])
        if not 0 <= args.baseline <= maximum:
            raise RuntimeError("Baseline outside native range")
        device.adb("push", str(args.preset_dex.resolve()), remote_preset)
        preset_ready = True
        device.adb("push", str(args.keys.resolve()), remote_keys)
        device.shell("chmod", "755", remote_keys)
        device.preset(args.baseline)

        hid = subprocess.Popen(
            ["adb", "-s", args.serial, "shell", remote_keys],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True)
        check("external HID starts",
              bool(select.select([hid.stdout], [], [], 5)[0])
              and hid.stdout.readline().strip() == "READY")
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
        check("one physical pair creates two corrections",
              down_state.count("raw privileged screen-on correction at release") >= 2)
    except Exception as exception:
        errors.append(str(exception))
    finally:
        if hid is not None:
            try:
                hid.stdin.write("q")
                hid.stdin.flush()
                hid.communicate(timeout=5)
            except Exception:
                hid.kill()
                hid.communicate()
        try:
            if saved_settings is not None:
                settings_probe(saved_settings)
                device.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
                deadline = time.monotonic() + 8
                while time.monotonic() < deadline:
                    current_services = components(device.shell(
                        "settings", "get", "secure",
                        "enabled_accessibility_services").strip())
                    if (ACCESSIBILITY in current_services
                            and device.shell("settings", "get", "secure",
                                             "accessibility_enabled").strip() == "1"):
                        break
                    time.sleep(.2)
            if preset_ready:
                device.preset(original_volume)
            elif device.volume() != original_volume:
                raise RuntimeError("Preset helper unavailable for volume restoration")
            device.shell("rm", "-f", remote_keys, remote_preset)
            check("native volume restored", device.volume() == original_volume)
            final_accessibility = components(device.shell(
                "settings", "get", "secure",
                "enabled_accessibility_services").strip())
            check("automatic accessibility remains enabled after restoration",
                  ACCESSIBILITY in final_accessibility
                  and device.shell("settings", "get", "secure",
                                   "accessibility_enabled").strip() == "1")
            check("other accessibility services remain preserved",
                  components(original_services) - {ACCESSIBILITY}
                  <= final_accessibility)
            final = device.service()
            check("original key backend is restored",
                  ("Shizuku=enabled=true, ready=true" in final
                   and "raw volume keys=ready=true" in final)
                  == ("Shizuku=enabled=true, ready=true" in original_service
                      and "raw volume keys=ready=true" in original_service)
                  and "pending correction=none" in final)
        except Exception as exception:
            errors.append("restore: " + str(exception))

    apk_path = device.shell("pm", "path", PACKAGE).strip().splitlines()[0]
    apk_path = apk_path.removeprefix("package:")
    report = {
        "serial": args.serial,
        "apk_sha256": hashlib.sha256(
            device.adb("exec-out", "cat", apk_path, binary=True)).hexdigest(),
        "fixture_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "hid_sha256": hashlib.sha256(args.keys.read_bytes()).hexdigest(),
        "input": "external UHID consumer keys; built-in physical buttons not pressed",
        "backend": args.backend,
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
