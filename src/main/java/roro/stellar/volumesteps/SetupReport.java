package roro.stellar.volumesteps;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;

/** Same-APK privileged processes return resource IDs; the app renders in its current locale. */
final class SetupReport {
    private final JSONArray rows = new JSONArray();

    void add(int label, int outcome) {
        rows.put(new JSONArray().put(label).put(outcome));
    }

    String encode() {
        return rows.toString();
    }

    static String format(Context context, String encoded) {
        try {
            JSONArray rows = new JSONArray(encoded);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.getJSONArray(i);
                text.append(context.getString(R.string.setup_report_line,
                        context.getString(row.getInt(0)), context.getString(row.getInt(1))));
            }
            return text.toString();
        } catch (JSONException | android.content.res.Resources.NotFoundException exception) {
            return context.getString(R.string.setup_failed);
        }
    }
}
