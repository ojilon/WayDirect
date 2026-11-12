package com.waydirect.ui;

/**
 * One row in the Files list / search results.
 * Public final fields — no getters needed; treat as read-only data.
 */
public class FileItem {

    public final String name;
    public final String path;       // full path (open / browse)
    public final String details;    // e.g. "Folder" or "2.4 MB"
    public final boolean directory;
    public final long sizeBytes;    // 0 for folders

    public FileItem(String name, String path, String details, boolean directory, long sizeBytes) {
        this.name = name;
        this.path = path;
        this.details = details;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
    }
}
