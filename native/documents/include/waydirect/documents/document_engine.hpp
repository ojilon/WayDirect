#pragma once
// wayer_documents â€” document filtering. Migrated from document_engine.hpp.
#include <string>
#include <string_view>

namespace waydirect::documents {
std::string filter_documents(std::string_view path);
} // namespace waydirect::documents

