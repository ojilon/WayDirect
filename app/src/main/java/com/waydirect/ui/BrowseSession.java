package com.waydirect.ui;

/**
 * In-process memory of the last folder each browser left open.
 *
 * <p>Survives configuration changes and tab switches in the same process.
 * Cleared when the process dies — not written to disk, not parcelled.
 * See docs/APP_MAP.md (shared browse path).
 *
 * <ul>
 *   <li>{@link #transferBrowsePath} — Transfer tab "browse for save folder"</li>
 *   <li>{@link #filesCurrentPath} — Files tab current directory</li>
 * </ul>
 */
public final class BrowseSession {

    /** Last folder open in the Transfer browse tab ({@code null} = not visited yet). */
    public static String transferBrowsePath = null;

    /** Last folder open in the Files tab ({@code null} = not visited yet). */
    public static String filesCurrentPath = null;

    private BrowseSession() {}
}
