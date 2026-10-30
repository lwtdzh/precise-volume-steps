package roro.stellar.volumesteps;

import android.os.Process;

public final class SetupShellService extends ISetupShell.Stub {
    public SetupShellService() {
        if (Process.myUid() != 2000) throw new SecurityException("Shell UID required");
    }
    @Override public String setup(int user) { return PrivilegedSetup.run(user); }
    @Override public void destroy() { System.exit(0); }
}
