#!/system/bin/sh
# Phone-side read-only recording. Place read_volume_events beside this script.
set -u

case "${1:-}" in
  ""|*[!0-9]*) exit 2 ;;
esac
seconds=$1
if [ "$seconds" -lt 1 ] || [ "$seconds" -gt 600 ]; then exit 2; fi
out=${2:-}
case "$out" in
  /data/local/tmp/pvs-*) ;;
  *) exit 2 ;;
esac
if [ -e "$out" ]; then echo "Output already exists" >&2; exit 2; fi
binary="$(dirname "$0")/pvs-read-volume-events"
if [ ! -x "$binary" ]; then echo "Read-only native reader missing" >&2; exit 2; fi
mkdir -p "$out" || exit 1
echo $$ > "$out/recorder.pid"
old_log_level=$(getprop log.tag.FineVolumeAdjustment)
reader_pid=""
log_pid=""

cleanup() {
  trap - EXIT INT TERM
  if [ -n "$reader_pid" ]; then
    kill -TERM "$reader_pid" 2>/dev/null || true
    wait "$reader_pid" 2>/dev/null || true
  fi
  if [ -n "$log_pid" ]; then
    kill -TERM "$log_pid" 2>/dev/null || true
    wait "$log_pid" 2>/dev/null || true
  fi
  timeout 5 dumpsys activity service roro.stellar.volumesteps/.VolumeKeeperService \
    > "$out/service-final.txt" 2>&1
  timeout 5 dumpsys audio > "$out/audio-final.txt" 2>&1
  cmd media_session volume --stream 3 --get > "$out/volume-final.txt" 2>&1
  setprop log.tag.FineVolumeAdjustment "$old_log_level"
  echo '{"kind":"complete"}' >> "$out/status.ndjson"
  touch "$out/complete"
}
trap cleanup EXIT INT TERM

# Open before screen changes or dump collection; never inject or grab input.
"$binary" "$seconds" /dev/input/event0 /dev/input/event1 \
  > "$out/events.ndjson" 2> "$out/reader-errors.txt" &
reader_pid=$!
i=0
while ! grep -q '"kind":"ready"' "$out/events.ndjson"; do
  if ! kill -0 "$reader_pid" 2>/dev/null || [ "$i" -ge 30 ]; then
    echo '{"kind":"error","reason":"reader startup failed"}' >> "$out/status.ndjson"
    exit 1
  fi
  i=$((i + 1))
  sleep 0.1
done
setprop log.tag.FineVolumeAdjustment D
logcat -v monotonic -T 1 FineVolumeAdjustment:D '*:S' \
  > "$out/app-logcat.txt" 2>&1 &
log_pid=$!
cat /proc/sys/kernel/random/boot_id > "$out/boot-id.txt"
timeout 5 dumpsys activity service roro.stellar.volumesteps/.VolumeKeeperService \
  > "$out/service-before.txt" 2>&1
timeout 5 dumpsys audio > "$out/audio-before.txt" 2>&1
cmd media_session volume --stream 3 --get > "$out/volume-before.txt" 2>&1
echo '{"kind":"ready"}' >> "$out/status.ndjson"
touch "$out/ready"

while kill -0 "$reader_pid" 2>/dev/null && [ ! -f "$out/stop" ]; do
  {
    # /proc/uptime includes suspend; native event timestamps use CLOCK_MONOTONIC.
    printf 'sample boot_seconds='
    cut -d' ' -f1 /proc/uptime
    timeout 3 cmd media_session volume --stream 3 --get
    timeout 3 dumpsys activity service roro.stellar.volumesteps/.VolumeKeeperService
  } >> "$out/samples.txt" 2>&1
  sleep 0.25
done
# Caller requests stop only after settled volume is checked; record one more second.
sleep 1
