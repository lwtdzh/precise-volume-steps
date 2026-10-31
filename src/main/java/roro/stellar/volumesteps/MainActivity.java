package roro.stellar.volumesteps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 10;
    private static final int REQUEST_BATTERY = 11;
    private static final int REQUEST_OVERLAY = 12;
    private static final int REQUEST_SHIZUKU = 13;
    private static final int REQUEST_SHIZUKU_COMPATIBILITY_GRANT = 14;
    private static final int COLOR_BACKGROUND = Color.rgb(247, 249, 247);
    private static final int COLOR_TEXT = Color.rgb(28, 30, 29);
    private static final int COLOR_SECONDARY = Color.rgb(85, 92, 88);
    private static final int COLOR_ACCENT = Color.rgb(0, 108, 76);
    private static final int COLOR_ERROR = Color.rgb(176, 0, 32);
    private static final int COLOR_DIVIDER = Color.rgb(218, 222, 219);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView serviceStatus;
    private TextView backgroundStatus;
    private TextView currentVolume;
    private TextView shizukuStatus;
    private Button compatibilityGrantButton;
    private AudioManager audioManager;
    private CompatibilityGrantBridge compatibilityGrant;
    private SetupShellBridge setupShell;
    private LinearLayout selectedModeContent;
    private TextView modeHelp;
    private boolean appSetupPermissionPending;
    private boolean permissionRequestInFlight;
    private boolean compatibilityGrantPermissionPending;
    private final Shizuku.OnBinderReceivedListener shizukuReceived = this::refreshShizukuStatus;
    private final Shizuku.OnBinderDeadListener shizukuDead = this::refreshShizukuStatus;
    private final Shizuku.OnRequestPermissionResultListener compatibilityGrantPermission =
            (requestCode, grantResult) -> {
                refreshShizukuStatus();
                if (requestCode == REQUEST_SHIZUKU && appSetupPermissionPending) {
                    appSetupPermissionPending = false;
                    if (grantResult == PackageManager.PERMISSION_GRANTED
                            && VolumeStepSettings.mode(this) == OperatingMode.SHIZUKU)
                        setupShizukuPermissions();
                    return;
                }
                if (requestCode != REQUEST_SHIZUKU_COMPATIBILITY_GRANT
                        || !compatibilityGrantPermissionPending) {
                    return;
                }
                compatibilityGrantPermissionPending = false;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    grantCompatibilityWithAuthorizedShizuku();
                } else {
                    showCompatibilityGrantMessage(R.string.compatibility_grant_permission_denied);
                }
            };

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
        compatibilityGrant = new CompatibilityGrantBridge(this);
        setupShell = new SetupShellBridge(this);
        Shizuku.addRequestPermissionResultListener(compatibilityGrantPermission, handler);
        configureSystemBars();
        setContentView(createContent());
        Shizuku.addBinderReceivedListenerSticky(shizukuReceived, handler);
        Shizuku.addBinderDeadListener(shizukuDead, handler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        VolumeKeeperService.start(this);
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        if (controller != null && VolumeStepSettings.isShizukuEnabled(this)) {
            controller.connectShizuku();
        }
        refreshServiceStatus();
        refreshBackgroundStatus();
        handler.removeCallbacks(volumeRefresh);
        handler.post(volumeRefresh);
        handler.post(this::showFirstLaunchPermissionGuide);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(volumeRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuReceived);
        Shizuku.removeBinderDeadListener(shizukuDead);
        Shizuku.removeRequestPermissionResultListener(compatibilityGrantPermission);
        handler.removeCallbacksAndMessages(null);
        compatibilityGrant.close();
        setupShell.close();
        super.onDestroy();
    }

    private View createContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        TextView title = text(getString(R.string.screen_title), 28, COLOR_TEXT);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);
        content.addView(text(getString(R.string.home_intro), 14, COLOR_SECONDARY));

        LinearLayout overview = card(content);
        Switch enabled = new Switch(this);
        enabled.setId(R.id.enable_correction);
        enabled.setText(R.string.home_enabled);
        enabled.setTextSize(18);
        enabled.setTextColor(COLOR_TEXT);
        enabled.setChecked(VolumeStepSettings.isEnabled(this));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            VolumeStepSettings.setEnabled(this, checked);
            if (checked) VolumeKeeperService.start(this); else VolumeKeeperService.stop(this);
            refreshServiceStatus();
        });
        overview.addView(enabled, matchWrap());
        serviceStatus = text("", 14, COLOR_SECONDARY);
        overview.addView(serviceStatus, matchWrap());
        currentVolume = text("", 16, COLOR_TEXT);
        overview.addView(currentVolume, matchWrap());
        LinearLayout steps = card(content);
        steps.addView(text(getString(R.string.home_steps), 18, COLOR_TEXT));
        TextView stepHint = text(stepHint(), 14, COLOR_SECONDARY);
        steps.addView(stepHint);
        NumberPicker picker = new NumberPicker(this);
        picker.setId(R.id.volume_steps);
        picker.setMinValue(VolumeStepSettings.MIN_STEPS);
        picker.setMaxValue(VolumeStepSettings.MAX_STEPS);
        picker.setValue(VolumeStepSettings.steps(this));
        picker.setWrapSelectorWheel(false);
        picker.setFormatter(value -> getString(R.string.home_step_value, value));
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setOnValueChangedListener((view, oldValue, value) -> {
            VolumeStepSettings.setSteps(this, value);
            stepHint.setText(stepHint());
        });
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(-1, dp(116));
        steps.addView(picker, pickerParams);

        LinearLayout modes = card(content);
        modes.addView(text(getString(R.string.home_mode), 18, COLOR_TEXT));
        RadioGroup choices = new RadioGroup(this);
        int[] labels = {R.string.mode_assistant, R.string.mode_shizuku, R.string.mode_root};
        int[] ids = {R.id.mode_assistant, R.id.mode_shizuku, R.id.mode_root};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(ids[i]);
            option.setText(labels[i]);
            option.setTextSize(16);
            option.setTextColor(COLOR_TEXT);
            choices.addView(option, new RadioGroup.LayoutParams(-1, -2));
        }
        choices.check(ids[VolumeStepSettings.mode(this).ordinal()]);
        choices.setOnCheckedChangeListener((group, id) -> {
            OperatingMode mode = id == R.id.mode_root ? OperatingMode.ROOT
                    : id == R.id.mode_shizuku ? OperatingMode.SHIZUKU : OperatingMode.ASSISTANT;
            VolumeStepSettings.setMode(this, mode);
            VolumeKeeperService.start(this);
            renderSelectedMode();
        });
        modes.addView(choices);
        modeHelp = text("", 14, COLOR_SECONDARY);
        modeHelp.setId(R.id.mode_help);
        modes.addView(modeHelp);
        shizukuStatus = text("", 14, COLOR_SECONDARY);
        shizukuStatus.setId(R.id.shizuku_status);
        modes.addView(shizukuStatus);
        selectedModeContent = new LinearLayout(this);
        selectedModeContent.setOrientation(LinearLayout.VERTICAL);
        modes.addView(selectedModeContent, matchWrap());
        renderSelectedMode();

        LinearLayout permissions = card(content);
        permissions.addView(text(getString(R.string.home_permissions), 18, COLOR_TEXT));
        backgroundStatus = text("", 14, COLOR_SECONDARY);
        backgroundStatus.setId(R.id.background_status);
        permissions.addView(backgroundStatus);
        permissions.addView(action(getString(R.string.home_permission_guide), R.id.permission_guide, this::showPermissionGuide), matchWrap());
        content.addView(action(getString(R.string.home_advanced), R.id.advanced_settings, this::showAdvanced), matchWrap());
        return scroll;
    }

    private String stepHint() {
        return getString(R.string.home_step_hint, 100d / VolumeStepSettings.steps(this));
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(12));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), COLOR_DIVIDER);
        card.setBackground(background);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(16);
        parent.addView(card, params);
        return card;
    }

    private Button action(String label, int id, Runnable callback) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> callback.run());
        return button;
    }

    private void renderSelectedMode() {
        selectedModeContent.removeAllViews();
        compatibilityGrantButton = null;
        OperatingMode mode = VolumeStepSettings.mode(this);
        if (mode == OperatingMode.ROOT) {
            modeHelp.setText(R.string.root_mode_help);
            selectedModeContent.addView(action(getString(R.string.root_setup), R.id.mode_setup, () -> {
                VolumeKeeperService.start(this);
                handler.postDelayed(() -> {
                    VolumeAdjustmentController controller = VolumeKeeperService.controller();
                    if (controller != null) controller.setupRoot(this::showSetupReport);
                    else showSetupReport(getString(R.string.root_enable_first));
                }, 150);
            }), matchWrap());
        } else if (mode == OperatingMode.SHIZUKU) {
            modeHelp.setText(R.string.shizuku_mode_help);
            selectedModeContent.addView(action(getString(R.string.shizuku_mode_setup),
                    R.id.shizuku_setup, this::connectShizuku), matchWrap());
        } else {
            modeHelp.setText(CompatibilityVolumeBridge.supportedDevice()
                    ? R.string.assistant_vivo_help : R.string.assistant_mode_help);
            selectedModeContent.addView(action(getString(R.string.assistant_accessibility), R.id.mode_setup,
                    this::openAccessibilitySettings), matchWrap());
            if (CompatibilityVolumeBridge.supportedDevice()) {
                selectedModeContent.addView(action(getString(R.string.helper_install), R.id.install_helper,
                        this::installBundledHelper), matchWrap());
                selectedModeContent.addView(action(getString(R.string.helper_background), View.generateViewId(), () -> {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + CompatibilityVolumeBridge.PACKAGE))); }
                    catch (RuntimeException exception) { showCompatibilityGrantMessage(R.string.compatibility_not_installed); }
                }), matchWrap());
                compatibilityGrantButton = action(getString(R.string.compatibility_grant_methods), R.id.compatibility_grant,
                        this::showCompatibilityGrantMethods);
                selectedModeContent.addView(compatibilityGrantButton, matchWrap());
            }
        }
        refreshShizukuStatus();
    }

    private void showAdvanced() {
        new AlertDialog.Builder(this).setTitle(R.string.advanced_title)
                .setItems(new String[]{VolumeStepSettings.isMediaOnly(this)
                        ? getString(R.string.advanced_scope_media) : getString(R.string.advanced_scope_all),
                        getString(R.string.export_diagnostics)},
                        (dialog, which) -> {
                            if (which == 1) { shareDiagnostics(); return; }
                            new AlertDialog.Builder(this).setTitle(R.string.advanced_scope)
                                    .setSingleChoiceItems(new String[]{getString(R.string.scope_media_recommended),
                                            getString(R.string.scope_all_short)},
                                            VolumeStepSettings.isMediaOnly(this) ? 0 : 1,
                                            (choice, selected) -> {
                                                VolumeStepSettings.setMediaOnly(this, selected == 0);
                                                choice.dismiss();
                                            }).setNegativeButton(android.R.string.cancel, null).show();
                        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void installBundledHelper() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle(R.string.helper_install_allow_title)
                    .setMessage(R.string.helper_install_allow_help)
                    .setPositiveButton(R.string.open_settings, (dialog, which) -> startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        try {
            HelperApkProvider.prepare(this);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(
                    HelperApkProvider.URI, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("内置助手", HelperApkProvider.URI));
            startActivity(intent);
        } catch (Exception exception) { showSetupReport(getString(R.string.helper_installer_failed, exception.getMessage())); }
    }

    private void showSetupReport(String report) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle(R.string.setup_result).setMessage(report)
                .setPositiveButton(R.string.home_permission_guide, (dialog, which) -> showPermissionGuide())
                .setNegativeButton(R.string.setup_done, null).show();
    }

    private void setupShizukuPermissions() {
        setupShell.setup(this::showSetupReport);
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        if (controller != null) controller.connectShizuku();
    }

    private void refreshShizukuStatus() {
        if (shizukuStatus == null) {
            return;
        }
        VolumeAdjustmentController controller = VolumeKeeperService.controller();
        if (VolumeStepSettings.mode(this) == OperatingMode.ROOT) {
            shizukuStatus.setText(controller == null ? getString(R.string.enable_first)
                    : controller.rootStatus());
            shizukuStatus.setTextColor(COLOR_SECONDARY);
            return;
        }
        if (VolumeStepSettings.mode(this) == OperatingMode.ASSISTANT) {
            int companion = controller == null ? R.string.compatibility_paused
                    : controller.compatibilityStatus();
            shizukuStatus.setText(companion);
            shizukuStatus.setTextColor(companion == R.string.compatibility_input_ready
                    || companion == R.string.compatibility_ready ? COLOR_ACCENT : COLOR_SECONDARY);
            refreshCompatibilityGrantButton();
            return;
        }
        int authorization = ShizukuVolumeBridge.authorizationStatus();
        if (authorization != R.string.shizuku_authorized) {
            shizukuStatus.setText(authorization);
            shizukuStatus.setTextColor(COLOR_ERROR);
            return;
        }
        int status = controller == null
                ? VolumeStepSettings.isEnabled(this) ? R.string.shizuku_paused
                        : R.string.shizuku_correction_off
                : controller.shizukuStatus();
        shizukuStatus.setText(getString(R.string.shizuku_authorized_status, getString(status)));
        shizukuStatus.setTextColor(status == R.string.shizuku_ready
                ? COLOR_ACCENT : status == R.string.shizuku_correction_off
                        || status == R.string.shizuku_connecting ? COLOR_SECONDARY : COLOR_ERROR);
    }

    private void refreshCompatibilityGrantButton() {
        if (compatibilityGrantButton == null) return;
        boolean installed;
        try {
            getPackageManager().getApplicationInfo(CompatibilityVolumeBridge.PACKAGE, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException exception) {
            installed = false;
        }
        boolean granted = installed && CompatibilityGrantBridge.isGranted(this);
        compatibilityGrantButton.setText(granted
                ? R.string.compatibility_grant_ready
                : R.string.compatibility_grant_methods);
        compatibilityGrantButton.setEnabled(installed && !granted);
    }

    private void showCompatibilityGrantMethods() {
        if (!isCompatibilityHelperInstalled()) {
            showCompatibilityGrantMessage(R.string.compatibility_not_installed);
            return;
        }
        if (CompatibilityGrantBridge.isGranted(this)) {
            refreshCompatibilityGrantButton();
            showCompatibilityGrantMessage(R.string.compatibility_grant_already_ready);
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(12));
        content.addView(text(
                getString(R.string.compatibility_grant_intro), 14, COLOR_SECONDARY),
                matchWrap());

        TextView usbTitle = text(
                getString(R.string.compatibility_grant_usb_title), 16, COLOR_TEXT);
        usbTitle.setTypeface(usbTitle.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams methodTitleParams = matchWrap();
        methodTitleParams.topMargin = dp(18);
        content.addView(usbTitle, methodTitleParams);

        TextView usbHelp = text(
                getString(R.string.compatibility_grant_usb_help), 14, COLOR_SECONDARY);
        LinearLayout.LayoutParams methodTextParams = matchWrap();
        methodTextParams.topMargin = dp(4);
        content.addView(usbHelp, methodTextParams);

        TextView command = text(CompatibilityGrantContract.ADB_COMMAND, 13, COLOR_TEXT);
        command.setTypeface(android.graphics.Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setGravity(Gravity.START);
        LinearLayout.LayoutParams commandParams = matchWrap();
        commandParams.topMargin = dp(10);
        content.addView(command, commandParams);

        Button copy = new Button(this);
        copy.setId(R.id.compatibility_copy_adb);
        copy.setText(R.string.compatibility_copy_adb);
        copy.setAllCaps(false);
        copy.setOnClickListener(view -> copyCompatibilityAdbCommand());
        LinearLayout.LayoutParams methodButtonParams = matchWrap();
        methodButtonParams.topMargin = dp(8);
        content.addView(copy, methodButtonParams);

        TextView shizukuTitle = text(
                getString(R.string.compatibility_grant_shizuku_title), 16, COLOR_TEXT);
        shizukuTitle.setTypeface(
                shizukuTitle.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(shizukuTitle, methodTitleParams);

        TextView shizukuHelp = text(
                getString(R.string.compatibility_grant_shizuku_help), 14, COLOR_SECONDARY);
        content.addView(shizukuHelp, methodTextParams);

        AlertDialog[] holder = new AlertDialog[1];
        Button shizuku = new Button(this);
        shizuku.setId(R.id.compatibility_grant_shizuku);
        shizuku.setText(R.string.compatibility_grant_shizuku);
        shizuku.setAllCaps(false);
        shizuku.setOnClickListener(view -> {
            if (holder[0] != null) holder[0].dismiss();
            grantCompatibilityWithShizuku();
        });
        content.addView(shizuku, methodButtonParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.compatibility_grant_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        holder[0].show();
    }

    private void copyCompatibilityAdbCommand() {
        getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText("ADB", CompatibilityGrantContract.ADB_COMMAND));
        Toast.makeText(this, R.string.compatibility_adb_copied, Toast.LENGTH_SHORT).show();
    }

    private void grantCompatibilityWithShizuku() {
        if (!isCompatibilityHelperInstalled()) {
            showCompatibilityGrantMessage(R.string.compatibility_not_installed);
            return;
        }
        if (CompatibilityGrantBridge.isGranted(this)) {
            refreshCompatibilityGrantButton();
            showCompatibilityGrantMessage(R.string.compatibility_grant_already_ready);
            return;
        }
        try {
            if (!Shizuku.pingBinder()) {
                showCompatibilityGrantUnavailable(R.string.shizuku_not_running);
            } else if (Shizuku.isPreV11()
                    || !ShizukuServerIdentity.supported(Shizuku.getUid())) {
                showCompatibilityGrantUnavailable(R.string.shizuku_unsupported);
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                grantCompatibilityWithAuthorizedShizuku();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                showCompatibilityGrantUnavailable(
                        R.string.compatibility_grant_permission_required);
            } else {
                compatibilityGrantPermissionPending = true;
                Shizuku.requestPermission(REQUEST_SHIZUKU_COMPATIBILITY_GRANT);
            }
        } catch (RuntimeException exception) {
            showCompatibilityGrantUnavailable(R.string.shizuku_not_running);
        }
    }

    private void grantCompatibilityWithAuthorizedShizuku() {
        compatibilityGrantButton.setEnabled(false);
        compatibilityGrantButton.setText(R.string.compatibility_grant_working);
        compatibilityGrant.grant((success, error) -> {
            if (success) {
                VolumeAdjustmentController controller = VolumeKeeperService.controller();
                if (controller != null) controller.refreshCompatibilityInput();
                showCompatibilityGrantMessage(R.string.compatibility_grant_success);
                handler.postDelayed(this::refreshShizukuStatus, 300);
            } else {
                showCompatibilityGrantMessage(R.string.compatibility_grant_failed);
                refreshCompatibilityGrantButton();
            }
        });
    }

    private void showCompatibilityGrantUnavailable(int message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.shizuku_open, (dialog, which) -> openShizuku())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCompatibilityGrantMessage(int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
            } else if (Shizuku.isPreV11()
                    || !ShizukuServerIdentity.supported(Shizuku.getUid())) {
                new AlertDialog.Builder(this).setMessage(R.string.shizuku_unsupported)
                        .setPositiveButton(android.R.string.ok, null).show();
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setupShizukuPermissions();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                openShizuku();
            } else {
                appSetupPermissionPending = true;
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
        serviceStatus.setText(!VolumeStepSettings.isEnabled(this) ? R.string.correction_stopped
                : connected ? R.string.service_enabled
                : AccessibilityStatus.isEnabled(this) ? R.string.service_connecting
                : R.string.service_disabled);
        serviceStatus.setTextColor(connected ? COLOR_ACCENT : COLOR_SECONDARY);
    }

    private void refreshBackgroundStatus() {
        if (backgroundStatus == null) {
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
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BATTERY || requestCode == REQUEST_OVERLAY) {
            permissionRequestInFlight = false;
            refreshBackgroundStatus();
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

    private void showFirstLaunchPermissionGuide() {
        if (!flag("permission_guide_shown_v1") && !permissionRequestInFlight) {
            showPermissionGuide();
        }
    }

    private void showPermissionGuide() {
        if (permissionRequestInFlight) return;
        mark("permission_guide_shown_v1");
        PermissionGuideState state = permissionGuideState();
        permissionRequestInFlight = true;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(12));

        OperatingMode mode = VolumeStepSettings.mode(this);
        TextView introduction = text(getString(mode != OperatingMode.ASSISTANT
                ? R.string.privileged_guide_summary : state.coreReady()
                        ? R.string.permission_guide_summary_ready
                        : R.string.permission_guide_summary_missing), 14, COLOR_SECONDARY);
        introduction.setGravity(Gravity.START);
        content.addView(introduction, matchWrap());
        if (mode != OperatingMode.ASSISTANT) {
            content.addView(text(getString(mode == OperatingMode.ROOT
                    ? R.string.root_guide_help : R.string.shizuku_guide_help),
                    14, COLOR_SECONDARY), matchWrap());
        }

        AlertDialog[] holder = new AlertDialog[1];
        addPermissionGuideRow(content,
                R.string.permission_accessibility_title,
                state.accessibility ? R.string.permission_status_ready
                        : R.string.permission_status_required,
                state.accessibility,
                R.string.open_accessibility_settings,
                R.id.guide_accessibility,
                holder,
                this::openAccessibilitySettings);
        addPermissionGuideRow(content,
                R.string.permission_notification_title,
                state.notification ? R.string.permission_status_ready
                        : R.string.permission_status_required,
                state.notification,
                R.string.open_notification_settings,
                R.id.guide_notification,
                holder,
                this::openNotificationPermission);
        addPermissionGuideRow(content,
                R.string.permission_battery_title,
                state.battery ? R.string.permission_status_ready
                        : R.string.permission_status_required,
                state.battery,
                R.string.open_battery_settings,
                R.id.guide_battery,
                holder,
                this::requestBatteryPermission);
        addPermissionGuideRow(content,
                R.string.permission_overlay_title,
                state.overlay ? R.string.permission_status_ready
                        : R.string.permission_status_required,
                state.overlay,
                R.string.open_overlay_settings,
                R.id.guide_overlay,
                holder,
                this::requestOverlayPermission);

        boolean vendorAutoStart = BackgroundAccess.hasVendorAutoStartSettings(this);
        addPermissionGuideRow(content,
                R.string.permission_auto_start_title,
                vendorAutoStart ? R.string.permission_status_manual
                        : R.string.permission_status_standard_ready,
                !vendorAutoStart,
                vendorAutoStart ? R.string.open_auto_start_settings
                        : R.string.view_auto_start_help,
                R.id.guide_auto_start,
                holder,
                vendorAutoStart
                        ? () -> BackgroundAccess.openAutoStartSettings(this)
                        : this::showAutoStartDialog);
        addPermissionGuideRow(content,
                R.string.permission_background_title,
                R.string.permission_status_manual,
                false,
                R.string.open_vendor_background_settings,
                R.id.guide_background,
                holder,
                () -> BackgroundAccess.openVendorBackgroundSettings(this));

        if (VolumeStepSettings.mode(this) == OperatingMode.ASSISTANT
                && CompatibilityVolumeBridge.supportedDevice()) {
            int helperStatus = !state.helperInstalled
                    ? R.string.permission_helper_not_installed
                    : state.helperOffDisplayReady()
                            ? R.string.permission_helper_ready
                            : R.string.permission_helper_optional;
            addPermissionGuideRow(content,
                    R.string.permission_helper_title,
                    helperStatus,
                    state.helperOffDisplayReady(),
                    state.helperOffDisplayReady()
                            ? R.string.compatibility_grant_ready
                            : R.string.compatibility_grant_methods,
                    R.id.guide_helper_grant,
                    holder,
                    this::showCompatibilityGrantMethods);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.permission_guide_title)
                .setView(scroll)
                .setNegativeButton(R.string.permission_guide_later, null)
                .setOnDismissListener(dialog -> permissionRequestInFlight = false)
                .create();
        holder[0].show();
    }

    private void addPermissionGuideRow(
            LinearLayout parent,
            int titleRes,
            int statusRes,
            boolean ready,
            int actionRes,
            int actionId,
            AlertDialog[] dialog,
            Runnable action
    ) {
        TextView title = text(getString(titleRes), 16, COLOR_TEXT);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(18);
        parent.addView(title, titleParams);

        TextView status = text(getString(statusRes), 13, ready ? COLOR_ACCENT : COLOR_SECONDARY);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(2);
        parent.addView(status, statusParams);

        Button button = new Button(this);
        button.setId(actionId);
        button.setText(actionRes);
        button.setAllCaps(false);
        button.setOnClickListener(view -> {
            if (dialog[0] != null) dialog[0].dismiss();
            action.run();
        });
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(6);
        parent.addView(button, buttonParams);
    }

    private PermissionGuideState permissionGuideState() {
        boolean helperInstalled = isCompatibilityHelperInstalled();
        return new PermissionGuideState(
                AccessibilityStatus.isEnabled(this),
                hasNotificationPermission(),
                BackgroundAccess.isIgnoringBatteryOptimizations(this),
                Settings.canDrawOverlays(this),
                helperInstalled,
                helperInstalled && CompatibilityGrantBridge.isGranted(this));
    }

    private boolean isCompatibilityHelperInstalled() {
        try {
            getPackageManager().getApplicationInfo(CompatibilityGrantContract.HELPER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private void openNotificationPermission() {
        if (!hasNotificationPermission()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && (!flag("notification_requested")
                || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            requestNotificationPermission();
            return;
        }
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
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
        String component = new ComponentName(this, FineVolumeAccessibilityService.class)
                .flattenToString();
        Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component);
        try {
            startActivity(details);
            return;
        } catch (RuntimeException exception) {
            // AOSP reserves this activity for system/installer apps.
        }
        if (CompatibilityVolumeBridge.supportedDevice()) {
            try {
                // Vivo exposes a fragment-validated settings entry. The system
                // detail fragment still enforces administrator/AppOp restrictions.
                startActivity(new Intent().setComponent(new ComponentName(
                        "com.android.settings", "com.vivo.settings.VivoSubSettings"))
                        .putExtra(":settings:show_fragment",
                                "com.android.settings.accessibility.AccessibilityDetailsSettingsFragment")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, component));
                return;
            } catch (RuntimeException ignored) { }
        }
        Bundle arguments = new Bundle();
        arguments.putString(":settings:fragment_args_key", component);
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", component)
                .putExtra(":settings:show_fragment_args", arguments));
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
