// =============================================================================
// route_log_sample.odin — SAMPLE for per-route daily logs from native
//
// Java writes:  <logsDir>/<route>/YYYY-MM-DD.log  via RouteLogger
// Native should append to the same file when Java passes log_path in the payload.
//
// Suggested payload convention (pipe-separated, last field):
//   ...|log_path
//
// Copy the helpers below into a real package (e.g. core) and call:
//   route_log_append(log_path, "I", "step_name detail")
// at each stage of a native action. Do NOT use core:os file APIs on Android —
// use the existing C FS wrappers (open/write/close) or a thin append helper.
//
// This file is documentation + sample only until you wire it into the build.
// =============================================================================

package core_heart

import "core:fmt"
import "core:strings"
import "core:time"

// Sample signature — implement with android_fs / fopen append.
// Returns false if path empty or write failed.
route_log_append :: proc(log_path: string, level: string, message: string) -> bool {
	if len(log_path) == 0 {
		return false
	}
	// TODO: open log_path with O_APPEND via C wrapper, write one line:
	//   HH:MM:SS.mmm [level] message\n
	// Example line format (match Java RouteLogger):
	//   14:22:01.123 [I] write_storage_stats start root=/storage/emulated/0
	_ = level
	_ = message
	return false // placeholder until FS append is wired
}

// Call at the start of a native action when payload includes log_path.
route_log_step :: proc(log_path: string, step: string, detail: string) {
	msg := step
	if len(detail) > 0 {
		msg = fmt.tprintf("%s | %s", step, detail)
	}
	_ = route_log_append(log_path, "I", msg)
}
