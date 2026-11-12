package com.waydirect.storage;

/** One entry when browsing storage — public final fields, no getters. */
public class StorageItem {
    public final String name;
    public final String path;
    public final boolean isDirectory;
    public final long size;

    public StorageItem(String name, String path, boolean isDirectory, long size) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
        this.size = size;
    }
}
