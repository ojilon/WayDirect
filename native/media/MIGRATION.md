# media â€” migration & improvements

**CMake target:** `WayDirect_media`  
**Status:** placeholder only â€” no legacy sources yet.

## When to add code

- Video/audio metadata extraction
- Thumbnail generation paths under `app_paths().cache`
- Not required for basic file listing

## Rules

- Depend on `WayDirect_core` (+ `WayDirect_storage` if scanning files)
- Prefer system players on Java for playback
- Third-party codecs go in `native/third_party/` with clear licenses

