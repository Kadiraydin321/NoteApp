// cpp/src/carver_engine.cpp
#include "carver_engine.hpp"
#include "ntfs_scanner.hpp"

#include <iostream>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <thread>
#include <chrono>
#include <cstring>
#include <algorithm>
#include <filesystem>

#ifdef _WIN32
#include <windows.h>
#endif

namespace fs = std::filesystem;

namespace byterescue {

CarverEngine::CarverEngine() {
    init_signatures();
}

CarverEngine::~CarverEngine() {
    stop_scan();
}

void CarverEngine::init_signatures() {
    // 1. PNG Image
    signatures_.push_back({
        "sig_png", "PNG Görseli", "png", "image/png", "images",
        { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A },
        { 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82 },
        0, 64, 50 * 1024 * 1024
    });

    // 2. JPEG / JPG Image
    signatures_.push_back({
        "sig_jpg", "JPEG Fotoğrafı", "jpg", "image/jpeg", "images",
        { 0xFF, 0xD8, 0xFF },
        { 0xFF, 0xD9 },
        0, 128, 50 * 1024 * 1024
    });

    // 3. PDF Document
    signatures_.push_back({
        "sig_pdf", "PDF Belgesi", "pdf", "application/pdf", "documents",
        { 0x25, 0x50, 0x44, 0x46, 0x2D }, // %PDF-
        { 0x25, 0x25, 0x45, 0x4F, 0x46 }, // %%EOF
        0, 128, 150 * 1024 * 1024
    });

    // 4. ZIP / Office Documents (DOCX, XLSX, PPTX, APK)
    signatures_.push_back({
        "sig_zip", "ZIP / Office Arşivi", "zip", "application/zip", "archives",
        { 0x50, 0x4B, 0x03, 0x04 },
        { 0x50, 0x4B, 0x05, 0x06 }, // End of central directory record
        0, 100, 500 * 1024 * 1024
    });

    // 5. GIF Image
    signatures_.push_back({
        "sig_gif", "GIF Animasyonu", "gif", "image/gif", "images",
        { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 }, // GIF89a
        { 0x00, 0x3B },
        0, 32, 50 * 1024 * 1024
    });

    // 6. MP4 Video
    signatures_.push_back({
        "sig_mp4", "MP4 / MOV Video", "mp4", "video/mp4", "media",
        { 0x66, 0x74, 0x79, 0x70 }, // ftyp box at offset 4
        {},
        4, 512, 1024 * 1024 * 1024 // 1GB
    });

    // 7. MP3 Audio
    signatures_.push_back({
        "sig_mp3", "MP3 Ses Dosyası", "mp3", "audio/mpeg", "media",
        { 0x49, 0x44, 0x33 }, // ID3v2 tag
        {},
        0, 256, 100 * 1024 * 1024
    });

    // 8. WEBP Image
    signatures_.push_back({
        "sig_webp", "WebP Görseli", "webp", "image/webp", "images",
        { 0x52, 0x49, 0x46, 0x46 }, // RIFF
        {},
        0, 32, 50 * 1024 * 1024
    });

    // 9. 7Z Archive
    signatures_.push_back({
        "sig_7z", "7-Zip Arşivi", "7z", "application/x-7z-compressed", "archives",
        { 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C },
        {},
        0, 64, 500 * 1024 * 1024
    });

    // 10. RAR Archive
    signatures_.push_back({
        "sig_rar", "RAR Arşivi", "rar", "application/vnd.rar", "archives",
        { 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07 },
        {},
        0, 64, 500 * 1024 * 1024
    });
}

std::string CarverEngine::format_size(uint64_t bytes) {
    if (bytes == 0) return "0 B";
    const char* units[] = { "B", "KB", "MB", "GB", "TB" };
    int i = 0;
    double d = static_cast<double>(bytes);
    while (d >= 1024.0 && i < 4) {
        d /= 1024.0;
        i++;
    }
    std::ostringstream ss;
    ss << std::fixed << std::setprecision(i == 0 ? 0 : (d >= 100.0 ? 0 : 1)) << d << " " << units[i];
    return ss.str();
}

std::string CarverEngine::to_hex_string(uint64_t val) {
    std::ostringstream ss;
    ss << "0x" << std::uppercase << std::setfill('0') << std::setw(8) << std::hex << val;
    return ss.str();
}

bool CarverEngine::start_scan(const ScanOptions& options) {
    if (is_running_.load()) {
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(files_mutex_);
        found_files_.clear();
    }

    {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_ = ScanStats();
        stats_.status = "running";
        stats_.current_location = options.source_path;
        stats_.start_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
    }

    is_running_.store(true);
    is_paused_.store(false);
    should_stop_.store(false);

    std::thread([this, options]() {
        this->run_scan_worker(options);
    }).detach();

    return true;
}

void CarverEngine::pause_scan() {
    if (is_running_.load()) {
        is_paused_.store(true);
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.status = "paused";
    }
}

void CarverEngine::resume_scan() {
    if (is_running_.load() && is_paused_.load()) {
        is_paused_.store(false);
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.status = "running";
    }
}

void CarverEngine::stop_scan() {
    if (is_running_.load()) {
        should_stop_.store(true);
        is_paused_.store(false);
        is_running_.store(false);
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.status = "stopped";
    }
}

ScanStats CarverEngine::get_stats() {
    std::lock_guard<std::mutex> lock(stats_mutex_);
    return stats_;
}

std::vector<FoundFile> CarverEngine::get_files(size_t limit, size_t offset) {
    std::lock_guard<std::mutex> lock(files_mutex_);
    if (offset >= found_files_.size()) {
        return {};
    }
    size_t count = std::min(limit, found_files_.size() - offset);
    return std::vector<FoundFile>(found_files_.begin() + offset, found_files_.begin() + offset + count);
}

void CarverEngine::run_scan_worker(ScanOptions options) {
    uint64_t scanned_acc = 0;

    try {
        fs::path p(options.source_path);

        // 1. Identify drives to check for deleted files
        std::vector<std::string> drives_to_check;
        std::string root = p.root_name().string();
        if (root.empty() && options.source_path.size() >= 2 && options.source_path[1] == ':') {
            root = options.source_path.substr(0, 2);
        }
        if (!root.empty()) {
            drives_to_check.push_back(root);
        } else {
            drives_to_check.push_back("C:");
            drives_to_check.push_back("D:");
        }

        // 1.1 Scan permanently deleted files directly from NTFS MFT if volume is accessible
        for (const auto& d : drives_to_check) {
            if (should_stop_.load()) break;
            if (NtfsScanner::is_volume_accessible(d)) {
                {
                    std::lock_guard<std::mutex> lock(stats_mutex_);
                    stats_.current_location = d + " [NTFS MFT] Kalıcı Silinen Dosyalar Taranıyor...";
                }
                auto ntfs_items = NtfsScanner::scan_deleted_files(d, options.source_path, should_stop_,
                    [this, &d](uint64_t scanned, uint64_t found) {
                        std::lock_guard<std::mutex> lock(stats_mutex_);
                        stats_.current_location = d + " MFT: " + std::to_string(scanned) + " kayıt (" + std::to_string(found) + " silinen bulundu)";
                    });

                std::lock_guard<std::mutex> flock(files_mutex_);
                for (const auto& nf : ntfs_items) {
                    FoundFile item;
                    item.id = "mft_" + std::to_string(nf.record_number);
                    item.name = nf.filename;
                    item.display_name = nf.filename;
                    item.type = nf.extension;
                    item.category = nf.category;
                    item.size_bytes = nf.file_size;
                    item.size_human = format_size(nf.file_size);
                    item.offset = 0;
                    item.hex_offset = "MFT #" + std::to_string(nf.record_number);
                    item.confidence = nf.is_resident ? 100 : 95;
                    item.is_recycle_bin = false;
                    item.is_mft_deleted = true;
                    item.is_recoverable = true;
                    item.status_label = nf.is_resident ? "%100 Kurtarılabilir (MFT)" : "%95 Kurtarılabilir (MFT)";
                    item.original_path = nf.original_path;
                    item.created_time_str = nf.created_time_str;
                    item.modified_time_str = nf.modified_time_str;
                    item.accessed_time_str = nf.accessed_time_str;
                    item.attributes_str = nf.attributes_str;
                    item.detection_source = "NTFS MFT (Kalıcı Silinmiş - Shift+Delete)";
                    item.mft_record_number = nf.record_number;
                    item.is_resident = nf.is_resident;
                    item.resident_data = nf.resident_data;
                    item.drive_letter = d;
                    item.preview_snippet = "Kalıcı Silinen Dosya (MFT): " + nf.original_path;
                    item.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

                    bool exists_already = false;
                    for (const auto& ex : found_files_) {
                        if (ex.id == item.id || (ex.name == item.name && ex.original_path == item.original_path)) {
                            exists_already = true;
                            break;
                        }
                    }
                    if (!exists_already) {
                        found_files_.push_back(item);
                    }
                }
            }
        }

        // 1.2 Check Recycle Bin for deleted items
        for (const auto& d : drives_to_check) {
            std::string rec_path = d + "\\$Recycle.Bin";
            std::error_code ec;
            if (fs::exists(rec_path, ec)) {
                scan_directory_recursive(rec_path, options, scanned_acc);
            }
        }

        // 2. Scan the source directory itself if it is not the Recycle Bin
        if (fs::exists(p) && fs::is_directory(p) &&
            options.source_path.find("$Recycle.Bin") == std::string::npos &&
            options.source_path.find("$RECYCLE.BIN") == std::string::npos) {
            scan_directory_recursive(options.source_path, options, scanned_acc);
        } else if (fs::exists(p) && fs::is_regular_file(p)) {
            // Raw carve disk image or single container file
            scan_file_carving(options.source_path, options, scanned_acc);
        }

        std::lock_guard<std::mutex> lock(stats_mutex_);
        if (!should_stop_.load()) {
            stats_.status = "completed";
            stats_.percentage = 100;
            {
                std::lock_guard<std::mutex> flock(files_mutex_);
                stats_.files_found = found_files_.size();
            }
        }
    } catch (const std::exception& ex) {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        stats_.status = "error";
        stats_.error_message = ex.what();
    }

    is_running_.store(false);
}

void CarverEngine::scan_directory_recursive(const std::string& dir_path, const ScanOptions& options, uint64_t& scanned_acc) {
    std::vector<std::string> dir_queue;
    dir_queue.push_back(dir_path);

    auto start_time = std::chrono::steady_clock::now();
    uint64_t last_stat_update = 0;

    bool is_recycle_bin_dir = (dir_path.find("$Recycle.Bin") != std::string::npos || dir_path.find("$RECYCLE.BIN") != std::string::npos);

    while (!dir_queue.empty() && !should_stop_.load()) {
        while (is_paused_.load() && !should_stop_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(150));
        }
        if (should_stop_.load()) break;

        std::string current = dir_queue.back();
        dir_queue.pop_back();

        std::error_code ec;
        fs::directory_iterator it(current, fs::directory_options::skip_permission_denied, ec);
        if (ec) continue;

        for (const auto& entry : it) {
            if (should_stop_.load()) break;

            std::string path_str = entry.path().string();
            std::string filename = entry.path().filename().string();

            if (entry.is_directory(ec)) {
                // Avoid recursive loops or huge unrelated folders
                if (filename != "node_modules" && filename != ".git" && filename != "AppData" && dir_queue.size() < 2000) {
                    dir_queue.push_back(path_str);
                }
            } else if (entry.is_regular_file(ec)) {
                uint64_t file_size = entry.file_size(ec);
                scanned_acc += file_size;

                if (is_recycle_bin_dir || current.find("$Recycle.Bin") != std::string::npos || current.find("$RECYCLE.BIN") != std::string::npos) {
                    // Check for Windows Recycle Bin Metadata File ($I...)
                    if (filename.rfind("$I", 0) == 0 && file_size >= 28) {
                        parse_recycle_bin_item(path_str, current, options.source_path);
                    }
                    // Do not carve random binaries or $R files inside Recycle Bin
                } else {
                    // In user directory: check for temporary, backup, or autosaved versions of deleted files
                    if (filename.rfind("~$", 0) == 0 || filename.rfind(".~", 0) == 0 ||
                        filename.rfind(".tmp") != std::string::npos || filename.rfind(".bak") != std::string::npos) {
                        FoundFile item;
                        item.id = "tmp_" + std::to_string(found_files_.size());
                        item.name = filename;
                        item.display_name = filename;
                        item.type = entry.path().extension().string();
                        if (!item.type.empty() && item.type[0] == '.') item.type = item.type.substr(1);
                        item.category = "documents";
                        item.size_bytes = file_size;
                        item.size_human = format_size(file_size);
                        item.offset = 0;
                        item.hex_offset = "0x00000000";
                        item.confidence = 90;
                        item.source_path = path_str;
                        item.original_path = path_str;
                        item.is_recycle_bin = false;
                        item.is_recoverable = true;
                        item.status_label = "Yedek / Geçici Dosya";
                        item.preview_snippet = "Gölge/Yedek Dosya: " + path_str;
                        item.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
                        {
                            std::lock_guard<std::mutex> lock(files_mutex_);
                            found_files_.push_back(item);
                        }
                    } else if (options.deep_carve) {
                        // Only carve if the file is an actual disk image or archive container
                        std::string ext = entry.path().extension().string();
                        std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
                        if (ext == ".img" || ext == ".bin" || ext == ".iso" || ext == ".vhd" || ext == ".dd") {
                            scan_file_carving(path_str, options, scanned_acc);
                        }
                    }
                }

                // Update real-time stats
                auto now = std::chrono::steady_clock::now();
                auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - start_time).count();
                if (elapsed_ms - last_stat_update > 150) {
                    last_stat_update = elapsed_ms;
                    double sec = elapsed_ms / 1000.0;
                    double mb = scanned_acc / (1024.0 * 1024.0);

                    std::lock_guard<std::mutex> lock(stats_mutex_);
                    stats_.bytes_scanned = scanned_acc;
                    stats_.speed_mbps = sec > 0.05 ? (mb / sec) : 0.0;
                    stats_.current_location = path_str;
                    {
                        std::lock_guard<std::mutex> flock(files_mutex_);
                        stats_.files_found = found_files_.size();
                    }
                }
            }
        }
    }
}

