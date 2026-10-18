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

int run_byterescue() {
#ifdef _WIN32
    // 1. Single-Instance Guard: prevent duplicate processes & port clashes
    HANDLE hMutex = CreateMutexA(NULL, TRUE, "Global\\ByteRescue_SingleInstance_App");
    if (GetLastError() == ERROR_ALREADY_EXISTS) {
        // Application is already active! Just pop open the browser and exit cleanly.
        ShellExecuteA(NULL, "open", "http://127.0.0.1:45678", NULL, NULL, SW_SHOWNORMAL);
        return 0;
    }

    SetConsoleOutputCP(CP_UTF8);
    SetConsoleCP(CP_UTF8);
    SetConsoleCtrlHandler(ConsoleHandler, TRUE);
#endif

    byterescue::HttpServer server(45678);
    if (!server.start()) {
        std::cerr << "[-] Hata: Sunucu başlatılamadı. Port meşgul olabilir.\n";
        return 1;
    }

    std::string url = server.get_url();

#ifdef _WIN32
    // Automatically launch user default web browser
    ShellExecuteA(NULL, "open", url.c_str(), NULL, NULL, SW_SHOWNORMAL);
#endif

    while (g_app_running.load() && server.is_running()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(300));
    }

    server.stop();

#ifdef _WIN32
    if (hMutex) {
        ReleaseMutex(hMutex);
        CloseHandle(hMutex);
    }
#endif

    return 0;
}

#ifdef _WIN32
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    return run_byterescue();
}
#endif

int main() {
    return run_byterescue();
}
