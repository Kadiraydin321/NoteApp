// cpp/src/main.cpp
#include "http_server.hpp"

#include <iostream>
#include <thread>
#include <chrono>
#include <atomic>

#ifdef _WIN32
#include <windows.h>
#include <shellapi.h>
#endif

static std::atomic<bool> g_app_running{ true };

#ifdef _WIN32
BOOL WINAPI ConsoleHandler(DWORD signal) {
    if (signal == CTRL_C_EVENT || signal == CTRL_CLOSE_EVENT || signal == CTRL_SHUTDOWN_EVENT) {
        g_app_running.store(false);
        return TRUE;
    }
    return FALSE;
}
#endif

int main() {
#ifdef _WIN32
    SetConsoleOutputCP(CP_UTF8);
    SetConsoleCP(CP_UTF8);
    SetConsoleCtrlHandler(ConsoleHandler, TRUE);
#endif

    std::cout << "========================================================\n";
    std::cout << "  ⚡ BYTERESCUE // VERİ KURTARICI\n";
    std::cout << "  Geliştirici : Kadir\n";
    std::cout << "  Altyapı     : Native C++ x64 Yüksek Başarımlı Motor\n";
    std::cout << "========================================================\n\n";

    std::cout << "[*] Yerel web arayüz sunucusu başlatılıyor...\n";

    byterescue::HttpServer server(45678);
    if (!server.start()) {
        std::cerr << "[-] Hata: Sunucu başlatılamadı. Port meşgul olabilir.\n";
        return 1;
    }

    std::string url = server.get_url();
    std::cout << "[+] Sunucu başarıyla çalışıyor: " << url << "\n";
    std::cout << "[*] Varsayılan internet tarayıcınız açılıyor...\n\n";

#ifdef _WIN32
    // Automatically launch user default web browser
    ShellExecuteA(NULL, "open", url.c_str(), NULL, NULL, SW_SHOWNORMAL);
#endif

    std::cout << "--------------------------------------------------------\n";
    std::cout << "  Tarayıcınızda arayüz açıldı. Tüm işlemleri oradan\n";
    std::cout << "  yönetebilirsiniz. Çıkmak için Ctrl+C tuşlayabilir\n";
    std::cout << "  veya bu pencereyi kapatabilirsiniz.\n";
    std::cout << "--------------------------------------------------------\n";

    while (g_app_running.load() && server.is_running()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(300));
    }

    std::cout << "\n[*] ByteRescue kapatılıyor...\n";
    server.stop();
    std::cout << "[+] Güvenle sonlandırıldı.\n";
    return 0;
}
