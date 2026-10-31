package roro.stellar.volumesteps;

import android.os.Process;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Ephemeral Shizuku service for the helper's one fixed permission grant. */
public final class CompatibilityGrantShellService extends ICompatibilityGrantShell.Stub {
    static final int GRANTED = 0;
    static final int FAILED = -1;

    public CompatibilityGrantShellService() {
        if (!ShizukuServerIdentity.supported(Process.myUid())) {
            throw new SecurityException("Root or shell Shizuku required");
        }
    }

    @Override public int grantInputPermission() {
        java.lang.Process command = null;
        try {
            command = new ProcessBuilder(CompatibilityGrantContract.shellCommand())
                    .redirectErrorStream(true)
                    .start();
            if (!command.waitFor(5, TimeUnit.SECONDS)) {
                command.destroyForcibly();
                return FAILED;
            }
            drain(command.getInputStream());
            return command.exitValue() == 0 ? GRANTED : FAILED;
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (command != null) command.destroyForcibly();
            return FAILED;
        }
    }

    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > 16 * 1024) throw new IOException("Unexpected grant output");
        }
    }

    @Override public void destroy() {
        System.exit(0);
    }
}
