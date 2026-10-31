package roro.stellar.volumesteps;

/** Official Shizuku servers run as root or Android's shell user. */
final class ShizukuServerIdentity {
    private ShizukuServerIdentity() { }

    static boolean supported(int uid) {
        return uid == 0 || uid == 2000;
    }
}
