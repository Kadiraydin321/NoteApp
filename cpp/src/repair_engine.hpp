// cpp/src/repair_engine.hpp
#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace byterescue {

struct RepairResult {
    bool success = false;
    bool was_modified = false;
    std::string original_file;
    std::string repaired_file;
    std::string file_type;
    std::string details;
    uint64_t original_size = 0;
    uint64_t repaired_size = 0;
};

class RepairEngine {
public:
    static RepairResult repair_file(const std::string& input_path, const std::string& output_path = "");
    static bool repair_buffer(const std::string& file_type, std::vector<uint8_t>& buffer, std::string& out_details);

private:
    static bool repair_jpeg(std::vector<uint8_t>& buf, std::string& details);
    static bool repair_png(std::vector<uint8_t>& buf, std::string& details);
    static bool repair_pdf(std::vector<uint8_t>& buf, std::string& details);
    static bool repair_zip(std::vector<uint8_t>& buf, std::string& details);
    static bool repair_mp4(std::vector<uint8_t>& buf, std::string& details);
    static std::string detect_type(const std::vector<uint8_t>& buf, const std::string& extension);
};

} // namespace byterescue
