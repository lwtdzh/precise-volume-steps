#!/usr/bin/env python3
"""Real Android locked-screen input and visible-panel checks using external HID."""
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

from android_device import Device, PACKAGE, HOST, normalized_components


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--keys", required=True, type=Path)
    parser.add_argument("--input-kind", choices=["uhid", "usb-hid"], default="uhid",
                        help="How the supplied key tool generates external HID input")
    parser.add_argument("--preset-dex", required=True, type=Path)
    parser.add_argument("--backend", choices=["ASSISTANT", "SHIZUKU", "ROOT"], required=True)
    parser.add_argument("--steps", required=True, type=int)
    parser.add_argument("--unlock-command", help="Private local command; never retained in reports")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or root in output.parents or not 5 <= args.steps <= 150:
        parser.error("Use an output outside the repository and 5..150 steps")
    output.mkdir(parents=True, exist_ok=True)
    d = Device(args.serial, "/data/local/tmp/pvs-lock-test-preset.dex")
    checks, errors, traces = [], [], []
    saved = None
    hid = None
    original = d.service()
    original_volume = d.volume()
    original_accessibility = d.shell("settings", "get", "secure",
                                     "enabled_accessibility_services").strip()
    if "screen interactive=true, keyguard locked=false" not in original:
        parser.error("Start awake and unlocked so setup and restoration are possible")
    if "playback active=false" not in original:
        parser.error("Pause existing playback before this controlled idle/playback fixture")
    before = {"service": original, "volume": original_volume,
              "accessibility": original_accessibility}
    hashes = {}
    for package in (PACKAGE, HOST, "com.vivo.finevolumesteps.helper"):
        path = d.shell("pm", "path", package).strip().splitlines()[0][8:]
        hashes[package] = hashlib.sha256(d.adb("exec-out", "cat", path, binary=True)).hexdigest()

    def check(name, value):
        checks.append({"test": name, "pass": bool(value)})
        print(("PASS " if value else "FAIL ") + name, flush=True)

    def settings_probe(values=None):
        command = ["am", "instrument", "-w"]
        if values is not None:
            command += ["-e", "restore",
                        base64.b64encode(json.dumps(values).encode()).decode()]
        text = d.shell(*command, HOST + "/.PhoneProbeInstrumentation")
        if "INSTRUMENTATION_CODE: 0" not in text or "INSTRUMENTATION_RESULT: error=" in text:
            raise RuntimeError("Settings fixture did not complete")
        return json.loads(re.search(r"INSTRUMENTATION_RESULT: settings=(.*)", text).group(1))

    def unlock():
        d.shell("input", "keyevent", 224)
        locked = "keyguard locked=true" in d.service()
        if locked and args.unlock_command:
            subprocess.run(shlex.split(args.unlock_command), check=True, timeout=30,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        elif locked:
            d.shell("wm", "dismiss-keyguard")
        if "screen interactive=true, keyguard locked=false" not in d.service():
            raise RuntimeError("Unlock failed")

    def ready():
        state = d.service()
        if args.backend == "ROOT":
            return "Root 已连接" in state and re.search(r"Root=.*devices=[1-9]", state)
        if args.backend == "SHIZUKU":
            return "Shizuku=enabled=true, ready=true" in state and "raw volume keys=ready=true" in state
        return "companion=supported=true, ready=true" in state and "registered=true" in state

    def send(key):
        hid.stdin.write(key)
        hid.stdin.flush()
        if not select.select([hid.stdout], [], [], 3)[0] or hid.stdout.readline().strip() != "OK " + key:
            raise RuntimeError("External key acknowledgement failed")

    def capture(label):
        state = d.service()
        traces.append({"label": label, "service": state, "volume": d.volume()})
        return state

    def helper_state(label, active):
        # Main-service diagnostics cache helper health for up to two seconds.
        # This checks the settled lifecycle, not immediate key-delivery timing.
        deadline = time.monotonic() + 3
        marker = "registered=true, active=" + str(active).lower()
        while marker not in d.service() and time.monotonic() < deadline:
            time.sleep(.1)
        return capture(label)

    def panel(label):
        text = d.shell("dumpsys", "window", "windows")
        blocks = re.split(r"(?=  Window #\d+ )", text)
        own = next((block for block in blocks if re.match(
            r"  Window #\d+ Window\{[^\n]*PreciseVolumeSteps", block)), "")
        (output / (label + "-window.txt")).write_text(own)
        visible = "isVisible=true" in own and "isOnScreen=true" in own
        check(label + " panel is visible above keyguard", visible)
        check(label + " uses accessibility overlay", "ty=ACCESSIBILITY_OVERLAY" in own)
        (output / (label + ".png")).write_bytes(
            d.adb("exec-out", "screencap", "-p", binary=True))

    try:
        saved = settings_probe()
        before["settings"] = saved
        settings_probe(dict(saved, enabled=True, steps=args.steps, media_only=True,
                            operating_mode=args.backend, shizuku_enabled=args.backend == "SHIZUKU"))
        d.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        d.restore_accessibility()
        d.adb("push", str(args.preset_dex), d.preset_dex)
        d.adb("push", str(args.keys), "/data/local/tmp/pvs-lock-test-hid")
        d.shell("chmod", 755, "/data/local/tmp/pvs-lock-test-hid")
        hid = subprocess.Popen(["adb", "-s", args.serial, "shell", "/data/local/tmp/pvs-lock-test-hid"],
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        if not select.select([hid.stdout], [], [], 5)[0] or hid.stdout.readline().strip() != "READY":
            raise RuntimeError("HID setup failed")
        deadline = time.monotonic() + 8
        while not ready() and time.monotonic() < deadline:
            time.sleep(.2)
        if not ready():
            raise RuntimeError("Selected write/input backend is unavailable")
        check("selected backend ready", True)
        maximum = int(re.search(r"native media volume=\d+ / (\d+)", d.service()).group(1))
        minimum = int(re.search(r", min=(\d+)", d.service()).group(1))
        intervals = min(args.steps, maximum - minimum)
        origin = minimum + (maximum - minimum) // 2
        for playback in (False, True):
            unlock()
            d.shell("am", "start", "-f", "0x20000000", "-n", HOST + "/.HostActivity",
                    "--ez", "play", str(playback).lower(), "--ez", "media_session", "true")
            d.shell("input", "keyevent", 3)
            d.preset(origin)
            d.shell("input", "keyevent", 223)
            time.sleep(.6)
            if args.backend == "ASSISTANT":
                state = helper_state(("playback" if playback else "idle") + "-screen-off", True)
                check(("playback" if playback else "idle") + " helper starts while screen is off",
                      "screen interactive=false" in state
                      and "registered=true, active=true" in state)
            d.shell("input", "keyevent", 224)
            time.sleep(1)
            label = "playback" if playback else "idle"
            state = (helper_state(label + "-locked", True) if args.backend == "ASSISTANT"
                     else capture(label + "-locked"))
            if "screen interactive=true, keyguard locked=true" not in state:
                raise RuntimeError("Fixture did not reach the lit keyguard")
            check(label + " locked display is on", True)
            if args.backend == "ASSISTANT":
                check(label + " helper remains active on lit keyguard",
                      "registered=true, active=true" in state)
            main_pid = d.shell("pidof", PACKAGE).strip()
            for number, (key, position) in enumerate((("-", -1), ("-", -2), ("+", -1), ("+", 0))):
                target = origin + math.floor(position * (maximum - minimum) / intervals + .5)
                send(key)
                deadline = time.monotonic() + 1.2
                while d.volume() != target and time.monotonic() < deadline:
                    time.sleep(.03)
                check(label + f" key {number} exact target", d.volume() == target)
                state = capture(label + f"-key-{number}")
                check(label + f" key {number} retains lit keyguard",
                      "screen interactive=true, keyguard locked=true" in state)
                check(label + f" key {number} retains playback state",
                      ("playback active=true" in state) == playback)
                if number == 0:
                    panel(label)
                time.sleep(1.1)
                check(label + f" key {number} no late overwrite", d.volume() == target)
            check(label + " preserves main process", main_pid == d.shell("pidof", PACKAGE).strip())
            before_preset = d.service()
            show_count = re.search(r"show count=(\d+)", before_preset).group(1)
            d.preset(origin + 1)
            time.sleep(.7)
            state = capture(label + "-passive")
            check(label + " passive change stays unchanged", d.volume() == origin + 1)
            check(label + " passive change does not reopen panel",
                  "overlay=attached=false" in state
                  and re.search(r"show count=(\d+)", state).group(1) == show_count)
            if args.backend == "ASSISTANT" and not playback:
                unlock()
                time.sleep(.5)
                state = helper_state("idle-unlocked", False)
                check("helper stops when unlocked without wireless prewarm",
                      "active=false, prewarm=false" in state)
        unlock()
    except Exception as error:
        errors.append(str(error))
    finally:
        if hid is not None:
            try:
                hid.stdin.write("q")
                hid.stdin.flush()
                hid.communicate(timeout=5)
            except Exception as error:
                errors.append("HID cleanup: " + str(error))
        try:
            unlock()
            d.shell("am", "start", "-f", "0x20000000", "-n", HOST + "/.HostActivity",
                    "--ez", "play", "false")
            d.preset(original_volume)
            if saved is not None:
                settings_probe(saved)
            d.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
            d.restore_accessibility()
            d.shell("rm", "-f", "/data/local/tmp/pvs-lock-test-hid", d.preset_dex)
            time.sleep(.5)
            check("restored original volume", d.volume() == original_volume)
            check("restored other accessibility services", normalized_components(
                d.shell("settings", "get", "secure", "enabled_accessibility_services").strip())
                == normalized_components(original_accessibility))
        except Exception as error:
            errors.append("Restoration: " + str(error))
        report = {"serial": args.serial, "api": d.shell("getprop", "ro.build.version.sdk").strip(),
                  "apk_sha256": hashes[PACKAGE], "hashes": hashes, "before": before,
                  "after": d.service(), "backend": args.backend, "steps": args.steps,
                  "input": ("UHID emulated external keys" if args.input_kind == "uhid"
                            else "external USB HID")
                  + "; actual Android audio and lit keyguard",
                  "results": checks, "traces": traces, "errors": errors,
                  "success": bool(checks) and all(c["pass"] for c in checks) and not errors}
        (output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(f'{sum(c["pass"] for c in checks)}/{len(checks)}; errors={errors}', flush=True)
    raise SystemExit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
