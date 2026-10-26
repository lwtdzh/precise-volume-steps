#!/bin/sh
# Read-only permission comparison. Usage: sh collect_android_permissions.sh SERIAL [OUTPUT_DIR]
# Uses standard adb commands. Does not root, install, grant permissions or change settings.
set -eu
serial=${1:?Usage: sh collect_android_permissions.sh SERIAL [OUTPUT_DIR]}
report_dir=${2:-"./precise-volume-permissions-$(date +%Y%m%d-%H%M%S)"}
command -v adb >/dev/null 2>&1 || { echo "adb is required" >&2; exit 1; }
adb -s "$serial" get-state >/dev/null
mkdir -p "$report_dir"

capture() {
    report_name=$1
    shift
    adb -s "$serial" shell "$@" > "$report_dir/$report_name.txt" 2>&1 || true
}

{
    for property in ro.product.manufacturer ro.product.model ro.build.version.release \
                    ro.build.version.sdk ro.build.version.incremental ro.build.fingerprint; do
        printf '%s=' "$property"
        adb -s "$serial" shell getprop "$property"
    done
} > "$report_dir/device.txt"

for package_name in roro.stellar.volumesteps tv.danmaku.bili \
                    com.android.settings com.vivo.permissionmanager com.iqoo.secure \
                    com.miui.securitycenter com.huawei.systemmanager \
                    com.coloros.safecenter; do
    capture "$package_name-paths" pm path "$package_name"
    if [ -n "$(sed -n '/^package:/p' "$report_dir/$package_name-paths.txt")" ]; then
        capture "$package_name-package" dumpsys package "$package_name"
        capture "$package_name-appops" cmd appops get "$package_name"
        capture "$package_name-boot-receivers" cmd package query-receivers \
                --brief -a android.intent.action.BOOT_COMPLETED -p "$package_name"
        capture "$package_name-standby" am get-standby-bucket "$package_name"
    fi
done
capture android-permission-definitions pm list permissions -f
capture volume-service dumpsys activity services roro.stellar.volumesteps
capture native-media-volume cmd media_session volume --stream 3 --get
capture enabled-accessibility settings get secure enabled_accessibility_services
printf 'Saved to %s\n' "$report_dir"
