#!/usr/bin/env python3
"""Disposable-emulator checks for the first-launch permission guide."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

from android_device import Device, HOST, PACKAGE, UI_OBSERVER

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_ACTIONS = {
    "id/guide_accessibility",
    "id/guide_notification",
    "id/guide_battery",
    "id/guide_overlay",
    "id/guide_auto_start",
    "id/guide_background",
}


class PermissionGuideTest:
    def __init__(self, args):
        self.args = args
        self.device = Device(args.serial)
        self.results = []
        self.output = Path(args.output) / args.serial
        self.output.mkdir(parents=True, exist_ok=True)
        self.apk = Path(args.apk)

    def check(self, name, condition, detail=""):
        self.results.append({"test": name, "pass": bool(condition), "detail": str(detail)})
        print(("PASS " if condition else "FAIL ") + name + " " + str(detail), flush=True)
        self.save(False)

    def save(self, complete):
        (self.output / "results.json").write_text(json.dumps({
            "serial": self.args.serial,
            "apk_sha256": hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            "results": self.results,
            "success": complete and all(item["pass"] for item in self.results),
        }, ensure_ascii=False, indent=2))

    def install(self, path):
        self.device.adb("install", "-r", path)

    def prepare(self):
        manufacturer = self.device.shell("getprop", "ro.product.manufacturer").strip()
        if not self.args.serial.startswith("emulator-"):
            raise RuntimeError("Permission guide setup mutates app data; use a disposable emulator")
        self.install(self.apk)
        self.install(ROOT / "tests/host/build/outputs/apk/debug/"
                     "precise-volume-steps-test-host-debug.apk")
        self.install(ROOT / "tests/ui/build/outputs/apk/debug/"
                     "precise-volume-steps-test-ui-debug.apk")
        self.device.shell("settings", "put", "secure",
                          "enabled_accessibility_services", "null")
        self.device.shell("settings", "put", "secure", "accessibility_enabled", "0")
        self.device.shell("pm", "clear", PACKAGE)
        subprocess.run(["adb", "-s", self.args.serial, "shell", "pm", "revoke",
                        PACKAGE, "android.permission.POST_NOTIFICATIONS"],
                       capture_output=True, check=False)
        self.device.shell("appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "deny")
        subprocess.run(["adb", "-s", self.args.serial, "shell", "cmd", "deviceidle",
                        "whitelist", "-" + PACKAGE], capture_output=True, check=False)
        self.device.shell("am", "start", "-f", "0x14000000",
                          "-n", PACKAGE + "/.MainActivity")
        time.sleep(.8)
        self.check("test device is AOSP-like emulator",
                   manufacturer.lower() != "vivo", manufacturer)

    def compatibility_grant_methods(self):
        helper = ROOT / "compatibility-helper/build/outputs/apk/release/"
        helper /= "compatibility-helper-release.apk"
        self.install(helper)
        subprocess.run(["adb", "-s", self.args.serial, "shell", "pm", "revoke",
                        "com.vivo.finevolumesteps.helper", "android.permission.DUMP"],
                       capture_output=True, check=False)
        subprocess.run(["adb", "-s", self.args.serial, "shell", "pkill", "-9",
                        "shizuku_server"], capture_output=True, check=False)
        output = self.device.shell(
            "am", "instrument", "-w",
            HOST + "/.CompatibilityGrantUiInstrumentation")
        match = re.search(r"INSTRUMENTATION_RESULT: tests=(.*)", output)
        code = re.search(r"INSTRUMENTATION_CODE: (-?\d+)", output)
        if match is None:
            raise RuntimeError("Grant-method UI fixture returned no tests: " + output)
        for item in json.loads(match.group(1)):
            self.check("grant methods: " + item["test"], item["pass"])
        self.check("grant-method UI fixture completed",
                   code is not None and code.group(1) == "0", output)

    @staticmethod
    def by_id(nodes, suffix):
        return next((node for node in nodes
                     if node.get("resource-id", "").endswith(suffix)), None)

    def collect_guide(self):
        found = {}
        texts = set()
        for _ in range(8):
            nodes = self.device.ui()
            for node in nodes:
                rid = node.get("resource-id", "")
                text = node.get("text", "")
                if rid:
                    found[rid.split(PACKAGE + ":")[-1]] = node
                if text:
                    texts.add(text)
            if EXPECTED_ACTIONS.issubset(found):
                return found, texts
            scroll = next((node for node in nodes
                           if node.get("class", "").endswith("ScrollView")), None)
            if scroll is None:
                break
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", scroll.get("bounds", "")))
            self.device.shell("input", "swipe", (x1 + x2) // 2, y2 - 80,
                              (x1 + x2) // 2, y1 + 80, 300)
            time.sleep(.3)
        return found, texts

    def find_main_action(self, suffix):
        for _ in range(8):
            nodes = self.device.ui()
            action = self.by_id(nodes, suffix)
            if action is not None:
                x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", action.get("bounds", "")))
                if x2 > x1 and y2 > y1:
                    return action
            scroll = next((node for node in nodes
                           if node.get("class", "").endswith("ScrollView")), None)
            if scroll is None:
                break
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", scroll.get("bounds", "")))
            self.device.shell("input", "swipe", (x1 + x2) // 2, y2 - 80,
                              (x1 + x2) // 2, y1 + 80, 300)
            time.sleep(.3)
        return None

    def run(self):
        nodes = self.device.ui()
        title = self.by_id(nodes, "android:id/alertTitle")
        self.check("clean data launch shows permission guide",
                   title is not None and title.get("text") == "权限设置向导")

        actions, texts = self.collect_guide()
        self.check("guide explains locked input and unlocked fallback",
                   any("锁屏和熄屏按键监听" in text
                       and "未授权不会影响已解锁的亮屏调节" in text
                       for text in texts), sorted(texts))
        self.check("all generic permission entrances are present",
                   EXPECTED_ACTIONS.issubset(actions), sorted(actions))
        self.check("all permission entrances are enabled and clickable",
                   all(actions[action].get("enabled") == "true"
                       and actions[action].get("clickable") == "true"
                       for action in EXPECTED_ACTIONS if action in actions))

        later = next(node for node in self.device.ui()
                     if node.get("resource-id", "").endswith("android:id/button2"))
        self.device.tap(later)
        nodes = self.device.ui()
        correction = self.by_id(nodes, "id/enable_correction")
        self.check("guide appears while correction defaults OFF",
                   correction is not None and correction.get("checked") == "false")
        guide_button = self.find_main_action("id/permission_guide")
        self.check("main page keeps a permanent permission guide entrance",
                   guide_button is not None and guide_button.get("text") == "查看权限指南")

        self.device.shell("am", "force-stop", PACKAGE)
        self.device.shell("am", "start", "-n", PACKAGE + "/.MainActivity")
        time.sleep(.6)
        nodes = self.device.ui()
        self.check("first-launch guide does not repeat after dismissal",
                   not any(node.get("text") == "权限设置向导"
                           and node.get("resource-id", "").endswith("android:id/alertTitle")
                           for node in nodes))

        guide_button = self.find_main_action("id/permission_guide")
        self.device.tap(guide_button)
        nodes = self.device.ui()
        self.check("permanent entrance reopens the guide",
                   any(node.get("text") == "权限设置向导"
                       and node.get("resource-id", "").endswith("android:id/alertTitle")
                       for node in nodes))
        accessibility = self.by_id(nodes, "id/guide_accessibility")
        self.device.tap(accessibility)
        nodes = self.device.ui()
        self.check("accessibility entrance opens Android settings",
                   any(node.get("package") == "com.android.settings" for node in nodes))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", default=str(
        ROOT / "build/outputs/apk/release/precise-volume-steps-release.apk"))
    parser.add_argument("--output", default="/tmp/pvs-permission-guide")
    args = parser.parse_args()
    test = PermissionGuideTest(args)
    complete = False
    try:
        test.prepare()
        test.run()
        test.compatibility_grant_methods()
        complete = all(item["pass"] for item in test.results)
    except Exception as exception:
        test.check("test harness completed", False, repr(exception))
    finally:
        test.save(complete)
    raise SystemExit(0 if complete else 1)


if __name__ == "__main__":
    main()
