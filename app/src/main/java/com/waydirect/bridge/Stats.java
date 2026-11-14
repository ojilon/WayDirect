package com.waydirect.bridge;

import android.content.Context;

/**
 * One doorway for storage numbers (Home + Storage tabs).
 *
 * Flow:
 *   1. Java builds payload (cache path | root | maxAge | deviceBytes)
 *   2. Bridge runs ACTION_GET_CACHED_STATS on the single native executor
 *   3. Native returns {status, path} envelope or error JSON
 *   4. Java reads the snapshot file and hands JSON to the UI
 *
 * Errors are never swallowed as empty — callers get real JSON with "error" or "reason".
 */
public final class Stats {

    private static final String ROUTE = "storage";
    private static final int MAX_AGE_SECONDS = 300; // 5 min

    public interface SnapshotCallback {
        void onSnapshot(String json);
    }

    public static void requestSnapshot(Context context, boolean force, SnapshotCallback callback) {
        if (context == null) {
            callback.onSnapshot("{\"error\":\"unavailable\",\"reason\":\"null_context\"}");
            return;
        }

        String cache = NativeCache.statsSnapshotPath(context);
        String root = AppDirs.externalRoot().getPath();
        long bytes = StorageCapacity.queryDeviceBytes(context);
        String logPath = RouteLogger.logPathFor(context, ROUTE);

        RouteLogger.step(context, ROUTE, "requestSnapshot", "force=" + force + " cache=" + cache);

        // payload: cache|root|maxAge|deviceBytes|logPath  (logPath last — native may ignore if not yet wired)
        String payload = cache + "|" + root + "|" + (force ? 0 : MAX_AGE_SECONDS) + "|" + bytes + "|" + logPath;

        Bridge.run(NativeEngine.ACTION_GET_CACHED_STATS, payload, cache, "stats", rawJson -> {
            RouteLogger.step(context, ROUTE, "nativeReply", truncate(rawJson, 200));

            String content = PathCache.readFile(PathCache.envelopePath(rawJson));
            if (content == null) {
                content = PathCache.readFile(cache);
                if (content != null) {
                    RouteLogger.warn(context, ROUTE, "using_stale_cache after null envelope");
                }
            }
            if (content == null) {
                // Surface the native reply itself if it looks like an error JSON
                if (rawJson != null && (rawJson.contains("\"error\"") || rawJson.contains("\"reason\""))) {
                    content = rawJson;
                    RouteLogger.error(context, ROUTE, "no_snapshot_file reply=" + truncate(rawJson, 300));
                } else {
                    content = "{\"error\":\"unavailable\",\"reason\":\"no_cache_and_no_native_file\"}";
                    RouteLogger.error(context, ROUTE, "unavailable raw=" + truncate(rawJson, 300));
                }
            }
            callback.onSnapshot(content);
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private Stats() {}
}
