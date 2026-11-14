package com.waydirect.utils;

/**
 * Shared human-readable size formatting — one place for all UI screens.
 *
 * <p>Use {@link #of(long)} from Fragments/adapters instead of a private
 * {@code formatSize} copy. Keeps KB/MB/GB wording consistent.
 */
public final class FormatSizes {
    private FormatSizes() {}

    public static String of(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
