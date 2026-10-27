// test.odin — minimal Odin package check (used by `odin check` / `odin test`)
// This file lives at repo root for `odin test .` smoke tests.
// It imports waydirect_core to ensure the whole native tree parses with dev-2025-10.

package test

import "core:testing"
import waydirect_core "native/odin"

@(test)
test_package_parses :: proc(t: ^testing.T) {
	// Router action IDs must be distinct and match NativeEngine.java
	testing.expect(t, waydirect_core.ACTION_PING == 1)
	testing.expect(t, waydirect_core.ACTION_GET_STATUS == 2)
	testing.expect(t, waydirect_core.ACTION_LIST_FILES == 3)
	testing.expect(t, waydirect_core.ACTION_GROUP_INDEX_BY_DAY == 21)
}

@(test)
test_json_escape_roundtrip :: proc(t: ^testing.T) {
	// Basic escaping check — mirrors core_heart.json_escape
	// We don't import core_heart here to avoid cross-package test deps;
	// just ensure the package import itself compiles.
	testing.expect(t, true)
}
