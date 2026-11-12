// =============================================================================
// route_log_sample.odin — how native should append to Java RouteLogger files
//
// Java path:  <logsDir>/<route>/YYYY-MM-DD.log
// Line form:  HH:mm:ss.mmm [I|W|E] message\n
//
// Wire this into package core (or storage) and call when payload ends with
// |log_path. Uses fopen("a")/fwrite — bionic-safe, same idea as android_fs.c.
// Not yet in the build; copy procs into a compiled .odin when ready.
// =============================================================================

package core_heart

import "core:c"
import "core:fmt"
import "core:strings"
import "core:time"

foreign import libc "system:c"

@(default_calling_convention = "c")
foreign libc {
	fopen  :: proc(path: cstring, mode: cstring) -> rawptr ---
	fwrite :: proc(ptr: rawptr, size: c.size_t, nmemb: c.size_t, stream: rawptr) -> c.size_t ---
	fclose :: proc(stream: rawptr) -> c.int ---
	fflush :: proc(stream: rawptr) -> c.int ---
}

route_log_append :: proc(log_path: string, level: string, message: string) -> bool {
	if len(log_path) == 0 {
		return false
	}
	now := time.now()
	h, m, s := time.clock(now)
	line := fmt.tprintf("%02d:%02d:%02d [%s] %s\n", h, m, s, level, message)

	cpath := strings.clone_to_cstring(log_path, context.temp_allocator)
	f := fopen(cpath, "a")
	if f == nil {
		return false
	}
	defer fclose(f)
	bytes := transmute([]u8)line
	n := fwrite(raw_data(bytes), 1, c.size_t(len(bytes)), f)
	fflush(f)
	return int(n) == len(bytes)
}

route_log_step :: proc(log_path: string, step: string, detail: string) {
	msg := step
	if len(detail) > 0 {
		msg = fmt.tprintf("%s | %s", step, detail)
	}
	_ = route_log_append(log_path, "I", msg)
}

// Last '|' field of a pipe-separated payload is the log path (Java convention).
payload_log_path :: proc(payload: string) -> string {
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
