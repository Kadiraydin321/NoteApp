// cpp/src/http_server.hpp
#pragma once

#include "carver_engine.hpp"
#include "disk_detector.hpp"
#include "folder_dialog.hpp"

#include <string>
#include <atomic>
#include <memory>
#include <cstdint>

namespace byterescue {

class HttpServer {
public:
    HttpServer(int start_port = 45678);
    ~HttpServer();

    bool start();
    void stop();
    bool is_running() const { return is_running_.load(); }
    int get_port() const { return bound_port_; }
    std::string get_url() const;

private:
    void listen_loop();
    void handle_client(uintptr_t client_socket);

    int start_port_;
    int bound_port_ = 0;
    std::atomic<bool> is_running_{false};
    uintptr_t listen_socket_ = 0;

    CarverEngine engine_;
};

} // namespace byterescue
