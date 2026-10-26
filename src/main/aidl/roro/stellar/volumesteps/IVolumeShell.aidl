package roro.stellar.volumesteps;

/** One bounded volume correction; no command execution or general system access. */
interface IVolumeShell {
    int adjust(int stream, int target, int expectedBefore, long expiresAt) = 0;
    void destroy() = 16777114;
}
