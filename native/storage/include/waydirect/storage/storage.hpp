#pragma once
// Public facade for the storage module â€” includes every public header.
#include <waydirect/storage/analyse_storage_space.hpp>
#include <waydirect/storage/cleaner.hpp>
#include <waydirect/storage/extension_map.hpp>
#include <waydirect/storage/index.hpp>
#include <waydirect/storage/list_files.hpp>
#include <waydirect/storage/organizer.hpp>
#include <waydirect/storage/safety.hpp>
#include <waydirect/storage/search.hpp>
#include <waydirect/storage/stats_db.hpp>
#include <waydirect/storage/stats_json.hpp>
#include <waydirect/storage/storage_cache.hpp>
#include <waydirect/storage/walker.hpp>

namespace waydirect::storage {

int storage_module_anchor();

} // namespace waydirect::storage

