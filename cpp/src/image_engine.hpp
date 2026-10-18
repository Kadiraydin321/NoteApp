// cpp/src/image_engine.hpp
#pragma once

#include <string>
#include <atomic>
#include <mutex>
#include <cstdint>

namespace byterescue {

struct ImageTaskStatus {
    bool is_active = false;
    bool is_complete = false;
    uint64_t bytes_written = 0;
    uint64_t total_bytes = 0;
    uint64_t bad_sectors = 0;
    double speed_mbps = 0.0;
    int percentage = 0;
    std::string source_path;
    std::string target_path;
    std::string error_message;
};

class ImageEngine {
public:
    static ImageEngine& instance();

    bool start_cloning(const std::string& source, const std::string& target_img);
    void stop_cloning();
    ImageTaskStatus get_status();

private:
    ImageEngine() = default;

    std::atomic<bool> should_stop_{false};
    std::atomic<bool> is_running_{false};
    ImageTaskStatus status_;
    std::mutex mutex_;
};

} // namespace byterescue
