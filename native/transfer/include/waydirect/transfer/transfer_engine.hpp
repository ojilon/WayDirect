#pragma once
// wayer_transfer â€” protocol helpers. Migrated from transfer_engine.hpp.
// Sockets/hotspot orchestration stays Java (NetworkManager); native for framing/hash/helpers.
#include <string>

namespace waydirect::transfer {
std::string get_network_info();
std::string start_listener(int port);
} // namespace waydirect::transfer

