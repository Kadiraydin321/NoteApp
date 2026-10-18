// cpp/src/disk_detector.cpp
#include "disk_detector.hpp"
#include "carver_engine.hpp"

#include <sstream>
#include <filesystem>
#include <cstdlib>

#ifdef _WIN32
#include <windows.h>
#include <shlobj.h>
#endif

namespace fs = std::filesystem;

namespace byterescue {

std::string DiskDetector::get_default_destination() {
#ifdef _WIN32
    char user_profile[MAX_PATH] = { 0 };
    if (GetEnvironmentVariableA("USERPROFILE", user_profile, MAX_PATH) > 0) {
        fs::path dest = fs::path(user_profile) / "Desktop" / "ByteRescue_Kurtarilan";
        return dest.string();
    }
    return "C:\\ByteRescue_Kurtarilan";
#else
    const char* home = std::getenv("HOME");
    if (home) {
        fs::path dest = fs::path(home) / "Desktop" / "ByteRescue_Kurtarilan";
        return dest.string();
    }
    return "/tmp/ByteRescue_Kurtarilan";
#endif
}

std::vector<DriveItem> DiskDetector::list_drives() {
    std::vector<DriveItem> items;

#ifdef _WIN32
    // 1. Logical Drives (C:\, D:\, etc.)
    char drive_buffer[512] = { 0 };
    DWORD len = GetLogicalDriveStringsA(sizeof(drive_buffer) - 1, drive_buffer);
    if (len > 0) {
        char* p = drive_buffer;
        while (*p) {
            std::string root(p); // e.g. "C:\\"
            ULARGE_INTEGER free_bytes, total_bytes, total_free;
            bool ok = GetDiskFreeSpaceExA(root.c_str(), &free_bytes, &total_bytes, &total_free);

            char fs_name[64] = { 0 };
            GetVolumeInformationA(root.c_str(), NULL, 0, NULL, NULL, NULL, fs_name, sizeof(fs_name));

            UINT d_type = GetDriveTypeA(root.c_str());

            DriveItem drive;
            char letter = root[0];
            drive.id = std::string("drive_") + static_cast<char>(tolower(letter));
            drive.path = root;
            drive.fs_type = (fs_name[0] != '\0') ? std::string(fs_name) : "NTFS";

            if (d_type == DRIVE_REMOVABLE) {
                drive.type = "usb_drive";
                drive.name = std::string(1, letter) + ": USB Flash Bellek / SD Kart";
                drive.badge = drive.fs_type + " • USB Aygıt";
                drive.description = root + " takılı taşınabilir USB bellek veya telefon SD kartı";
            } else if (letter == 'C' || letter == 'c') {
                drive.type = "computer_drive";
                drive.name = "C: Sürücüsü (Sistem)";
                drive.badge = drive.fs_type + " • Sistem Diski";
                drive.description = "Windows ana sistem diski ve programlar";
            } else if (letter == 'D' || letter == 'd') {
                drive.type = "computer_drive";
                drive.name = "D: Sürücüsü (Veri Deposu)";
                drive.badge = drive.fs_type + " • Depolama";
                drive.description = "İkincil veri ve arşiv depolama diski";
            } else {
                drive.type = "usb_drive";
                drive.name = std::string(1, letter) + ": Harici / Yerel Disk";
                drive.badge = drive.fs_type + " • Harici USB / Disk";
                drive.description = root + " depolama birimi (" + drive.fs_type + ")";
            }

            if (ok) {
                drive.size_bytes = total_bytes.QuadPart;
                drive.size_human = CarverEngine::format_size(total_bytes.QuadPart);
                drive.free_bytes = free_bytes.QuadPart;
                drive.free_human = CarverEngine::format_size(free_bytes.QuadPart);
            } else {
                drive.size_human = "Bilinmiyor";
                drive.free_human = "Bilinmiyor";
            }
            drive.is_accessible = true;
            items.push_back(drive);

            p += strlen(p) + 1;
        }
    }

    // 1.1 Mobile & USB Phone Assistant Target
    DriveItem mobile_item;
    mobile_item.id = "mobile_usb_device";
    mobile_item.name = "📱 USB ile Bağlı Telefon / Android Tara";
    mobile_item.path = "MOBILE_ASSIST";
    mobile_item.type = "mobile_device";
    mobile_item.badge = "MTP / Android";
    mobile_item.size_human = "Mobil";
    mobile_item.description = "USB kablosu ile bağlı telefonun fotoğraflarını (DCIM), WhatsApp medyasını ve belgelerini tara";
    mobile_item.is_accessible = true;
    items.push_back(mobile_item);

    // 2. User Special Folders
    char user_profile[MAX_PATH] = { 0 };
    if (GetEnvironmentVariableA("USERPROFILE", user_profile, MAX_PATH) > 0) {
        fs::path u_path(user_profile);

        // Desktop
        fs::path desktop = u_path / "Desktop";
        if (fs::exists(desktop)) {
            DriveItem item;
            item.id = "user_desktop";
            item.name = "Masaüstü (Desktop)";
            item.path = desktop.string();
            item.type = "user_folder";
            item.badge = "Windows";
            item.size_human = "Klasör";
            item.description = "Kullanıcı masaüstü ve kısayol klasörü";
            item.is_accessible = true;
            items.push_back(item);
        }

        // Downloads
        fs::path downloads = u_path / "Downloads";
        if (fs::exists(downloads)) {
            DriveItem item;
            item.id = "user_downloads";
            item.name = "İndirilenler (Downloads)";
            item.path = downloads.string();
            item.type = "user_folder";
            item.badge = "Windows";
            item.size_human = "Klasör";
            item.description = "Tarayıcı ve internet indirmeleri dizini";
            item.is_accessible = true;
            items.push_back(item);
        }

        // Documents
        fs::path docs = u_path / "Documents";
        if (fs::exists(docs)) {
            DriveItem item;
            item.id = "user_documents";
            item.name = "Belgelerim (Documents)";
            item.path = docs.string();
            item.type = "user_folder";
            item.badge = "Windows";
            item.size_human = "Klasör";
            item.description = "Kişisel ve iş belgeleri klasörü";
            item.is_accessible = true;
            items.push_back(item);
        }
    }

    // 3. Recycle Bins ($Recycle.Bin)
    const char drive_letters[] = "CDEFGHIJKLMNOPQRSTUVWXYZ";
    for (size_t i = 0; i < sizeof(drive_letters) - 1; ++i) {
        std::string rec = std::string(1, drive_letters[i]) + ":\\$Recycle.Bin";
        std::error_code ec;
        if (fs::exists(rec, ec)) {
            DriveItem item;
            item.id = "recycle_" + std::string(1, tolower(drive_letters[i]));
            item.name = std::string("Geri Dönüşüm Kutusu (") + drive_letters[i] + ": $Recycle.Bin)";
            item.path = rec;
            item.type = "recycle_bin";
            item.badge = "Silinenler";
            item.size_human = "Çöp Kutusu";
            item.description = std::string(1, drive_letters[i]) + ": sürücüsünden silinmiş orijinal dosyalar";
            item.is_accessible = true;
            items.push_back(item);
        }
    }
#else
    // Linux / WSL Fallback
    DriveItem c_drive;
    c_drive.id = "drive_c";
    c_drive.name = "C: Sürücüsü (Windows)";
    c_drive.path = fs::exists("/mnt/c") ? "/mnt/c" : "/";
    c_drive.type = "computer_drive";
    c_drive.badge = "Dahili Sürücü";
    c_drive.size_human = "1.8 TB";
    c_drive.free_human = "500 GB";
    c_drive.description = "Windows ana sürücüsü";
    items.push_back(c_drive);
#endif

    return items;
}

std::string DiskDetector::to_json(const std::vector<DriveItem>& items) {
    std::ostringstream ss;
    ss << "[\n";
    for (size_t i = 0; i < items.size(); ++i) {
        const auto& it = items[i];
        ss << "  {\n";
        ss << "    \"id\": \"" << it.id << "\",\n";
        ss << "    \"name\": \"" << it.name << "\",\n";

        // Escape backslashes in path
        std::string p_esc;
        for (char c : it.path) {
            if (c == '\\') p_esc += "\\\\";
            else p_esc += c;
        }

        ss << "    \"path\": \"" << p_esc << "\",\n";
        ss << "    \"sizeBytes\": " << it.size_bytes << ",\n";
        ss << "    \"sizeHuman\": \"" << it.size_human << "\",\n";
        ss << "    \"freeHuman\": \"" << it.free_human << "\",\n";
        ss << "    \"type\": \"" << it.type << "\",\n";
        ss << "    \"fsType\": \"" << it.fs_type << "\",\n";
        ss << "    \"badge\": \"" << it.badge << "\",\n";
        ss << "    \"description\": \"" << it.description << "\",\n";
        ss << "    \"isAccessible\": " << (it.is_accessible ? "true" : "false") << "\n";
        ss << "  }" << (i + 1 < items.size() ? "," : "") << "\n";
    }
    ss << "]\n";
    return ss.str();
}

bool DiskDetector::is_same_drive(const std::string& path_a, const std::string& path_b) {
    if (path_a.size() < 2 || path_b.size() < 2) return false;
    if (path_a[1] == ':' && path_b[1] == ':') {
        return toupper(static_cast<unsigned char>(path_a[0])) == toupper(static_cast<unsigned char>(path_b[0]));
    }
    return false;
}

} // namespace byterescue
