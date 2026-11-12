package com.waydirect.bridge;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single doorway to native code.
 * Serializes native jobs on one background thread; callbacks always on UI thread.
 * Optionally records steps under RouteLogger route "bridge".
 */
public final class Bridge {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void run(int actionId, String payload, NativeEngine.Callback callback) {
        run(actionId, payload, null, null, callback);
    }

    public static void run(int actionId, String payload, String leasePath, String leaseOwner,
                           NativeEngine.Callback callback) {
        executor.execute(() -> {
            String result;
            String owner = leaseOwner != null ? leaseOwner : "bridge";
            long t0 = System.currentTimeMillis();
            if (leasePath != null && !FileLeases.acquire(leasePath, owner)) {
                result = "{\"status\":\"error\",\"reason\":\"busy\"}";
            } else {
                try {
                    result = NativeEngine.processAction(actionId, payload != null ? payload : "");
                } catch (Throwable t) {
                    result = "{\"status\":\"error\",\"reason\":\"native_exception\",\"message\":\""
                            + sanitize(t.getMessage()) + "\"}";
                } finally {
                    if (leasePath != null) FileLeases.release(leasePath, owner);
                }
            }
            long ms = System.currentTimeMillis() - t0;
            // Lightweight bridge log (no Context here — use logcat only)
            android.util.Log.i("WayDirectBridge",
                    "action=" + actionId + " ms=" + ms + " lease=" + (leasePath != null)
                            + " replyChars=" + (result != null ? result.length() : 0));

            String done = result != null ? result : "{\"status\":\"error\",\"reason\":\"null_result\"}";
            mainHandler.post(() -> callback.onResult(done));
        });
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace('\\', ' ').replace('"', '\'');
    }

    private Bridge() {}
}
