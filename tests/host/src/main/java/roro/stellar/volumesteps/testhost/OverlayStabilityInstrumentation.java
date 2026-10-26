package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.ProgressBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Measures real overlay frames during controlled readback/status fixtures. */
public final class OverlayStabilityInstrumentation extends Instrumentation {
    private Object overlay;
    private Method show;
    private Method update;
    private Method dismiss;
    private Method diagnostics;
    private Object pending;
    private Object confirmed;
    private Object unconfirmed;
    private View panel;
    private View value;
    private View settings;
    private View progress;
    private final ArrayList<String> positions = new ArrayList<>();
    private final ArrayList<Integer> readbacks = new ArrayList<>();
    private final JSONArray tests = new JSONArray();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            ClassLoader loader = target.getClassLoader();
            runOnMainSync(() -> target.startForegroundService(new Intent().setClassName(
                    target.getPackageName(), target.getPackageName() + ".VolumeKeeperService")));
            Method controller = loader.loadClass(target.getPackageName() + ".VolumeKeeperService")
                    .getDeclaredMethod("controller");
            controller.setAccessible(true);
            Object owner = null;
            for (int n = 0; n < 50 && owner == null; n++) {
                owner = controller.invoke(null);
                if (owner == null) Thread.sleep(100);
            }
            if (owner == null) throw new IllegalStateException("Volume service not running");
            overlay = field(owner, "overlay");
            Class<?> type = overlay.getClass();
            Class<?> state = loader.loadClass(target.getPackageName()
                    + ".VolumeOverlayController$CorrectionState");
            pending = Enum.valueOf(state.asSubclass(Enum.class), "PENDING");
            confirmed = Enum.valueOf(state.asSubclass(Enum.class), "CONFIRMED");
            unconfirmed = Enum.valueOf(state.asSubclass(Enum.class), "UNCONFIRMED");
            show = method(type, "show", int.class, int.class, int.class, state);
            update = method(type, "update", int.class, int.class, int.class, state);
            dismiss = method(type, "dismiss");
            diagnostics = method(type, "diagnostics");
            panel = (View) field(overlay, "panel");
            value = (View) field(overlay, "valueView");
            settings = (View) field(overlay, "settingsView");
            progress = (View) field(overlay, "progressBar");
            Field displayed = declaredField(overlay, "displayedVolume");
            TextView systemText = (TextView) field(overlay, "systemView");
            invoke(dismiss);
            runOnMainSync(() -> panel.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            if (panel.isAttachedToWindow()) {
                                positions.add(position());
                                try { readbacks.add(displayed.getInt(overlay)); }
                                catch (IllegalAccessException exception) {
                                    throw new RuntimeException(exception);
                                }
                            }
                            return true;
                        }
                    }));
            // A fast acknowledgement arrives in the same display batch.
            runOnMainSync(() -> {
                try {
                    show.invoke(overlay, 3, 80, 150, pending);
                    update.invoke(overlay, 3, 60, 150, confirmed);
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            Thread.sleep(100);
            check("fast confirmation displays latest readback",
                    report().contains("displayed volume=60")
                            && report().contains("correction state=CONFIRMED"));
            check("fast confirmation avoids temporary native-volume frame",
                    !readbacks.isEmpty() && readbacks.get(0) == 60);
            check("native 60 of 150 renders 40 percent", mainValue().equals("40 / 100%"));
            AudioManager audio = target.getSystemService(AudioManager.class);
            AtomicReference<Boolean> nativeMatches = new AtomicReference<>();
            runOnMainSync(() -> nativeMatches.set(
                    systemText.getText().toString().contains(
                            audio.getStreamVolume(3) + " / " + audio.getStreamMaxVolume(3))));
            check("system indicator reads Android rather than synthetic panel targets",
                    nativeMatches.get());
            AtomicReference<Boolean> ordered = new AtomicReference<>();
            runOnMainSync(() -> {
                android.view.ViewGroup group = (android.view.ViewGroup) panel;
                int bars = 0;
                for (int i = 0; i < group.getChildCount(); i++) {
                    if (group.getChildAt(i) instanceof ProgressBar) bars++;
                }
                ordered.set(bars == 1 && group.indexOfChild(settings) < group.indexOfChild(systemText)
                        && group.indexOfChild(systemText) < group.indexOfChild(progress));
            });
            check("three text rows followed by exactly one progress bar", ordered.get());
            String baseline = mainPosition();
            result.putString("initial_bounds", baseline);
            for (int n = 0; n < 20; n++) {
                invoke(show, 3, n % 2 == 0 ? 150 : 1, 150, pending);
                Thread.sleep(65);
                check("pending layout stable " + n, mainPosition().equals(baseline));
                if (n == 0) {
                    AtomicReference<Boolean> fits = new AtomicReference<>();
                    runOnMainSync(() -> {
                        TextView text = (TextView) value;
                        fits.set(text.getLayout().getLineWidth(0) <= text.getWidth()
                                - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight());
                    });
                    check("three-digit volume fits without clipping", fits.get());
                }
                invoke(update, 3, n % 2 == 0 ? 147 : 3, 150, confirmed);
                Thread.sleep(65);
                check("confirmed layout stable " + n, mainPosition().equals(baseline));
            }
            for (int maximum : new int[]{15, 50, 100, 150, 255}) {
                invoke(show, 3, maximum * 3 / 5, maximum, confirmed);
                Thread.sleep(65);
                AtomicReference<Boolean> matches = new AtomicReference<>();
                runOnMainSync(() -> matches.set(
                        ((TextView) value).getText().toString().equals("60 / 100%")
                                && ((ProgressBar) progress).getProgress() == 60
                                && panel.getContentDescription().toString().contains("60 / 100%")));
                check("label, progress and accessibility use percentage for maximum " + maximum,
                        matches.get() && mainPosition().equals(baseline));
            }
            invoke(show, 3, 100, 150, unconfirmed);
            Thread.sleep(80);
            check("unconfirmed layout stable", mainPosition().equals(baseline));
            check("every rendered frame has identical panel and child bounds",
                    positions.size() > 40 && positions.stream().allMatch(baseline::equals));
            invoke(dismiss);
            invoke(update, 3, 50, 150, confirmed);
            Thread.sleep(80);
            check("passive update cannot reopen panel", !report().contains("attached=true"));
            invoke(show, 3, 60, 150, pending);
            invoke(dismiss);
            Thread.sleep(80);
            check("dismiss cancels queued attachment", !report().contains("attached=true"));
            invoke(show, 3, 60, 150, confirmed);
            Thread.sleep(1400);
            check("panel still dismisses after inactivity", !report().contains("attached=true"));
            result.putInt("frames", positions.size());
            result.putString("bounds", baseline);
            result.putString("input", "controlled overlay render fixtures; no system volume writes");
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            if (panel != null) {
                try { result.putString("failed_bounds", mainPosition()); }
                catch (Exception ignored) { }
            }
        } finally {
            if (dismiss != null) {
                try { invoke(dismiss); } catch (Exception ignored) { }
            }
        }
        result.putString("tests", tests.toString());
        finish(result.containsKey("error") ? 1 : 0, result);
    }

    private String position() {
        StringBuilder text = new StringBuilder();
        for (View view : new View[]{panel, value, settings, progress}) {
            int[] xy = new int[2];
            view.getLocationOnScreen(xy);
            text.append(Arrays.toString(xy)).append(':').append(view.getWidth())
                    .append('x').append(view.getHeight()).append(';');
        }
        return text.toString();
    }
    private String mainPosition() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        runOnMainSync(() -> result.set(position()));
        return result.get();
    }
    private String mainValue() {
        AtomicReference<String> result = new AtomicReference<>();
        runOnMainSync(() -> result.set(((TextView) value).getText().toString()));
        return result.get();
    }
    private String report() throws Exception { return (String) invoke(diagnostics); }
    private static Field declaredField(Object owner, String name) throws Exception {
        Field value = owner.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }
    private static Object field(Object owner, String name) throws Exception {
        return declaredField(owner, name).get(owner);
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method result = type.getDeclaredMethod(name, args);
        result.setAccessible(true);
        return result;
    }
    private Object invoke(Method method, Object... args) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        runOnMainSync(() -> {
            try { result.set(method.invoke(overlay, args)); }
            catch (Exception exception) { error.set(exception); }
        });
        if (error.get() != null) throw error.get();
        return result.get();
    }
    private void check(String name, boolean pass) throws Exception {
        tests.put(new JSONObject().put("test", name).put("pass", pass));
        Bundle status = new Bundle();
        status.putString("test", name);
        status.putBoolean("pass", pass);
        sendStatus(0, status);
        if (!pass) throw new IllegalStateException(name);
    }
}
