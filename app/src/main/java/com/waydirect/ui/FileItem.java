package com.waydirect.ui;

/**
 * One row in a file list (Files, Transfer search/browse, Cleaner, session queue).
 *
 * <p><b>OOP convention:</b> public final fields only — no getters.
 * Build with the constructor; read {@code item.name} / {@code item.path} in adapters
 * and click handlers. Do not add setters or mutate after construction.
 *
 * <p>Used with {@link FileAdapter} and {@code res/layout/item_file.xml}.
 * See docs/APP_MAP.md.
 */
public class FileItem {

    /** Display name (file or folder name only). */
    public final String name;
    /** Absolute path — open, browse, delete, upload. */
    public final String path;
    /** Secondary line, e.g. {@code "Folder"}, {@code "2.4 MB"}, status text. */
    public final String details;
    public final boolean directory;
    /** Byte size for files; {@code 0} for folders / unknown. */
    public final long sizeBytes;

    public FileItem(String name, String path, String details, boolean directory, long sizeBytes) {
        this.name = name;
        this.path = path;
        this.details = details;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
    }
}
