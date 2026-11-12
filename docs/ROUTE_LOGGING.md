# Route logging

## Layout (private app storage)

```text
<filesDir>/WayDirect/logs/
  storage/YYYY-MM-DD.log
  files/YYYY-MM-DD.log
  bridge/YYYY-MM-DD.log      (optional — bridge also uses logcat)
  preview/YYYY-MM-DD.log
  transfer/YYYY-MM-DD.log
  cleaner/YYYY-MM-DD.log
  …
```

- One **folder per route** (short name: letters, digits, `_`, `-`).
- One **file per calendar day** (device local date).
- Line format: `HH:mm:ss.SSS [I|W|E] message`

Java API: `com.waydirect.bridge.RouteLogger`

```java
RouteLogger.step(context, "storage", "ui_loadStats", "force=true");
RouteLogger.error(context, "storage", "statvfs_failed");
String path = RouteLogger.logPathFor(context, "storage"); // pass to native
```

## Java → native chain

1. UI starts work → `RouteLogger.step(route, "ui_…")`
2. `Stats` / `NativeEngine` builds payload and **appends `logPath`** when useful
3. `Bridge.run` executes native (logcat line with action id + duration)
4. Native (when wired) appends steps to the same file
5. UI handles reply → `RouteLogger.step` or `error` with reason

Storage snapshot already passes `logPath` as the last `|` field of the payload.

## Native sample

See `native/core/src/route_log_sample.odin` — copy the helpers into a real package
and implement append via C FS (`open`/`write` with append), not `core:os` on Android.

## Where to look on device

Internals tab → private home → `logs/<route>/`  
or:

```bash
adb shell run-as com.waydirect.debug cat files/WayDirect/logs/storage/$(date +%F).log
```

(Package id may be `com.waydirect` for release builds.)
