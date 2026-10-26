package roro.stellar.volumesteps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 10;
    private static final int REQUEST_BATTERY = 11;
    private static final int REQUEST_OVERLAY = 12;
    private static final int REQUEST_SHIZUKU = 13;
    private static final int COLOR_BACKGROUND = Color.rgb(247, 249, 247);
    private static final int COLOR_TEXT = Color.rgb(28, 30, 29);
    private static final int COLOR_SECONDARY = Color.rgb(85, 92, 88);
    private static final int COLOR_ACCENT = Color.rgb(0, 108, 76);
    private static final int COLOR_ERROR = Color.rgb(176, 0, 32);
    private static final int COLOR_DIVIDER = Color.rgb(218, 222, 219);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView serviceStatus;
    private TextView autoStartStatus;
    private TextView backgroundStatus;
    private Button backgroundPermissionButton;
    private TextView currentVolume;
    private TextView shizukuStatus;
    private AudioManager audioManager;
    private boolean permissionRequestInFlight;
    private boolean accessibilityPromptShown;

    private final Runnable volumeRefresh = new Runnable() {
        @Override
        public void run() {
            refreshCurrentVolume();
            refreshServiceStatus();
            refreshBackgroundStatus();
            refreshShizukuStatus();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = getSystemService(AudioManager.class);
        configureSystemBars();
        setContentView(createContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        VolumeKeeperService.start(this);
        refreshServiceStatus();
        refreshBackgroundStatus();
        handler.removeCallbacks(volumeRefresh);
        handler.post(volumeRefresh);
        continuePermissionSetup();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(volumeRefresh);
        super.onPause();
    }

    private View createContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text(getString(R.string.screen_title), 28, COLOR_TEXT);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);

        serviceStatus = text("", 15, COLOR_ERROR);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        content.addView(serviceStatus, statusParams);

        autoStartStatus = text("", 14, COLOR_SECONDARY);
        LinearLayout.LayoutParams autoStartParams = matchWrap();
        autoStartParams.topMargin = dp(4);
        content.addView(autoStartStatus, autoStartParams);

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText(R.string.open_accessibility_settings);
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setOnClickListener(view ->
                openAccessibilitySettings());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(12);
        content.addView(accessibilityButton, buttonParams);

        content.addView(divider(), dividerParams(28, 24));

        Switch enabled = new Switch(this);
        enabled.setId(R.id.enable_correction);
        enabled.setText(R.string.correction_enabled);
        enabled.setTextColor(COLOR_TEXT);
        enabled.setTextSize(17);
        enabled.setGravity(Gravity.CENTER_VERTICAL);
        enabled.setChecked(VolumeStepSettings.isEnabled(this));
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            VolumeStepSettings.setEnabled(this, isChecked);
            if (isChecked) {
                VolumeKeeperService.start(this);
                continuePermissionSetup();
            } else {
                VolumeKeeperService.stop(this);
            }
            refreshBackgroundStatus();
        });
        content.addView(enabled, matchWrap());

        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(28);

        TextView backgroundLabel = sectionLabel(R.string.background_section);
        content.addView(backgroundLabel, sectionParams);

        backgroundStatus = text("", 14, COLOR_SECONDARY);
        backgroundStatus.setId(R.id.background_status);
        LinearLayout.LayoutParams backgroundStatusParams = matchWrap();
        backgroundStatusParams.topMargin = dp(10);
        content.addView(backgroundStatus, backgroundStatusParams);

        backgroundPermissionButton = new Button(this);
        backgroundPermissionButton.setId(R.id.background_permissions);
        backgroundPermissionButton.setAllCaps(false);
        backgroundPermissionButton.setOnClickListener(view ->
                handleBackgroundPermissionAction());
        LinearLayout.LayoutParams backgroundButtonParams = matchWrap();
        backgroundButtonParams.topMargin = dp(10);
        content.addView(backgroundPermissionButton, backgroundButtonParams);

        Button autoStartButton = new Button(this);
        autoStartButton.setId(R.id.auto_start_settings);
        autoStartButton.setText(BackgroundAccess.hasVendorAutoStartSettings(this)
                ? R.string.open_auto_start_settings : R.string.view_auto_start_help);
        autoStartButton.setAllCaps(false);
        autoStartButton.setOnClickListener(view -> showAutoStartDialog());
        LinearLayout.LayoutParams autoStartButtonParams = matchWrap();
        autoStartButtonParams.topMargin = dp(6);
        content.addView(autoStartButton, autoStartButtonParams);

        Button vendorBackground = new Button(this);
        vendorBackground.setText(R.string.open_vendor_background_settings);
        vendorBackground.setAllCaps(false);
        vendorBackground.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.vendor_background_title)
                .setMessage(R.string.vendor_background_help)
                .setPositiveButton(R.string.open_vendor_background_settings,
                        (dialog, which) -> BackgroundAccess.openVendorBackgroundSettings(this))
                .setNegativeButton(android.R.string.cancel, null).show());
        content.addView(vendorBackground, autoStartButtonParams);

        TextView shizukuLabel = sectionLabel(R.string.shizuku_section);
        content.addView(shizukuLabel, sectionParams);
        shizukuStatus = text("", 14, COLOR_SECONDARY);
        shizukuStatus.setId(R.id.shizuku_status);
        content.addView(shizukuStatus, backgroundStatusParams);
        Button shizukuSetup = new Button(this);
        shizukuSetup.setId(R.id.shizuku_setup);
        shizukuSetup.setText(R.string.shizuku_setup);
        shizukuSetup.setAllCaps(false);
        shizukuSetup.setOnClickListener(view -> showShizukuSetup());
        content.addView(shizukuSetup, backgroundButtonParams);

        if (CompatibilityVolumeBridge.supportedDevice()) {
            Button companionSetup = new Button(this);
            companionSetup.setText(R.string.compatibility_setup);
            companionSetup.setAllCaps(false);
            companionSetup.setOnClickListener(view -> new AlertDialog.Builder(this)
                    .setTitle(R.string.compatibility_setup)
                    .setMessage(R.string.compatibility_help)
                    .setPositiveButton(R.string.compatibility_open_permissions, (dialog, which) -> {
                        try {
                            getPackageManager().getApplicationInfo(CompatibilityVolumeBridge.PACKAGE, 0);
                            BackgroundAccess.openAutoStartSettings(this, CompatibilityVolumeBridge.PACKAGE);
                        } catch (PackageManager.NameNotFoundException exception) {
                            new AlertDialog.Builder(this)
                                    .setMessage(R.string.compatibility_not_installed)
                                    .setPositiveButton(android.R.string.ok, null).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null).show());
            content.addView(companionSetup, autoStartButtonParams);
        }

        TextView stepsLabel = sectionLabel(R.string.step_count);
        content.addView(stepsLabel, sectionParams);

        NumberPicker stepPicker = new NumberPicker(this);
        stepPicker.setId(R.id.volume_steps);
        stepPicker.setMinValue(VolumeStepSettings.MIN_STEPS);
        stepPicker.setMaxValue(VolumeStepSettings.MAX_STEPS);
        stepPicker.setValue(VolumeStepSettings.steps(this));
        stepPicker.setWrapSelectorWheel(false);
        stepPicker.setFormatter(value -> getString(R.string.step_count_value, value));
        stepPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        stepPicker.setOnValueChangedListener((picker, oldValue, newValue) -> {
            VolumeStepSettings.setSteps(this, newValue);
            VolumeKeeperService.start(this);
        });
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                dp(180),
                dp(128)
        );
        pickerParams.gravity = Gravity.START;
        pickerParams.topMargin = dp(6);
        content.addView(stepPicker, pickerParams);

        TextView scopeLabel = sectionLabel(R.string.scope);
        content.addView(scopeLabel, sectionParams);

        RadioGroup scope = new RadioGroup(this);
        scope.setOrientation(RadioGroup.VERTICAL);
        RadioButton mediaOnly = radioButton(R.string.scope_media);
        RadioButton allStreams = radioButton(R.string.scope_all);
        mediaOnly.setId(View.generateViewId());
        allStreams.setId(View.generateViewId());
        scope.addView(mediaOnly);
        scope.addView(allStreams);
        scope.check(VolumeStepSettings.isMediaOnly(this)
                ? mediaOnly.getId()
                : allStreams.getId());
        scope.setOnCheckedChangeListener((group, checkedId) ->
                VolumeStepSettings.setMediaOnly(this, checkedId == mediaOnly.getId()));
        LinearLayout.LayoutParams scopeParams = matchWrap();
        scopeParams.topMargin = dp(8);
        content.addView(scope, scopeParams);

        content.addView(divider(), dividerParams(28, 20));

        currentVolume = text("", 16, COLOR_TEXT);
        content.addView(currentVolume);

        TextView privacy = text(getString(R.string.pass_through_status), 14, COLOR_SECONDARY);
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.topMargin = dp(10);
        content.addView(privacy, privacyParams);

        Button diagnostics = new Button(this);
        diagnostics.setId(R.id.export_diagnostics);
        diagnostics.setText(R.string.export_diagnostics);
        diagnostics.setAllCaps(false);
        diagnostics.setOnClickListener(view -> shareDiagnostics());
        content.addView(diagnostics, privacyParams);

        return scrollView;
    }

    private void refreshShizukuStatus() {
        if (shizukuStatus == null) {
            return;
        }
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        int status = controller == null ? VolumeStepSettings.isShizukuEnabled(this)
                ? R.string.shizuku_paused : R.string.shizuku_off : controller.shizukuStatus();
        int companion = controller == null ? R.string.compatibility_paused
                : controller.compatibilityStatus();
        shizukuStatus.setText(status == R.string.shizuku_ready ? getString(status)
                : companion == R.string.compatibility_ready ? getString(companion)
                : getString(status) + (companion == R.string.compatibility_not_applicable
                ? "" : "\n" + getString(companion)));
        shizukuStatus.setTextColor(status == R.string.shizuku_ready
                || companion == R.string.compatibility_ready ? COLOR_ACCENT : COLOR_SECONDARY);
    }

    private void showShizukuSetup() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.shizuku_section)
                .setMessage(R.string.shizuku_help)
                .setPositiveButton(R.string.shizuku_connect, (ignored, which) -> connectShizuku())
                .setNegativeButton(android.R.string.cancel, null);
        if (VolumeStepSettings.isShizukuEnabled(this)) {
            dialog.setNeutralButton(R.string.shizuku_disable, (ignored, which) -> {
                VolumeStepSettings.setShizukuEnabled(this, false);
                refreshShizukuStatus();
            });
        }
        dialog.show();
    }

    private void connectShizuku() {
        VolumeStepSettings.setShizukuEnabled(this, true);
        VolumeKeeperService.start(this);
        try {
            if (!Shizuku.pingBinder()) {
                openShizuku();
            } else if (Shizuku.isPreV11() || Shizuku.getUid() != 2000) {
                new AlertDialog.Builder(this).setMessage(R.string.shizuku_unsupported)
                        .setPositiveButton(android.R.string.ok, null).show();
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                VolumeAdjustmentController controller = VolumeKeeperService.controller();
                if (controller != null) {
                    controller.connectShizuku();
                }
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                openShizuku();
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU);
            }
        } catch (RuntimeException exception) {
            openShizuku();
        }
        refreshShizukuStatus();
    }

    private void openShizuku() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        startActivity(launch == null ? new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://shizuku.rikka.app/download/")) : launch);
    }

    private void refreshServiceStatus() {
        boolean connected = FineVolumeAccessibilityService.isConnected();
        serviceStatus.setText(connected ? R.string.service_enabled
                : AccessibilityStatus.isEnabled(this) ? R.string.service_connecting
                : R.string.service_disabled);
        serviceStatus.setTextColor(connected ? COLOR_ACCENT : COLOR_SECONDARY);
        autoStartStatus.setText(BackgroundAccess.hasVendorAutoStartSettings(this)
                ? R.string.autostart_vendor_required : R.string.autostart_standard);
    }

    private void refreshBackgroundStatus() {
        if (backgroundStatus == null || backgroundPermissionButton == null) {
            return;
        }
        boolean notificationAllowed = hasNotificationPermission();
        boolean batteryUnrestricted =
                BackgroundAccess.isIgnoringBatteryOptimizations(this);
        backgroundStatus.setText(getString(
                R.string.background_status,
                getString(runtimeStatus()),
                getString(notificationAllowed
                        ? R.string.notification_allowed
                        : R.string.notification_required),
                getString(batteryUnrestricted
                        ? R.string.battery_unrestricted
                        : R.string.battery_restricted),
                getString(Settings.canDrawOverlays(this)
                        ? R.string.overlay_allowed : R.string.overlay_required)
        ));

        if (!notificationAllowed) {
            backgroundPermissionButton.setText(R.string.allow_background_notification);
            backgroundPermissionButton.setEnabled(true);
        } else if (!batteryUnrestricted) {
            backgroundPermissionButton.setText(R.string.disable_battery_optimization);
            backgroundPermissionButton.setEnabled(true);
        } else if (!Settings.canDrawOverlays(this)) {
            backgroundPermissionButton.setText(R.string.allow_volume_overlay);
            backgroundPermissionButton.setEnabled(true);
        } else {
            backgroundPermissionButton.setText(R.string.background_permissions_ready);
            backgroundPermissionButton.setEnabled(false);
        }
    }

    private int runtimeStatus() {
        if (!VolumeStepSettings.isEnabled(this)) {
            return R.string.correction_stopped;
        }
        if (VolumeKeeperService.getStartError() != null) {
            return R.string.correction_start_failed;
        }
        if (!VolumeKeeperService.isRunning() || VolumeKeeperService.controller() == null) {
            return R.string.correction_starting;
        }
        if (!FineVolumeAccessibilityService.isConnected()) {
            return R.string.correction_waiting_accessibility;
        }
        return R.string.correction_running;
    }

    private void handleBackgroundPermissionAction() {
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                    && (!flag("notification_requested")
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                requestNotificationPermission();
            } else {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
            return;
        }
        if (!BackgroundAccess.isIgnoringBatteryOptimizations(this)) {
            requestBatteryPermission();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        }
    }

    private boolean hasNotificationPermission() {
        return getSystemService(NotificationManager.class).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            permissionRequestInFlight = false;
            refreshBackgroundStatus();
            continuePermissionSetup();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BATTERY || requestCode == REQUEST_OVERLAY) {
            permissionRequestInFlight = false;
            refreshBackgroundStatus();
            continuePermissionSetup();
        }
    }

    private boolean flag(String key) {
        return VolumeStepSettings.preferences(this).getBoolean(key, false);
    }

    private void mark(String key) {
        VolumeStepSettings.preferences(this).edit().putBoolean(key, true).apply();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        mark("notification_requested");
        permissionRequestInFlight = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS);
    }

    private void continuePermissionSetup() {
        if (!VolumeStepSettings.isEnabled(this) || permissionRequestInFlight) {
            return;
        }
        if (!AccessibilityStatus.isEnabled(this)) {
            if (!accessibilityPromptShown) {
                boolean recovery = flag("accessibility_explained");
                accessibilityPromptShown = true;
                mark("accessibility_explained");
                permissionRequestInFlight = true;
                new AlertDialog.Builder(this)
                        .setTitle(recovery ? R.string.accessibility_recovery_title
                                : R.string.accessibility_setup_title)
                        .setMessage(recovery ? R.string.accessibility_recovery_help
                                : R.string.accessibility_service_description)
                        .setPositiveButton(R.string.open_accessibility_settings,
                                (dialog, which) -> openAccessibilitySettings())
                        .setNegativeButton(android.R.string.cancel, null)
                        .setOnDismissListener(dialog -> {
                            permissionRequestInFlight = false;
                            handler.postDelayed(() -> {
                                if (hasWindowFocus()) {
                                    continuePermissionSetup();
                                }
                            }, 150);
                        })
                        .show();
                return;
            }
        } else {
            accessibilityPromptShown = false;
        }
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !flag("notification_requested")) {
                requestNotificationPermission();
                return;
            }
        }
        if (!BackgroundAccess.isIgnoringBatteryOptimizations(this)
                && !flag("battery_requested")) {
            mark("battery_requested");
            requestBatteryPermission();
            return;
        }
        if (!Settings.canDrawOverlays(this) && !flag("overlay_requested")) {
            mark("overlay_requested");
            requestOverlayPermission();
            return;
        }
        if (BackgroundAccess.hasVendorAutoStartSettings(this)
                && !flag("auto_start_explained_v2")) {
            showAutoStartDialog();
        }
    }

    private void showAutoStartDialog() {
        boolean vendor = BackgroundAccess.hasVendorAutoStartSettings(this);
        mark("auto_start_explained_v2");
        permissionRequestInFlight = true;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.auto_start_setup_title)
                .setMessage(vendor ? R.string.auto_start_vendor_help
                        : R.string.auto_start_standard_help)
                .setOnDismissListener(ignored -> permissionRequestInFlight = false);
        if (vendor) {
            dialog.setPositiveButton(R.string.open_auto_start_settings,
                    (ignored, which) -> BackgroundAccess.openAutoStartSettings(this))
                    .setNegativeButton(android.R.string.cancel, null);
        } else {
            dialog.setPositiveButton(android.R.string.ok, null);
        }
        dialog.show();
    }

    private void openAccessibilitySettings() {
        Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME",
                        new ComponentName(this, FineVolumeAccessibilityService.class)
                                .flattenToString());
        try {
            startActivity(details);
        } catch (RuntimeException exception) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private void refreshCurrentVolume() {
        if (audioManager == null || currentVolume == null) {
            return;
        }
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        currentVolume.setText(getString(R.string.current_media_volume,
                VolumeDisplay.percent(current, max), VolumeStepSettings.steps(this)));
    }

    private void requestOverlayPermission() {
        permissionRequestInFlight = true;
        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())), REQUEST_OVERLAY);
    }

    private void requestBatteryPermission() {
        permissionRequestInFlight = true;
        BackgroundAccess.requestBatteryOptimizationExemption(this, REQUEST_BATTERY);
    }

    private void shareDiagnostics() {
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        String version = "unknown";
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException ignored) { }
        String data = "device=" + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT
                + "\nkernel=" + System.getProperty("os.version")
                + "\npackage=" + getPackageName()
                + "\napp version=" + version
                + "\nstandard boot permission granted="
                        + (checkSelfPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
                        == PackageManager.PERMISSION_GRANTED)
                + "\nboot receiver=" + bootReceiverDiagnostics()
                + "\nenabled=" + VolumeStepSettings.isEnabled(this)
                + "\nconfigured steps=" + VolumeStepSettings.steps(this)
                + "\nmedia only=" + VolumeStepSettings.isMediaOnly(this)
                + "\nkeeper running=" + VolumeKeeperService.isRunning()
                + "\naccessibility enabled=" + AccessibilityStatus.isEnabled(this)
                + "\naccessibility connected=" + FineVolumeAccessibilityService.isConnected()
                + "\noverlay granted=" + Settings.canDrawOverlays(this)
                + "\nbattery exempt=" + BackgroundAccess.isIgnoringBatteryOptimizations(this)
                + "\nlast boot action=" + VolumeStepSettings.preferences(this)
                        .getString("last_boot_action", "none")
                + "\nlast boot time=" + VolumeStepSettings.preferences(this)
                        .getLong("last_boot_time", 0)
                + "\n" + (controller == null ? "monitor not running" : controller.diagnostics());
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_diagnostics))
                .putExtra(Intent.EXTRA_TEXT, data), getString(R.string.export_diagnostics)));
    }

    private String bootReceiverDiagnostics() {
        try {
            android.content.pm.ActivityInfo receiver = getPackageManager().getReceiverInfo(
                    new ComponentName(this, BootReceiver.class), 0);
            int state = getPackageManager().getComponentEnabledSetting(
                    new ComponentName(this, BootReceiver.class));
            boolean enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && receiver.enabled;
            android.content.pm.ApplicationInfo app = getApplicationInfo();
            return "enabled=" + enabled + ", exported=" + receiver.exported
                    + ", directBootAware=" + receiver.directBootAware
                    + ", app stopped="
                    + ((app.flags & android.content.pm.ApplicationInfo.FLAG_STOPPED) != 0);
        } catch (PackageManager.NameNotFoundException exception) {
            return "missing: " + exception;
        }
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private TextView sectionLabel(int textRes) {
        TextView label = text(getString(textRes), 14, COLOR_SECONDARY);
        label.setAllCaps(true);
        return label;
    }

    private RadioButton radioButton(int textRes) {
        RadioButton button = new RadioButton(this);
        button.setText(textRes);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        return divider;
    }

    private LinearLayout.LayoutParams dividerParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
