#pragma once
// wayer_storage_organizer â€” plan/apply file organization.
#include <string>

namespace waydirect::storage {
// Report-only: returns JSON plan of {from, to} moves. Does NOT touch disk.
std::string plan_organize(const std::string& root_path);
// Same plan written to out_path; reply is {"status":"ok","path":...} only.
std::string plan_organize_to_file(const std::string& root_path, const std::string& out_path);
// Executes a plan file {"moves":[{"from","to"}]} Java wrote, writes a report
// {"moved","failed","skipped","errors"} to report_path. The old pipe-delimited
// format is retired â€” plans are files now.
std::string apply_organize_file(const std::string& plan_path, const std::string& report_path);
} // namespace waydirect::storage

