#!/usr/bin/env python3
"""Normal installed app: no instrumentation, root, key injection or permission changes."""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

PKG = "roro.stellar.volumesteps"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    results = []
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    def shell(*command):
        return subprocess.run(["adb", "-s", args.serial, "shell", *map(str, command)],
                              capture_output=True, text=True, check=True, timeout=20).stdout

    def volume():
        return int(re.search(r"volume is (\d+)", shell(
            "cmd", "media_session", "volume", "--stream", "3", "--get")).group(1))

    def service():
        return shell("dumpsys", "activity", "service", PKG + ".VolumeKeeperService")

    def check(name, passed, detail):
        results.append({"test": name, "pass": bool(passed), "detail": detail})
        print(("PASS " if passed else "FAIL ") + name, flush=True)

    original = volume()
    before = service()
    # Key diagnostics must remain unchanged; polling/broadcasts cannot authorize UI.
    old_writes = before.count("key write ")
    old_confirms = before.count("key confirmed ")
    check("normal service and accessibility connected",
          "accessibility connected=true" in before and "enabled=true" in before, before)
    try:
        for label, value in (("idle", original), ("quiet external preset",
                             max(0, original - 1) if original else 1),
                             ("mute preset", 0), ("restore preset", original)):
            if label != "idle":
                shell("cmd", "media_session", "volume", "--stream", "3", "--set", value)
            samples = []
            for _ in range(5):
                time.sleep(.25)
                samples.append(service())
            current = volume()
            check(label + " has no app panel or correction",
                  all("overlay=attached=false" in s for s in samples)
                  and all(s.count("key write ") == old_writes
                          and s.count("key confirmed ") == old_confirms for s in samples),
                  {"volume": current, "requested": value,
                   "request_applied_by_system": current == value, "service": samples[-1]})
    finally:
        shell("cmd", "media_session", "volume", "--stream", "3", "--set", original)
        time.sleep(.5)
        check("original volume restored", volume() == original, {"original": original})
        (output / "results.json").write_text(json.dumps({
            "device": shell("getprop", "ro.product.model").strip(),
            "api": shell("getprop", "ro.build.version.sdk").strip(),
            "input": "external shell presets; no physical keys", "results": results,
        }, ensure_ascii=False, indent=2))
    if not all(r["pass"] for r in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
