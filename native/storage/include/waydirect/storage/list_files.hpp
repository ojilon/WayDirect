#pragma once
// wayer_storage â€” non-recursive directory listing.
#include <string>
#include <string_view>

namespace waydirect::storage {
std::string list_files(std::string_view path);
} // namespace waydirect::storage