void CarverEngine::parse_recycle_bin_item(const std::string& i_file_path, const std::string& current_dir, const std::string& target_scope) {
    try {
        std::vector<uint8_t> buf;
#ifdef _WIN32
        HANDLE hFile = CreateFileA(i_file_path.c_str(), GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);
        if (hFile == INVALID_HANDLE_VALUE) return;
        DWORD fSize = GetFileSize(hFile, NULL);
        if (fSize < 28 || fSize > 65536) { CloseHandle(hFile); return; }
        buf.resize(fSize);
        DWORD bytesRead = 0;
        ReadFile(hFile, buf.data(), fSize, &bytesRead, NULL);
        CloseHandle(hFile);
        if (bytesRead < 28) return;
#else
        std::ifstream f(i_file_path, std::ios::binary);
        if (!f.is_open()) return;
        f.seekg(0, std::ios::end);
        size_t size = f.tellg();
        f.seekg(0, std::ios::beg);
        if (size < 28) return;
        buf.resize(size);
        f.read(reinterpret_cast<char*>(buf.data()), size);
#endif

        uint64_t ver = *reinterpret_cast<uint64_t*>(&buf[0]);
        uint64_t orig_size = *reinterpret_cast<uint64_t*>(&buf[8]);

        std::string orig_path;
        if (ver == 1 && buf.size() >= 544) {
            // Windows Vista/7/8 format: 260 UTF-16 characters at offset 24
            const uint8_t* u16 = &buf[24];
            for (size_t i = 0; i < 260 && (24 + i * 2 + 1) < buf.size(); ++i) {
                uint16_t ch = static_cast<uint16_t>(u16[i * 2]) | (static_cast<uint16_t>(u16[i * 2 + 1]) << 8);
                if (ch == 0) break;
                if (ch < 0x80) orig_path.push_back(static_cast<char>(ch));
                else if (ch < 0x800) {
                    orig_path.push_back(static_cast<char>(0xC0 | (ch >> 6)));
                    orig_path.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                } else {
                    orig_path.push_back(static_cast<char>(0xE0 | (ch >> 12)));
                    orig_path.push_back(static_cast<char>(0x80 | ((ch >> 6) & 0x3F)));
                    orig_path.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                }
            }
        } else {
            // Windows 10/11 format: uint32 path_len at offset 24, UTF-16 path at offset 28
            uint32_t path_len = *reinterpret_cast<uint32_t*>(&buf[24]);
            if (buf.size() >= 28 + path_len * 2) {
                const uint8_t* u16 = &buf[28];
                for (uint32_t i = 0; i < path_len && (28 + i * 2 + 1) < buf.size(); ++i) {
                    uint16_t ch = static_cast<uint16_t>(u16[i * 2]) | (static_cast<uint16_t>(u16[i * 2 + 1]) << 8);
                    if (ch == 0) break;
                    if (ch < 0x80) orig_path.push_back(static_cast<char>(ch));
                    else if (ch < 0x800) {
                        orig_path.push_back(static_cast<char>(0xC0 | (ch >> 6)));
                        orig_path.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                    } else {
                        orig_path.push_back(static_cast<char>(0xE0 | (ch >> 12)));
                        orig_path.push_back(static_cast<char>(0x80 | ((ch >> 6) & 0x3F)));
                        orig_path.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                    }
                }
            }
        }

        // Scope filter: if target_scope is a specific folder, check if orig_path belongs to it
        if (!target_scope.empty()) {
            std::string s_scope = target_scope;
            std::string s_orig = orig_path;
            std::transform(s_scope.begin(), s_scope.end(), s_scope.begin(), ::tolower);
            std::transform(s_orig.begin(), s_orig.end(), s_orig.begin(), ::tolower);
            std::replace(s_scope.begin(), s_scope.end(), '/', '\\');
            std::replace(s_orig.begin(), s_orig.end(), '/', '\\');

            while (!s_scope.empty() && s_scope.back() == '\\') {
                s_scope.pop_back();
            }

            bool is_drive_root = (s_scope.size() <= 3 && s_scope.find(':') != std::string::npos);
            bool is_recycle_bin = (s_scope.find("recycle.bin") != std::string::npos);

            if (!is_drive_root && !is_recycle_bin) {
                // If not in this directory or subdirectories, skip
                if (s_orig != s_scope && s_orig.rfind(s_scope + "\\", 0) != 0) {
                    return;
                }
            }
        }

        fs::path p(i_file_path);
        std::string i_name = p.filename().string();
        std::string r_name = "$R" + i_name.substr(2);
        fs::path r_path = fs::path(current_dir) / r_name;
        std::string r_str = r_path.string();

        bool has_data_file = false;
        uint64_t r_file_size = 0;

#ifdef _WIN32
        DWORD r_attrs = GetFileAttributesA(r_str.c_str());
        if (r_attrs != INVALID_FILE_ATTRIBUTES) {
            has_data_file = true;
            HANDLE hR = CreateFileA(r_str.c_str(), GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);
            if (hR != INVALID_HANDLE_VALUE) {
                LARGE_INTEGER li;
                if (GetFileSizeEx(hR, &li)) {
                    r_file_size = static_cast<uint64_t>(li.QuadPart);
                }
                CloseHandle(hR);
            }
        }
#else
        std::error_code ec_r;
        has_data_file = fs::exists(r_path, ec_r);
        if (has_data_file && !ec_r) {
            r_file_size = fs::file_size(r_path, ec_r);
        }
#endif

        std::string final_name = fs::path(orig_path).filename().string();
        if (final_name.empty()) final_name = "silinen_" + i_name;

        std::string ext;
        size_t dot_pos = final_name.find_last_of('.');
        if (dot_pos != std::string::npos) ext = final_name.substr(dot_pos + 1);
        std::string ext_lower = ext;
        std::transform(ext_lower.begin(), ext_lower.end(), ext_lower.begin(), ::tolower);

        std::string cat = "documents";
        if (ext_lower == "png" || ext_lower == "jpg" || ext_lower == "jpeg" || ext_lower == "webp" || ext_lower == "gif" || ext_lower == "bmp") cat = "images";
        else if (ext_lower == "mp4" || ext_lower == "mp3" || ext_lower == "wav" || ext_lower == "mkv" || ext_lower == "avi") cat = "media";
        else if (ext_lower == "zip" || ext_lower == "rar" || ext_lower == "7z" || ext_lower == "tar" || ext_lower == "gz") cat = "archives";
        else if (ext_lower == "txt" || ext_lower == "pdf" || ext_lower == "docx" || ext_lower == "xlsx" || ext_lower == "pptx" || ext_lower == "csv" || ext_lower == "json" || ext_lower == "md") cat = "documents";

        uint64_t final_size = (has_data_file && r_file_size > 0) ? r_file_size : orig_size;

        FoundFile item;
        item.id = "rec_" + i_name;
        item.name = final_name;
        item.display_name = final_name;
        item.type = ext.empty() ? "bin" : ext;
        item.category = cat;
        item.size_bytes = final_size;
        item.size_human = format_size(item.size_bytes);
        item.offset = 0;
        item.hex_offset = "0x00000000";
        item.is_recycle_bin = true;
        item.original_path = orig_path;
        item.preview_snippet = "Orijinal Konum: " + orig_path;
        item.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

        uint64_t del_time = (buf.size() >= 24) ? *reinterpret_cast<uint64_t*>(&buf[16]) : 0;
        item.detection_source = "Geri Dönüşüm Kutusu ($I/$R)";
        item.created_time_str = "-";
        item.modified_time_str = NtfsScanner::format_filetime(del_time);
        item.accessed_time_str = "-";
        item.attributes_str = "Gizli, Sistem, Arşiv";

#ifdef _WIN32
        if (has_data_file) {
            HANDLE hQuery = CreateFileA(r_str.c_str(), GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);
            if (hQuery != INVALID_HANDLE_VALUE) {
                FILETIME ftCreate, ftAccess, ftWrite;
                if (GetFileTime(hQuery, &ftCreate, &ftAccess, &ftWrite)) {
                    uint64_t c_val = (static_cast<uint64_t>(ftCreate.dwHighDateTime) << 32) | ftCreate.dwLowDateTime;
                    uint64_t a_val = (static_cast<uint64_t>(ftAccess.dwHighDateTime) << 32) | ftAccess.dwLowDateTime;
                    uint64_t w_val = (static_cast<uint64_t>(ftWrite.dwHighDateTime) << 32) | ftWrite.dwLowDateTime;
                    item.created_time_str = NtfsScanner::format_filetime(c_val);
                    if (del_time == 0) item.modified_time_str = NtfsScanner::format_filetime(w_val);
                    item.accessed_time_str = NtfsScanner::format_filetime(a_val);
                }
                DWORD fattr = GetFileAttributesA(r_str.c_str());
                if (fattr != INVALID_FILE_ATTRIBUTES) {
                    item.attributes_str = NtfsScanner::format_dos_attributes(fattr);
                }
                CloseHandle(hQuery);
            }
        }
#endif

        if (has_data_file) {
            item.confidence = 100;
            item.is_recoverable = true;
            item.status_label = "%100 Kurtarılabilir";
            item.source_path = r_str;
        } else {
            item.confidence = 0;
            item.is_recoverable = false;
            item.status_label = "Veri Üzerine Yazılmış";
            item.source_path = "";
        }

        {
            std::lock_guard<std::mutex> lock(files_mutex_);
            // Avoid duplicate additions
            bool exists_already = false;
            for (const auto& existing : found_files_) {
                if (existing.id == item.id) { exists_already = true; break; }
            }
            if (!exists_already) {
                found_files_.push_back(item);
            }
        }
    } catch (...) {}
}

