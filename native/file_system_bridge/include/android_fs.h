#ifndef WAYDIRECT_ANDROID_FS_H
#define WAYDIRECT_ANDROID_FS_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- single directory entry (no statx) ---- */
typedef struct WaydirectFsEntry {
    const char *name;       /* valid until next next() or close */
    size_t      name_len;
    int         is_dir;     /* 1 dir, 0 file, -1 other */
    int64_t     size;       /* files only */
    int64_t     mtime;      /* unix seconds */
} WaydirectFsEntry;

typedef struct WaydirectFsDir WaydirectFsDir;

/* Open directory for reading */
WaydirectFsDir *fs_opendir(const char *path);

/* Read next entry; returns 1 on success, 0 on end/error.
 * Output 'out' points to internal buffer valid until next readdir/close. */
int fs_readdir(WaydirectFsDir *d, WaydirectFsEntry *out);

/* Close directory handle */
void fs_closedir(WaydirectFsDir *d);

/* ---- recursive walk callback (heap-friendly, depth-limited) ---- */
typedef void (*WaydirectFsFileCb)(const char *path, const char *name,
                              int64_t size, int64_t mtime, void *ud);

/* Walk files under root, calling cb for each regular file.
 * Skips excluded names (thumbnails, Android/data, etc.).
 * Returns number of files visited (or 0 on error). */
int fs_walk_files(const char *root, WaydirectFsFileCb cb, void *ud);

/* ---- path ops (bionic-safe) ---- */
int fs_stat(const char *path, int64_t *size_out, int64_t *mtime_out, int *is_dir_out);
int fs_mkdir_p(const char *path);                    /* 0 ok */
int fs_write_file(const char *path, const void *data, size_t n);

/* Returns malloc'd buffer + sets *n_out; caller free(); NULL on error.
 * Reads up to max_bytes (0 = no limit). */
char *fs_read_file(const char *path, size_t max_bytes, size_t *n_out);

int fs_rename(const char *from, const char *to);
int fs_remove(const char *path);

#ifdef __cplusplus
}
#endif

#endif /* WAYDIRECT_ANDROID_FS_H */
