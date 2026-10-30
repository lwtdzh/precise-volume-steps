#!/usr/bin/env python3
"""Installed-phone volume test with explicit backend, simulated keys and real audio."""
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
from android_device import Device, PACKAGE, HOST, UI_OBSERVER, normalized_components


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--keys", required=True, type=Path)
    parser.add_argument("--input-source", choices=("usb", "kernel"), default="usb",
                        help="USB consumer HID or BUS_HOST uinput; neither is physical input")
    parser.add_argument("--sleep-settle-seconds", type=float,
                        help="Wait after sleep before keys; default USB 1s, kernel 5s")
    parser.add_argument("--preset-dex", required=True, type=Path)
    parser.add_argument("--steps", required=True, type=int)
    parser.add_argument("--backend", choices=("shizuku", "ordinary"), default="shizuku")
    parser.add_argument("--scenario", choices=("both", "idle", "playback"), default="both")
    parser.add_argument("--ten-presses-only", action="store_true",
                        help="Record all ten down/up readbacks even when correction fails")
    parser.add_argument("--baseline", type=int)
    parser.add_argument("--unlock-command", help="External local command; credentials stay outside this script")
    parser.add_argument("--shizuku-starter", type=Path,
                        help="Also test Shizuku loss/recovery using its official ARM64 starter")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("Store phone captures outside the repository")
    if not 5 <= args.steps <= 150:
        parser.error("steps must be in 5..150")
    sleep_settle = args.sleep_settle_seconds
    if sleep_settle is None:
        sleep_settle = 5 if args.input_source == "kernel" else 1
    if not 1 <= sleep_settle <= 30:
        parser.error("sleep-settle-seconds must be in 1..30")
    if args.shizuku_starter and args.backend != "shizuku":
        parser.error("Shizuku recovery requires --backend shizuku")
    output.mkdir(parents=True, exist_ok=True)
    prefix = (f"screen-off-{args.input_source}-{args.backend}-{args.scenario}"
              f"-{args.steps}-{time.time_ns()}")
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
    restart_needed = False
    before = {"service": original_service, "volume": original_volume,
              "accessibility": original_accessibility,
              "boot_id": d.shell("cat", "/proc/sys/kernel/random/boot_id").strip(),
              "api": d.shell("getprop", "ro.build.version.sdk").strip(),
              "fingerprint": d.shell("getprop", "ro.build.fingerprint").strip(),
              "fixture_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "hid_sha256": hashlib.sha256(args.keys.read_bytes()).hexdigest()}
    apk_path = d.shell("pm", "path", PACKAGE).strip().splitlines()[0].removeprefix("package:")
    digest = hashlib.sha256(d.adb("exec-out", "cat", apk_path, binary=True)).hexdigest()
    host_path = d.shell("pm", "path", HOST).strip().splitlines()[0].removeprefix("package:")
    before["host_apk_sha256"] = hashlib.sha256(
        d.adb("exec-out", "cat", host_path, binary=True)).hexdigest()
    ui_path = d.shell("pm", "path", UI_OBSERVER).strip().splitlines()[0].removeprefix("package:")
    before["ui_observer_apk_sha256"] = hashlib.sha256(
        d.adb("exec-out", "cat", ui_path, binary=True)).hexdigest()
    if "companion=supported=true" in original_service:
        helper_path = d.shell("pm", "path", "com.vivo.finevolumesteps.helper").strip()
        helper_path = helper_path.splitlines()[0].removeprefix("package:")
        before["helper_apk_sha256"] = hashlib.sha256(
            d.adb("exec-out", "cat", helper_path, binary=True)).hexdigest()

    def check(name, passed, retain_failure=False):
        checks.append({"test": name, "pass": bool(passed)})
        print(("PASS " if passed else "FAIL ") + name, flush=True)
        if not passed and not retain_failure:
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

    def notification():
        # Retain only this application's notification, never other apps' contents.
        dump = d.shell("dumpsys", "notification", "--noredact")
        records = re.split(r"(?=NotificationRecord\()", dump)
        return next((record for record in records
                     if re.search(r"pkg=" + re.escape(PACKAGE) + r"\s", record)), "")

    def status_text():
        d.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        for _ in range(8):
            nodes = d.ui()
            status = next((n for n in nodes
                           if n.get("resource-id") == PACKAGE + ":id/shizuku_status"
                           and n.get("text")), None)
            if status is not None:
                return status.get("text")
            scroll = next((n for n in nodes if n.get("class") == "android.widget.ScrollView"), None)
            if scroll is None:
                break
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", scroll.get("bounds", "")))
            d.shell("input", "swipe", (x1 + 3 * x2) // 4, y2 - 150,
                    (x1 + 3 * x2) // 4, y1 + 150, 350)
            time.sleep(.4)
        raise RuntimeError("Shizuku status is not visible")

    try:
        d.adb("push", str(args.preset_dex.resolve()), d.preset_dex)
        preset_ready = True
        unlock()
        saved_shortcuts = d.snapshot_volume_shortcuts()
        before["volume_shortcuts"] = saved_shortcuts
        d.set_volume_shortcuts(saved_shortcuts, disabled=True)
        check("conflicting volume-key shortcuts are confirmed disabled", True)
        settings = settings_probe()
        configured = dict(settings, steps=args.steps, shizuku_enabled=args.backend == "shizuku",
                          operating_mode="SHIZUKU" if args.backend == "shizuku" else "ASSISTANT",
                          media_only=True, enabled=True)
        settings_probe(configured)
        d.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        d.restore_accessibility()
        if args.backend == "shizuku":
            check("Shizuku volume and raw input are ready",
                  "Shizuku=enabled=true, ready=true" in d.service()
                  and "raw volume keys=ready=true" in d.service())
        else:
            state = d.service()
            check("ordinary mode has Shizuku and raw input disabled",
                  "Shizuku=enabled=false, ready=false" in state
                  and "raw volume keys=ready=false" in state)
            check("ordinary mode has connected accessibility",
                  "accessibility connected=true" in state)
            if d.shell("getprop", "ro.product.manufacturer").strip().lower() == "vivo":
                check("ordinary Vivo helper is connected",
                      "companion=supported=true, ready=true" in state)
        d.adb("push", str(args.keys.resolve()), "/data/local/tmp/pvs-test-hid")
        d.shell("chmod", 755, "/data/local/tmp/pvs-test-hid")
        key_command = ["adb", "-s", args.serial, "shell", "/data/local/tmp/pvs-test-hid"]
        if args.input_source == "kernel":
            key_command.append("--kernel")
        hid = subprocess.Popen(key_command,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True)
        check("persistent " + args.input_source + " key simulation created",
              bool(select.select([hid.stdout], [], [], 5)[0])
              and hid.stdout.readline().strip() == "READY")
        before["input_capabilities_after_creation"] = d.shell("getevent", "-lp")
        time.sleep(1)
        if args.shizuku_starter:
            pid = d.shell("pidof", PACKAGE).strip()
            server = d.shell("pidof", "shizuku_server").strip().split()
            check("recovery fixture has one running Shizuku server", len(server) == 1)
            d.adb("push", str(args.shizuku_starter.resolve()), "/data/local/tmp/pvs-test-shizuku-starter")
            d.shell("chmod", 755, "/data/local/tmp/pvs-test-shizuku-starter")
            d.shell("kill", *server)
            restart_needed = True
            time.sleep(1)
            state = capture("Shizuku stopped")
            check("stopped Shizuku disables writes and raw reader",
                  "Shizuku=enabled=true, ready=false" in state
                  and "raw volume keys=ready=false" in state)
            ui_status = status_text()
            check("connected helper does not hide stopped Shizuku",
                  "Shizuku 未运行" in ui_status
                  and ("companion=supported=true, ready=true" not in state
                       or "独立兼容助手已连接" in ui_status))
            check("existing notification reports missing screen-off listener",
                  "熄屏按键监听未就绪" in notification()
                  and "Shizuku 未运行" in notification())
            apk = d.shell("pm", "path", "moe.shizuku.privileged.api").strip().splitlines()[0]
            started = d.shell("/data/local/tmp/pvs-test-shizuku-starter",
                              "--apk=" + apk.removeprefix("package:"))
            check("official Shizuku starter succeeds", "exit with 0" in started)
            deadline = time.monotonic() + 8
            while time.monotonic() < deadline:
                if "raw volume keys=ready=true" in d.service():
                    break
                time.sleep(.2)
            state = capture("Shizuku restarted")
            check("both Shizuku connections recover automatically",
                  "Shizuku=enabled=true, ready=true" in state
                  and "raw volume keys=ready=true" in state)
            restart_needed = False
            check("recovery preserves main process and configured steps",
                  d.shell("pidof", PACKAGE).strip() == pid
                  and "steps=" + str(args.steps) in state)
            check("recovered notification clears missing-listener warning",
                  "熄屏按键监听未就绪" not in notification()
                  and "精准音量正在运行" in notification())
        scenarios = (False, True) if args.scenario == "both" else (args.scenario == "playback",)
        for playback in scenarios:
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
            time.sleep(sleep_settle)
            check(label + " display is truly off", "screen interactive=false" in capture(label + " before"))
            if args.ten_presses_only:
                for direction, key in ((-1, "-"), (1, "+")):
                    for n in range(1, 11):
                        position = -n if direction < 0 else n - 10
                        send(key)
                        target = expected(origin, position, maximum)
                        matched = wait(target)
                        time.sleep(1.05)
                        state = capture(label + f" ten-only {key} press {n}")
                        check(label + f" ten-only {key} press {n} settled readback",
                              matched and d.volume() == target, retain_failure=True)
                        check(label + f" ten-only {key} press {n} remains asleep",
                              "screen interactive=false" in state
                              and "overlay=attached=false" in state)
                        check(label + f" ten-only {key} press {n} retains audio state",
                              ("playback active=true" in state) == playback)
                check(label + " ten-only preserves main and player processes",
                      d.shell("pidof", PACKAGE).strip() == pid
                      and d.shell("pidof", HOST).strip() == player_pid)
                continue
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
            for n in range(1, 11):
                send("-")
                check(label + f" ten-press sequence position {-n}",
                      wait(expected(origin, -n, maximum)))
            state = capture(label + " ten asleep down presses")
            check(label + " ten presses keep screen off and panel hidden",
                  "screen interactive=false" in state and "overlay=attached=false" in state)
            for n in range(9, -1, -1):
                send("+")
                check(label + f" ten-press reversal position {-n}",
                      wait(expected(origin, -n, maximum)))
            check(label + " ten-press reversal restores the origin", d.volume() == origin)
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
        if restart_needed:
            try:
                apk = d.shell("pm", "path", "moe.shizuku.privileged.api").strip().splitlines()[0]
                d.shell("/data/local/tmp/pvs-test-shizuku-starter",
                        "--apk=" + apk.removeprefix("package:"))
            except Exception as error:
                errors.append("Shizuku restoration: " + str(error))
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
            if saved_shortcuts is not None:
                check("volume-key shortcuts restored with confirmed readback", True)
            check("volume and other accessibility services restored",
                  d.volume() == original_volume
                  and normalized_components(d.shell(
                      "settings", "get", "secure", "enabled_accessibility_services").strip())
                  == normalized_components(original_accessibility))
            d.shell("rm", "-f", "/data/local/tmp/pvs-test-hid", d.preset_dex,
                    "/data/local/tmp/pvs-test-shizuku-starter")
            if "mWakefulness=Asleep" in original_screen:
                d.shell("input", "keyevent", 223)
        except Exception as error:
            errors.append("Restoration: " + str(error))
        report = {"serial": args.serial, "api": before["api"],
                  "apk_sha256": digest, "suite": "screen-off-hid",
                  "steps": args.steps, "backend": args.backend, "scenario": args.scenario,
                  "input_source": args.input_source, "sleep_settle_seconds": sleep_settle,
                  "ten_presses_only": args.ten_presses_only,
                  "shizuku_recovery": args.shizuku_starter is not None,
                  "input": ("BUS_HOST uinput kernel simulation"
                            if args.input_source == "kernel" else "external UHID consumer keys")
                           + "; built-in physical buttons not pressed",
                  "success": bool(checks) and all(c["pass"] for c in checks) and not errors,
                  "results": checks, "errors": errors, "before": before,
                  "traces": traces, "after": d.service()}
        (output / (prefix + ".json")).write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(output / (prefix + ".json"), flush=True)
    raise SystemExit(0 if report["success"] else 1)


if __name__ == "__main__":
    main()
