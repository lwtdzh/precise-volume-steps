#!/usr/bin/env python3
"""Rootable AOSP emulator tests. The delivered APK does not use root.

Build with -PvolumeStepsE2E=true, compile native_keys.c using the NDK, then:
python3 tests/e2e.py --serial emulator-5578 --keys /tmp/fvs-native-keys

Volume baselines are set BEFORE physical input. AudioTrack playback and Android's
own input/audio handling produce every volume change under test.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PKG = "roro.stellar.volumesteps"
HOST = PKG + ".testhost"
SERVICE = PKG + "/.FineVolumeAccessibilityService"


class Suite:
    def __init__(self, args):
        self.args = args
        self.output = Path(args.output) / args.serial
        self.output.mkdir(parents=True, exist_ok=True)
        self.results = []
        self.api = self.shell("getprop", "ro.build.version.sdk").strip()
        self.maximum = int(re.search(r"range \[\d+\.\.(\d+)\]", self.shell(
            "cmd", "media_session", "volume", "--stream", "3", "--get")).group(1))

    def adb(self, *args, timeout=25, check=True):
        p = subprocess.run(["adb", "-s", self.args.serial, *map(str, args)],
                           capture_output=True, timeout=timeout)
        if check and p.returncode:
            raise RuntimeError(p.stderr.decode(errors="replace"))
        return p.stdout.decode(errors="replace")

    def shell(self, *args, **kwargs):
        return self.adb("shell", *args, **kwargs)

    def check(self, name, condition, detail=""):
        item = {"test": name, "pass": bool(condition), "detail": str(detail)}
        self.results.append(item)
        print(("PASS " if condition else "FAIL ") + name + " " + str(detail), flush=True)
        self.save()

    def save(self):
        (self.output / "results.json").write_text(json.dumps({
            "api": self.api, "serial": self.args.serial,
            "apk_sha256": getattr(self, "apk_hash", None), "results": self.results,
            "input": "external emulator input; actual Android audio",
            "success": getattr(self, "completed", False)
        }, ensure_ascii=False, indent=2))

    def ui(self, name="ui"):
        for attempt in range(3):
            result = self.shell("am", "instrument", "-w", HOST + "/.DumpUiInstrumentation")
            if "No active window" not in result:
                break
            # Settings can briefly have no active window while changing pages.
            time.sleep(.5)
        if "INSTRUMENTATION_CODE: 0" not in result:
            raise RuntimeError(result)
        xml = self.shell("cat", "/sdcard/Android/data/" + HOST + "/files/ui.xml")
        (self.output / (name + ".xml")).write_text(xml)
        return ET.fromstring(xml)

    def find(self, tree, text=None, rid=None, cls=None):
        for node in tree.iter("node"):
            if text is not None and text not in node.get("text", ""):
                continue
            if rid is not None and not node.get("resource-id", "").endswith(rid):
                continue
            if cls is not None and not node.get("class", "").endswith(cls):
                continue
            return node
        raise AssertionError(f"UI node missing: text={text} id={rid} class={cls}")

    def tap(self, node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        self.shell("input", "tap", (x1+x2)//2, (y1+y2)//2)
        time.sleep(.4)

    def click(self, text=None, rid=None, cls=None):
        self.tap(self.find(self.ui(), text, rid, cls))

    def scroll_to(self, text=None, rid=None, cls=None):
        for _ in range(6):
            tree = self.ui()
            node = self.find(tree, text, rid, cls)
            _, top, _, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
            _, _, width, height = map(int, re.findall(r"\d+", tree[0].get("bounds")))
            if 0 <= top < bottom <= height - 60:
                return node
            x = round(width * .75)  # Keep the gesture away from the step picker.
            start, end = (round(height * .8), round(height * .35))
            if top < 0:
                start, end = end, start
            self.shell("input", "swipe", x, start, x, end, 300)
        raise AssertionError("UI control did not become fully visible")

    def screenshot(self, name):
        p = subprocess.run(["adb", "-s", self.args.serial, "exec-out", "screencap", "-p"],
                           capture_output=True, check=True, timeout=20)
        (self.output / (name + ".png")).write_bytes(p.stdout)

    def launch(self, host=False, consume=False, play=True, stream=3,
               native_stride=1, media_session=False, player_keys=False):
        if host:
            self.shell("am", "start", "-f", "0x14000000", "-n", HOST + "/.HostActivity",
                       "--ez", "consume", str(consume).lower(),
                       "--ez", "play", str(play).lower(), "--ei", "stream", stream,
                       "--ei", "native_stride", native_stride,
                       "--ez", "media_session", str(media_session).lower(),
                       "--ez", "player_keys", str(player_keys).lower())
        else:
            self.shell("am", "start", "-f", "0x14000000", "-n", PKG + "/.MainActivity")
        time.sleep(.6)

    def a11y(self, enabled):
        # Used for repeatable lifecycle cases; first grant is through Settings UI.
        self.shell("settings", "put", "secure", "enabled_accessibility_services",
                   SERVICE if enabled else "null")
        self.shell("settings", "put", "secure", "accessibility_enabled", int(enabled))
        time.sleep(1)

    def prepare(self):
        self.adb("root")
        self.adb("wait-for-device")
        self.adb("push", self.args.keys, "/data/local/tmp/fvs-native-keys")
        self.shell("chmod", "755", "/data/local/tmp/fvs-native-keys")
        for scale in ["window_animation_scale", "transition_animation_scale",
                      "animator_duration_scale"]:
            self.shell("settings", "put", "global", scale, "0")
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        self.shell("input", "keyevent", "KEYCODE_BACK")
        self.shell("input", "keyevent", "KEYCODE_BACK")
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.shell("setprop", "log.tag.FineVolumeService", "DEBUG")
        self.shell("setprop", "log.tag.FineVolumeAdjustment", "DEBUG")
        apk = Path(self.args.apk)
        self.adb("install", "-r", apk)
        self.apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
        self.adb("install", "-r", ROOT / "tests/host/build/outputs/apk/debug/precise-volume-steps-test-host-debug.apk")
        self.shell("logcat", "-c")

    def permission_setup(self, deny=False):
        self.a11y(False)
        self.shell("pm", "clear", PKG)
        self.shell("appops", "set", PKG, "SYSTEM_ALERT_WINDOW", "deny")
        self.shell("cmd", "deviceidle", "whitelist", "-" + PKG)
        self.launch()
        tree = self.ui("clean-install")
        switch = self.find(tree, rid="id/enable_correction")
        self.check("clean install switch OFF", switch.get("checked") == "false")
        status = self.find(tree, rid="id/background_status").get("text")
        self.check("clean install status agrees", "音量修正已关闭" in status, status)
        self.check("AOSP has no vendor self-start page",
                   any("无需额外自启动授权" in n.get("text", "") for n in tree.iter("node")))
        self.tap(switch)
        tree = self.ui("accessibility-explanation")
        self.check("turning ON requests accessibility",
                   self.find(tree, rid="android:id/alertTitle").get("text") == "开启音量按键服务")
        self.tap(self.find(tree, rid="android:id/button1"))
        tree = self.ui("accessibility-settings")
        if not any(n.get("class", "").endswith("Switch") for n in tree.iter("node")):
            # Android 13 details intent falls back to the installed-service list.
            self.tap(self.find(tree, text="精准音量服务"))
            tree = self.ui("accessibility-service-settings")
            if not any(n.get("class", "").endswith("Switch") for n in tree.iter("node")):
                self.tap(self.find(tree, text="精准音量服务"))
                time.sleep(.7)
                tree = self.ui("accessibility-service-settings")
        self.tap(self.find(tree, cls="Switch"))
        tree = self.ui("accessibility-grant")
        candidates = [n for n in tree.iter("node")
                      if n.get("resource-id") in ("android:id/button1",)
                      or n.get("resource-id", "").endswith("permission_enable_allow_button")
                      or n.get("text") in ("Allow", "ALLOW", "允许")]
        self.tap(candidates[-1])
        self.shell("input", "keyevent", "KEYCODE_BACK")
        self.launch()
        tree = self.ui("notification-request")
        self.check(("denial: " if deny else "") + "runtime notification permission requested",
                   any("permissioncontroller" in n.get("package", "") for n in tree.iter("node")))
        self.tap(self.find(tree, rid="permission_deny_button" if deny else "permission_allow_button"))
        tree = self.ui("battery-request")
        self.check(("denial: " if deny else "") + "battery exemption requested automatically",
                   "IgnoreBatteryOptimizations" in self.shell("dumpsys", "activity", "activities"))
        self.tap(self.find(tree, rid="android:id/button1"))
        tree = self.ui("overlay-permission")
        if not any(n.get("class", "").endswith("Switch")
                   or n.get("text") == "Allow display over other apps"
                   for n in tree.iter("node")):
            # The app row can exist in the XML while still below the visible list.
            for _ in range(4):
                app = next((n for n in tree.iter("node")
                            if n.get("text") == PKG
                            or "精准音量" in n.get("text", "")
                            or n.get("content-desc") == "精准音量"), None)
                if app is None:
                    time.sleep(.5)
                    tree = self.ui("overlay-loading-list")
                    continue
                _, top, _, bottom = map(int, re.findall(r"\d+", app.get("bounds")))
                _, _, _, screen_bottom = map(int, re.findall(r"\d+", tree[0].get("bounds")))
                if top < bottom and bottom <= screen_bottom:
                    break
                self.shell("input", "swipe", "540", "1650", "540", "850", "300")
                tree = self.ui("overlay-scrolled-list")
            else:
                raise AssertionError("App's overlay permission row did not become visible")
            self.tap(app)
            tree = self.ui("overlay-app-permission")
        self.check("floating-window permission is requested", any(
            n.get("package") == "com.android.settings" for n in tree.iter("node")))
        switches = [n for n in tree.iter("node") if n.get("class", "").endswith("Switch")]
        if switches:
            if switches[0].get("checked") == "false":
                self.tap(switches[0])
        else:
            # Android 16 uses a Compose preference row instead of a Switch node.
            self.tap(self.find(tree, text="Allow display over other apps"))
        self.launch()
        tree = self.ui("running-denied" if deny else "running")
        self.check("floating-window permission is granted",
                   "悬浮窗已允许" in self.find(tree, rid="id/background_status").get("text"))
        status = self.find(tree, rid="id/background_status").get("text")
        self.check(("denial: " if deny else "") + "ON switch and running status agree",
                   "后台音量修正运行中" in status
                   and self.find(tree, rid="id/enable_correction").get("checked") == "true", status)
        self.screenshot("running-denied" if deny else "running")
        if deny:
            self.click(rid="id/background_permissions")
            tree = self.ui("notification-second-denial")
            denial = next(n for n in tree.iter("node")
                          if "permission_deny" in n.get("resource-id", ""))
            self.tap(denial)
            self.click(rid="id/background_permissions")
            activity = self.shell("dumpsys", "activity", "activities")
            self.check("permanent notification denial opens app notification settings",
                       "AppNotificationSettings" in activity or "APP_NOTIFICATION_SETTINGS" in activity)
            self.shell("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
            self.shell("input", "keyevent", "KEYCODE_BACK")
            self.launch()
        self.click(rid="id/auto_start_settings")
        tree = self.ui("standard-self-start-explanation")
        self.check("self-start button explains standard Android boot recovery",
                   any("开机广播" in n.get("text", "") for n in tree.iter("node")))
        self.tap(self.find(tree, rid="android:id/button1"))
        if not deny:
            tree = self.ui()
            picker = self.find(tree, cls="NumberPicker")
            self.tap(list(picker)[0])
            tree = self.ui("step-setting")
            self.check("in-app step picker changes persisted setting",
                       self.find(tree, rid="android:id/numberpicker_input").get("text") == "29")

    def configure_steps(self, steps):
        self.launch()
        picker = self.scroll_to(cls="NumberPicker")
        tree = self.ui()
        current = int(self.find(tree, rid="android:id/numberpicker_input").get("text"))
        decrement, increment = list(picker)[0], list(picker)[-1]
        for _ in range(abs(current - steps)):
            self.tap(decrement if current > steps else increment)
        saved = self.shell("cat", "/data/user_de/0/" + PKG
                           + "/shared_prefs/volume_step_settings.xml")
        self.check("step count set through app UI", f'name="steps" value="{steps}"' in saved)

    def volume(self, stream=3):
        text = self.shell("cmd", "media_session", "volume", "--stream", stream, "--get")
        return int(re.search(r"volume is (\d+)", text).group(1))

    def baseline(self, value, stream=3):
        time.sleep(.4)
        self.shell("cmd", "media_session", "volume", "--stream", stream, "--set", value)
        # Give the monitor time to observe this fixture value before input.
        time.sleep(.7)
        actual = self.volume(stream)
        if actual != value:
            raise AssertionError(f"baseline {value} != {actual}")

    def keys(self, sequence, hold=70, gap=100):
        self.shell("log", "-t", "FvsE2E", "keys:" + sequence + ":hold:" + str(hold))
        self.shell("/data/local/tmp/fvs-native-keys", sequence, hold, gap)

    def overlays(self):
        text = self.shell("dumpsys", "window", "windows")
        return re.findall(r"Window #\d+ Window\{([^\s]+) u0 PreciseVolumeSteps\}", text)

    def report(self):
        return self.shell("dumpsys", "activity", "service", PKG + ".VolumeKeeperService")

    def wait_report(self, predicate, timeout=1.5):
        deadline = time.monotonic() + timeout
        while True:
            report = self.report()
            if predicate(report) or time.monotonic() >= deadline:
                return report
            time.sleep(.025)

    def panel_count(self, report):
        return int(re.search(r"show count=(\d+)", report).group(1))

    def rejected_write_tests(self):
        if not self.args.serial.startswith("emulator-"):
            raise ValueError("Rejected-write tests require a disposable emulator")
        self.configure_steps(5)
        # The player changes native volume by four indices. Only the target
        # app's writes are denied; actual input and player changes still work.
        self.launch(host=True, native_stride=4)
        time.sleep(1.5)
        previous = self.shell("appops", "get", PKG, "AUDIO_MEDIA_VOLUME")
        match = re.search(r"AUDIO_MEDIA_VOLUME: (\w+)", previous)
        previous_mode = match.group(1) if match else "default"
        try:
            self.shell("appops", "set", PKG, "AUDIO_MEDIA_VOLUME", "ignore")
            self.baseline(6)
            before = self.panel_count(self.report())
            self.keys("+")
            report = self.wait_report(lambda r: "pending correction=3:9" in r)
            self.check("pending correction does not open a temporary-value panel",
                       "overlay=attached=false" in report
                       and self.panel_count(report) == before
                       and "pending correction=3:9" in report, report)
            # An unrelated change during the write is read back at its outcome.
            self.shell("cmd", "media_session", "volume", "--stream", "3", "--set", 11)
            report = self.wait_report(lambda r: "displayed volume=11" in r
                                      and "overlay=attached=true" in r)
            self.check("failed correction opens once with its actual final value",
                       "overlay=attached=true" in report and "displayed volume=11" in report
                       and "correction state=UNCONFIRMED" in report
                       and self.panel_count(report) == before + 1, report)
            (self.output / "rejected-write.txt").write_text(report)
            self.check("ignored write reports unconfirmed state with actual volume",
                       "correction state=UNCONFIRMED" in report
                       and "pending correction=none" in report and self.volume() == 11, report)
            report = self.wait_report(lambda r: "overlay=attached=false" in r)
            self.check("failed outcome panel dismisses without reopening or retrying",
                       "overlay=attached=false" in report
                       and self.panel_count(report) == before + 1
                       and len(re.findall(r"key write stream=", report)) == 1, report)
            self.shell("cmd", "media_session", "volume", "--stream", "3", "--set", 6)
            time.sleep(.8)
            self.check("idle and external preset after rejection stay quiet",
                       self.volume() == 6 and not self.overlays()
                       and self.panel_count(self.report()) == before + 1)
            self.keys("+")
            self.wait_report(lambda r: "pending correction=3:9" in r)
            self.keys("-")
            report = self.wait_report(lambda r: "displayed volume=6" in r
                                      and self.panel_count(r) == before + 2
                                      and "pending correction=none" in r)
            self.check("next press starts from actual value after rejected write",
                       self.volume() == 6 and "pending correction=none" in report
                       and "correction state=UNCONFIRMED" in report, report)
            time.sleep(1.5)
        finally:
            self.shell("appops", "set", PKG, "AUDIO_MEDIA_VOLUME", previous_mode)
        self.baseline(6)
        before = self.panel_count(self.report())
        self.keys("+")
        report = self.wait_report(lambda r: "correction state=CONFIRMED" in r
                                  and "displayed volume=9" in r
                                  and "overlay=attached=true" in r
                                  and self.panel_count(r) == before + 1)
        self.check("accepted correction updates same panel to confirmed actual value",
                   self.volume() == 9 and "overlay=attached=true" in report
                   and "displayed volume=9" in report
                   and self.panel_count(report) == before + 1, report)
        self.shell("cmd", "media_session", "volume", "--stream", "3", "--set", 7)
        report = self.wait_report(lambda r: "displayed volume=7" in r)
        self.check("passive change after success preserves actual value without a new show",
                   self.volume() == 7 and "displayed volume=7" in report
                   and self.panel_count(report) == before + 1, report)
        time.sleep(1.5)
        self.check("no late acknowledgement popup", not self.overlays())
        self.launch(host=True, consume=True)
        self.baseline(6)
        before = self.panel_count(self.report())
        self.keys("+")
        time.sleep(.9)
        self.check("consumed key without native change never opens panel",
                   self.volume() == 6 and not self.overlays()
                   and self.panel_count(self.report()) == before, self.report())

    def expected(self, initial, sequence, steps=5, maximum=None):
        maximum = self.maximum if maximum is None else maximum
        positions = sorted(set(round(i * maximum / min(steps, maximum))
                               for i in range(min(steps, maximum)+1)))
        value = initial
        for key in sequence:
            options = [p for p in positions if p > value] if key == "+" else [
                p for p in positions if p < value]
            value = (min(options) if key == "+" else max(options)) if options else value
        return value

    def audio_tests(self):
        self.configure_steps(5)
        self.launch(host=True)
        self.check("real media playback is active",
                   "state:started" in self.shell("dumpsys", "audio")
                   or "state=started" in self.shell("dumpsys", "audio"))
        self.shell("input", "keyevent", "KEYCODE_HOME")
        for label, sequence, initial in [
            ("background single up", "+", 6), ("background single down", "-", 6),
            ("rapid repeated up", "+++", 3), ("rapid repeated down", "---", 12),
            ("mixed directions", "+-+-+", 6), ("maximum", "+++", self.maximum),
            ("minimum", "---", 0)
        ]:
            self.baseline(initial)
            self.keys(sequence)
            actual = self.volume()
            expected = self.expected(initial, sequence)
            self.check(label, actual == expected, f"{initial} → {actual}, expected {expected}")
        self.launch(host=True)
        self.baseline(6)
        # Keep actual input active while inspecting the overlay; two separate
        # helper launches can exceed its 1.2 s timeout under emulator load.
        process = subprocess.Popen(
            ["adb", "-s", self.args.serial, "shell", "/data/local/tmp/fvs-native-keys",
             "+++", "70", "350"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        first = []
        try:
            for _ in range(30):
                first = self.overlays() if self.volume() >= 9 else []
                if first:
                    break
                time.sleep(.05)
            process.communicate(timeout=15)
            second = self.overlays()
        finally:
            if process.poll() is None:
                process.terminate()
                process.communicate(timeout=5)
        self.check("one overlay reused for consecutive corrections",
                   len(first) == 1 and first == second and self.volume() == 15,
                   f"{first} / {second}, volume {self.volume()}")
        windows = self.shell("dumpsys", "window", "windows")
        (self.output / "overlay-window.txt").write_text(windows)
        self.check("overlay neither takes focus nor handles touch",
                   bool(re.search(r"ty=(ACCESSIBILITY_OVERLAY|APPLICATION_OVERLAY|2032|2038)", windows))
                   and (("NOT_FOCUSABLE" in windows and "NOT_TOUCHABLE" in windows)
                        or bool(re.search(r"fl=0x[0-9a-f]*18\b", windows))))
        time.sleep(1.5)
        self.check("overlay dismisses after inactivity", not self.overlays())
        self.baseline(6)
        self.shell("cmd", "media_session", "volume", "--stream", "3", "--set", "7")
        time.sleep(1)
        self.check("external media changes are preserved without popup",
                   self.volume() == 7 and not self.overlays(), self.volume())
        self.check("notification includes normalized volume and configured steps",
                   "47 / 100" in self.notification() and "5 阶" in self.notification())
        self.baseline(3)
        self.keys("+", hold=1400)
        self.check("held volume key repeats to limit", self.volume() == self.maximum, self.volume())
        self.launch(host=True, consume=True)
        # AOSP's native volume dialog owns key focus until its timeout.
        time.sleep(3.5)
        self.check("key-consuming app has input focus",
                   bool(re.search(r"mCurrentFocus=.*testhost.*HostActivity",
                                  self.shell("dumpsys", "window"))))
        self.baseline(6)
        self.keys("++-")
        log = self.shell("logcat", "-d", "-s", "VolumeTestHost:I", "*:S")
        self.check("other app receives and consumes volume keys",
                   "consumed:24" in log and "consumed:25" in log, log[-600:])
        self.check("consumed keys do not change volume", self.volume() == 6, self.volume())
        time.sleep(1.3)
        self.check("consumed keys do not show correction overlay", not self.overlays())
        self.launch(host=True)
        self.baseline(6)
        screenshots_before = self.shell("find", "/sdcard/Pictures/Screenshots", "-type", "f", check=False)
        self.keys("screenshot")
        screenshots_after = self.shell("find", "/sdcard/Pictures/Screenshots", "-type", "f", check=False)
        self.check("screenshot chord creates an actual screenshot",
                   len(screenshots_after.splitlines()) > len(screenshots_before.splitlines()))
        self.check("power + volume-down screenshot leaves volume unchanged",
                   self.volume() == 6, self.volume())
        self.check("screenshot does not show correction overlay", not self.overlays())
        self.baseline(3)
        self.keys("+")
        corrected = self.volume()
        time.sleep(1.3)
        self.keys("screenshot")
        self.check("screenshot following successful correction leaves volume unchanged",
                   corrected == 6 and self.volume() == corrected and not self.overlays())
        # Stress repeated keys whose native system adjustment is still pending.
        self.shell("input", "keyevent", "KEYCODE_HOME")
        for run in range(3):
            for sequence in ("++++", "----", "+-+-", "--++", "++--"):
                self.baseline(6)
                self.keys(sequence, hold=20, gap=20)
                expected = self.expected(6, sequence)
                self.check(f"fast sequence {sequence} run {run+1}", self.volume() == expected,
                           f"{self.volume()}, expected {expected}")
        self.baseline(6)
        self.shell("input", "keyevent", "KEYCODE_SLEEP")
        self.keys("+")
        self.check("screen off without key callbacks preserves native volume",
                   self.volume() == 7, self.volume())
        self.check("screen off without key callbacks does not open panel", not self.overlays())
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        self.launch()
        self.tap(self.scroll_to(text="全部可识别音频流"))
        self.launch(host=True, play=False, stream=2)
        self.baseline(1, stream=2)
        self.keys("+")
        self.check("all-stream scope corrects native ring volume", self.volume(2) == 3, self.volume(2))
        self.launch()
        self.tap(self.scroll_to(text="仅媒体音量"))
        time.sleep(.5)
        settings = self.shell("cat", "/data/user_de/0/" + PKG
                              + "/shared_prefs/volume_step_settings.xml")
        self.check("media-only scope is persisted", 'name="media_only" value="true"' in settings)
        self.launch(host=True, play=False, stream=2)
        time.sleep(3.5)
        self.baseline(1, stream=2)
        self.keys("+")
        self.check("media-only scope preserves native ring adjustment", self.volume(2) == 2, self.volume(2))
        self.launch(host=True)
        self.configure_steps(30)
        self.launch(host=True)
        self.baseline(6)
        self.keys("+")
        self.check("native matching step keeps correct volume", self.volume() == 7, self.volume())
        self.check("native matching step still shows app popup", bool(self.overlays()))
        self.screenshot("popup-native-step")
        self.check("notification updates changed step setting",
                   "47 / 100" in self.notification() and "30 阶" in self.notification())
        self.configure_steps(5)

    def running(self):
        state = self.shell("dumpsys", "activity", "services", PKG)
        return ".VolumeKeeperService" in state and "isForeground=true" in state

    def notification(self):
        text = self.shell("dumpsys", "notification", "--noredact")
        for section in re.split(r"\n\s+NotificationRecord\(", text):
            if re.search(r"pkg=" + re.escape(PKG) + r"(\s|,)", section):
                return section
        return ""

    def wait_running(self):
        for _ in range(30):
            if self.running():
                time.sleep(.8)
                return True
            time.sleep(1)
        return False

    def wait_stopped(self):
        # Service removal is asynchronous; Android can retain its service
        # record briefly after stopService has returned.
        for _ in range(30):
            if not self.running():
                return True
            time.sleep(.1)
        return False

    def correction(self, label):
        self.baseline(6)
        self.keys("+")
        self.check(label, self.volume() == 9, self.volume())

    def display_tests(self):
        self.configure_steps(30)
        self.launch(host=True)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.baseline(6)
        self.keys("+")
        text = self.notification()
        self.check("notification shows current configured step position",
                   "47 / 100" in text and "14 / 30 阶" in text,
                   "native 7/15, display 47/100, configured 14/30")
        self.shell("cmd", "statusbar", "expand-notifications")
        time.sleep(3.5)  # Wait for the native volume dialog to leave the screenshot.
        self.screenshot("notification-configured-step")
        self.shell("cmd", "statusbar", "collapse")
        self.configure_steps(5)
        self.a11y(False)
        self.launch(host=True)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.shell("kill", "-9", self.shell("pidof", PKG).strip())
        self.check("process kill without accessibility restores foreground monitor",
                   self.wait_running())
        self.baseline(6)
        self.keys("+")
        self.check("native adjustment after recovery without accessibility",
                   self.volume() == 7, self.volume())
        self.check("no popup after recovery without accessibility", not self.overlays())

    def locked_boot_tests(self):
        # Use only our disposable Android 16 emulator. Always remove the test PIN.
        if self.args.serial != "emulator-5602":
            raise ValueError("Locked-boot test is restricted to emulator-5602")
        self.configure_steps(5)
        self.a11y(False)
        self.shell("locksettings", "set-pin", "1234")
        try:
            old_id = self.shell("cat", "/proc/sys/kernel/random/boot_id").strip()
            self.adb("reboot")
            for _ in range(100):
                try:
                    new_id = self.shell("cat", "/proc/sys/kernel/random/boot_id").strip()
                    if new_id != old_id and self.shell("getprop", "sys.boot_completed").strip() == "1":
                        break
                except RuntimeError:
                    pass
                time.sleep(.5)
            else:
                raise AssertionError("PIN-protected reboot did not complete")
            self.adb("root")
            self.adb("wait-for-device", timeout=30)
            state = self.shell("dumpsys", "user")
            self.check("secure reboot keeps user credential storage locked",
                       "RUNNING_LOCKED" in state)
            self.check("locked boot restores monitor without accessibility or opening app",
                       self.wait_running())
            prefs = self.shell("cat", "/data/user_de/0/" + PKG
                               + "/shared_prefs/volume_step_settings.xml")
            self.check("locked-boot receiver reads enabled device-protected settings",
                       "android.intent.action.LOCKED_BOOT_COMPLETED" in prefs
                       and 'name="enabled" value="true"' in prefs)
            self.shell("input", "keyevent", "KEYCODE_WAKEUP")
            self.screenshot("locked-boot")
            (self.output / "locked-boot-state.txt").write_text(
                state + "\n" + prefs + "\n" + self.shell("dumpsys", "activity", "services", PKG))
        finally:
            self.shell("input", "keyevent", "KEYCODE_WAKEUP")
            if "RUNNING_LOCKED" in self.shell("dumpsys", "user"):
                self.shell("input", "swipe", "540", "2000", "540", "700", "300")
                self.shell("input", "text", "1234")
                self.shell("input", "keyevent", "KEYCODE_ENTER")
                time.sleep(1)
            self.shell("locksettings", "clear", "--old", "1234")
            self.shell("wm", "dismiss-keyguard")
            self.launch()
            self.check("temporary test PIN is removed and user storage is unlocked",
                       "RUNNING_UNLOCKED" in self.shell("dumpsys", "user")
                       and "secure=false" in self.shell("dumpsys", "window", "policy"))

    def poll_only_tests(self):
        self.configure_steps(5)
        self.launch(host=True)
        self.a11y(False)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.baseline(6)
        logfile = self.output / "poll-only-instrumentation.txt"
        with logfile.open("w") as output:
            process = subprocess.Popen(
                ["adb", "-s", self.args.serial, "shell", "am", "instrument", "-w",
                 HOST + "/.PollOnlyInstrumentation"], stdout=output, stderr=subprocess.STDOUT)
            try:
                for _ in range(100):
                    if "mode=poll-only" in logfile.read_text():
                        break
                    if process.poll() is not None:
                        raise RuntimeError(logfile.read_text())
                    time.sleep(.1)
                else:
                    raise RuntimeError("Polling test did not start")
                self.check("foreground monitor active with key and broadcast callbacks absent",
                           self.running())
                self.baseline(6)
                self.keys("+")
                time.sleep(.4)
                stride = max(1, (self.maximum + 14) // 15)
                self.check("poll-only leaves native volume up unchanged",
                           self.volume() == min(self.maximum, 6 + stride), self.volume())
                self.check("poll-only does not show a popup", not self.overlays())
                self.baseline(6)
                self.keys("-")
                time.sleep(.4)
                self.check("poll-only leaves native volume down unchanged",
                           self.volume() == max(0, 6 - stride), self.volume())
                process.wait(timeout=30)
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=5)
        log = logfile.read_text()
        self.check("poll-only test uses real polling and no key callbacks",
                   "INSTRUMENTATION_CODE: 0" in log
                   and "last volume source=poll" in log and "last key age ms=never" in log)
        self.launch()
        self.check("monitor recovers after test instrumentation", self.wait_running())

    def playback_stride_tests(self):
        self.check("native media range is 0–150", self.maximum == 150, self.maximum)
        if self.maximum != 150:
            raise AssertionError("Configure the test emulator's native range before this phase")
        self.configure_steps(30)
        for accessibility in (False, True):
            self.a11y(accessibility)
            self.check("monitor runs during playback stride tests", self.wait_running())
            self.launch(host=True, native_stride=10, media_session=True)
            time.sleep(3.5)
            media = self.shell("dumpsys", "media_session")
            audio = self.shell("dumpsys", "audio")
            self.check("local MediaSession is actively playing",
                       "Volume playback regression" in media and "state=PLAYING(3)" in media)
            self.check("AudioTrack is started during stride tests",
                       "state:started" in audio or "state=started" in audio)
            for sequence in ("+", "-", "+++", "---", "+-"):
                self.baseline(30)
                self.keys(sequence)
                popup = self.overlays()
                time.sleep(.4)
                actual = self.volume()
                expected = self.expected(30, sequence, steps=30) if accessibility else (
                        30 + 10 * (sequence.count("+") - sequence.count("-")))
                self.check(f"playback stride 10 / accessibility {accessibility} / {sequence}",
                           actual == expected, f"30 → {actual}, expected {expected}")
                if sequence == "+":
                    self.check(f"playback popup / accessibility {accessibility}",
                               bool(popup) == accessibility)
                    self.screenshot(f"popup-playback-accessibility-{accessibility}")
                time.sleep(3.5)  # Let Android's volume UI release input focus.
            self.launch(host=True, play=False, player_keys=True)
            time.sleep(3.5)
            self.check("playback stops before pause comparison",
                       "Volume playback regression" not in self.shell("dumpsys", "media_session"))
            self.baseline(30)
            self.keys("+")
            time.sleep(.4)
            self.check(f"paused stride 1 / accessibility {accessibility}",
                       self.volume() == (35 if accessibility else 31), self.volume())
            time.sleep(1.3)
            for preset in (75, 92, 93, 30):
                self.shell("cmd", "media_session", "volume", "--stream", "3", "--set", preset)
                time.sleep(.7)
                self.check(f"external preset {preset} preserved / accessibility {accessibility}",
                           self.volume() == preset, self.volume())
                self.check(f"external preset {preset} no popup / accessibility {accessibility}",
                           not self.overlays())
        (self.output / "audio.txt").write_text(audio)
        (self.output / "media-session.txt").write_text(media)
        self.launch(host=True, play=False)
        self.launch()

    def lifecycle_tests(self):
        self.launch()
        self.shell("input", "swipe", "540", "650", "540", "1600", "300")
        self.click(rid="id/enable_correction")
        status = self.find(self.ui("disabled"), rid="id/background_status").get("text")
        self.check("OFF switch and stopped status agree", "音量修正已关闭" in status, status)
        self.check("disable stops foreground keeper", self.wait_stopped())
        self.launch(host=True)
        self.baseline(6)
        self.keys("+")
        self.check("disabled app leaves native one-step adjustment", self.volume() == 7, self.volume())
        self.launch()
        self.click(rid="id/enable_correction")
        self.check("re-enable restarts keeper", self.wait_running())
        self.launch(host=True)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        pid = self.shell("pidof", PKG).strip()
        self.shell("kill", "-9", pid)
        self.check("process kill recovers without opening app", self.wait_running())
        self.correction("correction after process recovery")
        # Remove the real activity task through Android's task manager.
        self.launch()
        activities = self.shell("dumpsys", "activity", "activities")
        task = re.search(r"Task\{[^}\n]*#(\d+)[^\n]*A=\d+:" + re.escape(PKG)
                         + r"(?=\s|\})", activities)
        if task is None:
            raise AssertionError("Application activity task missing before removal")
        (self.output / "before-task-removal.txt").write_text(activities)
        self.shell("am", "stack", "remove", task.group(1))
        time.sleep(.8)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        after = self.shell("dumpsys", "activity", "activities")
        (self.output / "after-task-removal.txt").write_text(after)
        self.check("application activity task removed",
                   not re.search(r"Task\{[^}\n]*#" + task.group(1) + r"\b", after),
                   "task " + task.group(1))
        self.check("keeper remains after task removal", self.running())
        self.correction("correction with app task absent")
        self.a11y(False)
        self.launch()
        status = self.find(self.ui("accessibility-revoked"), rid="id/background_status").get("text")
        self.check("volume monitor waits for key service after accessibility revocation",
                   "等待音量按键服务" in status and self.running(), status)
        self.launch(host=True)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.baseline(6)
        self.keys("+")
        self.check("no correction without accessibility callbacks",
                   self.volume() == 7, self.volume())
        self.check("no popup without accessibility callbacks", not self.overlays())
        self.a11y(True)
        self.check("accessibility reconnect restores keeper", self.wait_running())
        self.launch(host=True)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        (self.output / "before-reboot-logcat.txt").write_text(self.shell("logcat", "-d"))
        boot_id = self.shell("cat", "/proc/sys/kernel/random/boot_id").strip()
        self.adb("reboot")
        for _ in range(240):
            try:
                new_id = self.shell("cat", "/proc/sys/kernel/random/boot_id").strip()
                if new_id != boot_id and self.shell("getprop", "sys.boot_completed").strip() == "1":
                    break
            except RuntimeError:
                pass
            time.sleep(.5)
        else:
            raise AssertionError("New boot did not complete")
        self.adb("root")
        self.adb("wait-for-device", timeout=30)
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        self.check("actual reboot restores foreground keeper without opening app",
                   self.wait_running())
        self.launch(host=True)
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.correction("background correction after actual reboot")
        self.shell("am", "force-stop", PKG)
        time.sleep(1)
        self.check("force-stop obeys Android and stops app", not self.running())
        self.baseline(6)
        self.keys("+")
        self.check("force-stopped app makes no secondary correction", self.volume() == 7, self.volume())
        self.launch()
        self.check("reopen after force-stop recovers monitor without accessibility regrant", self.wait_running())
        self.a11y(True)
        self.launch(host=True)
        self.correction("correction after force-stop recovery")
        log = self.shell("logcat", "-d")
        (self.output / "logcat.txt").write_text(log)
        self.check("no application crash", not re.search(
            r"FATAL EXCEPTION:[\s\S]{0,300}Process: " + re.escape(PKG) + r",", log))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--serial", required=True)
    p.add_argument("--keys", required=True)
    p.add_argument("--apk", default=str(ROOT / "build/outputs/apk/release/precise-volume-steps-release.apk"))
    p.add_argument("--output", default="/tmp/fvs-emulator-tests")
    p.add_argument("--phase", choices=["all", "setup", "audio", "lifecycle", "poll",
                                       "display", "locked-boot", "playback", "rejected"], default="all")
    args = p.parse_args()
    if not args.serial.startswith("emulator-"):
        p.error("This destructive setup suite is for disposable emulators only")
    suite = Suite(args)
    try:
        suite.prepare()
        if args.phase in ("audio", "lifecycle", "poll", "rejected"):
            suite.a11y(False)
            suite.a11y(True)
            suite.check("test fixture accessibility connected", suite.wait_running())
        if args.phase in ("all", "setup"):
            suite.permission_setup()
            suite.permission_setup(deny=True)
        if args.phase in ("all", "audio"):
            suite.audio_tests()
        if args.phase in ("all", "lifecycle"):
            suite.lifecycle_tests()
        if args.phase in ("all", "poll"):
            suite.poll_only_tests()
        if args.phase == "display":
            suite.display_tests()
        if args.phase == "locked-boot":
            suite.locked_boot_tests()
        if args.phase == "playback":
            suite.playback_stride_tests()
        if args.phase == "rejected":
            suite.rejected_write_tests()
        suite.completed = bool(suite.results) and all(r["pass"] for r in suite.results)
    except Exception as e:
        suite.check("test harness completed", False, repr(e))
        try:
            suite.ui("failure")
            (suite.output / "failure-logcat.txt").write_text(suite.shell("logcat", "-d"))
        except Exception:
            pass
        raise
    finally:
        suite.save()
        (suite.output / "logcat.txt").write_text(suite.shell("logcat", "-d"))
    raise SystemExit(0 if all(r["pass"] for r in suite.results) else 1)


if __name__ == "__main__":
    main()
