"""Explicit-device helpers; UI inspection does not suppress accessibility."""
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "roro.stellar.volumesteps"
HOST = PACKAGE + ".testhost"
UI_OBSERVER = PACKAGE + ".uitools"


class Device:
    def __init__(self, serial, preset_dex=None):
        self.serial = serial
        self.preset_dex = preset_dex

    def adb(self, *args, timeout=25, binary=False):
        return subprocess.check_output(["adb", "-s", self.serial, *map(str, args)],
                                       timeout=timeout, text=not binary)

    def shell(self, *args):
        return self.adb("shell", *args)

    def service(self):
        return self.shell("dumpsys", "activity", "service", PACKAGE + "/.VolumeKeeperService")

    def volume(self):
        return int(re.search(r"volume is (\d+)", self.shell(
            "cmd", "media_session", "volume", "--stream", 3, "--get")).group(1))

    def preset(self, value):
        if self.preset_dex:
            self.shell("CLASSPATH=" + shlex.quote(self.preset_dex)
                       + " app_process /system/bin ShellVolumePreset " + str(int(value)))
        else:
            self.shell("cmd", "media_session", "volume", "--stream", 3, "--set", value)
        time.sleep(.5)
        if self.volume() != value:
            raise RuntimeError("System rejected the test baseline")

    def ui(self):
        result = self.shell("am", "instrument", "-w", HOST + "/.DumpUiInstrumentation")
        if "INSTRUMENTATION_CODE: 0" not in result:
            raise RuntimeError("UI inspection failed")
        path = re.search(r"INSTRUMENTATION_RESULT: path=(.*)", result).group(1)
        return list(ET.fromstring(self.shell("cat", path)).iter("node"))

    def tap(self, node):
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds", "")))
        if x2 <= x1 or y2 <= y1:
            raise RuntimeError("UI element has no visible bounds")
        self.shell("input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
        time.sleep(.4)

    def _vivo_shortcut_page(self):
        self.shell("am", "start", "-f", "0x14000000",
                   "-n", "com.vivo.SmartKey/.MainActivity")
        time.sleep(.8)
        for _ in range(8):
            nodes = self.ui()
            if any(n.get("package") == "com.vivo.SmartKey"
                   and n.get("content-desc", "").startswith("快捷启动.") for n in nodes):
                return nodes
            time.sleep(.2)
        raise RuntimeError("Vivo volume-key shortcut page did not open")

    def _vivo_camera_shortcut_page(self):
        nodes = self._vivo_shortcut_page()
        self.tap(next(n for n in nodes if n.get("text") == "快捷启动相机"))
        time.sleep(.8)
        for _ in range(8):
            nodes = self.ui()
            if any(n.get("package") == "com.android.camera"
                   and n.get("text") == "快捷启动相机" for n in nodes):
                return nodes
            time.sleep(.2)
        raise RuntimeError("Vivo camera shortcut setting did not open")

    def snapshot_volume_shortcuts(self):
        if self.shell("getprop", "ro.product.manufacturer").strip().lower() != "vivo":
            return None
        values = [(namespace, key, self.shell("settings", "get", namespace, key).strip())
                  for namespace, key in (("system", "smartkey_primary_switch"),
                                         ("secure", "quick_start_camera"),
                                         ("secure", "quick_start_camera_switch"))]
        nodes = self._vivo_camera_shortcut_page()
        labels = {"关闭", "仅启动相机", "启动相机并进入抓拍", "启动人文街拍相机"}
        selected = [n.get("text") for n in nodes if n.get("text") in labels]
        if len(selected) != 1:
            raise RuntimeError("Cannot snapshot Vivo camera shortcut selection")
        return {"values": values, "camera_option": selected[0]}

    def set_volume_shortcuts(self, snapshot, disabled):
        if snapshot is None:
            return
        desired = {(ns, key): value for ns, key, value in snapshot["values"]}
        if disabled:
            desired[("system", "smartkey_primary_switch")] = "0"
            desired[("secure", "quick_start_camera_switch")] = "0"
        nodes = self._vivo_shortcut_page()
        switches = [n for n in nodes if n.get("class") == "android.widget.Switch"
                    and n.get("package") == "com.vivo.SmartKey"]
        # Vivo's large switch row reports clickable=true but only the inner
        # switch responds. Use its inspected bounds, never fixed coordinates.
        switch = min(switches, key=lambda n: (
            int(re.findall(r"\d+", n.get("bounds"))[2])
            - int(re.findall(r"\d+", n.get("bounds"))[0])))
        wanted = desired[("system", "smartkey_primary_switch")]
        for _ in range(3):
            if self.shell("settings", "get", "system", "smartkey_primary_switch").strip() == wanted:
                break
            self.tap(switch)
            time.sleep(.3)
        else:
            raise RuntimeError("Vivo long-press shortcut did not match requested value")
        nodes = self._vivo_camera_shortcut_page()
        self.tap(next(n for n in nodes if n.get("text") == "快捷启动相机"))
        option = "关闭" if disabled else snapshot["camera_option"]
        self.tap(next(n for n in self.ui() if n.get("text") == option))
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            actual = {(ns, key): self.shell("settings", "get", ns, key).strip()
                      for ns, key in desired}
            if actual == desired:
                return
            time.sleep(.2)
        raise RuntimeError("Vivo shortcut settings did not match requested values")

    def restore_accessibility(self):
        if "accessibility connected=true" in self.service():
            return
        original = self.shell("settings", "get", "secure", "enabled_accessibility_services").strip()
        self.shell("am", "start", "-a", "android.settings.ACCESSIBILITY_SETTINGS")
        for _ in range(12):
            nodes = self.ui()
            scroll = next((n for n in nodes if n.get("class") in (
                "android.widget.ScrollView", "androidx.recyclerview.widget.RecyclerView",
                "android.widget.ListView")), None)
            viewport = scroll if scroll is not None else nodes[0]
            vx1, vy1, vx2, vy2 = map(int, re.findall(r"-?\d+", viewport.get("bounds", "")))

            def visible(node):
                x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds", "")))
                return vx1 <= (x1 + x2) // 2 < vx2 and vy1 <= (y1 + y2) // 2 < vy2

            entry = next((n for n in nodes if n.get("package") == "com.android.settings"
                          and n.get("text") == "精准音量服务" and visible(n)), None)
            if entry is not None:
                time.sleep(.4)
                fresh = next((n for n in self.ui() if n.get("package") == "com.android.settings"
                              and n.get("text") == "精准音量服务"
                              and n.get("bounds") == entry.get("bounds")), None)
                if fresh is not None:
                    self.tap(fresh)
                    break
                continue
            if scroll is None:
                raise RuntimeError("Cannot locate accessibility list")
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", scroll.get("bounds", "")))
            self.shell("input", "swipe", (x1 + x2) // 2, y2 - 150,
                       (x1 + x2) // 2, y1 + 150, 450)
            time.sleep(.8)
        else:
            raise RuntimeError("精准音量服务 not found")

        def switch():
            for _ in range(5):
                nodes = self.ui()
                if any(n.get("class") == "android.widget.Switch"
                       and n.get("content-desc") == "精准音量服务" for n in nodes):
                    break
                time.sleep(.2)
            if not any(n.get("text") == "精准音量服务" for n in nodes):
                raise RuntimeError("Unexpected accessibility detail page")
            candidates = [n for n in nodes if n.get("class") == "android.widget.Switch"
                          and n.get("package") == "com.android.settings"]
            if not candidates:
                raise RuntimeError("Service switch not found")
            main = next((n for n in candidates if n.get("content-desc") == "精准音量服务"), None)
            if main is not None:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", main.get("bounds")))
                contained = []
                for candidate in candidates:
                    cx1, cy1, cx2, cy2 = map(int, re.findall(r"\d+", candidate.get("bounds")))
                    if x1 <= cx1 < cx2 <= x2 and y1 <= cy1 < cy2 <= y2:
                        contained.append((cx2 - cx1, candidate))
                return min(contained, key=lambda item: item[0])[1]
            # Vivo exposes both the preference row and its inner native switch.
            return min(candidates, key=lambda n: (
                int(re.findall(r"\d+", n.get("bounds"))[2])
                - int(re.findall(r"\d+", n.get("bounds"))[0])))

        def confirm(text):
            node = next((n for n in self.ui() if n.get("package") == "com.android.settings"
                         and n.get("text") == text and n.get("enabled") == "true"), None)
            if node is not None:
                self.tap(node)

        current = switch()
        if current.get("checked") == "true":
            self.tap(current)
            confirm("关闭")
        current = switch()
        if current.get("checked") == "false":
            self.tap(current)
            confirm("允许")
        time.sleep(.5)
        after = self.shell("settings", "get", "secure", "enabled_accessibility_services").strip()
        if set(after.split(":")) != set(original.split(":")):
            raise RuntimeError("Accessibility list changed unexpectedly")
        deadline = time.monotonic() + 4
        while time.monotonic() < deadline:
            if "accessibility connected=true" in self.service():
                return
            time.sleep(.2)
        raise RuntimeError("Accessibility switch is enabled but service did not connect")
