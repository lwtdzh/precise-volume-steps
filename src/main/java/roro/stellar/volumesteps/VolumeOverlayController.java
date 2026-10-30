package roro.stellar.volumesteps;

import android.content.Context;
import android.app.KeyguardManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

final class VolumeOverlayController {
    enum CorrectionState {
        PENDING, SETTLING, CONFIRMED, UNCONFIRMED;

        boolean holdsReadback() {
            return this == PENDING || this == SETTLING;
        }
    }

    private static final long DISMISS_DELAY_MS = 1200;

    private final Context service;
    private WindowManager windowManager;
    private Context windowContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private final TextView titleView;
    private final TextView valueView;
    private final TextView settingsView;
    private final TextView systemView;
    private final TextView statusView;
    private final ProgressBar progressBar;
    private final AudioManager audio;
    private int systemVolume = -1;
    private int systemMaximum = -1;
    private String volumeDescription = "";
    private final Runnable refreshSystem = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            if (!updatesHeld && !correctionState.holdsReadback()) readSystemVolume();
            handler.postDelayed(this, 100);
        }
    };
    private final Runnable dismissRunnable = this::dismiss;
    private final Runnable renderRunnable = this::flushRender;

    private boolean attached;
    private boolean attachRequested;
    private boolean renderQueued;
    private boolean updatesHeld;
    private int requestedStream = -1;
    private int requestedVolume;
    private int requestedMaximum;
    private CorrectionState requestedState;
    private int settlingReadback = -1;
    private String lastError;
    private int displayedStream = -1;
    private int displayedVolume = -1;
    private CorrectionState correctionState = CorrectionState.CONFIRMED;
    private long showCount;

    VolumeOverlayController(Context service) {
        this.service = service;
        audio = service.getSystemService(AudioManager.class);

        panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 31, 33, 36));
        background.setCornerRadius(dp(8));
        panel.setBackground(background);
        panel.setElevation(dp(8));

        LinearLayout header = new LinearLayout(service);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        titleView = new TextView(service);
        titleView.setTextColor(Color.rgb(205, 211, 208));
        titleView.setTextSize(14);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        valueView = new TextView(service);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(20);
        valueView.setGravity(Gravity.END);
        valueView.setSingleLine(true);
        valueView.setFontFeatureSettings("tnum");
        header.addView(valueView, new LinearLayout.LayoutParams(
                (int) Math.ceil(valueView.getPaint().measureText(
                        service.getString(R.string.overlay_volume_value, 100))) + dp(4),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        settingsView = new TextView(service);
        settingsView.setTextColor(Color.rgb(205, 211, 208));
        settingsView.setTextSize(13);
        settingsView.setSingleLine(true);
        settingsView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        settingsParams.topMargin = dp(6);
        panel.addView(settingsView, settingsParams);

        // Keep this row measured even when confirmation clears its text.
        statusView = new TextView(service);
        statusView.setTextColor(Color.rgb(205, 211, 208));
        statusView.setTextSize(13);
        statusView.setSingleLine(true);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(24 * service.getResources().getDisplayMetrics().scaledDensity));
        statusParams.topMargin = dp(2);
        progressBar = new ProgressBar(
                service,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progressBar.setProgressTintList(ColorStateList.valueOf(
                Color.rgb(72, 216, 158)
        ));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(
                Color.rgb(78, 82, 81)
        ));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
        );
        progressParams.topMargin = dp(10);
        systemView = new TextView(service);
        systemView.setTextColor(Color.rgb(205, 211, 208));
        systemView.setTextSize(13);
        systemView.setSingleLine(true);
        systemView.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        systemParams.topMargin = dp(6);
        panel.addView(systemView, systemParams);
        panel.addView(progressBar, progressParams);
        panel.addView(statusView, statusParams);
    }

    boolean isShowing() {
        return attached;
    }

    private void render(int stream, int current, int max, CorrectionState state) {
        displayedStream = stream;
        displayedVolume = current;
        correctionState = state;
        int percent = VolumeDisplay.percent(current, max);
        titleView.setText(stream == AudioManager.STREAM_MUSIC
                ? R.string.overlay_media_volume : R.string.overlay_other_volume);
        valueView.setText(service.getString(
                R.string.overlay_volume_value,
                percent
        ));
        int steps = VolumeStepSettings.steps(service);
        int level = VolumeDisplay.settingLevel(current,
                service.getSystemService(AudioManager.class).getStreamMinVolume(stream), max, steps);
        String setting = service.getString(R.string.overlay_step_setting, level, steps);
        settingsView.setText(setting);
        String status = switch (state) {
            case PENDING -> service.getString(R.string.overlay_correction_pending);
            case UNCONFIRMED -> service.getString(R.string.overlay_correction_unconfirmed);
            case SETTLING, CONFIRMED -> "";
        };
        statusView.setText(status);
        progressBar.setMax(100);
        progressBar.setProgress(percent, false);
        String description = service.getString(
                R.string.overlay_accessibility_description,
                titleView.getText(),
                percent,
                level,
                steps
        );
        volumeDescription = description + "，" + setting
                + (status.isEmpty() ? "" : "，" + status);
        if (!state.holdsReadback()) readSystemVolume();
    }

    private void readSystemVolume() {
        try {
            // Independent readback: never use the requested target or configured steps.
            int actual = audio.getStreamVolume(displayedStream);
            int maximum = audio.getStreamMaxVolume(displayedStream);
            systemVolume = actual;
            systemMaximum = maximum;
            String text = service.getString(R.string.overlay_system_volume, actual, maximum);
            if (!TextUtils.equals(systemView.getText(), text)) systemView.setText(text);
            systemView.setContentDescription(systemView.getText());
        } catch (RuntimeException exception) {
            systemVolume = -1;
            systemMaximum = -1;
            String text = service.getString(R.string.overlay_system_volume_unavailable);
            if (!TextUtils.equals(systemView.getText(), text)) systemView.setText(text);
        }
        panel.setContentDescription(volumeDescription + "，" + systemView.getText());
    }

    /** Only a confirmed native button adjustment may attach or extend the panel. */
    void show(int stream, int current, int max, CorrectionState state) {
        updatesHeld = false;
        attachRequested = true;
        queueRender(stream, current, max, state);
        showCount++;
        handler.removeCallbacks(dismissRunnable);
        handler.postDelayed(dismissRunnable, DISMISS_DELAY_MS);
    }

    /** Display a real readback promptly, freezing it until the late-write guard finishes. */
    void showSettlingReadback(int stream, int actual, int max) {
        show(stream, actual, max, CorrectionState.SETTLING);
        settlingReadback = actual;
    }

    private void queueRender(int stream, int current, int max, CorrectionState state) {
        settlingReadback = -1;
        requestedStream = stream;
        requestedVolume = current;
        requestedMaximum = max;
        requestedState = state;
        if (!renderQueued) {
            renderQueued = true;
            handler.post(renderRunnable);
        }
    }

    /** Keep the previous final value while the next key adjustment is unresolved. */
    void holdUpdates() {
        updatesHeld = true;
        handler.removeCallbacks(refreshSystem);
        handler.removeCallbacks(renderRunnable);
        renderQueued = false;
        attachRequested = false;
    }

    /** A consumed/expired key can resume passive updates without opening UI. */
    void releaseUpdates() {
        updatesHeld = false;
        handler.removeCallbacks(refreshSystem);
        if (attached) handler.postDelayed(refreshSystem, 100);
    }

    private void flushRender() {
        renderQueued = false;
        if (!attached && !attachRequested) {
            return;
        }
        Context accessibility = FineVolumeAccessibilityService.connectedService();
        // Application overlays are hidden behind keyguard on the tested firmware.
        // Use the already-bound accessibility window without waking or unlocking.
        Context owner = service.getSystemService(KeyguardManager.class).isKeyguardLocked()
                && accessibility != null ? accessibility
                : Settings.canDrawOverlays(service) ? service : accessibility;
        if (owner == null) {
            attachRequested = false;
            lastError = "Floating-window permission missing; accessibility disconnected";
            return;
        }
        if (attached && owner != windowContext) {
            removePanel();
        }
        render(requestedStream, requestedVolume, requestedMaximum, requestedState);
        if (settlingReadback >= 0) {
            systemVolume = settlingReadback;
            systemMaximum = requestedMaximum;
            systemView.setText(service.getString(R.string.overlay_system_volume,
                    systemVolume, systemMaximum));
            systemView.setContentDescription(systemView.getText());
            panel.setContentDescription(volumeDescription + "，" + systemView.getText());
            settlingReadback = -1;
        }
        attachRequested = false;
        if (!attached) {
            windowContext = owner;
            windowManager = owner.getSystemService(WindowManager.class);
            try {
                windowManager.addView(panel, createLayoutParams());
                attached = true;
                lastError = null;
            } catch (RuntimeException exception) {
                lastError = exception.toString();
                return;
            }
        }
        handler.removeCallbacks(refreshSystem);
        if (attached && !updatesHeld) handler.postDelayed(refreshSystem, 100);
    }

    /** Acknowledgements and passive observations never reopen or extend a panel. */
    void update(int stream, int current, int max, CorrectionState state) {
        if (!updatesHeld && (attached || attachRequested) && requestedStream == stream) {
            queueRender(stream, current, max, state);
        }
    }

    void updateActual(int stream, int current, int max) {
        update(stream, current, max, requestedState);
    }

    void dismiss() {
        handler.removeCallbacks(dismissRunnable);
        handler.removeCallbacks(refreshSystem);
        handler.removeCallbacks(renderRunnable);
        renderQueued = false;
        attachRequested = false;
        updatesHeld = false;
        removePanel();
    }

    private void removePanel() {
        if (!attached) {
            return;
        }
        try {
            windowManager.removeViewImmediate(panel);
        } catch (RuntimeException ignored) {
            // The accessibility connection may already be gone.
        }
        attached = false;
        windowContext = null;
    }

    void accessibilityChanged() {
        if ((attached && windowContext != service)
                || (!Settings.canDrawOverlays(service)
                        && !FineVolumeAccessibilityService.isConnected())) {
            dismiss();
        }
    }

    String diagnostics() {
        return "attached=" + attached + ", permission=" + Settings.canDrawOverlays(service)
                + ", error=" + lastError + ", show count=" + showCount
                + ", displayed stream=" + displayedStream + ", displayed volume=" + displayedVolume
                + ", system readback=" + systemVolume + "/" + systemMaximum
                + ", correction state=" + correctionState
                + ", status text=\"" + statusView.getText() + "\""
                + ", render queued=" + renderQueued + ", updates held=" + updatesHeld;
    }

    private WindowManager.LayoutParams createLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(260),
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowContext == service ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        params.x = dp(20);
        params.windowAnimations = 0;
        params.setTitle("PreciseVolumeSteps");
        return params;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
