// =============================================================================
// route_log_sample.odin — how native should append to Java RouteLogger files
//
// Java path:  <logsDir>/<route>/YYYY-MM-DD.log
// Line form:  HH:mm:ss.mmm [I|W|E] message\n
//
// Copy these procs into package core (or storage) and call them when the
// action payload ends with |log_path. Prefer C open(O_APPEND)/write/close —
// same family as android_fs.c — not core:os on Android 10.
//
// Minimal end-to-end sample (storage stats):
//   1. Java: RouteLogger.logPath(ctx, "storage")  → pass as last | field
//   2. Odin: log_path := payload_log_path(payload)
//            route_log_step(log_path, "stats_begin", root)
//            … work …
//            route_log_step(log_path, "stats_done", fmt.tprintf("%d bytes", total))
// =============================================================================

package core_heart

import "core:c"
import "core:fmt"
import "core:strings"
import "core:time"

// Thin libc bindings for append-only logging (bionic-safe).
foreign import libc "system:c"

@(default_calling_convention = "c")
foreign libc {
	fopen  :: proc(path: cstring, mode: cstring) -> rawptr ---
	fwrite :: proc(ptr: rawptr, size: c.size_t, nmemb: c.size_t, stream: rawptr) -> c.size_t ---
	fclose :: proc(stream: rawptr) -> c.int ---
	fflush :: proc(stream: rawptr) -> c.int ---
}

// route_log_append writes one line. Returns false if path empty or open failed.
route_log_append :: proc(log_path: string, level: string, message: string) -> bool {
	if len(log_path) == 0 {
		return false
	}
	now := time.now()
	h, m, s := time.clock(now)
	// Approximate milliseconds from the duration since unix epoch.
	ns := time.duration_nanoseconds(time.since(time.Time{}))
	ms := (ns / 1_000_000) % 1000
	line := fmt.tprintf("%02d:%02d:%02d.%03d [%s] %s\n", h, m, s, ms, level, message)

	cpath := strings.clone_to_cstring(log_path, context.temp_allocator)
	f := fopen(cpath, "a")
	if f == nil {
		return false
	}
	defer fclose(f)
	cdata := raw_data(transmute([]u8)line)
	n := fwrite(cdata, 1, c.size_t(len(line)), f)
	fflush(f)
	return int(n) == len(line)
}

route_log_step :: proc(log_path: string, step: string, detail: string) {
	msg := step
	if len(detail) > 0 {
		msg = fmt.tprintf("%s | %s", step, detail)
	}
	_ = route_log_append(log_path, "I", msg)
}

route_log_warn :: proc(log_path: string, step: string, detail: string) {
	msg := step
	if len(detail) > 0 {
		msg = fmt.tprintf("%s | %s", step, detail)
	}
	_ = route_log_append(log_path, "W", msg)
}

route_log_error :: proc(log_path: string, step: string, detail: string) {
	msg := step
	if len(detail) > 0 {
		msg = fmt.tprintf("%s | %s", step, detail)
	}
	_ = route_log_append(log_path, "E", msg)
}

// Example: extract last | field from a pipe payload as log_path
payload_log_path :: proc(payload: string) -> string {
	// find last '|'
	idx := -1
	for i := len(payload) - 1; i >= 0; i -= 1 {
		if payload[i] == '|' {
			idx = i
			break
		}
	}
	if idx < 0 || idx + 1 >= len(payload) {
		return ""
	}
	return payload[idx + 1:]
}

// --- usage sketch (not compiled as-is) ---------------------------------------
//
// handle_storage_stats :: proc(payload: string) -> string {
//     log_path := payload_log_path(payload)
//     route_log_step(log_path, "stats_begin", "")
//
//     total, err := scan_tree(...)
//     if err != nil {
//         route_log_error(log_path, "stats_fail", fmt.tprintf("%v", err))
//         return error_json(err)
//     }
//
//     route_log_step(log_path, "stats_done", fmt.tprintf("total=%d", total))
//     return ok_json(total)
// }
