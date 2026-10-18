// cpp/src/vss_scanner.cpp
#include "vss_scanner.hpp"

#include <iostream>
#include <sstream>
#include <cstdio>
#include <algorithm>

#ifdef _WIN32
#include <windows.h>
#endif

namespace byterescue {

static std::string trim(const std::string& s) {
    auto start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    auto end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

std::vector<VssSnapshot> VssScanner::list_snapshots(const std::string& drive_letter) {
    std::vector<VssSnapshot> results;

#ifdef _WIN32
    std::string drive = drive_letter.substr(0, 2);
    if (drive.empty()) drive = "C:";

    std::string cmd = "vssadmin list shadows /for=" + drive;

    // Use pipe to run vssadmin silently
    FILE* pipe = _popen(cmd.c_str(), "r");
    if (!pipe) {
        return results;
    }

    char buffer[512];
    std::string output;
    while (fgets(buffer, sizeof(buffer), pipe) != NULL) {
        output += buffer;
    }
    _pclose(pipe);

    // Parse vssadmin output
    std::istringstream iss(output);
    std::string line;
    VssSnapshot current;
    bool in_snapshot = false;

    while (std::getline(iss, line)) {
        line = trim(line);
        if (line.empty()) continue;

        if (line.find("Gölge Kopya Kimliği:") != std::string::npos ||
            line.find("Shadow Copy ID:") != std::string::npos) {
            if (in_snapshot && !current.shadow_copy_id.empty()) {
                results.push_back(current);
                current = VssSnapshot();
            }
            in_snapshot = true;
            size_t colon = line.find(':');
            current.shadow_copy_id = trim(line.substr(colon + 1));
            current.id = "vss_" + std::to_string(results.size() + 1);
            current.original_volume = drive + "\\";
        } else if (line.find("Oluşturulma Saati:") != std::string::npos ||
                   line.find("Creation Time:") != std::string::npos) {
            size_t colon = line.find(':');
            current.creation_time = trim(line.substr(colon + 1));
        } else if (line.find("Gölge Kopya Birimi:") != std::string::npos ||
                   line.find("Shadow Copy Volume:") != std::string::npos ||
                   line.find("Shadow Copy Volume Name:") != std::string::npos) {
            size_t colon = line.find(':');
            current.shadow_volume = trim(line.substr(colon + 1));
            current.description = "Windows Sistem Geri Yükleme / Gölge Kopyası (" + current.creation_time + ")";
        }
    }

    if (in_snapshot && !current.shadow_copy_id.empty()) {
        results.push_back(current);
    }
#endif

    // If no active snapshots found or on non-Windows, provide a helpful representative status
    if (results.empty()) {
        VssSnapshot note;
        note.id = "vss_none";
        note.original_volume = drive_letter;
        note.description = drive_letter + " üzerinde etkin Sistem Geri Yükleme / VSS gölge kopyası bulunamadı.";
        note.is_accessible = false;
        results.push_back(note);
    }

    return results;
}

std::string VssScanner::to_json(const std::vector<VssSnapshot>& snapshots) {
    std::ostringstream ss;
    ss << "[\n";
    for (size_t i = 0; i < snapshots.size(); ++i) {
        const auto& s = snapshots[i];
        ss << "  {\n";
        ss << "    \"id\": \"" << s.id << "\",\n";
        ss << "    \"shadowCopyId\": \"" << s.shadow_copy_id << "\",\n";
        ss << "    \"originalVolume\": \"" << s.original_volume << "\",\n";
        ss << "    \"shadowVolume\": \"" << s.shadow_volume << "\",\n";
        ss << "    \"creationTime\": \"" << s.creation_time << "\",\n";
        ss << "    \"description\": \"" << s.description << "\",\n";
        ss << "    \"isAccessible\": " << (s.is_accessible ? "true" : "false") << "\n";
        ss << "  }" << (i + 1 < snapshots.size() ? "," : "") << "\n";
    }
    ss << "]\n";
    return ss.str();
}

} // namespace byterescue
