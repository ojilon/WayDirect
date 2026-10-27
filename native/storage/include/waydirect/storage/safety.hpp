#pragma once
// wayer_storage â€” directory exclusion rules for tree walks.
// Migrated from native/storage/safety_rules.hpp (defined in src/safety.cpp).
#include <filesystem>

namespace waydirect::storage {
namespace fs = std::filesystem;

// Names checked once per directory, at the top of the subtree â€” not per-file.
bool is_excluded_dir(const fs::path& dir);
} // namespace waydirect::storage

