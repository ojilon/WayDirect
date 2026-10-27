#pragma once
// Public facade for transfer module.
// Sockets/hotspot orchestration may remain Java (NetworkManager); native for framing/hash/helpers.
#include <waydirect/transfer/transfer_engine.hpp>

namespace waydirect::transfer {
int transfer_module_anchor();
}

