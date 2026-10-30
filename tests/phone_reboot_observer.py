#!/usr/bin/env python3
"""Read-only recording of built-in Vivo buttons, idle or Bilibili, after reboot.

Start the desired playback/screen state yourself. This tool changes no settings,
starts no player, injects no keys and never starts Shizuku. GPIO/PMIC input is
recorded separately from the installed app's delivery and real volume readback.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import threading
import time
from android_device import Device, PACKAGE


def owns_active_media(audio_dump, uid):
    """Match the exact Android UID and a started media AudioTrack."""
    return any(f"u/pid:{uid}/" in line and "state:started" in line
               and "AudioPlaybackConfiguration" in line and "USAGE_MEDIA" in line
               for line in audio_dump.splitlines())


def process_pid(device, package):
    try:
        return device.shell("pidof", package).strip()
    except subprocess.CalledProcessError as error:
        if error.returncode == 1:
            return ""
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scenario", choices=("idle", "bilibili"), required=True)
    parser.add_argument("--backend", choices=("record", "ordinary", "shizuku"), default="record")
    parser.add_argument("--seconds", type=int, default=30)
    parser.add_argument("--progress", action="store_true",
                        help="Print each physical key release while recording")
    parser.add_argument("--releases", type=int,
                        help="Finish after this many volume releases and one second of settling")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or root in output.parents:
        parser.error("Store phone captures outside the repository")
    if not 1 <= args.seconds <= 600:
        parser.error("seconds must be in 1..600")
    if args.releases is not None and not 1 <= args.releases <= 200:
        parser.error("releases must be in 1..200")
    output.mkdir(parents=True, exist_ok=True)
    d = Device(args.serial)
    prefix = f"physical-reboot-{args.scenario}-{time.time_ns()}"
    events, samples, errors, readers, threads = [], [], [], [], []
    lock = threading.Lock()
    boot = d.shell("cat", "/proc/sys/kernel/random/boot_id").strip()
    caps = d.shell("getevent", "-lp")
    nodes = []
    button_paths = {}
    for block in re.split(r"(?=add device \d+:)", caps):
        # On the tested Vivo these are built-in dedicated GPIO/PMIC nodes.
        # Headset, keyboard and external UHID input cannot certify these cases.
        match = re.search(r"add device \d+: (/dev/input/event\d+)", block)
        name = re.search(r'name:\s*"([^"]+)"', block)
        if (match and name and name.group(1) in ("gpio-keys", "mtk-pmic-keys")
                and re.search(r"KEY_VOLUME(?:UP|DOWN)", block)):
            node = match.group(1)
            # uinput can reuse a hardware name. Verify the actual kernel device
            # location so a test clone cannot be certified as a built-in button.
            path = d.shell("readlink", "-f",
                           "/sys/class/input/" + Path(node).name + "/device").strip()
            if not path.startswith("/sys/devices/platform/"):
                raise SystemExit("Unverified built-in source or simulated button node: " + node)
            button_paths[node] = path
            nodes.append((node, name.group(1)))
    if not nodes:
        raise SystemExit("No tested built-in GPIO/PMIC volume node; inspect capabilities first")
    apk = d.shell("pm", "path", PACKAGE).strip().splitlines()[0].removeprefix("package:")
    before = {"boot_id": boot, "fingerprint": d.shell("getprop", "ro.build.fingerprint").strip(),
              "service": d.service(), "volume": d.volume(), "nodes": nodes,
              "button_device_paths": button_paths,
              "apk_sha256": hashlib.sha256(d.adb("exec-out", "cat", apk, binary=True)).hexdigest()}
    player_uid = None
    if args.scenario == "bilibili":
        installed = d.shell("pm", "list", "packages", "--user", 0, "-U", "tv.danmaku.bili")
        match = re.search(r"^package:tv\.danmaku\.bili\s+uid:(\d+)$", installed, re.M)
        if match is None:
            raise SystemExit("Exact Bilibili package is not installed")
        player_uid = int(match.group(1))
        before["player_uid"] = player_uid
        before["player_pid"] = process_pid(d, "tv.danmaku.bili")
        audio = d.shell("dumpsys", "audio")
        before["player_owns_active_media"] = owns_active_media(audio, player_uid)
        (output / (prefix + "-before-audio.txt")).write_text(audio)
    before["main_pid"] = process_pid(d, PACKAGE)
    started = time.monotonic()

    def read(node, process):
        for line in process.stdout:
            match = re.search(r"\[\s*(\d+\.\d+)\]\s+EV_KEY\s+"
                              r"(KEY_VOLUMEUP|KEY_VOLUMEDOWN|KEY_POWER)\s+(DOWN|UP|REPEAT)", line)
            if match:
                with lock:
                    events.append({"received_seconds": time.monotonic() - started,
                                   "device_event_seconds": float(match.group(1)), "node": node,
                                   "key": match.group(2), "action": match.group(3)})
                    if args.progress and match.group(3) == "UP":
                        print(f"Physical release: {match.group(2)} on {node}", flush=True)
            elif "Permission denied" in line:
                with lock:
                    errors.append("Dedicated input reader denied")

    try:
        for node, _ in nodes:
            process = subprocess.Popen(["adb", "-s", args.serial, "shell", "getevent", "-lt", node],
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            readers.append(process)
            thread = threading.Thread(target=read, args=(node, process), daemon=True)
            thread.start()
            threads.append(thread)
        print(f"Recording {args.scenario} for {args.seconds}s; use built-in volume buttons.", flush=True)
        while time.monotonic() - started < args.seconds:
            sample = {"seconds": time.monotonic() - started, "volume": d.volume(),
                      "service": d.service()}
            if player_uid is not None:
                sample["player_owns_active_media"] = owns_active_media(
                    d.shell("dumpsys", "audio"), player_uid)
                sample["player_pid"] = process_pid(d, "tv.danmaku.bili")
            samples.append(sample)
            if args.releases is not None:
                with lock:
                    released = [e for e in events
                                if e["action"] == "UP" and e["key"] != "KEY_POWER"]
                if (len(released) >= args.releases
                        and sample["seconds"] - released[-1]["received_seconds"] >= 1):
                    break
            time.sleep(.25)
    except Exception as error:
        errors.append(str(error))
    finally:
        for process in readers:
            process.terminate()
        for process in readers:
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)
        for thread in threads:
            thread.join(timeout=1)
        for process in readers:
            process.stdout.close()
        after = {"boot_id": d.shell("cat", "/proc/sys/kernel/random/boot_id").strip(),
                 "service": d.service(), "volume": d.volume()}
        if after["boot_id"] != boot:
            errors.append("Phone rebooted during the recording")
        released = [e for e in events if e["action"] == "UP" and e["key"] != "KEY_POWER"]
        if not released:
            errors.append("No built-in volume releases recorded; physical behavior is unverified")
        if args.releases is not None and len(released) < args.releases:
            errors.append(f"Required {args.releases} volume releases; recorded {len(released)}")
        required_audio = "playback active=" + str(args.scenario == "bilibili").lower()
        if not samples or any("screen interactive=false" not in s["service"]
                              or required_audio not in s["service"] for s in samples):
            errors.append("Required screen-off/playback state was not maintained")
        if args.backend == "ordinary" and any(
                "Shizuku=enabled=false, ready=false" not in s["service"]
                or "raw volume keys=ready=false" not in s["service"]
                or "companion=supported=true, ready=true" not in s["service"]
                or "accessibility connected=true" not in s["service"] for s in samples):
            errors.append("Ordinary helper/accessibility state was not maintained")
        if args.backend == "shizuku" and any(
                "Shizuku=enabled=true, ready=true" not in s["service"]
                or "raw volume keys=ready=true" not in s["service"] for s in samples):
            errors.append("Selected Shizuku/raw input state was not maintained")
        # A live Bilibili process alone does not establish Bilibili playback.
        # Retain audio evidence privately so its UID/session can be checked.
        if args.scenario == "bilibili":
            audio = d.shell("dumpsys", "audio")
            (output / (prefix + "-audio.txt")).write_text(audio)
            (output / (prefix + "-sessions.txt")).write_text(d.shell("dumpsys", "media_session"))
            after["player_owns_active_media"] = owns_active_media(audio, player_uid)
            after["player_pid"] = process_pid(d, "tv.danmaku.bili")
            if (not before["player_owns_active_media"]
                    or not after["player_owns_active_media"]
                    or any(not s.get("player_owns_active_media") for s in samples)):
                errors.append("Exact Bilibili UID did not own active media throughout recording")
            if (not before["player_pid"] or after["player_pid"] != before["player_pid"]
                    or any(s.get("player_pid") != before["player_pid"] for s in samples)):
                errors.append("Bilibili process changed during recording")
        after["main_pid"] = process_pid(d, PACKAGE)
        if not before["main_pid"] or after["main_pid"] != before["main_pid"]:
            errors.append("Main process changed during recording")
        result = {"scenario": args.scenario, "backend": args.backend,
                  "input": "built-in GPIO/PMIC buttons",
                  "required_releases": args.releases,
                  "before": before, "events": events, "samples": samples, "after": after,
                  "errors": errors, "recording_complete": not errors,
                  "volume_behavior": "Requires per-press trace review; no automatic pass inferred"}
        path = output / (prefix + ".json")
        path.write_text(json.dumps(result, ensure_ascii=False, indent=2))
        print(path, flush=True)
    raise SystemExit(1 if errors else 0)


if __name__ == "__main__":
    main()
