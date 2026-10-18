// cpp/src/ntfs_scanner.hpp
#pragma once

#include "carver_engine.hpp"

#include <string>
#include <vector>
#include <cstdint>
#include <atomic>
#include <functional>

namespace byterescue {

struct MftRun {
    uint64_t lcn = 0;
    uint64_t cluster_count = 0;
};

struct NtfsDeletedFile {
    uint64_t record_number = 0;
    uint64_t parent_record_number = 0;
    std::string filename;
    std::string original_path;
    std::string extension;
    std::string category;
    uint64_t file_size = 0;
    uint64_t alloc_size = 0;
    uint64_t created_time = 0;
    uint64_t modified_time = 0;
    uint64_t accessed_time = 0;
    uint32_t dos_attributes = 0;
    std::string created_time_str;
    std::string modified_time_str;
    std::string accessed_time_str;
    std::string attributes_str;
    bool is_directory = false;
    bool is_resident = false;
    std::vector<uint8_t> resident_data;
    std::vector<MftRun> run_list;
    std::string drive_letter; // e.g. "C:"
};

class NtfsScanner {
public:
    static bool is_volume_accessible(const std::string& drive_letter);
    static std::string format_filetime(uint64_t filetime);
    static std::string format_dos_attributes(uint32_t attrs);

    static std::vector<NtfsDeletedFile> scan_deleted_files(
        const std::string& drive_letter,
        const std::string& target_scope,
        std::atomic<bool>& should_stop,
        std::function<void(uint64_t scanned, uint64_t found)> progress_callback = nullptr
    );

    static bool recover_deleted_file(
        const NtfsDeletedFile& file,
        const std::string& dest_path
    );
};

} // namespace byterescue
