// cpp/src/carver_engine.cpp
#include "carver_engine.hpp"

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
        if (!fs::exists(p)) {
            std::lock_guard<std::mutex> lock(stats_mutex_);
            stats_.status = "error";
            stats_.error_message = "Hedef yol bulunamadı: " + options.source_path;
            is_running_.store(false);
            return;
        }

        if (fs::is_directory(p)) {
            scan_directory_recursive(options.source_path, options, scanned_acc);

            // Also check Windows Recycle Bin for files deleted from this directory/volume
            std::string root = p.root_name().string();
            if (root.empty() && options.source_path.size() >= 2 && options.source_path[1] == ':') {
                root = options.source_path.substr(0, 2);
            }
            if (!root.empty()) {
                std::string recycle_path = root + "\\$Recycle.Bin";
                if (fs::exists(recycle_path) && options.source_path.find("$Recycle.Bin") == std::string::npos && options.source_path.find("$RECYCLE.BIN") == std::string::npos) {
                    scan_directory_recursive(recycle_path, options, scanned_acc);
                }
            }
        } else {
            // Single disk image or file carving
            scan_file_carving(options.source_path, options, scanned_acc);
        }

        std::lock_guard<std::mutex> lock(stats_mutex_);
        if (!should_stop_.load()) {
            stats_.status = "completed";
            stats_.percentage = 100;
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
                // Avoid recursive loops or huge node_modules
                if (filename != "node_modules" && filename != ".git" && filename != "$RECYCLE.BIN" && dir_queue.size() < 10000) {
                    dir_queue.push_back(path_str);
                } else if (filename == "$RECYCLE.BIN" || filename == "$Recycle.Bin") {
                    dir_queue.push_back(path_str);
                }
            } else if (entry.is_regular_file(ec)) {
                uint64_t file_size = entry.file_size(ec);
                scanned_acc += file_size;

                // Check for Windows Recycle Bin Metadata File ($I...)
                if (filename.rfind("$I", 0) == 0 && file_size >= 28) {
                    parse_recycle_bin_item(path_str, current);
                } else {
                    // Quick inspection or carving
                    scan_file_carving(path_str, options, scanned_acc);
                }

                // Update real-time stats
                auto now = std::chrono::steady_clock::now();
                auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - start_time).count();
                if (elapsed_ms - last_stat_update > 200) {
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

void CarverEngine::parse_recycle_bin_item(const std::string& i_file_path, const std::string& current_dir) {
    try {
        std::ifstream f(i_file_path, std::ios::binary);
        if (!f.is_open()) return;

        f.seekg(0, std::ios::end);
        size_t size = f.tellg();
        f.seekg(0, std::ios::beg);
        if (size < 28) return;

        std::vector<uint8_t> buf(size);
        f.read(reinterpret_cast<char*>(buf.data()), size);

        uint64_t orig_size = *reinterpret_cast<uint64_t*>(&buf[8]);
        uint32_t path_len = *reinterpret_cast<uint32_t*>(&buf[24]);

        std::string orig_path;
        if (size >= 28 + path_len * 2) {
            const uint8_t* u16 = &buf[28];
            for (uint32_t i = 0; i < path_len && (28 + i * 2 + 1) < size; ++i) {
                uint16_t ch = static_cast<uint16_t>(u16[i * 2]) | (static_cast<uint16_t>(u16[i * 2 + 1]) << 8);
                if (ch == 0) break;
                if (ch < 0x80) {
                    orig_path.push_back(static_cast<char>(ch));
                } else if (ch < 0x800) {
                    orig_path.push_back(static_cast<char>(0xC0 | (ch >> 6)));
                    orig_path.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                } else {
                    orig_path.push_back(static_cast<char>(0xE0 | (ch >> 12)));
                    orig_path.push_back(static_cast<char>(0x80 | ((ch >> 6) & 0x3F)));
                    orig_path.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                }
            }
        }

        fs::path p(i_file_path);
        std::string i_name = p.filename().string();
        std::string r_name = "$R" + i_name.substr(2);
        fs::path r_path = fs::path(current_dir) / r_name;

        bool has_data_file = fs::exists(r_path);
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

        FoundFile item;
        item.id = "file_" + std::to_string(std::chrono::system_clock::now().time_since_epoch().count()) + "_" + std::to_string(found_files_.size());
        item.name = final_name;
        item.display_name = final_name;
        item.type = ext.empty() ? "bin" : ext;
        item.category = cat;
        item.size_bytes = has_data_file ? fs::file_size(r_path) : orig_size;
        item.size_human = format_size(item.size_bytes);
        item.offset = 0;
        item.hex_offset = to_hex_string(item.offset);
        item.confidence = 98;
        item.source_path = has_data_file ? r_path.string() : i_file_path;
        item.original_path = orig_path;
        item.is_recycle_bin = true;
        item.preview_snippet = "Orijinal Konum: " + orig_path;
        item.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();

        {
            std::lock_guard<std::mutex> lock(files_mutex_);
            found_files_.push_back(item);
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

    std::ifstream file(target.source_path, std::ios::binary);
    if (!file.is_open()) return "Kaynak dosya açılamadı: " + target.source_path;

    file.seekg(target.offset);
    std::vector<uint8_t> buf(length);
    file.read(reinterpret_cast<char*>(buf.data()), length);
    size_t bytes_read = file.gcount();

    std::ostringstream ss;
    for (size_t i = 0; i < bytes_read; i += 16) {
        ss << std::uppercase << std::setfill('0') << std::setw(8) << std::hex << (target.offset + i) << "  ";

        // Hex bytes
        for (size_t j = 0; j < 16; ++j) {
            if (i + j < bytes_read) {
                ss << std::uppercase << std::setfill('0') << std::setw(2) << std::hex << static_cast<int>(buf[i + j]) << " ";
            } else {
                ss << "   ";
            }
            if (j == 7) ss << " ";
        }

        ss << " |";
        // ASCII printable
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

    std::string ext = target.type;
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);

    std::ifstream file(target.source_path, std::ios::binary);
    if (!file.is_open()) {
        prev.type = "text";
        return prev;
    }

    file.seekg(target.offset);
    size_t preview_len = static_cast<size_t>(std::min<uint64_t>(target.size_bytes, 4 * 1024 * 1024)); // max 4MB preview
    if (preview_len == 0) preview_len = 64 * 1024;

    std::vector<uint8_t> buf(preview_len);
    file.read(reinterpret_cast<char*>(buf.data()), preview_len);
    size_t bytes_read = file.gcount();
    buf.resize(bytes_read);

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

        for (const auto& item : to_recover) {
            std::ifstream in(item.source_path, std::ios::binary);
            if (!in.is_open()) {
                res.failed_count++;
                continue;
            }

            fs::path out_path = dest / item.name;
            // Avoid collisions
            int counter = 1;
            while (fs::exists(out_path)) {
                std::string stem = fs::path(item.name).stem().string();
                std::string ext = fs::path(item.name).extension().string();
                out_path = dest / (stem + "_" + std::to_string(counter++) + ext);
            }

            std::ofstream out(out_path, std::ios::binary);
            if (!out.is_open()) {
                res.failed_count++;
                continue;
            }

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

            res.recovered_count++;
            res.saved_files.push_back(out_path.string());
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
            r_out << "  \"hedef_dizin\": \"" << dest_dir << "\"\n";
            r_out << "}\n";
            res.report_path = report_file.string();
        }

        res.success = true;
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

} // namespace byterescue
