# Storage, Transfer & shared file operations

Guide for the Storage tab, Transfer tab, and the shared `FileMutator` used across the app.

---

## Storage screen

### Purpose
Show clear storage statistics. Cleanup moved to the **Cleaner** tab.

### Files
| File | Role |
|------|------|
| `fragment_storage.xml` | Summary, progress, stacked category bars, refresh |
| `StorageFragment.java` | `bridge/Stats.requestSnapshot`, fills UI, sets bar weights |

### Data flow
```
StorageFragment / HomeFragment
    â†’ bridge/Stats.requestSnapshot (one shared snapshot file, leased)
    â†’ NativeEngine action 13 â†’ C++ computes (JSON lib) or serves the file
    â†’ Java reads the file, fills UI (same parsers as before)
    â†’ SQLite history row recorded best-effort (cache/stats/stats.db)
```

Device capacity comes from `StorageCapacity.queryDeviceBytes`
(`StorageStatsManager`, `StatFs` fallback); native falls back to its retail
floor when Java passes 0.

### Category â€œgraphâ€
No chart library. A horizontal `LinearLayout` with five coloured `View`s.  
Java sets each childâ€™s `layout_weight` proportional to bytes.

### Next for Storage
- Per-folder drill-down from the new `folders` array in snapshots
- Storage trend graph from `cache/stats/stats.db` (see `CLEANER_IDEAS.md`)

---

## Transfer screen

### Purpose
Connect the phone to **WayDirectPC** over hotspot, test link, download/upload files.

### Files
| File | Role |
|------|------|
| `fragment_transfer.xml` | DrawerLayout + tab strip + ViewFlipper (guide \| transfer \| search \| browse \| network) |
| `TransferFragment.java` | UI, index search/confirm, save path, queue, NetworkManager |
| `storage/FileIndexer.java` | Shared-storage roots + default Downloads save path (walk retired) |
| `transfer/TransferQueue.java` | `modules/transfer/queue.json` with per-file status |
| `network/NetworkManager.java` | Socket protocol `/ask` and `/upload` (absolute path OK) |
| `network/NetworkCallback.java` | Progress + completion (background thread) |
| `transfer/TransferController.java` | C++ listener wrapper (Action 6) |
| `core/Config.java` | `HOST` + `PORT` |

### Networking rule
- **Sockets / hotspot protocol â†’ Java** (`NetworkManager`)
- **Heavy non-network work â†’ C++**

