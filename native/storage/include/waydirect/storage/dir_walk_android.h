/* C bridge for directory listing on Android.
 * Uses only opendir/readdir/stat — no statx (blocked by Android 10 seccomp).
 * Called from Odin via foreign import.
 */
#ifndef WAYDIRECT_DIR_WALK_ANDROID_H
#define WAYDIRECT_DIR_WALK_ANDROID_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* One entry returned to Odin. name is UTF-8, not necessarily NUL-padded beyond name_len. */
typedef struct WaydirectDirEntry {
    const char *name;
    size_t      name_len;
    int         is_dir;
    int64_t     size;
} WaydirectDirEntry;

/* Opaque directory handle */
typedef struct WaydirectDir WaydirectDir;

/* Open directory at path (NUL-terminated). Returns NULL on failure. */
WaydirectDir *dir_open(const char *path);

/* Write next entry into *out. Returns 1 if ok, 0 if end or error. */
int dir_next(WaydirectDir *dir, WaydirectDirEntry *out);

/* Close and free. Safe to call with NULL. */
void dir_close(WaydirectDir *dir);

#ifdef __cplusplus
}
#endif

#endif /* WAYDIRECT_DIR_WALK_ANDROID_H */
