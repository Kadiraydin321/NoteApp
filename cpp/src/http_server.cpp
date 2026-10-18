// cpp/src/http_server.cpp
#include "http_server.hpp"
#include "embedded_web.hpp"

#include <iostream>
#include <sstream>
#include <iomanip>
#include <vector>
#include <thread>
#include <cstring>
#include <algorithm>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#define closesocket close
#define SOCKET int
#define INVALID_SOCKET -1
#define SOCKET_ERROR -1
#endif

namespace byterescue {

HttpServer::HttpServer(int start_port) : start_port_(start_port) {}

HttpServer::~HttpServer() {
    stop();
}

std::string HttpServer::get_url() const {
    return "http://127.0.0.1:" + std::to_string(bound_port_);
}

bool HttpServer::start() {
#ifdef _WIN32
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return false;
    }
#endif

    SOCKET listen_sock = INVALID_SOCKET;
    int port = start_port_;

    for (int attempts = 0; attempts < 20; ++attempts, ++port) {
        listen_sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (listen_sock == INVALID_SOCKET) {
            continue;
        }

        int opt = 1;
#ifdef _WIN32
        setsockopt(listen_sock, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&opt), sizeof(opt));
#else
        setsockopt(listen_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
#endif

        sockaddr_in server_addr{};
        server_addr.sin_family = AF_INET;
        server_addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK); // 127.0.0.1 only
        server_addr.sin_port = htons(static_cast<uint16_t>(port));

        if (bind(listen_sock, reinterpret_cast<sockaddr*>(&server_addr), sizeof(server_addr)) == 0) {
            if (listen(listen_sock, 16) == 0) {
                bound_port_ = port;
                break;
            }
        }

        closesocket(listen_sock);
        listen_sock = INVALID_SOCKET;
    }

    if (listen_sock == INVALID_SOCKET) {
#ifdef _WIN32
        WSACleanup();
#endif
        return false;
    }

    listen_socket_ = static_cast<uintptr_t>(listen_sock);
    is_running_.store(true);

    std::thread([this]() {
        this->listen_loop();
    }).detach();

    return true;
}

void HttpServer::stop() {
    if (is_running_.load()) {
        is_running_.store(false);
        if (listen_socket_ != 0 && listen_socket_ != static_cast<uintptr_t>(INVALID_SOCKET)) {
            closesocket(static_cast<SOCKET>(listen_socket_));
            listen_socket_ = 0;
        }
#ifdef _WIN32
        WSACleanup();
#endif
    }
}

void HttpServer::listen_loop() {
    while (is_running_.load()) {
        sockaddr_in client_addr{};
        int client_len = sizeof(client_addr);
        SOCKET client_sock = accept(static_cast<SOCKET>(listen_socket_), reinterpret_cast<sockaddr*>(&client_addr), &client_len);
        if (client_sock == INVALID_SOCKET) {
            if (!is_running_.load()) break;
            continue;
        }

        std::thread([this, client_sock]() {
            this->handle_client(static_cast<uintptr_t>(client_sock));
        }).detach();
    }
}

// Helpers for quick JSON string escaping
static std::string json_escape(const std::string& s) {
    std::ostringstream o;
    for (char c : s) {
        if (c == '"') o << "\\\"";
        else if (c == '\\') o << "\\\\";
        else if (c == '\b') o << "\\b";
        else if (c == '\f') o << "\\f";
        else if (c == '\n') o << "\\n";
        else if (c == '\r') o << "\\r";
        else if (c == '\t') o << "\\t";
        else if (static_cast<unsigned char>(c) <= '\x1f') {
            o << "\\u" << std::hex << std::setw(4) << std::setfill('0') << static_cast<int>(c);
        } else {
            o << c;
        }
    }
    return o.str();
}