void CarverEngine::scan_file_carving(const std::string& file_path, const ScanOptions& options, uint64_t& scanned_acc) {
    std::ifstream file(file_path, std::ios::binary);
    if (!file.is_open()) return;

    file.seekg(0, std::ios::end);
    uint64_t file_size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (file_size < 32) return;

    // Buffer chunk for block scanning
    const size_t CHUNK = 1024 * 1024; // 1MB
    const size_t OVERLAP = 4096;
    std::vector<uint8_t> buffer(CHUNK + OVERLAP);

    uint64_t file_offset = 0;

    while (file_offset < file_size && !should_stop_.load()) {
        while (is_paused_.load() && !should_stop_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(150));
        }
        if (should_stop_.load()) break;

        size_t read_bytes = static_cast<size_t>(std::min<uint64_t>(CHUNK + OVERLAP, file_size - file_offset));
        file.seekg(file_offset);
        file.read(reinterpret_cast<char*>(buffer.data()), read_bytes);
        size_t bytes_in_buffer = file.gcount();
        if (bytes_in_buffer < 32) break;

        // Slide over buffer with sector alignment
        size_t step = options.sector_align > 0 ? options.sector_align : 512;
        if (step > bytes_in_buffer) step = 1;

        for (size_t i = 0; i + 32 <= bytes_in_buffer; i += step) {
            for (const auto& sig : signatures_) {
                // Category filter check
                if (!options.categories.empty()) {
                    bool match_cat = false;
                    for (const auto& c : options.categories) {
                        if (c == "all" || c == sig.category) { match_cat = true; break; }
                    }
                    if (!match_cat) continue;
                }

                // Check header
                if (i + sig.header_offset + sig.header.size() <= bytes_in_buffer) {
                    if (std::memcmp(&buffer[i + sig.header_offset], sig.header.data(), sig.header.size()) == 0) {
                        uint64_t match_offset = file_offset + i;
                        
                        // Deduce approximate size from footer or container
                        uint64_t carved_size = 64 * 1024; // default 64KB estimate
                        if (!sig.footer.empty()) {
                            // Look ahead for footer
                            for (size_t f = i + sig.header.size(); f + sig.footer.size() <= bytes_in_buffer && f - i < sig.max_size; ++f) {
                                if (std::memcmp(&buffer[f], sig.footer.data(), sig.footer.size()) == 0) {
                                    carved_size = (f + sig.footer.size()) - i;
                                    break;
                                }
                            }
                        }

                        std::string gen_id = "carved_" + std::to_string(match_offset) + "_" + sig.extension;
                        std::string disp_name = "kurtarilan_" + to_hex_string(match_offset) + "." + sig.extension;

                        FoundFile f_item;
                        f_item.id = gen_id;
                        f_item.name = disp_name;
                        f_item.display_name = disp_name;
                        f_item.type = sig.extension;
                        f_item.category = sig.category;
                        f_item.size_bytes = carved_size;
                        f_item.size_human = format_size(carved_size);
                        f_item.offset = match_offset;
                        f_item.hex_offset = to_hex_string(match_offset);
                        f_item.confidence = 95;
                        f_item.source_path = file_path;
                        f_item.is_recycle_bin = false;
                        f_item.preview_snippet = sig.name + " (" + sig.extension + ")";
                        f_item.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

                        {
                            std::lock_guard<std::mutex> lock(files_mutex_);
                            // avoid duplicates at same offset
                            if (found_files_.empty() || found_files_.back().offset != match_offset) {
                                found_files_.push_back(f_item);
                            }
                        }
                    }
                }
            }
        }

        file_offset += CHUNK;
    }
}

