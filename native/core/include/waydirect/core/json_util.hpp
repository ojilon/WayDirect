#pragma once
// wayer_core â€” JSON output helpers (defined in src/json_util.cpp).
// New code must include this file:
//   #include <waydirect/core/json_util.hpp>
// and call waydirect::core::json::escape().

#include <string>
#include <string_view>

namespace waydirect::core::json {

// Escape one string for embedding inside "..." in hand-written JSON.
std::string escape(std::string_view s);

} // namespace waydirect::core::json

// Back-compat alias for any out-of-tree call sites still on waydirect::json::escape.
namespace waydirect::json {
using waydirect::core::json::escape;
} // namespace waydirect::json