void HttpServer::handle_client(uintptr_t client_socket) {
    SOCKET sock = static_cast<SOCKET>(client_socket);

    // 1. Read HTTP headers until \r\n\r\n
    std::string req;
    char buffer[4096];
    size_t header_end = std::string::npos;

    while (header_end == std::string::npos) {
        int bytes = recv(sock, buffer, sizeof(buffer), 0);
        if (bytes <= 0) break;
        req.append(buffer, bytes);
        header_end = req.find("\r\n\r\n");
        if (req.size() > 65536) break;
    }

    if (header_end == std::string::npos) {
        closesocket(sock);
        return;
    }

    std::string header_str = req.substr(0, header_end);
    std::string body = req.substr(header_end + 4);

    std::istringstream req_stream(header_str);
    std::string method, full_path, http_ver;
    req_stream >> method >> full_path >> http_ver;

    std::string path = full_path;
    std::string query;
    size_t q_pos = full_path.find('?');
    if (q_pos != std::string::npos) {
        path = full_path.substr(0, q_pos);
        query = full_path.substr(q_pos + 1);
    }

    // 2. Parse Content-Length and read full body
    size_t content_length = 0;
    size_t cl_pos = header_str.find("Content-Length:");
    if (cl_pos == std::string::npos) cl_pos = header_str.find("content-length:");
    if (cl_pos != std::string::npos) {
        size_t end_line = header_str.find("\r\n", cl_pos);
        std::string cl_val = header_str.substr(cl_pos + 15, end_line - (cl_pos + 15));
        try {
            content_length = std::stoull(cl_val);
        } catch (...) {}
    }

    if (header_str.find("100-continue") != std::string::npos || header_str.find("100-Continue") != std::string::npos) {
        const char* resp_100 = "HTTP/1.1 100 Continue\r\n\r\n";
        send(sock, resp_100, static_cast<int>(strlen(resp_100)), 0);
    }

    while (body.size() < content_length) {
        int bytes_to_read = static_cast<int>((std::min<size_t>)(sizeof(buffer), content_length - body.size()));
        int bytes = recv(sock, buffer, bytes_to_read, 0);
        if (bytes <= 0) break;
        body.append(buffer, bytes);
    }

    std::string content_type = "application/json; charset=utf-8";
    std::string resp_body;
    int status_code = 200;

    if (path == "/" || path == "/index.html") {
        content_type = "text/html; charset=utf-8";
        resp_body = EMBEDDED_INDEX_HTML;
    } else if (path == "/api/status") {
        ScanStats st = engine_.get_stats();
        bool is_admin = false;
#ifdef _WIN32
        HANDLE hToken = NULL;
        if (OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &hToken)) {
            TOKEN_ELEVATION elevation;
            DWORD cbSize = sizeof(TOKEN_ELEVATION);
            if (GetTokenInformation(hToken, TokenElevation, &elevation, sizeof(elevation), &cbSize)) {
                is_admin = elevation.TokenIsElevated != 0;
            }
            CloseHandle(hToken);
        }
#endif
        std::ostringstream ss;
        ss << "{\n";
        ss << "  \"status\": \"" << st.status << "\",\n";
        ss << "  \"isAdmin\": " << (is_admin ? "true" : "false") << ",\n";
        ss << "  \"bytesScanned\": " << st.bytes_scanned << ",\n";
        ss << "  \"totalBytes\": " << st.total_bytes << ",\n";
        ss << "  \"filesFound\": " << st.files_found << ",\n";
        ss << "  \"speedMBps\": " << st.speed_mbps << ",\n";
        ss << "  \"etaSeconds\": " << st.eta_seconds << ",\n";
        ss << "  \"percentage\": " << st.percentage << ",\n";
        ss << "  \"currentLocation\": \"" << json_escape(st.current_location) << "\",\n";
        ss << "  \"errorMessage\": \"" << json_escape(st.error_message) << "\"\n";
        ss << "}";
        resp_body = ss.str();
    } else if (path == "/api/scan/start" && method == "POST") {
        // Parse sourcePath from body
        std::string source_path = "C:\\";
        size_t sp = body.find("\"sourcePath\"");
        if (sp != std::string::npos) {
            size_t colon = body.find(':', sp);
            size_t q1 = body.find('"', colon);
            size_t q2 = body.find('"', q1 + 1);
            if (q1 != std::string::npos && q2 != std::string::npos) {
                source_path = body.substr(q1 + 1, q2 - q1 - 1);
                // unescape simple backslashes
                std::string unesc;
                for (size_t i = 0; i < source_path.size(); ++i) {
                    if (source_path[i] == '\\' && i + 1 < source_path.size() && source_path[i + 1] == '\\') {
                        unesc += '\\';
                        i++;
                    } else {
                        unesc += source_path[i];
                    }
                }
                source_path = unesc;
            }
        }

        ScanOptions opt;
        opt.source_path = source_path;
        opt.categories = { "all" };
        opt.deep_carve = true;

        bool started = engine_.start_scan(opt);
        resp_body = started ? "{\"success\":true}" : "{\"success\":false,\"error\":\"Tarama zaten çalışıyor\"}";
    } else if (path == "/api/scan/pause" && method == "POST") {
        engine_.pause_scan();
        resp_body = "{\"success\":true}";
    } else if (path == "/api/scan/resume" && method == "POST") {
        engine_.resume_scan();
        resp_body = "{\"success\":true}";
    } else if (path == "/api/scan/stop" && method == "POST") {
        engine_.stop_scan();
        resp_body = "{\"success\":true}";
    } else if (path == "/api/files") {
        auto files = engine_.get_files(1000, 0);
        std::ostringstream ss;
        ss << "[\n";
        for (size_t i = 0; i < files.size(); ++i) {
            const auto& f = files[i];
            ss << "  {\n";
            ss << "    \"id\": \"" << f.id << "\",\n";
            ss << "    \"name\": \"" << json_escape(f.name) << "\",\n";
            ss << "    \"displayName\": \"" << json_escape(f.display_name) << "\",\n";
            ss << "    \"type\": \"" << f.type << "\",\n";
            ss << "    \"category\": \"" << f.category << "\",\n";
            ss << "    \"sizeBytes\": " << f.size_bytes << ",\n";
            ss << "    \"sizeHuman\": \"" << f.size_human << "\",\n";
            ss << "    \"offset\": " << f.offset << ",\n";
            ss << "    \"hexOffset\": \"" << f.hex_offset << "\",\n";
            ss << "    \"confidence\": " << f.confidence << ",\n";
            ss << "    \"sourcePath\": \"" << json_escape(f.source_path) << "\",\n";
            ss << "    \"originalPath\": \"" << json_escape(f.original_path) << "\",\n";
            ss << "    \"isRecycleBin\": " << (f.is_recycle_bin ? "true" : "false") << ",\n";
            ss << "    \"isMftDeleted\": " << (f.is_mft_deleted ? "true" : "false") << ",\n";
            ss << "    \"isRecoverable\": " << (f.is_recoverable ? "true" : "false") << ",\n";
            ss << "    \"statusLabel\": \"" << json_escape(f.status_label) << "\",\n";
            ss << "    \"previewSnippet\": \"" << json_escape(f.preview_snippet) << "\",\n";
            ss << "    \"createdTime\": \"" << json_escape(f.created_time_str) << "\",\n";
            ss << "    \"modifiedTime\": \"" << json_escape(f.modified_time_str) << "\",\n";
            ss << "    \"accessedTime\": \"" << json_escape(f.accessed_time_str) << "\",\n";
            ss << "    \"attributes\": \"" << json_escape(f.attributes_str) << "\",\n";
            ss << "    \"detectionSource\": \"" << json_escape(f.detection_source) << "\",\n";
            ss << "    \"mftRecordNumber\": " << f.mft_record_number << ",\n";
            ss << "    \"isResident\": " << (f.is_resident ? "true" : "false") << "\n";
            ss << "  }" << (i + 1 < files.size() ? "," : "") << "\n";
        }
        ss << "]\n";
        resp_body = ss.str();
    } else if (path == "/api/preview") {
        std::string file_id;
        size_t id_pos = query.find("id=");
        if (id_pos != std::string::npos) {
            file_id = query.substr(id_pos + 3);
            size_t amp = file_id.find('&');
            if (amp != std::string::npos) file_id = file_id.substr(0, amp);
        }
        PreviewData prev = engine_.get_file_preview(file_id);
        FoundFile target_meta;
        bool found_meta = false;
        for (const auto& f : engine_.get_files(10000, 0)) {
            if (f.id == file_id) { target_meta = f; found_meta = true; break; }
        }

        std::ostringstream ss;
        ss << "{\n";
        ss << "  \"id\": \"" << file_id << "\",\n";
        ss << "  \"name\": \"" << (found_meta ? json_escape(target_meta.name) : "") << "\",\n";
        ss << "  \"type\": \"" << prev.type << "\",\n";
        ss << "  \"fileExt\": \"" << (found_meta ? json_escape(target_meta.type) : "") << "\",\n";
        ss << "  \"mime\": \"" << prev.mime << "\",\n";
        ss << "  \"sizeBytes\": " << (found_meta ? target_meta.size_bytes : 0) << ",\n";
        ss << "  \"sizeHuman\": \"" << (found_meta ? target_meta.size_human : "") << "\",\n";
        ss << "  \"originalPath\": \"" << (found_meta ? json_escape(target_meta.original_path) : "") << "\",\n";
        ss << "  \"confidence\": " << (found_meta ? target_meta.confidence : 95) << ",\n";
        ss << "  \"statusLabel\": \"" << (found_meta ? json_escape(target_meta.status_label) : "") << "\",\n";
        ss << "  \"data\": \"" << json_escape(prev.data) << "\",\n";
        ss << "  \"hex\": \"" << json_escape(prev.hex) << "\"\n";
        ss << "}";
        resp_body = ss.str();
    } else if (path == "/api/recover" && method == "POST") {
        // Extract destination dir and fileIds
        std::string dest_dir = DiskDetector::get_default_destination();
        size_t dd = body.find("\"destDir\"");
        if (dd == std::string::npos) dd = body.find("\"destination\"");
        if (dd != std::string::npos) {
            size_t colon = body.find(':', dd);
            size_t q1 = body.find('"', colon);
            size_t q2 = body.find('"', q1 + 1);
            if (q1 != std::string::npos && q2 != std::string::npos) {
                dest_dir = body.substr(q1 + 1, q2 - q1 - 1);
                // unescape
                std::string unesc;
                for (size_t i = 0; i < dest_dir.size(); ++i) {
                    if (dest_dir[i] == '\\' && i + 1 < dest_dir.size() && dest_dir[i + 1] == '\\') {
                        unesc += '\\';
                        i++;
                    } else {
                        unesc += dest_dir[i];
                    }
                }
                dest_dir = unesc;
            }
        }

        std::vector<std::string> file_ids;
        size_t fi = body.find("\"fileIds\"");
        if (fi == std::string::npos) fi = body.find("\"file_ids\"");
        if (fi != std::string::npos) {
            size_t b1 = body.find('[', fi);
            size_t b2 = body.find(']', b1);
            if (b1 != std::string::npos && b2 != std::string::npos) {
                std::string list_str = body.substr(b1 + 1, b2 - b1 - 1);
                size_t p = 0;
                while ((p = list_str.find('"', p)) != std::string::npos) {
                    size_t p2 = list_str.find('"', p + 1);
                    if (p2 == std::string::npos) break;
                    std::string id_val = list_str.substr(p + 1, p2 - p - 1);
                    if (!id_val.empty()) file_ids.push_back(id_val);
                    p = p2 + 1;
                }
            } else {
                // Single string fallback
                size_t colon = body.find(':', fi);
                size_t q1 = body.find('"', colon);
                size_t q2 = body.find('"', q1 + 1);
                if (q1 != std::string::npos && q2 != std::string::npos) {
                    file_ids.push_back(body.substr(q1 + 1, q2 - q1 - 1));
                }
            }
        }

        std::cout << "[+] /api/recover cagrildi. Hedef: " << dest_dir << " Dosya adedi: " << file_ids.size() << "\n";
        RecoveryResult res = engine_.recover_files(file_ids, dest_dir);
        std::cout << "[+] Kurtarma sonucu: " << res.recovered_count << " basarili, " << res.failed_count << " basarisiz.\n";

        std::ostringstream ss;
        ss << "{\n";
        ss << "  \"success\": " << (res.success ? "true" : "false") << ",\n";
        ss << "  \"recoveredCount\": " << res.recovered_count << ",\n";
        ss << "  \"failedCount\": " << res.failed_count << ",\n";
        ss << "  \"destinationDir\": \"" << json_escape(res.destination_dir) << "\",\n";
        ss << "  \"reportPath\": \"" << json_escape(res.report_path) << "\",\n";
        ss << "  \"error\": \"" << json_escape(res.error) << "\"\n";
        ss << "}";
        resp_body = ss.str();
    } else if (path == "/api/drives") {
        auto drives = DiskDetector::list_drives();
        resp_body = DiskDetector::to_json(drives);
    } else if (path == "/api/browse-folder") {
        std::string folder = FolderDialog::open_folder_dialog("ByteRescue - Taranacak Klasörü Seçin");
        resp_body = "{\"path\":\"" + json_escape(folder) + "\"}";
    } else if (path == "/api/default-dest") {
        std::string d = DiskDetector::get_default_destination();
        resp_body = "{\"path\":\"" + json_escape(d) + "\"}";
    } else if (path == "/api/create-test-disk") {
        std::string test_path = DiskDetector::get_default_destination() + "_Test_Disk.img";
        std::string gen = engine_.create_test_disk(test_path);
        resp_body = "{\"path\":\"" + json_escape(gen) + "\"}";
    } else if (path == "/api/shutdown-computer" && method == "POST") {
        resp_body = "{\"success\":true,\"message\":\"Bilgisayar kapatılıyor...\"}";
#ifdef _WIN32
        system("shutdown /s /t 10 /c \"ByteRescue: Islemler tamamlandi. Bilgisayar 10 saniye icinde kapatiliyor.\"");
#endif
    } else if ((path == "/api/exit" || path == "/api/quit") && method == "POST") {
        resp_body = "{\"success\":true}";
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::milliseconds(300));
            this->stop();
            exit(0);
        }).detach();
    } else if (path == "/api/shutdown" && method == "POST") {
        resp_body = "{\"shuttingDown\":true}";
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            this->stop();
            exit(0);
        }).detach();
    } else if (path == "/api/restart-elevated" && method == "POST") {
#ifdef _WIN32
        char exePath[MAX_PATH] = { 0 };
        GetModuleFileNameA(NULL, exePath, MAX_PATH);
        HINSTANCE hInst = ShellExecuteA(NULL, "runas", exePath, NULL, NULL, SW_SHOWNORMAL);
        if (reinterpret_cast<uintptr_t>(hInst) > 32) {
            resp_body = "{\"success\":true,\"message\":\"Yönetici olarak yeniden başlatılıyor...\"}";
            std::thread([this]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(800));
                this->stop();
                exit(0);
            }).detach();
        } else {
            resp_body = "{\"success\":false,\"error\":\"Yönetici izni verilmedi veya iptal edildi.\"}";
        }
