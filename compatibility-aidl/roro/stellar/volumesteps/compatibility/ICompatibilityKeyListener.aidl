package roro.stellar.volumesteps.compatibility;

/** Exact physical-key state changes observed by the independently signed helper. */
oneway interface ICompatibilityKeyListener {
    void onKey(int code, int value, long observedAfter, long observedAt);
    void onError(String message);
}
