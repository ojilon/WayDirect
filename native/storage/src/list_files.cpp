// wayer_storage â€” non-recursive directory listing.
#include <waydirect/storage/list_files.hpp>

#include <waydirect/core/json_util.hpp>

#include <filesystem>
#include <sstream>
#include <system_error>

namespace waydirect::storage {
namespace fs = std::filesystem;

std::string list_files(std::string_view path) {
    std::error_code ec;
    fs::path dir_path(path);

    if (!fs::exists(dir_path, ec) || !fs::is_directory(dir_path, ec)) {
        return R"({"error":"invalid_directory"})";
    }

    std::ostringstream json;
    json << R"({"files": [)";
    bool first = true;

    for (const auto& entry : fs::directory_iterator(dir_path, ec)) {
        if (ec) break;
        if (!first) json << ",";
        json << "\"" << core::json::escape(entry.path().filename().string()) << "\"";
        first = false;
    }

    json << "]}";
    return json.str();
}

} // namespace waydirect::storage

