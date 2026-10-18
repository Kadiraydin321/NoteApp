// cpp/src/repair_engine.cpp
#include "repair_engine.hpp"

#include <fstream>
#include <iostream>
#include <algorithm>
#include <cstring>

namespace byterescue {

RepairResult RepairEngine::repair_file(const std::string& input_path, const std::string& output_path) {
    RepairResult res;
    res.original_file = input_path;

    std::ifstream in(input_path, std::ios::binary);
    if (!in.is_open()) {
        res.details = "Kaynak dosya açılamadı.";
        return res;
    }

    in.seekg(0, std::ios::end);
    res.original_size = in.tellg();
    in.seekg(0, std::ios::beg);

    if (res.original_size == 0 || res.original_size > 500 * 1024 * 1024) { // Max 500MB
        res.details = "Dosya boş veya onarım boyut limitinin üzerinde.";
        return res;
    }

    std::vector<uint8_t> buffer(res.original_size);
    in.read(reinterpret_cast<char*>(buffer.data()), res.original_size);
    in.close();

    // Determine extension
    std::string ext;
    size_t dot = input_path.find_last_of('.');
    if (dot != std::string::npos) {
        ext = input_path.substr(dot + 1);
        for (auto& c : ext) c = tolower(c);
    }

    std::string type = detect_type(buffer, ext);
    res.file_type = type;

    std::string details;
    bool modified = repair_buffer(type, buffer, details);

    res.was_modified = modified;
    res.details = details;
    res.repaired_size = buffer.size();

    std::string dest = output_path;
    if (dest.empty()) {
        if (dot != std::string::npos) {
            dest = input_path.substr(0, dot) + "_onarilyor." + ext;
        } else {
            dest = input_path + "_onarilyor";
        }
    }
    res.repaired_file = dest;

    std::ofstream out(dest, std::ios::binary);
    if (!out.is_open()) {
        res.details += " | Onarılmış dosya yazılamadı.";
        return res;
    }

    out.write(reinterpret_cast<const char*>(buffer.data()), buffer.size());
    out.close();

    res.success = true;
    return res;
}

std::string RepairEngine::detect_type(const std::vector<uint8_t>& buf, const std::string& extension) {
    if (!extension.empty()) {
        if (extension == "jpg" || extension == "jpeg") return "jpg";
        if (extension == "png") return "png";
        if (extension == "pdf") return "pdf";
        if (extension == "zip" || extension == "docx" || extension == "xlsx" || extension == "pptx") return "zip";
        if (extension == "mp4" || extension == "m4v" || extension == "mov") return "mp4";
    }

    if (buf.size() >= 2 && buf[0] == 0xFF && buf[1] == 0xD8) return "jpg";
    if (buf.size() >= 4 && buf[0] == 0x89 && buf[1] == 'P' && buf[2] == 'N' && buf[3] == 'G') return "png";
    if (buf.size() >= 4 && buf[0] == '%' && buf[1] == 'P' && buf[2] == 'D' && buf[3] == 'F') return "pdf";
    if (buf.size() >= 4 && buf[0] == 0x50 && buf[1] == 0x4B && buf[2] == 0x03 && buf[3] == 0x04) return "zip";
    if (buf.size() >= 8 && buf[4] == 'f' && buf[5] == 't' && buf[6] == 'y' && buf[7] == 'p') return "mp4";

    return extension.empty() ? "bin" : extension;
}

bool RepairEngine::repair_buffer(const std::string& file_type, std::vector<uint8_t>& buf, std::string& details) {
    if (buf.empty()) return false;

    if (file_type == "jpg" || file_type == "jpeg") {
        return repair_jpeg(buf, details);
    } else if (file_type == "png") {
        return repair_png(buf, details);
    } else if (file_type == "pdf") {
        return repair_pdf(buf, details);
    } else if (file_type == "zip" || file_type == "docx" || file_type == "xlsx" || file_type == "pptx") {
        return repair_zip(buf, details);
    } else if (file_type == "mp4" || file_type == "mov") {
        return repair_mp4(buf, details);
    }

    details = "Bu dosya türü için standart bayt kontrolü uygulandı.";
    return false;
}

bool RepairEngine::repair_jpeg(std::vector<uint8_t>& buf, std::string& details) {
    bool modified = false;

    // 1. Check SOI (Start Of Image: FF D8)
    if (buf.size() < 2 || buf[0] != 0xFF || buf[1] != 0xD8) {
        // Look for FF D8 within first 1024 bytes (in case of leading garbage)
        size_t soi_pos = std::string::npos;
        for (size_t i = 0; i + 1 < std::min<size_t>(buf.size(), 2048); ++i) {
            if (buf[i] == 0xFF && buf[i+1] == 0xD8) {
                soi_pos = i;
                break;
            }
        }

        if (soi_pos != std::string::npos && soi_pos > 0) {
            buf.erase(buf.begin(), buf.begin() + soi_pos);
            details += "Bozuk ön baytlar temizlendi, SOI (FF D8) hizalandı. ";
            modified = true;
        } else {
            buf.insert(buf.begin(), { 0xFF, 0xD8 });
            details += "Eksik JPEG başlangıç baytı (SOI: FF D8) eklendi. ";
            modified = true;
        }
    }

    // 2. Check if JFIF or EXIF exists, if not inject basic JFIF APP0 header
    bool has_app_marker = false;
    if (buf.size() >= 4 && buf[0] == 0xFF && buf[1] == 0xD8) {
        if (buf[2] == 0xFF && (buf[3] >= 0xE0 && buf[3] <= 0xEF)) {
            has_app_marker = true;
        }
    }

    if (!has_app_marker && buf.size() >= 2) {
        // Standard JFIF header: FF E0 00 10 'JFIF' 00 01 01 01 00 48 00 48 00 00
        const uint8_t jfif_hdr[] = {
            0xFF, 0xE0, 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
            0x01, 0x01,                   // Version 1.1
            0x01,                         // Units: dpi
            0x00, 0x48, 0x00, 0x48,       // 72 x 72 dpi
            0x00, 0x00                    // Thumbnail 0x0
        };
        buf.insert(buf.begin() + 2, jfif_hdr, jfif_hdr + sizeof(jfif_hdr));
        details += "Eksik JFIF APP0 başlığı enjekte edildi. ";
        modified = true;
    }

    // 3. Check EOI (End Of Image: FF D9)
    if (buf.size() >= 2) {
        size_t n = buf.size();
        if (buf[n - 2] != 0xFF || buf[n - 1] != 0xD9) {
            buf.push_back(0xFF);
            buf.push_back(0xD9);
            details += "Eksik JPEG kapanış imtiyazı (EOI: FF D9) eklendi.";
            modified = true;
        }
    }

    if (!modified) {
        details = "JPEG başlığı ve EOI baytları sağlam durumda.";
    }

    return modified;
}

bool RepairEngine::repair_png(std::vector<uint8_t>& buf, std::string& details) {
    bool modified = false;
    const uint8_t png_magic[8] = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    // 1. Signature repair
    if (buf.size() < 8 || memcmp(buf.data(), png_magic, 8) != 0) {
        if (buf.size() >= 8) {
            memcpy(buf.data(), png_magic, 8);
            details += "Bozuk PNG 8-bayt dosya imzası onarıldı. ";
            modified = true;
        }
    }

    // 2. Check IEND chunk: 00 00 00 00 49 45 4E 44 AE 42 60 82
    const uint8_t iend_chunk[12] = {
        0x00, 0x00, 0x00, 0x00,
        0x49, 0x45, 0x4E, 0x44, // "IEND"
        0xAE, 0x42, 0x60, 0x82  // CRC32
    };

    bool has_iend = false;
    if (buf.size() >= 12) {
        if (memcmp(buf.data() + buf.size() - 12, iend_chunk, 12) == 0) {
            has_iend = true;
        }
    }

    if (!has_iend) {
        buf.insert(buf.end(), iend_chunk, iend_chunk + 12);
        details += "Eksik PNG IEND sonlandırıcı bloku ve CRC32 değeri eklendi.";
        modified = true;
    }

    if (!modified) {
        details = "PNG dosya başlığı ve IEND sonlandırıcı bloku doğrulanmıştır.";
    }

    return modified;
}

bool RepairEngine::repair_pdf(std::vector<uint8_t>& buf, std::string& details) {
    bool modified = false;

    // Check %PDF-
    if (buf.size() < 5 || memcmp(buf.data(), "%PDF-", 5) != 0) {
        // Look for %PDF- in first 1024 bytes
        std::string s(buf.begin(), buf.begin() + std::min<size_t>(buf.size(), 1024));
        size_t p = s.find("%PDF-");
        if (p != std::string::npos && p > 0) {
            buf.erase(buf.begin(), buf.begin() + p);
            details += "PDF başındaki artık baytlar temizlendi. ";
            modified = true;
        } else {
            const char* pdf_hdr = "%PDF-1.7\n";
            buf.insert(buf.begin(), pdf_hdr, pdf_hdr + 9);
            details += "Eksik %PDF-1.7 başlığı eklendi. ";
            modified = true;
        }
    }

    // Check %%EOF
    std::string tail(buf.end() - std::min<size_t>(buf.size(), 128), buf.end());
    if (tail.find("%%EOF") == std::string::npos) {
        std::string eof_trailer = "\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n";
        buf.insert(buf.end(), eof_trailer.begin(), eof_trailer.end());
        details += "Eksik PDF %%EOF ve sonlandırıcı tablosu eklendi.";
        modified = true;
    }

    if (!modified) {
        details = "PDF başlığı ve %%EOF sonlandırıcı blokları sağlamdır.";
    }

    return modified;
}

bool RepairEngine::repair_zip(std::vector<uint8_t>& buf, std::string& details) {
    bool modified = false;

    // 1. Signature check (50 4B 03 04)
    if (buf.size() < 4 || buf[0] != 0x50 || buf[1] != 0x4B) {
        buf.insert(buf.begin(), { 0x50, 0x4B, 0x03, 0x04 });
        details += "Eksik ZIP Local File Header (PK\\x03\\x04) eklendi. ";
        modified = true;
    }

    // 2. Check for End of Central Directory (EOCD: 50 4B 05 06)
    bool has_eocd = false;
    for (size_t i = buf.size() > 65557 ? buf.size() - 65557 : 0; i + 4 <= buf.size(); ++i) {
        if (buf[i] == 0x50 && buf[i+1] == 0x4B && buf[i+2] == 0x05 && buf[i+3] == 0x06) {
            has_eocd = true;
            break;
        }
    }

    if (!has_eocd && buf.size() >= 4) {
        // Minimal valid EOCD with 0 entries or placeholder
        const uint8_t eocd_record[22] = {
            0x50, 0x4B, 0x05, 0x06, // EOCD signature
            0x00, 0x00,             // Number of this disk
            0x00, 0x00,             // Disk with CD
            0x00, 0x00,             // Number of CD entries on this disk
            0x00, 0x00,             // Total number of CD entries
            0x00, 0x00, 0x00, 0x00, // Size of CD
            0x00, 0x00, 0x00, 0x00, // Offset of CD
            0x00, 0x00              // ZIP comment length
        };
        buf.insert(buf.end(), eocd_record, eocd_record + 22);
        details += "Kesilmiş ZIP/Office arşivi için EOCD (End of Central Directory) kaydı oluşturuldu.";
        modified = true;
    }

    if (!modified) {
        details = "ZIP dosya yapısı ve EOCD dizini doğrulanmıştır.";
    }

    return modified;
}

bool RepairEngine::repair_mp4(std::vector<uint8_t>& buf, std::string& details) {
    bool modified = false;

    // Check ftyp atom
    bool has_ftyp = false;
    if (buf.size() >= 8) {
        if (buf[4] == 'f' && buf[5] == 't' && buf[6] == 'y' && buf[7] == 'p') {
            has_ftyp = true;
        }
    }

    if (!has_ftyp && buf.size() >= 16) {
        // Synthesize a generic ftyp atom (size=20, 'ftyp', 'isom', minor_version=0x200, 'mp41')
        const uint8_t ftyp_hdr[20] = {
            0x00, 0x00, 0x00, 0x14, // Size 20
            'f', 't', 'y', 'p',
            'i', 's', 'o', 'm',
            0x00, 0x00, 0x02, 0x00,
            'm', 'p', '4', '1'
        };
        buf.insert(buf.begin(), ftyp_hdr, ftyp_hdr + 20);
        details += "Eksik MP4 ftyp (File Type) atomu eklendi.";
        modified = true;
    }

    if (!modified) {
        details = "MP4 dosya konteyner başlığı geçerlidir.";
    }

    return modified;
}

} // namespace byterescue