std::string CarverEngine::get_hex_dump(const std::string& file_id, size_t length) {
    FoundFile target;
    bool found = false;
    {
        std::lock_guard<std::mutex> lock(files_mutex_);
        for (const auto& f : found_files_) {
            if (f.id == file_id) { target = f; found = true; break; }
        }
    }
    if (!found) return "Dosya bulunamadı.";
    std::vector<uint8_t> buf(length);
    size_t bytes_read = 0;

    if (target.is_resident && !target.resident_data.empty()) {
        size_t to_copy = std::min(length, target.resident_data.size());
        buf.assign(target.resident_data.begin(), target.resident_data.begin() + to_copy);
        bytes_read = to_copy;
    } else {
        if (target.source_path.empty()) return "Veri üzerine yazılmış (Yalnızca metaveri mevcut).";

#ifdef _WIN32
    HANDLE hFile = CreateFileA(target.source_path.c_str(), GENERIC_READ,
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
        NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);
    if (hFile != INVALID_HANDLE_VALUE) {
        if (target.offset > 0) {
            LARGE_INTEGER li;
            li.QuadPart = target.offset;
            SetFilePointerEx(hFile, li, NULL, FILE_BEGIN);
        }
        DWORD read_dw = 0;
        ReadFile(hFile, buf.data(), static_cast<DWORD>(length), &read_dw, NULL);
        bytes_read = read_dw;
        CloseHandle(hFile);
    }
#else
    std::ifstream file(target.source_path, std::ios::binary);
    if (file.is_open()) {
        file.seekg(target.offset);
        file.read(reinterpret_cast<char*>(buf.data()), length);
        bytes_read = file.gcount();
    }
#endif
    }

    if (bytes_read == 0) return "Kaynak dosya okunamadı: " + target.source_path;

    std::ostringstream ss;
    for (size_t i = 0; i < bytes_read; i += 16) {
        ss << std::uppercase << std::setfill('0') << std::setw(8) << std::hex << (target.offset + i) << "  ";

        for (size_t j = 0; j < 16; ++j) {
            if (i + j < bytes_read) {
                ss << std::uppercase << std::setfill('0') << std::setw(2) << std::hex << static_cast<int>(buf[i + j]) << " ";
            } else {
                ss << "   ";
            }
            if (j == 7) ss << " ";
        }

        ss << " |";
        for (size_t j = 0; j < 16; ++j) {
            if (i + j < bytes_read) {
                uint8_t c = buf[i + j];
                ss << (c >= 32 && c <= 126 ? static_cast<char>(c) : '.');
            } else {
                ss << " ";
            }
        }
        ss << "|\n";
    }

    return ss.str();
}

