// cpp/src/carver_engine.hpp
#pragma once

#include <string>
#include <vector>
#include <memory>
#include <atomic>
#include <mutex>
#include <functional>
#include <cstdint>

namespace byterescue {

struct FileSignature {
    std::string id;
    std::string name;
    std::string extension;
    std::string mime;
    std::string category; // "images", "documents", "media", "archives", "code"
    std::vector<uint8_t> header;
    std::vector<uint8_t> footer;
    size_t header_offset = 0;
    size_t min_size = 32;
    size_t max_size = 100 * 1024 * 1024; // 100MB default
};

struct FoundFile {
    std::string id;
    std::string name;
    std::string display_name;
    std::string type;
    std::string category;
    uint64_t size_bytes = 0;
    std::string size_human;
    uint64_t offset = 0;
    std::string hex_offset;
    int confidence = 95;
    std::string source_path;
    std::string original_path;
    bool is_recycle_bin = false;
    bool is_mft_deleted = false;
    bool is_recoverable = true;
    std::string status_label = "Kurtarılabilir";
    std::string preview_snippet;
    uint64_t timestamp = 0;

    // Detailed file attributes & timestamps
    std::string created_time_str;
    std::string modified_time_str;
    std::string accessed_time_str;
    std::string attributes_str;
    std::string detection_source; // "NTFS MFT (Kalıcı Silinmiş)", "Geri Dönüşüm Kutusu", "Derin Sektör Taraması"
    uint64_t mft_record_number = 0;
    bool is_resident = false;
    std::vector<uint8_t> resident_data;
    std::string internal_metadata;
    std::string drive_letter;
};

struct ScanStats {
    uint64_t bytes_scanned = 0;
    uint64_t total_bytes = 0;
    uint64_t files_found = 0;
    uint64_t start_time_ms = 0;
    double speed_mbps = 0.0;
    uint32_t eta_seconds = 0;
    int percentage = 0;
    std::string status = "idle"; // idle, running, paused, completed, error, stopped
    std::string current_location;
    std::string error_message;
};

struct ScanOptions {
    std::string source_path;
    std::vector<std::string> categories;
    bool deep_carve = true;
    size_t sector_align = 512;
    size_t chunk_size = 2 * 1024 * 1024; // 2MB
};

struct PreviewData {
    std::string type; // "image", "text", "binary"
    std::string mime;
    std::string data; // base64 or text
    std::string hex;
};

struct RecoveryResult {
    bool success = false;
    size_t recovered_count = 0;
    size_t failed_count = 0;
    std::string destination_dir;
    std::string report_path;
    std::vector<std::string> saved_files;
    std::string error;
};

class CarverEngine {
public:
    CarverEngine();
    ~CarverEngine();

    // Scan controls
    bool start_scan(const ScanOptions& options);
    void pause_scan();
    void resume_scan();
    void stop_scan();

    // Query status & results
    ScanStats get_stats();
    std::vector<FoundFile> get_files(size_t limit = 1000, size_t offset = 0);
    std::string get_hex_dump(const std::string& file_id, size_t length = 256);
    PreviewData get_file_preview(const std::string& file_id);

    // File recovery
    RecoveryResult recover_files(const std::vector<std::string>& file_ids, const std::string& dest_dir);

    // Built-in test disk generator
    std::string create_test_disk(const std::string& output_path);

    // Helpers
    static std::string format_size(uint64_t bytes);
    static std::string to_hex_string(uint64_t val);

private:
    void init_signatures();
    void run_scan_worker(ScanOptions options);
    void scan_directory_recursive(const std::string& dir_path, const ScanOptions& options, uint64_t& scanned_acc);
    void scan_file_carving(const std::string& file_path, const ScanOptions& options, uint64_t& scanned_acc);
    void parse_recycle_bin_item(const std::string& i_file_path, const std::string& current_dir, const std::string& target_scope);

    std::vector<FileSignature> signatures_;
    std::vector<FoundFile> found_files_;
    std::mutex files_mutex_;
    std::mutex stats_mutex_;

    ScanStats stats_;
    std::atomic<bool> is_running_{false};
    std::atomic<bool> is_paused_{false};
    std::atomic<bool> should_stop_{false};
};

} // namespace byterescue
