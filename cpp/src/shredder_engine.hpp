// cpp/src/shredder_engine.hpp
#pragma once

#include <string>
#include <cstdint>

namespace byterescue {

struct ShredResult {
    bool success = false;
    std::string file_path;
    uint64_t bytes_shredded = 0;
    int passes_completed = 0;
    std::string message;
};

class ShredderEngine {
public:
    static ShredResult shred_file(const std::string& path, int passes = 3);
};

} // namespace byterescue