### How to transfer
1. Set `Config.HOST` / `Config.PORT` to your PC hotspot address.
2. Open **Transfer** tab â†’ **Network** tab â†’ **Test connection**.
3. Enter a **file name** (keyword, not necessarily full path).
4. **Download** runs `/ask <name>` â†’ file lands in **save folder** (default `/storage/emulated/0/Download`). Change via right sidebar â†’ **Select save folder** (browse tab) or **Refresh file index**.
5. **Upload** searches the native index â†’ **Confirm** (single hit) or **pick** (multiple) â†’ `/upload` with absolute path to PC. Or tick files in **Browse**/**Search** â†’ **Send** for a one-by-one queue.
6. Upload basenames are space-sanitized for the protocol (`file name` â†’ `file_name`); local files are never renamed.

Activity log shows handshake and result. Session counters update on success; bandwidth is estimated from elapsed time. The session section tracks queue rows with live status; Cancel stops between files.

### Right sidebar (transfer-specific)
- **Select save folder** â€” switches ViewFlipper to folder browser (similar spirit to Files drawer).
- **Refresh file index** â€” rebuilds the native index (`BUILD_INDEX`), shared by upload search.
- Appearance: theme / blur controls shared by all windows.

### Protocol (phone â†” WayDirectPC)
```
Download:
  phone â†’ /ask <filename>\n            (always newline-terminated)
  pc    â†’ FOUND <size>\n + <size> raw bytes
          (header and body can arrive in one TCP burst â€” frame the
          header on \n, then read exactly <size> bytes)
  pc    â†’ MATCHES <n>\n + up to 50 name lines  (vague query â€”
          show names, /ask one by exact name)
  pc    â†’ ERROR <code>\n

Upload:
  phone â†’ /upload <size> <basename>\n   (basename space-sanitized)
  pc    â†’ READY                         (bare token, NO trailing
          newline â€” never line-read here, or both sides deadlock)
  phone â†’ <raw bytes>
  pc    â†’ DONE\n  (upload confirmed) | ERROR <code>\n
```

Client timeouts: 10 s connect, 30 s read â†’ "Timed out waiting for PC
(check hotspot & retry)." (`NetworkManager.CONNECT_TIMEOUT_MS` /
`READ_TIMEOUT_MS`).

### Protocol fix notes (`NetworkManager`)
- Commands were sent without `\n`, costing the server's ~500 ms fallback delay each time.
- The `FOUND` header used a single `read(1024)`, so coalesced file bytes were parsed as header text and lost (plus `parseLong` crashes). Now framed byte-at-a-time off the raw stream with exact-size body reads.
- No socket timeouts meant an infinite hang on a silent PC; `READY`/`DONE` and `MATCHES` handling as above.
- Covered by `NetworkManagerLoopbackTest` (coalesced `FOUND`, `MATCHES`, bare-`READY` + `DONE`, silent-server timeout).

### Next for Transfer
- Folder transfer (list of files)
- Queue append/dedupe across sends (currently replaced per send)
- Per-file retry on failure

---

## Shared file operations (`FileMutator`)

**Single place** for create / rename / delete.

| Method | Use |
|--------|-----|
| `createFile` / `createDirectory` | New items |
| `rename` | Rename in place |
| `delete` | File or folder (recursive) |

Used from Files long-press and path long-press; reuse from Storage cleanup later.

## Where files live: private internals vs shared outputs

WayDirect is itself a file explorer, so its working files must not leak into
shared storage (see `storage/AppDirs.java` â€” single policy for the app):

- **Private** (`getFilesDir()/WayDirect`, invisible to explorers/other apps):
  native index (`cache/index/files.json`), stats snapshots, logs, future DB.
  The index is a full inventory of the user's storage â€” keeping it internal
  is a safety property, not just tidiness. Native receives this root once via
  action 14 (`INIT_APP_PATHS`).
- **Shared** (visible to other phones/apps): only files the user explicitly
  receives or creates â€” transfer downloads default to public `Download/`,
  created documents land wherever the user is browsing. Resolved via
  `Environment`, never hardcoded (`/storage/emulated/0/...` breaks on
  multi-user / adoptable storage).

---

## Action IDs (native)

| ID | Name | Used by |
|----|------|---------|
| 3  | LIST_FILES | Files |
| 6  | START_LISTENER | Transfer |
| 7  | STORAGE_STATS | retired (merged into 13) |
| 8  | SEARCH_FILES | Files (file-out) |
| 9  | FIND_LARGE | Cleaner (file-out) |
| 10 | FIND_DUPLICATES | Duplicates |
| 11 | PLAN_ORGANIZE | Organize (plan file out) |
| 12 | APPLY_ORGANIZE | Organize (plan file in, report out) |
| 13 | GET_CACHED_STATS | Storage + Home via bridge/Stats (file-backed, force on 0) |
| 14 | INIT_APP_PATHS | MainActivity (once at startup) |
| 15 | BUILD_INDEX | planned UI (Transfer refresh / Files) |
| 16 | INVALIDATE_CACHE | delete / organize flows via `NativeCache` |
| 17 | INDEX_META | planned UI |
| 18 | SEARCH_INDEX | planned UI (`query\|max_results`) |
| 19 | READ_TEXT_FILE | Document viewer (`path\|max_bytes`, read-only) |

---

## Config

```java
// core/Config.java
public static final String HOST = "192.168.43.41";
public static final int PORT = 5000;
```

