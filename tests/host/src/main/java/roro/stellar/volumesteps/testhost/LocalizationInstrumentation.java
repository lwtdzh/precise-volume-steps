package roro.stellar.volumesteps.testhost;

import android.app.Instrumentation;
import android.app.LocaleConfig;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real Android resource matching and panel measurement; no settings or volume writes. */
public final class LocalizationInstrumentation extends Instrumentation {
    private final JSONArray checks = new JSONArray();
    private static final Pattern FORMAT = Pattern.compile("%(\\d+)\\$[.\\d]*([sdf])");

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context app = getTargetContext();
            if (Build.VERSION.SDK_INT >= 33) {
                LocaleConfig config = new LocaleConfig(app);
                check("generated locale config readable", config.getStatus() == LocaleConfig.STATUS_SUCCESS);
                LocaleList locales = config.getSupportedLocales();
                Set<String> tags = new HashSet<>();
                for (int i = 0; i < locales.size(); i++) tags.add(locales.get(i).toLanguageTag());
                for (String tag : new String[]{"en", "ja", "zh-Hans", "zh-Hant",
                        "zh-Hant-HK", "zh-Hant-MO", "zh-Hant-TW"})
                    check("system app-language list includes " + tag, tags.contains(tag));
            }
            for (String tag : new String[]{"en-US", "en-GB", "ja-JP", "zh-CN", "zh-SG",
                    "zh-HK", "zh-MO", "zh-TW", "zh-Hant-HK", "zh-Hant-MO",
                    "zh-Hant-TW", "zh-Hant", "qaa"}) {
                Configuration configuration = new Configuration(app.getResources().getConfiguration());
                configuration.setLocales(LocaleList.forLanguageTags(tag));
                Context localized = app.createConfigurationContext(configuration);
                String title = tag.startsWith("ja") ? "精密音量"
                        : tag.equals("zh-CN") || tag.equals("zh-SG") ? "精准音量"
                        : tag.startsWith("zh") ? "精準音量" : "Precise Volume";
                check(tag + " title and script", text(localized, "app_name").equals(title));
                String step = tag.contains("HK") || tag.contains("MO") ? "50 檔"
                        : tag.equals("zh-CN") || tag.equals("zh-SG") ? "50 档"
                        : tag.startsWith("zh") ? "50 段"
                        : tag.startsWith("ja") ? "50 段階" : "50 steps";
                check(tag + " regional wording", localized.getString(id(localized,
                        "home_step_value"), 50).equals(step));
                int count = 0;
                for (Field field : Class.forName("roro.stellar.volumesteps.R$string").getFields()) {
                    int resource = field.getInt(null);
                    String name = localized.getResources().getResourceEntryName(resource);
                    String raw = localized.getString(resource);
                    Matcher matcher = FORMAT.matcher(raw);
                    Object[] args = {150, 150, 150, 150};
                    while (matcher.find()) {
                        int index = Integer.parseInt(matcher.group(1)) - 1;
                        if (matcher.group(2).equals("f")) args[index] = 2.0;
                    }
                    String formatted = localized.getString(resource, args);
                    if (formatted.isEmpty()) throw new AssertionError(tag + " empty: " + name);
                    count++;
                }
                check(tag + " all strings format without errors (" + count + ")", count >= 200);
                checkReport(localized, tag);
                runOnMainSync(() -> checkPanel(localized, tag));
            }
            result.putString("tests", checks.toString());
            finish(0, result);
        } catch (Throwable exception) {
            result.putString("tests", checks.toString());
            result.putString("error", exception.toString());
            finish(1, result);
        }
    }

    private void checkReport(Context context, String tag) throws Exception {
        Class<?> reportClass = Class.forName("roro.stellar.volumesteps.SetupReport");
        Constructor<?> constructor = reportClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object report = constructor.newInstance();
        Method add = reportClass.getDeclaredMethod("add", int.class, int.class);
        Method encode = reportClass.getDeclaredMethod("encode");
        Method format = reportClass.getDeclaredMethod("format", Context.class, String.class);
        add.setAccessible(true);
        encode.setAccessible(true);
        format.setAccessible(true);
        add.invoke(report, id(context, "setup_notification"), id(context, "setup_applied"));
        String actual = (String) format.invoke(null, context, encode.invoke(report));
        String expected = context.getString(id(context, "setup_report_line"),
                text(context, "setup_notification"), text(context, "setup_applied"));
        check(tag + " privileged report uses app locale", actual.equals(expected));
        check(tag + " malformed report has localized recovery",
                format.invoke(null, context, "invalid").equals(text(context, "setup_failed")));
    }

    private void checkPanel(Context context, String tag) {
        try {
            Class<?> overlayClass = Class.forName("roro.stellar.volumesteps.VolumeOverlayController");
            Class<?> stateClass = Class.forName(
                    "roro.stellar.volumesteps.VolumeOverlayController$CorrectionState");
            Constructor<?> constructor = overlayClass.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            Object overlay = constructor.newInstance(context);
            Object confirmed = stateClass.getEnumConstants()[2];
            Method render = overlayClass.getDeclaredMethod(
                    "render", int.class, int.class, int.class, stateClass);
            render.setAccessible(true);
            AudioManager audio = context.getSystemService(AudioManager.class);
            render.invoke(overlay, AudioManager.STREAM_MUSIC,
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), confirmed);
            Field panelField = overlayClass.getDeclaredField("panel");
            panelField.setAccessible(true);
            LinearLayout panel = (LinearLayout) panelField.get(overlay);
            float density = context.getResources().getDisplayMetrics().density;
            panel.measure(View.MeasureSpec.makeMeasureSpec(Math.round(260 * density),
                    View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0,
                    View.MeasureSpec.UNSPECIFIED));
            panel.layout(0, 0, panel.getMeasuredWidth(), panel.getMeasuredHeight());
            for (String fieldName : new String[]{"titleView", "valueView", "settingsView", "systemView"}) {
                Field field = overlayClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                TextView view = (TextView) field.get(overlay);
                check(tag + " panel " + fieldName + " fits",
                        view.getPaint().measureText(view.getText().toString())
                                <= view.getWidth() - view.getCompoundPaddingLeft()
                                - view.getCompoundPaddingRight() + 1);
            }
            check(tag + " native readback label", panel.getContentDescription().toString()
                    .contains(text(context, "overlay_system_volume").split(" · ")[0]));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int id(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        if (id == 0) throw new AssertionError("Missing resource " + name);
        return id;
    }

    private String text(Context context, String name) {
        return context.getString(id(context, name));
    }

    private void check(String name, boolean passed) throws org.json.JSONException {
        checks.put(new JSONObject().put("test", name).put("pass", passed));
        if (!passed) throw new AssertionError(name);
    }
}