#else
        resp_body = "{\"success\":false,\"error\":\"Yalnızca Windows ortamında desteklenir.\"}";
#endif
    } else if (path == "/api/file-details") {
        std::string file_id;
        size_t id_pos = query.find("id=");
        if (id_pos != std::string::npos) {
            file_id = query.substr(id_pos + 3);
            size_t amp = file_id.find('&');
            if (amp != std::string::npos) file_id = file_id.substr(0, amp);
        }
        auto files = engine_.get_files(1000, 0);
        const FoundFile* found_ptr = nullptr;
        for (const auto& f : files) {
            if (f.id == file_id) { found_ptr = &f; break; }
        }
        if (found_ptr) {
            const auto& f = *found_ptr;
            std::ostringstream ss;
            ss << "{\n";
            ss << "  \"id\": \"" << f.id << "\",\n";
            ss << "  \"name\": \"" << json_escape(f.name) << "\",\n";
            ss << "  \"originalPath\": \"" << json_escape(f.original_path) << "\",\n";
            ss << "  \"type\": \"" << f.type << "\",\n";
            ss << "  \"category\": \"" << f.category << "\",\n";
            ss << "  \"sizeBytes\": " << f.size_bytes << ",\n";
            ss << "  \"sizeHuman\": \"" << f.size_human << "\",\n";
            ss << "  \"createdTime\": \"" << json_escape(f.created_time_str) << "\",\n";
            ss << "  \"modifiedTime\": \"" << json_escape(f.modified_time_str) << "\",\n";
            ss << "  \"accessedTime\": \"" << json_escape(f.accessed_time_str) << "\",\n";
            ss << "  \"attributes\": \"" << json_escape(f.attributes_str) << "\",\n";
            ss << "  \"detectionSource\": \"" << json_escape(f.detection_source) << "\",\n";
            ss << "  \"confidence\": " << f.confidence << ",\n";
            ss << "  \"statusLabel\": \"" << json_escape(f.status_label) << "\",\n";
            ss << "  \"isRecoverable\": " << (f.is_recoverable ? "true" : "false") << ",\n";
            ss << "  \"isMftDeleted\": " << (f.is_mft_deleted ? "true" : "false") << ",\n";
            ss << "  \"mftRecordNumber\": " << f.mft_record_number << ",\n";
            ss << "  \"isResident\": " << (f.is_resident ? "true" : "false") << ",\n";
            ss << "  \"hexOffset\": \"" << f.hex_offset << "\"\n";
            ss << "}";
            resp_body = ss.str();
        } else {
            status_code = 404;
            resp_body = "{\"error\":\"Dosya bulunamadı\"}";
        }
    } else if (path == "/api/same-drive-check") {
        std::string src, dest;
        size_t s1 = query.find("src=");
        if (s1 != std::string::npos) {
            src = query.substr(s1 + 4);
            size_t amp = src.find('&');
            if (amp != std::string::npos) src = src.substr(0, amp);
        }
        size_t d1 = query.find("dest=");
        if (d1 != std::string::npos) {
            dest = query.substr(d1 + 5);
            size_t amp = dest.find('&');
            if (amp != std::string::npos) dest = dest.substr(0, amp);
        }
        bool same = DiskDetector::is_same_drive(src, dest);
        resp_body = "{\"sameDrive\":" + std::string(same ? "true" : "false") + "}";
    } else if (path == "/api/session/export") {
        auto files = engine_.get_files(10000, 0);
        std::ostringstream ss;
        ss << "{\n";
        ss << "  \"version\": \"1.3.0\",\n";
        ss << "  \"generator\": \"ByteRescue Native by Kadir\",\n";
        ss << "  \"filesCount\": " << files.size() << ",\n";
        ss << "  \"files\": [\n";
        for (size_t i = 0; i < files.size(); ++i) {
            const auto& f = files[i];
            ss << "    {\n";
            ss << "      \"id\": \"" << f.id << "\",\n";
            ss << "      \"name\": \"" << json_escape(f.name) << "\",\n";
            ss << "      \"displayName\": \"" << json_escape(f.display_name) << "\",\n";
            ss << "      \"type\": \"" << f.type << "\",\n";
            ss << "      \"category\": \"" << f.category << "\",\n";
            ss << "      \"sizeBytes\": " << f.size_bytes << ",\n";
            ss << "      \"sizeHuman\": \"" << f.size_human << "\",\n";
            ss << "      \"confidence\": " << f.confidence << ",\n";
            ss << "      \"sourcePath\": \"" << json_escape(f.source_path) << "\",\n";
            ss << "      \"originalPath\": \"" << json_escape(f.original_path) << "\",\n";
            ss << "      \"isRecycleBin\": " << (f.is_recycle_bin ? "true" : "false") << ",\n";
            ss << "      \"isMftDeleted\": " << (f.is_mft_deleted ? "true" : "false") << ",\n";
            ss << "      \"isRecoverable\": " << (f.is_recoverable ? "true" : "false") << ",\n";
            ss << "      \"statusLabel\": \"" << json_escape(f.status_label) << "\",\n";
            ss << "      \"createdTime\": \"" << json_escape(f.created_time_str) << "\",\n";
            ss << "      \"modifiedTime\": \"" << json_escape(f.modified_time_str) << "\",\n";
            ss << "      \"accessedTime\": \"" << json_escape(f.accessed_time_str) << "\",\n";
            ss << "      \"attributes\": \"" << json_escape(f.attributes_str) << "\",\n";
            ss << "      \"detectionSource\": \"" << json_escape(f.detection_source) << "\",\n";
            ss << "      \"mftRecordNumber\": " << f.mft_record_number << ",\n";
            ss << "      \"isResident\": " << (f.is_resident ? "true" : "false") << "\n";
            ss << "    }" << (i + 1 < files.size() ? "," : "") << "\n";
        }
        ss << "  ]\n";
        ss << "}";
        resp_body = ss.str();
    } else if (path == "/api/session/import" && method == "POST") {
        // Parse simple session files if provided
        resp_body = "{\"success\":true}";
    } else {
        status_code = 404;
        resp_body = "{\"error\":\"Not Found\"}";
    }

    std::ostringstream resp;
    resp << "HTTP/1.1 " << status_code << (status_code == 200 ? " OK\r\n" : " Not Found\r\n");
    resp << "Content-Type: " << content_type << "\r\n";
    resp << "Content-Length: " << resp_body.size() << "\r\n";
    resp << "Access-Control-Allow-Origin: *\r\n";
    resp << "Access-Control-Allow-Headers: *\r\n";
    resp << "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n";
    resp << "Connection: close\r\n\r\n";
    resp << resp_body;

    std::string resp_str = resp.str();
    send(sock, resp_str.data(), static_cast<int>(resp_str.size()), 0);
    closesocket(sock);
}

} // namespace byterescue
