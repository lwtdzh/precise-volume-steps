package roro.stellar.volumesteps;

import android.os.Process;

public final class SetupShellService extends ISetupShell.Stub {
    public SetupShellService() {
        if (!ShizukuServerIdentity.supported(Process.myUid()))
            throw new SecurityException("Root or shell UID required");
    }
    @Override public String setup(int user) { return PrivilegedSetup.run(user); }
    @Override public void destroy() { System.exit(0); }
}
