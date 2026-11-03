import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** Disposable-emulator client. Run as the manager UID using its official API. */
public final class ShizukuFlags {
    public static void main(String[] args) throws Exception {
        java.util.concurrent.CountDownLatch received = new java.util.concurrent.CountDownLatch(1);
        IBinder[] found = {null};
        Bundle data = new Bundle();
        data.putBinder("binder", new Binder() {
            @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) {
                found[0] = request.readStrongBinder();
                received.countDown();
                return true;
            }
        });
        android.content.Intent intent = new android.content.Intent(
                "rikka.shizuku.intent.action.REQUEST_BINDER")
                .setPackage("moe.shizuku.privileged.api").putExtra("data", data);
        java.lang.reflect.Method getService = android.app.ActivityManager.class
                .getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object activity = getService.invoke(null);
        java.lang.reflect.Method broadcast = java.util.Arrays.stream(activity.getClass().getMethods())
                .filter(method -> method.getName().equals("broadcastIntent")
                        && method.getParameterCount() == 13).findFirst().orElseThrow();
        broadcast.invoke(activity, null, intent, null, null, 0, null, null, null,
                -1, null, true, false, 0);
        if (!received.await(5, java.util.concurrent.TimeUnit.SECONDS) || found[0] == null)
            throw new IllegalStateException("Shizuku server missing");
        IBinder binder = found[0];
        int uid = Integer.parseInt(args[0]);
        if (args.length > 1) {
            transact(binder, 107, uid, 6, Integer.parseInt(args[1])).recycle();
        }
        Parcel flags = transact(binder, 106, uid, 6);
        System.out.println("flags=" + flags.readInt());
        flags.recycle();
        System.exit(0);
    }

    private static Parcel transact(IBinder binder, int code, int... values) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService");
            for (int value : values) data.writeInt(value);
            if (!binder.transact(code, data, reply, 0)) throw new IllegalStateException("Unknown API");
            reply.readException();
            return reply;
        } finally {
            data.recycle();
        }
    }
}
