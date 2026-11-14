package com.waydirect.bridge;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-route daily log files under private storage.
 *
 * Layout:
 *   &lt;filesDir&gt;/WayDirect/logs/&lt;route&gt;/YYYY-MM-DD.log
 *
 * Routes are short names: "storage", "files", "bridge", "transfer", "cleaner", …
 * One file per route per calendar day (device local time).
 *
 * When calling native, pass {@link #logPathFor(Context, String)} in the payload
 * (or as a dedicated field) so Odin can append the same chain of steps.
 * See native/core/src/route_log_sample.odin for the Odin side sample.
 */
public final class RouteLogger {

    private static final String TAG = "WayDirectLog";
    private static final ExecutorService writer = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /** Absolute path of today's log file for this route (creates parent dirs). */
    public static String logPathFor(Context context, String route) {
        String safe = sanitize(route);
        File dir = new File(PathRegistry.logsDir(context), safe);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, DAY.format(new Date()) + ".log").getAbsolutePath();
    }

    /** Append one line: timestamp + level + message. Never throws. */
    public static void log(Context context, String route, String level, String message) {
        if (context == null || route == null) return;
        final String path = logPathFor(context, route);
        final String line = TS.format(new Date()) + " [" + level + "] " +
                (message != null ? message : "") + "\n";
        writer.execute(() -> {
            try (FileWriter fw = new FileWriter(path, true)) {
                fw.write(line);
            } catch (IOException e) {
                Log.w(TAG, "write failed route=" + route + ": " + e.getMessage());
            }
        });
        // Also mirror to logcat for adb while developing
        if ("E".equals(level)) Log.e(TAG, route + ": " + message);
        else if ("W".equals(level)) Log.w(TAG, route + ": " + message);
        else Log.i(TAG, route + ": " + message);
    }

    public static void info(Context context, String route, String message) {
        log(context, route, "I", message);
    }

    public static void warn(Context context, String route, String message) {
        log(context, route, "W", message);
    }

    public static void error(Context context, String route, String message) {
        log(context, route, "E", message);
    }

    /** Step marker — use at each stage of a Java→native→Java path. */
    public static void step(Context context, String route, String stepName, String detail) {
        info(context, route, stepName + (detail != null && !detail.isEmpty() ? " | " + detail : ""));
    }

    private static String sanitize(String route) {
        if (route == null || route.isEmpty()) return "general";
        StringBuilder sb = new StringBuilder(route.length());
        for (int i = 0; i < route.length(); i++) {
            char c = route.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') sb.append(c);
            else sb.append('_');
        }
        return sb.toString();
    }

    private RouteLogger() {}
}