static const char BASE64_CHARS[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static std::string base64_encode_bytes(const uint8_t* data, size_t len) {
    std::string ret;
    int i = 0;
    uint8_t char_array_3[3];
    uint8_t char_array_4[4];

    while (len--) {
        char_array_3[i++] = *(data++);
        if (i == 3) {
            char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
            char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
            char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
            char_array_4[3] = char_array_3[2] & 0x3f;
            for (i = 0; i < 4; i++) ret += BASE64_CHARS[char_array_4[i]];
            i = 0;
        }
    }

    if (i) {
        for (int j = i; j < 3; j++) char_array_3[j] = '\0';
        char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
        char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
        char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
        char_array_4[3] = char_array_3[2] & 0x3f;
        for (int j = 0; j < i + 1; j++) ret += BASE64_CHARS[char_array_4[j]];
        while ((i++ < 3)) ret += '=';
    }

    return ret;
}

PreviewData CarverEngine::get_file_preview(const std::string& file_id) {
    PreviewData prev;
    FoundFile target;
    bool found = false;
    {
        std::lock_guard<std::mutex> lock(files_mutex_);
        for (const auto& f : found_files_) {
            if (f.id == file_id) { target = f; found = true; break; }
        }
    }
    if (!found) {
        prev.type = "text";
        prev.hex = "Dosya bulunamadı.";
        return prev;
    }

    prev.hex = get_hex_dump(file_id, 256);

    std::vector<uint8_t> buf;
    size_t bytes_read = 0;

    if (target.is_resident && !target.resident_data.empty()) {
        buf = target.resident_data;
        bytes_read = buf.size();
    } else {
        if (target.source_path.empty()) {
            prev.type = "text";
            prev.data = "Bu dosyanın asıl içeriği üzerine yazılmış veya temizlenmiş. Yalnızca metaveri kaydı kurtarıldı.";
            return prev;
        }

        size_t preview_len = static_cast<size_t>(std::min<uint64_t>(target.size_bytes, 4 * 1024 * 1024));
        if (preview_len == 0) preview_len = 64 * 1024;
        buf.resize(preview_len);

#ifdef _WIN32
        HANDLE hFile = CreateFileA(target.source_path.c_str(), GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);
        if (hFile != INVALID_HANDLE_VALUE) {
            if (target.offset > 0) {
                LARGE_INTEGER li;
                li.QuadPart = target.offset;
                SetFilePointerEx(hFile, li, NULL, FILE_BEGIN);
            }
            DWORD read_dw = 0;
            ReadFile(hFile, buf.data(), static_cast<DWORD>(preview_len), &read_dw, NULL);
            bytes_read = read_dw;
            CloseHandle(hFile);
        }
#else
        std::ifstream file(target.source_path, std::ios::binary);
        if (file.is_open()) {
            file.seekg(target.offset);
            file.read(reinterpret_cast<char*>(buf.data()), preview_len);
            bytes_read = file.gcount();
        }
#endif
    }

    buf.resize(bytes_read);

    std::string ext = target.type;
    for (char& c : ext) c = static_cast<char>(tolower(c));
    if (ext.empty() && target.name.find('.') != std::string::npos) {
        ext = target.name.substr(target.name.rfind('.') + 1);
        for (char& c : ext) c = static_cast<char>(tolower(c));
    }

    if (ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "gif" || ext == "webp") {
        prev.type = "image";
        if (ext == "png") prev.mime = "image/png";
        else if (ext == "jpg" || ext == "jpeg") prev.mime = "image/jpeg";
        else if (ext == "gif") prev.mime = "image/gif";
        else if (ext == "webp") prev.mime = "image/webp";

        prev.data = "data:" + prev.mime + ";base64," + base64_encode_bytes(buf.data(), buf.size());
    } else if (ext == "txt" || ext == "log" || ext == "json" || ext == "csv" || ext == "md" || ext == "xml" || ext == "html") {
        prev.type = "text";
        prev.mime = "text/plain";
        size_t text_len = std::min<size_t>(buf.size(), 4096);
        prev.data = std::string(reinterpret_cast<const char*>(buf.data()), text_len);
    } else {
        prev.type = "binary";
        prev.mime = "application/octet-stream";
    }

    return prev;
}

RecoveryResult CarverEngine::recover_files(const std::vector<std::string>& file_ids, const std::string& dest_dir) {
    RecoveryResult res;
    res.destination_dir = dest_dir;

    try {
        fs::path dest(dest_dir);
        if (!fs::exists(dest)) {
            fs::create_directories(dest);
        }

        std::vector<FoundFile> to_recover;
        {
            std::lock_guard<std::mutex> lock(files_mutex_);
            for (const auto& id : file_ids) {
                for (const auto& f : found_files_) {
                    if (f.id == id) {
                        to_recover.push_back(f);
                        break;
                    }
                }
            }
        }

        std::cout << "[CarverEngine::recover_files] to_recover eleman sayisi: " << to_recover.size() << "\n";

        for (const auto& item : to_recover) {
            fs::path out_path = dest / item.name;
            int counter = 1;
            std::error_code ec_out;
            while (fs::exists(out_path, ec_out)) {
                std::string stem = fs::path(item.name).stem().string();
                std::string ext = fs::path(item.name).extension().string();
                out_path = dest / (stem + "_" + std::to_string(counter++) + ext);
            }

            bool write_ok = false;

            if (item.is_mft_deleted) {
                if (item.is_resident && !item.resident_data.empty()) {
                    std::ofstream out(out_path, std::ios::binary);
                    if (out.is_open()) {
                        out.write(reinterpret_cast<const char*>(item.resident_data.data()), item.resident_data.size());
                        write_ok = true;
                    }
                } else {
                    NtfsDeletedFile ndf;
                    ndf.filename = item.name;
                    ndf.file_size = item.size_bytes;
                    ndf.drive_letter = item.drive_letter.empty() ? "C:" : item.drive_letter;
                    ndf.is_resident = false;
                    write_ok = NtfsScanner::recover_deleted_file(ndf, out_path.string());
                }

                if (write_ok) {
                    std::cout << "[+] MFT Dosyasi Kurtarildi: " << out_path.string() << "\n";
                    res.recovered_count++;
                    res.saved_files.push_back(out_path.string());
                } else {
                    std::cout << "[-] MFT Dosyasi Kurtarma Basarisiz: " << item.name << "\n";
                    res.failed_count++;
                }
                continue;
            }

            if (item.source_path.empty() || !item.is_recoverable) {
                std::cout << "[-] Dosya atlandi (veri yok): " << item.name << "\n";
                res.failed_count++;
                continue;
            }
#ifdef _WIN32
            HANDLE hIn = CreateFileA(item.source_path.c_str(), GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);

            if (hIn != INVALID_HANDLE_VALUE) {
                if (item.offset > 0) {
                    LARGE_INTEGER li;
                    li.QuadPart = item.offset;
                    SetFilePointerEx(hIn, li, NULL, FILE_BEGIN);
                }

                HANDLE hOut = CreateFileA(out_path.string().c_str(), GENERIC_WRITE,
                    FILE_SHARE_READ, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);

                if (hOut != INVALID_HANDLE_VALUE) {
                    DWORD to_read = static_cast<DWORD>(item.size_bytes);
                    DWORD total_written = 0;
                    std::vector<char> buf(64 * 1024);

                    while (total_written < to_read) {
                        DWORD chunk = (std::min)(static_cast<DWORD>(buf.size()), to_read - total_written);
                        DWORD bytes_read = 0;
                        if (!ReadFile(hIn, buf.data(), chunk, &bytes_read, NULL) || bytes_read == 0) {
                            break;
                        }
                        DWORD bytes_written = 0;
                        if (!WriteFile(hOut, buf.data(), bytes_read, &bytes_written, NULL) || bytes_written == 0) {
                            break;
                        }
                        total_written += bytes_written;
                    }

                    CloseHandle(hOut);
                    write_ok = (total_written > 0 || to_read == 0);
                }
                CloseHandle(hIn);
            }
#else
            std::ifstream in(item.source_path, std::ios::binary);
            if (in.is_open()) {
                std::ofstream out(out_path, std::ios::binary);
                if (out.is_open()) {
                    in.seekg(item.offset);
                    size_t bytes_to_copy = static_cast<size_t>(item.size_bytes);
                    std::vector<char> buffer(64 * 1024);
                    while (bytes_to_copy > 0 && in) {
                        size_t step = std::min(bytes_to_copy, buffer.size());
                        in.read(buffer.data(), step);
                        size_t read_bytes = in.gcount();
                        if (read_bytes == 0) break;
                        out.write(buffer.data(), read_bytes);
                        bytes_to_copy -= read_bytes;
                    }
                    write_ok = true;
                }
            }
#endif
            if (write_ok) {
                std::cout << "[+] Kurtarildi: " << out_path.string() << " (" << item.size_bytes << " bayt)\n";
                res.recovered_count++;
                res.saved_files.push_back(out_path.string());
            } else {
                std::cout << "[-] Kurtarma basarisiz: " << item.source_path << "\n";
                res.failed_count++;
            }
        }

        // Generate JSON recovery report
        fs::path report_file = dest / "kurtarma_raporu.json";
        std::ofstream r_out(report_file);
        if (r_out.is_open()) {
            r_out << "{\n";
            r_out << "  \"yazilim\": \"ByteRescue Native C++ Veri Kurtarıcı\",\n";
            r_out << "  \"gelistirici\": \"Kadir\",\n";
            r_out << "  \"kurtarilan_dosya_sayisi\": " << res.recovered_count << ",\n";
            r_out << "  \"hatali_dosya_sayisi\": " << res.failed_count << ",\n";
            std::string esc_dest;
            for (char c : dest_dir) {
                if (c == '\\') esc_dest += "\\\\";
                else esc_dest += c;
            }
            r_out << "  \"hedef_dizin\": \"" << esc_dest << "\"\n";
            r_out << "}\n";
            res.report_path = report_file.string();
        }

        res.success = (res.recovered_count > 0);
    } catch (const std::exception& ex) {
        res.error = ex.what();
        res.success = false;
    }

    return res;
}

std::string CarverEngine::create_test_disk(const std::string& output_path) {
    try {
        std::ofstream f(output_path, std::ios::binary);
        if (!f.is_open()) return "";

        // 10MB zeroed disk image
        const size_t DISK_SIZE = 10 * 1024 * 1024;
        std::vector<uint8_t> zeros(64 * 1024, 0);
        for (size_t written = 0; written < DISK_SIZE; written += zeros.size()) {
            f.write(reinterpret_cast<const char*>(zeros.data()), zeros.size());
        }

        // Helper to inject file at offset
        auto inject = [&](uint64_t offset, const std::vector<uint8_t>& data) {
            f.seekp(offset);
            f.write(reinterpret_cast<const char*>(data.data()), data.size());
        };

        // 1. PNG at 1MB (0x100000)
        std::vector<uint8_t> png = {
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG header
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, 0xDE,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82 // IEND
        };
        inject(0x100000, png);

        // 2. JPG at 2.5MB (0x280000)
        std::vector<uint8_t> jpg = {
            0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00, 0xFF, 0xD9
        };
        inject(0x280000, jpg);

        // 3. PDF at 4MB (0x400000)
        std::string pdf_str = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n%%EOF\n";
        inject(0x400000, std::vector<uint8_t>(pdf_str.begin(), pdf_str.end()));

        // 4. ZIP at 6MB (0x600000)
        std::vector<uint8_t> zip = {
            0x50, 0x4B, 0x03, 0x04, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        inject(0x600000, zip);

        return output_path;
    } catch (...) {
        return "";
    }
}

void CarverEngine::load_session_files(const std::vector<FoundFile>& files) {
    std::lock_guard<std::mutex> lock(files_mutex_);
    found_files_ = files;
    std::lock_guard<std::mutex> slock(stats_mutex_);
    stats_.files_found = found_files_.size();
    stats_.status = "completed";
    stats_.percentage = 100;
}

} // namespace byterescue
