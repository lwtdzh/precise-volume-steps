#!/usr/bin/env python3
"""Installed-phone raw input test with external HID, real audio and one live main PID."""
import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import re
import select
import shlex
import subprocess
import time
from android_device import Device, PACKAGE, HOST, UI_OBSERVER


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--keys", required=True, type=Path)
    parser.add_argument("--preset-dex", required=True, type=Path)
    parser.add_argument("--steps", required=True, type=int)
    parser.add_argument("--baseline", type=int)
    parser.add_argument("--unlock-command", help="External local command; credentials stay outside this script")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("Store phone captures outside the repository")
    if not 5 <= args.steps <= 150:
        parser.error("steps must be in 5..150")
    output.mkdir(parents=True, exist_ok=True)
    prefix = f"screen-off-hid-{args.steps}-{time.time_ns()}"
    d = Device(args.serial, "/data/local/tmp/pvs-test-preset.dex")
    checks, errors, traces = [], [], []
    hid = None
    preset_ready = False
    settings = None
    original_volume = d.volume()
    original_accessibility = d.shell("settings", "get", "secure", "enabled_accessibility_services").strip()
    original_service = d.service()
    original_screen = d.shell("dumpsys", "power")
    saved_shortcuts = None
    before = {"service": original_service, "volume": original_volume,
              "accessibility": original_accessibility,
              "api": d.shell("getprop", "ro.build.version.sdk").strip(),
              "fingerprint": d.shell("getprop", "ro.build.fingerprint").strip()}
    apk_path = d.shell("pm", "path", PACKAGE).strip().splitlines()[0].removeprefix("package:")
    digest = hashlib.sha256(d.adb("exec-out", "cat", apk_path, binary=True)).hexdigest()
    host_path = d.shell("pm", "path", HOST).strip().splitlines()[0].removeprefix("package:")
    before["host_apk_sha256"] = hashlib.sha256(
        d.adb("exec-out", "cat", host_path, binary=True)).hexdigest()
    ui_path = d.shell("pm", "path", UI_OBSERVER).strip().splitlines()[0].removeprefix("package:")
    before["ui_observer_apk_sha256"] = hashlib.sha256(
        d.adb("exec-out", "cat", ui_path, binary=True)).hexdigest()

    def check(name, passed):
        checks.append({"test": name, "pass": bool(passed)})
        print(("PASS " if passed else "FAIL ") + name, flush=True)
        if not passed:
            raise RuntimeError(name)

    def capture(label):
        state = d.service()
        traces.append({"label": label, "volume": d.volume(), "service": state})
        return state

    def settings_probe(saved=None):
        command = ["am", "instrument", "-w"]
        if saved is not None:
            command += ["-e", "restore", base64.b64encode(json.dumps(saved).encode()).decode()]
        result = d.shell(*command, HOST + "/.PhoneProbeInstrumentation")
        if "INSTRUMENTATION_CODE: 0" not in result:
            raise RuntimeError("Settings fixture failed")
        return json.loads(re.search(r"INSTRUMENTATION_RESULT: settings=(.*)", result).group(1))

    def unlock():
        d.shell("input", "keyevent", 224)
        time.sleep(.3)
        locked = "keyguard locked=true" in d.service()
        if locked and args.unlock_command:
            # A firmware shortcut may have opened the secure camera over keyguard.
            d.shell("input", "keyevent", 4)
            d.shell("input", "keyevent", 223)
            time.sleep(.4)
            d.shell("input", "keyevent", 224)
            time.sleep(.4)
            # No shell interpolation or logging of the private external command.
            subprocess.run(shlex.split(args.unlock_command), check=True, timeout=25,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        elif locked:
            d.shell("wm", "dismiss-keyguard")
        time.sleep(.5)
        check("screen is awake and unlocked", "screen interactive=true, keyguard locked=false" in d.service())

    def send(key):
        hid.stdin.write(key)
        hid.stdin.flush()
        if not select.select([hid.stdout], [], [], 3)[0]:
            raise RuntimeError("HID acknowledgement timed out")
        if hid.stdout.readline().strip() != "OK " + key:
            raise RuntimeError("HID press failed")

    def wait(expected):
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            if d.volume() == expected:
                time.sleep(.15)
                return d.volume() == expected
            time.sleep(.04)
        return False

    def expected(origin, position, maximum):
        return max(0, min(maximum, origin + math.floor(
            position * maximum / min(args.steps, maximum) + .5)))

    try:
        d.adb("push", str(args.preset_dex.resolve()), d.preset_dex)
        preset_ready = True
        unlock()
        saved_shortcuts = d.snapshot_volume_shortcuts()
        before["volume_shortcuts"] = saved_shortcuts
        d.set_volume_shortcuts(saved_shortcuts, disabled=True)
        check("conflicting volume-key shortcuts are confirmed disabled", True)
        settings = settings_probe()
        configured = dict(settings, steps=args.steps, shizuku_enabled=True, media_only=True, enabled=True)
        settings_probe(configured)
        d.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        d.restore_accessibility()
        check("Shizuku volume and raw input are ready",
              "Shizuku=enabled=true, ready=true" in d.service()
              and "raw volume keys=ready=true" in d.service())
        d.adb("push", str(args.keys.resolve()), "/data/local/tmp/pvs-test-hid")
        d.shell("chmod", 755, "/data/local/tmp/pvs-test-hid")
        hid = subprocess.Popen(["adb", "-s", args.serial, "shell",
                                "/data/local/tmp/pvs-test-hid"],
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True)
        check("persistent external HID created", bool(select.select([hid.stdout], [], [], 5)[0])
              and hid.stdout.readline().strip() == "READY")
        time.sleep(1)
        for playback in (False, True):
            unlock()
            # Reusing the host task without SINGLE_TOP only brings it forward;
            # Android does not deliver the new play extra to onNewIntent.
            d.shell("am", "start", "-f", "0x20000000", "-n", HOST + "/.HostActivity",
                    "--ez", "play", str(playback).lower(), "--ez", "media_session", "true")
            time.sleep(.5)
            d.shell("input", "keyevent", 3)
            maximum = int(re.search(r"native media volume=\d+ / (\d+)", d.service()).group(1))
            origin = args.baseline if args.baseline is not None else math.floor(maximum * .81)
            d.preset(origin)
            time.sleep(.4)
            label = "playback" if playback else "idle"
            check(label + " audio state matches fixture",
                  ("playback active=true" in d.service()) == playback)
            pid = d.shell("pidof", PACKAGE).strip()
            player_pid = d.shell("pidof", HOST).strip()
            d.shell("input", "keyevent", 223)
            time.sleep(1)
            check(label + " display is truly off", "screen interactive=false" in capture(label + " before"))
            for n in range(1, 6):
                send("-")
                check(label + f" asleep press {n} has the exact native value",
                      wait(expected(origin, -n, maximum)))
                state = capture(label + f" asleep press {n}")
                check(label + f" press {n} keeps screen off and panel hidden",
                      "screen interactive=false" in state and "overlay=attached=false" in state)
            unlock()
            check(label + " UI inspection preserves the player process",
                  d.shell("pidof", HOST).strip() == player_pid)
            check(label + " audio state remains correct across wake and unlock",
                  ("playback active=true" in d.service()) == playback)
            send("-")
            check(label + " awake sixth press continues the same path",
                  wait(expected(origin, -6, maximum)))
            capture(label + " awake sixth press")
            check(label + " main PID is unchanged across sleep and wake",
                  d.shell("pidof", PACKAGE).strip() == pid)
            # Retrace through the same installed service while asleep.
            d.shell("input", "keyevent", 223)
            time.sleep(.6)
            for n in range(5, -1, -1):
                send("+")
                check(label + f" reverse to position {-n}",
                      wait(expected(origin, -n, maximum)))
            check(label + " reversal restores the exact off-grid origin", d.volume() == origin)
            check(label + " audio state remains correct before rapid burst",
                  ("playback active=true" in capture(label + " before rapid burst")) == playback)
            # No per-key settling: several real raw presses share pending work.
            for key in ("-", "-", "-", "+", "-", "-", "+"):
                send(key)
            check(label + " rapid mixed burst ends three steps below origin",
                  wait(expected(origin, -3, maximum)))
            check(label + " rapid mixed burst keeps display off",
                  "screen interactive=false" in capture(label + " rapid mixed burst"))
            for key in ("+", "+", "+"):
                send(key)
            check(label + " rapid reversal restores the exact origin", wait(origin))
            check(label + " rapid reversal keeps display off",
                  "screen interactive=false" in capture(label + " rapid reversal"))
            send("D")
            check(label + " released hold counts four configured steps",
                  wait(expected(origin, -4, maximum)))
            check(label + " down hold keeps display off",
                  "screen interactive=false" in capture(label + " down hold"))
            send("U")
            check(label + " opposite hold restores the exact origin", wait(origin))
            check(label + " up hold keeps display off",
                  "screen interactive=false" in capture(label + " up hold"))
            time.sleep(2.2)
            state = capture(label + " final asleep")
            check(label + " audio state remains correct after bursts and holds",
                  ("playback active=true" in state) == playback)
            check(label + " wake lock expires and display stays off",
                  "key wake lock held=false" in state and "screen interactive=false" in state)
            # A passive preset in this same power state must not be inferred as a key.
            d.preset(origin - 1)
            time.sleep(.8)
            check(label + " passive asleep preset remains unchanged", d.volume() == origin - 1)
            check(label + " passive asleep preset keeps panel hidden",
                  "overlay=attached=false" in capture(label + " passive preset"))
    except Exception as error:
        errors.append(str(error))
        try: capture("failure")
        except Exception: pass
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
            unlock()
            d.shell("am", "start", "-f", "0x20000000", "-n", HOST + "/.HostActivity",
                    "--ez", "play", "false")
            if preset_ready:
                d.preset(original_volume)
        except Exception as error:
            errors.append("Volume/playback restoration: " + str(error))
        try:
            if settings is not None:
                settings_probe(settings)
                d.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
                d.restore_accessibility()
        except Exception as error:
            errors.append("Settings/accessibility restoration: " + str(error))
        try:
            d.set_volume_shortcuts(saved_shortcuts, disabled=False)
            check("volume-key shortcuts restored with confirmed readback", True)
            check("volume and other accessibility services restored",
                  d.volume() == original_volume
                  and set(d.shell("settings", "get", "secure", "enabled_accessibility_services")
                          .strip().split(":")) == set(original_accessibility.split(":")))
            d.shell("rm", "-f", "/data/local/tmp/pvs-test-hid", d.preset_dex)
            if "mWakefulness=Asleep" in original_screen:
                d.shell("input", "keyevent", 223)
        except Exception as error:
            errors.append("Restoration: " + str(error))
        report = {"serial": args.serial, "api": before["api"],
                  "apk_sha256": digest, "suite": "screen-off-hid",
                  "steps": args.steps, "backend": "shizuku",
                  "input": "external UHID consumer keys; built-in physical buttons not pressed",
                  "success": bool(checks) and all(c["pass"] for c in checks) and not errors,
                  "results": checks, "errors": errors, "before": before,
                  "traces": traces, "after": d.service()}
        (output / (prefix + ".json")).write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(output / (prefix + ".json"), flush=True)
    raise SystemExit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
