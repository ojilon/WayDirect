// waydirect_shim.cpp â€” C++ shim exposing extern "C" symbols for the WayDirect router.
// Each function is a thin wrapper over the existing C++ domain module.
//
// Returned const char* pointers point into a thread_local std::string slot via keep();
// the pointer is valid until the next shim call on the same thread that returns a string.
// No <jni.h> here â€” jni/waydirect_engine.c is the only jni.h user.
//
// Symbol names match the `foreign shim` block in native/odin/waydirect_core.odin exactly
// (SEAM RULE: name + signature preserved across C++ shim â†’ C file â†’ Odin proc).
#include <waydirect/core/json_util.hpp>
#include <waydirect/core/paths.hpp>
#include <waydirect/core/logging.hpp>
#include <waydirect/storage/storage.hpp>
#include <waydirect/documents/documents.hpp>
#include <waydirect/transfer/transfer.hpp>
#include <waydirect/preview/preview.hpp>

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>

namespace {

    thread_local std::string g_return_slot;
    
    const char* keep(std::string s) {
        g_return_slot = std::move(s);
        return g_return_slot.c_str();
    }
    
    } // namespace
    
    extern "C" {
    
    const char* list_files(const char* path) {
        return keep(waydirect::storage::list_files(path));
    }
    
    const char* get_network_info() {
        return keep(waydirect::transfer::get_network_info());
    }
    
    const char* filter_documents(const char* payload) {
        return keep(waydirect::documents::filter_documents(payload));
    }
    
    const char* start_listener(int port) {
        return keep(waydirect::transfer::start_listener(port));
    }
    
    const char* search_files_to_file(const char* root, const char* query, const char* out_path) {
        return keep(waydirect::storage::search_files_to_file(root, query, out_path));
    }
    
    const char* find_large_files_to_file(const char* root, uint64_t min_bytes, int max_results,
                                               const char* out_path) {
        return keep(waydirect::storage::find_large_files_to_file(
            root, min_bytes, max_results, out_path));
    }
    
    const char* find_duplicates_to_file(const char* root, const char* out_path) {
        return keep(waydirect::storage::find_duplicates_to_file(root, out_path));
    }
    
    const char* plan_organize_to_file(const char* root, const char* out_path) {
        return keep(waydirect::storage::plan_organize_to_file(root, out_path));
    }
    
    const char* apply_organize_file(const char* plan_path, const char* report_path) {
        return keep(waydirect::storage::apply_organize_file(plan_path, report_path));
    }
    
    const char* read_cache_if_fresh(const char* cache_path, int max_age) {
        return keep(waydirect::storage::read_cache_if_fresh(cache_path, max_age));
    }
    
    const char* write_storage_stats(const char* root, const char* cache_path, uint64_t known_bytes) {
        return keep(waydirect::storage::write_storage_stats(root, cache_path, known_bytes));
    }
    
    const char* read_text_file(const char* path, uint64_t max_bytes) {
        return keep(waydirect::preview::read_text_file(
            path, static_cast<std::size_t>(max_bytes)));
    }
    
    bool invalidate_cache(const char* cache_path) {
        return waydirect::storage::invalidate_cache(cache_path);
    }
    
    const char* init_app_paths(const char* app_root) {
        std::string root(app_root);
        auto report = waydirect::core::ensure_app_dirs(root);
        waydirect::core::set_app_paths(report.paths);
        std::string manifest = waydirect::core::write_paths_manifest(report);
        std::string reply = R"({"status":"paths_set","manifest":")";
        reply += waydirect::core::json::escape(manifest);
        reply += R"(","all_ready":)";
        reply += report.all_ready() ? "true" : "false";
        reply += R"(,"root":")";
        reply += waydirect::core::json::escape(report.paths.root);
        reply += R"(","cache":")";
        reply += waydirect::core::json::escape(report.paths.cache);
        reply += R"(","temp":")";
        reply += waydirect::core::json::escape(report.paths.temp);
        reply += R"(","logs":")";
        reply += waydirect::core::json::escape(report.paths.logs);
        reply += "\"}";
        return keep(reply);
    }
    
    const char* json_escape(const char* s) {
        return keep(waydirect::core::json::escape(s));
    }
    
    void log_info(const char* tag, const char* msg) {
        waydirect::core::log::info(tag, msg);
    }
    
    void log_error(const char* tag, const char* msg) {
        waydirect::core::log::error(tag, msg);
    }

} // extern "C"

