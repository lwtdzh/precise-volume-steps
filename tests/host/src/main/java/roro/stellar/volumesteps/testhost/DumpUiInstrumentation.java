package roro.stellar.volumesteps.testhost;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import org.xmlpull.v1.XmlSerializer;

/** Observes settings/UI without disconnecting the service being tested. */
public final class DumpUiInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(
                    UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            try {
                automation.waitForIdle(100, 2000);
            } catch (java.util.concurrent.TimeoutException ignored) { }
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root == null) throw new IllegalStateException("No active window");
            File file = new File(getTargetContext().getExternalFilesDir(null), "ui.xml");
            try (FileOutputStream output = new FileOutputStream(file)) {
                XmlSerializer xml = Xml.newSerializer();
                xml.setOutput(output, "UTF-8");
                xml.startDocument("UTF-8", true);
                xml.startTag(null, "hierarchy");
                dump(xml, root);
                xml.endTag(null, "hierarchy");
                xml.endDocument();
            }
            result.putString("path", file.getAbsolutePath());
            finish(0, result);
        } catch (Exception exception) {
            result.putString("error", exception.toString());
            finish(1, result);
        }
    }

    private void dump(XmlSerializer xml, AccessibilityNodeInfo node) throws Exception {
        xml.startTag(null, "node");
        attribute(xml, "text", node.getText());
        attribute(xml, "resource-id", node.getViewIdResourceName());
        attribute(xml, "class", node.getClassName());
        attribute(xml, "package", node.getPackageName());
        attribute(xml, "content-desc", node.getContentDescription());
        attribute(xml, "checked", String.valueOf(node.isChecked()));
        attribute(xml, "enabled", String.valueOf(node.isEnabled()));
        attribute(xml, "clickable", String.valueOf(node.isClickable()));
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        attribute(xml, "bounds", bounds.toShortString());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) dump(xml, child);
        }
        xml.endTag(null, "node");
    }

    private void attribute(XmlSerializer xml, String name, CharSequence value) throws Exception {
        xml.attribute(null, name, value == null ? "" : value.toString());
    }
}
