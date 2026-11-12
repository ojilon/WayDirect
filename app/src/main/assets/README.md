# App assets (bundled with the APK)

Put **read-only** files you ship with the app here. They are packed into the APK and
read at runtime via `AssetManager` (not the same as user storage).

Suggested layout:

```text
assets/
  README.md                 ← this file
  icons/                    ← PNG/SVG you design for in-app previews / empty states
    empty_folder.png
    file_types/
  samples/                  ← optional demo text/json for Internals or docs
  templates/                ← HTML/CSS shells if you add a WebView later
```

## How Java reads an asset

```java
try (InputStream in = context.getAssets().open("icons/empty_folder.png")) {
    // decode bitmap, etc.
}
```

## What does *not* go here

- User files, cache, logs → private `files/WayDirect/` (see `AppDirs` / `PathRegistry`)
- Vector icons used in menus/toolbars → prefer `res/drawable/` (Android resources)
- Launcher icon → `res/drawable` / mipmap

## Preview icons

For the Files list and text viewer empty states, drop PNGs under `assets/icons/`
and load them when you wire empty-state UI. Keep sizes modest (e.g. 128–256 px).
