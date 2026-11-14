package com.waydirect.ui;

import android.content.Context; // Activity / Fragment context — needed to start the viewer Intent

/**
 * Single entry point: "user tapped a file" → correct viewer Activity.
 *
 * <h3>Call route</h3>
 * <pre>
 * FilesFragment / CleanerFragment / …
 *   → FileOpenHelper.open(context, path)
 *     → ImageActivity | VideoActivity | TextViewerActivity | DocumentActivity
 * </pre>
 *
 * <p>Runtime conversion: path string → lowercase extension → {@code switch} →
 * that Activity's {@code open(context, path)} (usually puts path in an Intent extra).
 *
 * <p>Text-like types use {@link TextViewerActivity} (native-capped read).
 * Unknown types fall through to {@link DocumentActivity}.
 *
 * <p>Icons in the list are separate ({@link FileAdapter}); this class only starts viewers.
 */
public final class FileOpenHelper {

    private FileOpenHelper() {}

    /**
 * @param context usually {@code requireContext()} from a Fragment
 * @param path absolute filesystem path of the file to open
 */
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

            // Plain / structured text — dedicated viewer (not the system editor)
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
