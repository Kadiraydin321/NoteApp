// cpp/src/image_engine.cpp
#include "image_engine.hpp"

#include <iostream>
#include <fstream>
#include <thread>
#include <chrono>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#endif

namespace byterescue {

ImageEngine& ImageEngine::instance() {
    static ImageEngine s_instance;
    return s_instance;
}

ImageTaskStatus ImageEngine::get_status() {
    std::lock_guard<std::mutex> lock(mutex_);
    return status_;
}

void ImageEngine::stop_cloning() {
    should_stop_.store(true);
}

bool ImageEngine::start_cloning(const std::string& source, const std::string& target_img) {
    if (is_running_.load()) return false;

    {
        std::lock_guard<std::mutex> lock(mutex_);
        status_ = ImageTaskStatus();
        status_.is_active = true;
        status_.source_path = source;
        status_.target_path = target_img;
    }

    should_stop_.store(false);
    is_running_.store(true);

    std::thread([this, source, target_img]() {
        uint64_t total_size = 0;
        std::string win_source = source;

#ifdef _WIN32
        // If drive letter e.g. "E:\" or "E:", normalize to "\\.\E:" for raw volume read
        if (win_source.size() >= 2 && win_source[1] == ':') {
            std::string root = win_source.substr(0, 2) + "\\";
            ULARGE_INTEGER free_bytes, total_bytes, total_free;
            if (GetDiskFreeSpaceExA(root.c_str(), &free_bytes, &total_bytes, &total_free)) {
                total_size = total_bytes.QuadPart;
            }
        }
#endif

        if (total_size == 0) {
            // Default 1GB estimate or test size
            total_size = 1024ULL * 1024ULL * 1024ULL;
        }

        {
            std::lock_guard<std::mutex> lock(this->mutex_);
            this->status_.total_bytes = total_size;
        }

        std::ofstream out(target_img, std::ios::binary);
        if (!out.is_open()) {
            std::lock_guard<std::mutex> lock(this->mutex_);
            this->status_.is_active = false;
            this->status_.error_message = "Hedef .img dosyası oluşturulamadı: " + target_img;
            this->is_running_.store(false);
            return;
        }

        const size_t CHUNK_SIZE = 64 * 1024; // 64KB blocks
        std::vector<uint8_t> buffer(CHUNK_SIZE, 0);

        uint64_t bytes_copied = 0;
        uint64_t bad_sectors = 0;
        auto start_time = std::chrono::steady_clock::now();

#ifdef _WIN32
        std::string raw_path = win_source;
        if (win_source.size() >= 2 && win_source[1] == ':') {
            raw_path = "\\\\.\\" + win_source.substr(0, 2);
        }

        HANDLE hSource = CreateFileA(
            raw_path.c_str(),
            GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            NULL,
            OPEN_EXISTING,
            FILE_FLAG_SEQUENTIAL_SCAN,
            NULL
        );

        if (hSource != INVALID_HANDLE_VALUE) {
            while (!this->should_stop_.load() && bytes_copied < total_size) {
                DWORD bytes_read = 0;
                BOOL success = ReadFile(hSource, buffer.data(), static_cast<DWORD>(CHUNK_SIZE), &bytes_read, NULL);

                if (!success || bytes_read == 0) {
                    DWORD err = GetLastError();
                    if (err == ERROR_HANDLE_EOF || bytes_read == 0) {
                        break; // EOF reached
                    }
                    // Bad sector encountered: fill with zeros and continue
                    std::fill(buffer.begin(), buffer.end(), 0);
                    bytes_read = static_cast<DWORD>(CHUNK_SIZE);
                    bad_sectors++;

                    // Advance pointer past bad sector
                    LARGE_INTEGER li;
                    li.QuadPart = bytes_copied + CHUNK_SIZE;
                    SetFilePointerEx(hSource, li, NULL, FILE_BEGIN);
                }

                out.write(reinterpret_cast<const char*>(buffer.data()), bytes_read);
                bytes_copied += bytes_read;

                auto now = std::chrono::steady_clock::now();
                double elapsed_sec = std::chrono::duration<double>(now - start_time).count();
                double speed = (elapsed_sec > 0.05) ? (bytes_copied / (1024.0 * 1024.0 * elapsed_sec)) : 0.0;
                int pct = (total_size > 0) ? static_cast<int>((bytes_copied * 100) / total_size) : 0;
                if (pct > 100) pct = 100;

                {
                    std::lock_guard<std::mutex> lock(this->mutex_);
                    this->status_.bytes_written = bytes_copied;
                    this->status_.bad_sectors = bad_sectors;
                    this->status_.speed_mbps = speed;
                    this->status_.percentage = pct;
                }
            }
            CloseHandle(hSource);
        } else {
            // Fallback for directory or normal file copying to image
            std::ifstream in(source, std::ios::binary);
            if (in.is_open()) {
                in.seekg(0, std::ios::end);
                total_size = in.tellg();
                in.seekg(0, std::ios::beg);

                while (!this->should_stop_.load() && in.read(reinterpret_cast<char*>(buffer.data()), CHUNK_SIZE)) {
                    std::streamsize bytes_read = in.gcount();
                    out.write(reinterpret_cast<const char*>(buffer.data()), bytes_read);
                    bytes_copied += bytes_read;

                    auto now = std::chrono::steady_clock::now();
                    double elapsed_sec = std::chrono::duration<double>(now - start_time).count();
                    double speed = (elapsed_sec > 0.05) ? (bytes_copied / (1024.0 * 1024.0 * elapsed_sec)) : 0.0;
                    int pct = (total_size > 0) ? static_cast<int>((bytes_copied * 100) / total_size) : 0;

                    {
                        std::lock_guard<std::mutex> lock(this->mutex_);
                        this->status_.bytes_written = bytes_copied;
                        this->status_.speed_mbps = speed;
                        this->status_.percentage = pct;
                    }
                }
                in.close();
            }
        }
#endif

        out.close();

        {
            std::lock_guard<std::mutex> lock(this->mutex_);
            this->status_.is_active = false;
            this->status_.is_complete = !this->should_stop_.load();
            this->status_.percentage = 100;
        }
        this->is_running_.store(false);
    }).detach();

    return true;
}

} // namespace byterescue
