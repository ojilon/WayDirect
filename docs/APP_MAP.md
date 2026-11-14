# App map — where to change things

Short guide so Java and XML stay easy to update. Prefer **small edits** in the file named below; avoid cross-package rewrites unless the map says so.

Related: [XML_UI_GUIDE.md](XML_UI_GUIDE.md) (layouts/tokens), [ROUTE_LOGGING.md](ROUTE_LOGGING.md) (logs).

---

## Packages under `app/src/main/java/com/waydirect/`

| Package | Owns | Edit when… |
|---------|------|------------|
| **`ui/`** | Fragments, adapters, activities, list rows | Screen behaviour, list clicks, dialogs |
| **`bridge/`** | Doorway to native (paths, cache, `RouteLogger`, `NativeEngine`) | How Java talks to Odin / log paths |
| **`core/`** | `MainActivity`, theme, blur, `Config` | Nav host, theme cycle, host/port |
| **`storage/`** | Indexer, mutator, search helpers | Create/rename/delete, index roots |
| **`transfer/`** | Queue file, recent store, transfer controller | Multi-send queue on disk |
| **`network/`** | Sockets to PC | Upload/download protocol |
| **`utils/`** | Shared pure helpers (`FormatSizes`, …) | Formatting used by more than one screen |

**Rule of thumb:** UI screens live in `ui/`. They call `bridge` + `storage`/`transfer`/`network`. They do **not** open files with raw `File` IO for app data — use `PathRegistry` / `PathCache` / `AppDirs`.

---

## Screens → files

| Tab / screen | Java | Layout |
|--------------|------|--------|
| Home | `ui/HomeFragment.java` | `res/layout/fragment_home.xml` |
| Files | `ui/FilesFragment.java` | `res/layout/fragment_files.xml` |
| Storage | `ui/StorageFragment.java` | `res/layout/fragment_storage.xml` |
| Transfer | `ui/TransferFragment.java` | `res/layout/fragment_transfer.xml` |
| Cleaner | `ui/CleanerFragment.java` | `res/layout/fragment_cleaner.xml` |
| Internals | `ui/InternalFragment.java` | `res/layout/fragment_internal.xml` |
| List row | `ui/FileAdapter.java` + `ui/FileItem.java` | `res/layout/item_file.xml` |
| Text / image / video / doc | `*Activity.java` + `FileOpenHelper` | `activity_*.xml` |

Shared browse path across tabs: `ui/BrowseSession.java` (static fields only).

---

## OOP habits in this app

1. **`FileItem`** — public final fields (`name`, `path`, `details`, `directory`, `sizeBytes`). No getters. Treat as immutable row data.
2. **Fragments** — inflate ViewBinding in `onCreateView`, null `binding` in `onDestroyView`. Put setup in named private methods (`setupRecycler`, `setupDrawer`, …).
3. **Adapters** — `FileAdapter` owns selection mode; fragments pass click listeners, not business rules into the adapter.
4. **Native results** — async callbacks; always guard `if (binding == null) return;` and prefer `RouteLogger.step(ctx, route, step, detail)` on real user/native actions.
5. **Sizes** — `FormatSizes.of(bytes)` from `utils/`; do not copy local `formatSize` helpers into new screens.

---

## XML tokens (change once)

| File | Change for |
|------|------------|
| `res/values/dimens.xml` | Spacing, radii, `button_min_height` (48dp), list row height |
| `res/values/themes.xml` | Card / bottom-nav styles |
| `res/values/colors.xml` (+ night) | Palette |
| `res/values/strings.xml` | User-visible text (avoid literals in XML) |

Prefer `@dimen/spacing_md` and `@dimen/button_min_height` over raw `16dp` / `40dp` in new layout work.

---

## Logging

- Java: `RouteLogger` → `files/WayDirect/logs/<route>/YYYY-MM-DD.log`
- Routes: `"files"`, `"transfer"`, `"cleaner"`, `"storage"`, …
- Native sample append: `native/core/src/route_log_sample.odin`

---

## What not to do in drive-by edits

- Do not reintroduce getters on `FileItem`.
- Do not put large JSON parsing in adapters — keep it in the Fragment (or a small helper next to it).
- Do not add logic under `app/src/main/cpp/` — native lives in `native/`.
- Prefer one focused commit per concern (one screen comment pass, one dimens tweak, one doc).
