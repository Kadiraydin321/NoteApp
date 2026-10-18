// cpp/src/vss_scanner.hpp
#pragma once

#include <string>
#include <vector>

namespace byterescue {

struct VssSnapshot {
    std::string id;
    std::string shadow_copy_id;
    std::string original_volume;
    std::string shadow_volume; // e.g. "\\?\GLOBALROOT\Device\HarddiskVolumeShadowCopy1"
    std::string creation_time;
    std::string description;
    bool is_accessible = true;
};

class VssScanner {
public:
    static std::vector<VssSnapshot> list_snapshots(const std::string& drive_letter = "C:");
    static std::string to_json(const std::vector<VssSnapshot>& snapshots);
};

} // namespace byterescue
