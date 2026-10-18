// cpp/src/disk_detector.hpp
#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace byterescue {

struct DriveItem {
    std::string id;
    std::string name;
    std::string path;
    std::string fs_type; // "NTFS", "FAT32", "exFAT", etc.
    uint64_t size_bytes = 0;
    std::string size_human;
    uint64_t free_bytes = 0;
    std::string free_human;
    std::string type; // "computer_drive", "user_folder", "recycle_bin"
    std::string badge;
    std::string description;
    bool is_accessible = true;
};

class DiskDetector {
public:
    static std::vector<DriveItem> list_drives();
    static std::string get_default_destination();
    static bool is_same_drive(const std::string& path_a, const std::string& path_b);
    static std::string to_json(const std::vector<DriveItem>& items);
};

} // namespace byterescue
