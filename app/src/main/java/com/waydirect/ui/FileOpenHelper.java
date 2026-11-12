package com.waydirect.ui;

import android.content.Context;

/**
 * Chooses the right viewer for a file path.
 *
 * Text-like → TextViewerActivity (native-capped read).
 * Images / video → dedicated activities.
 * Everything else → DocumentActivity foundation.
 */
public final class FileOpenHelper {

    private FileOpenHelper() {}

    public static void open(Context context, String path) {
        if (path == null || path.isEmpty()) return;

        String lower = path.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : "";

        switch (ext) {
            case "jpg":
            case "jpeg":
            case "png":
            case "webp":
            case "gif":
            case "bmp":
                ImageActivity.open(context, path);
                break;

            case "mp4":
            case "mkv":
            case "webm":
            case "3gp":
            case "avi":
                VideoActivity.open(context, path);
                break;

            // Plain / structured text — dedicated viewer
            case "txt":
            case "text":
            case "log":
            case "md":
            case "markdown":
            case "json":
            case "html":
            case "htm":
            case "xml":
            case "csv":
            case "tsv":
            case "yml":
            case "yaml":
            case "toml":
            case "ini":
            case "conf":
            case "odin":
            case "java":
            case "c":
            case "h":
            case "cpp":
            case "hpp":
            case "kt":
            case "py":
            case "js":
            case "ts":
            case "css":
            case "sh":
            case "bat":
            case "gradle":
            case "properties":
                TextViewerActivity.open(context, path);
                break;

            default:
                DocumentActivity.open(context, path);
                break;
        }
    }
}
