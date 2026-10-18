// cpp/src/ntfs_scanner.cpp
#include "ntfs_scanner.hpp"

#include <iostream>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <unordered_map>
#include <cstring>

#ifdef _WIN32
#include <windows.h>
#endif

namespace byterescue {

#pragma pack(push, 1)
struct NtfsBootSector {
    uint8_t jump[3];
    char oem_id[8];
    uint16_t bytes_per_sector;
    uint8_t sectors_per_cluster;
    uint16_t reserved_sectors;
    uint8_t zeros1[3];
    uint16_t unused1;
    uint8_t media_descriptor;
    uint16_t zeros2;
    uint16_t sectors_per_track;
    uint16_t number_of_heads;
    uint32_t hidden_sectors;
    uint32_t unused2;
    uint32_t unused3;
    uint64_t total_sectors;
    uint64_t mft_lcn;
    uint64_t mft_mirr_lcn;
    int8_t clusters_per_mft_record;
    uint8_t unused4[3];
    int8_t clusters_per_index_buffer;
    uint8_t unused5[3];
    uint64_t volume_serial_number;
    uint32_t checksum;
};
#pragma pack(pop)

static void apply_mft_fixup(uint8_t* record, size_t record_size, uint16_t seq_offset, uint16_t seq_size) {
    if (seq_offset >= record_size || seq_size < 2) return;
    const uint16_t* usa = reinterpret_cast<const uint16_t*>(record + seq_offset);
    uint16_t seq_num = usa[0];
    size_t num_sectors = record_size / 512;
    for (size_t i = 1; i < seq_size && i <= num_sectors; ++i) {
        uint16_t* sector_end = reinterpret_cast<uint16_t*>(record + (i * 512) - 2);
        if (*sector_end == seq_num) {
            *sector_end = usa[i];
        }
    }
}

static std::vector<MftRun> parse_run_list(const uint8_t* data, size_t max_len) {
    std::vector<MftRun> runs;
    size_t offset = 0;
    int64_t current_lcn = 0;

    while (offset < max_len && data[offset] != 0) {
        uint8_t header = data[offset++];
        uint8_t len_bytes = header & 0x0F;
        uint8_t off_bytes = (header >> 4) & 0x0F;

        if (len_bytes == 0 || offset + len_bytes + off_bytes > max_len) break;

        uint64_t cluster_count = 0;
        for (int i = 0; i < len_bytes; ++i) {
            cluster_count |= (static_cast<uint64_t>(data[offset++]) << (i * 8));
        }

        int64_t lcn_offset = 0;
        if (off_bytes > 0) {
            for (int i = 0; i < off_bytes; ++i) {
                lcn_offset |= (static_cast<int64_t>(data[offset++]) << (i * 8));
            }
            if (data[offset - 1] & 0x80) {
                for (int i = off_bytes; i < 8; ++i) {
                    lcn_offset |= (static_cast<int64_t>(0xFF) << (i * 8));
                }
            }
            current_lcn += lcn_offset;
        }

        MftRun r;
        r.lcn = (off_bytes > 0) ? static_cast<uint64_t>(current_lcn) : 0;
        r.cluster_count = cluster_count;
        runs.push_back(r);
    }
    return runs;
}

bool NtfsScanner::is_volume_accessible(const std::string& drive_letter) {
#ifdef _WIN32
    std::string drive = drive_letter;
    if (drive.size() >= 2 && drive[1] == ':') drive = drive.substr(0, 2);
    std::string vol_path = "\\\\.\\" + drive;
    HANDLE h = CreateFileA(vol_path.c_str(), GENERIC_READ,
        FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, 0, NULL);
    if (h != INVALID_HANDLE_VALUE) {
        CloseHandle(h);
        return true;
    }
#endif
    return false;
}

std::string NtfsScanner::format_filetime(uint64_t ft_val) {
    if (ft_val == 0) return "-";
#ifdef _WIN32
    FILETIME ft;
    ft.dwLowDateTime = static_cast<DWORD>(ft_val & 0xFFFFFFFF);
    ft.dwHighDateTime = static_cast<DWORD>(ft_val >> 32);
    FILETIME local_ft;
    FileTimeToLocalFileTime(&ft, &local_ft);
    SYSTEMTIME st;
    if (FileTimeToSystemTime(&local_ft, &st)) {
        char buf[64];
        snprintf(buf, sizeof(buf), "%04d-%02d-%02d %02d:%02d:%02d",
            st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
        return std::string(buf);
    }
#endif
    return "-";
}

std::string NtfsScanner::format_dos_attributes(uint32_t attrs) {
    std::string s;
    if (attrs & 0x0001) s += "Salt Okunur, ";
    if (attrs & 0x0002) s += "Gizli, ";
    if (attrs & 0x0004) s += "Sistem, ";
    if (attrs & 0x0020) s += "Arşiv, ";
    if (attrs & 0x0800) s += "Sıkıştırılmış, ";
    if (attrs & 0x4000) s += "Şifrelenmiş, ";
    if (!s.empty()) {
        s.pop_back();
        s.pop_back();
    } else {
        s = "Normal";
    }
    return s;
}

std::vector<NtfsDeletedFile> NtfsScanner::scan_deleted_files(
    const std::string& drive_letter,
    const std::string& target_scope,
    std::atomic<bool>& should_stop,
    std::function<void(uint64_t, uint64_t)> progress_callback)
{
    std::vector<NtfsDeletedFile> deleted_files;
#ifdef _WIN32
    std::string drive = drive_letter;
    if (drive.size() >= 2 && drive[1] == ':') drive = drive.substr(0, 2);
    std::string vol_path = "\\\\.\\" + drive;

    HANDLE hVol = CreateFileA(vol_path.c_str(), GENERIC_READ,
        FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, 0, NULL);
    if (hVol == INVALID_HANDLE_VALUE) {
        std::cerr << "[-] NtfsScanner: Hacim acilamadi: " << vol_path << " (Yonetici yetkisi gerekebilir)\n";
        return deleted_files;
    }

    // Read Boot Sector
    NtfsBootSector boot{};
    DWORD bytesRead = 0;
    if (!ReadFile(hVol, &boot, sizeof(boot), &bytesRead, NULL) || bytesRead < sizeof(boot)) {
        CloseHandle(hVol);
        return deleted_files;
    }

    if (strncmp(boot.oem_id, "NTFS    ", 8) != 0) {
        CloseHandle(hVol);
        return deleted_files;
    }

    uint32_t bytes_per_sector = boot.bytes_per_sector;
    uint32_t sectors_per_cluster = boot.sectors_per_cluster;
    uint32_t bytes_per_cluster = bytes_per_sector * sectors_per_cluster;
    uint64_t mft_lcn = boot.mft_lcn;

    uint32_t record_size = 1024;
    if (boot.clusters_per_mft_record > 0) {
        record_size = boot.clusters_per_mft_record * bytes_per_cluster;
    } else {
        record_size = 1 << (-boot.clusters_per_mft_record);
    }
    if (record_size < 512 || record_size > 4096) record_size = 1024;

    // Read MFT Record 0 ($MFT descriptor)
    uint64_t mft_byte_offset = mft_lcn * bytes_per_cluster;
    LARGE_INTEGER li;
    li.QuadPart = static_cast<LONGLONG>(mft_byte_offset);
    SetFilePointerEx(hVol, li, NULL, FILE_BEGIN);

    std::vector<uint8_t> rec0(record_size);
    if (!ReadFile(hVol, rec0.data(), record_size, &bytesRead, NULL) || bytesRead < record_size) {
        CloseHandle(hVol);
        return deleted_files;
    }

    uint16_t seq_off0 = *reinterpret_cast<uint16_t*>(&rec0[0x04]);
    uint16_t seq_sz0 = *reinterpret_cast<uint16_t*>(&rec0[0x06]);
    apply_mft_fixup(rec0.data(), record_size, seq_off0, seq_sz0);

    // Find $DATA attribute of Record 0 to get $MFT cluster runs
    uint16_t first_attr0 = *reinterpret_cast<uint16_t*>(&rec0[0x14]);
    uint16_t cur_attr0 = first_attr0;
    std::vector<MftRun> mft_runs;

    while (cur_attr0 + 8 <= record_size) {
        uint32_t attr_type = *reinterpret_cast<uint32_t*>(&rec0[cur_attr0]);
        if (attr_type == 0xFFFFFFFF) break;
        uint32_t attr_len = *reinterpret_cast<uint32_t*>(&rec0[cur_attr0 + 4]);
        if (attr_len == 0 || cur_attr0 + attr_len > record_size) break;

        uint8_t non_res = rec0[cur_attr0 + 8];
        if (attr_type == 0x80 && non_res == 1) { // Non-resident $DATA
            uint16_t run_offset = *reinterpret_cast<uint16_t*>(&rec0[cur_attr0 + 0x20]);
            if (cur_attr0 + run_offset < record_size) {
                mft_runs = parse_run_list(&rec0[cur_attr0 + run_offset], record_size - (cur_attr0 + run_offset));
                break;
            }
        }
        cur_attr0 += attr_len;
    }

    if (mft_runs.empty()) {
        // Fallback: assume contiguous $MFT starting at mft_lcn
        MftRun r;
        r.lcn = mft_lcn;
        r.cluster_count = 10000;
        mft_runs.push_back(r);
    }

    // Normalize scope path for filtering
    std::string norm_scope = target_scope;
    std::transform(norm_scope.begin(), norm_scope.end(), norm_scope.begin(), ::tolower);
    std::replace(norm_scope.begin(), norm_scope.end(), '/', '\\');
    while (!norm_scope.empty() && norm_scope.back() == '\\') norm_scope.pop_back();
    bool is_root_scope = (norm_scope.empty() || (norm_scope.size() <= 3 && norm_scope.find(':') != std::string::npos));

    // Map: directory record number -> { parent record number, folder name }
    std::unordered_map<uint64_t, std::pair<uint64_t, std::string>> dir_tree;
    dir_tree[5] = { 5, drive + "\\" }; // Record 5 is root directory

    // Vector of raw deleted candidates before path resolution
    std::vector<NtfsDeletedFile> raw_deleted;

    uint64_t total_records_scanned = 0;
    const size_t BATCH_RECORDS = 64; // Read 64KB per chunk
    std::vector<uint8_t> batch_buf(BATCH_RECORDS * record_size);

    for (const auto& run : mft_runs) {
        if (should_stop.load()) break;
        if (run.lcn == 0) continue; // sparse

        uint64_t run_bytes = run.cluster_count * bytes_per_cluster;
        uint64_t run_records = run_bytes / record_size;
        uint64_t run_start_offset = run.lcn * bytes_per_cluster;

        for (uint64_t r_idx = 0; r_idx < run_records && !should_stop.load(); r_idx += BATCH_RECORDS) {
            uint64_t to_read_records = std::min<uint64_t>(BATCH_RECORDS, run_records - r_idx);
            uint64_t chunk_bytes = to_read_records * record_size;

            LARGE_INTEGER chunk_pos;
            chunk_pos.QuadPart = static_cast<LONGLONG>(run_start_offset + r_idx * record_size);
            SetFilePointerEx(hVol, chunk_pos, NULL, FILE_BEGIN);

            DWORD read_actual = 0;
            if (!ReadFile(hVol, batch_buf.data(), static_cast<DWORD>(chunk_bytes), &read_actual, NULL) || read_actual < record_size) {
                break;
            }

            uint64_t actual_records = read_actual / record_size;

            for (uint64_t b = 0; b < actual_records && !should_stop.load(); ++b) {
                uint64_t current_rec_num = total_records_scanned + b;
                uint8_t* rec = &batch_buf[b * record_size];

                // Verify "FILE" magic
                if (rec[0] != 'F' || rec[1] != 'I' || rec[2] != 'L' || rec[3] != 'E') {
                    continue;
                }

                uint16_t flags = *reinterpret_cast<uint16_t*>(&rec[0x16]);
                bool in_use = (flags & 0x0001) != 0;
                bool is_dir = (flags & 0x0002) != 0;

                uint16_t seq_off = *reinterpret_cast<uint16_t*>(&rec[0x04]);
                uint16_t seq_sz = *reinterpret_cast<uint16_t*>(&rec[0x06]);
                apply_mft_fixup(rec, record_size, seq_off, seq_sz);

                uint16_t cur_attr = *reinterpret_cast<uint16_t*>(&rec[0x14]);

                NtfsDeletedFile item{};
                item.record_number = current_rec_num;
                item.drive_letter = drive;
                item.is_directory = is_dir;

                std::string win32_name;
                std::string dos_name;

                while (cur_attr + 8 <= record_size) {
                    uint32_t a_type = *reinterpret_cast<uint32_t*>(&rec[cur_attr]);
                    if (a_type == 0xFFFFFFFF) break;
                    uint32_t a_len = *reinterpret_cast<uint32_t*>(&rec[cur_attr + 4]);
                    if (a_len == 0 || cur_attr + a_len > record_size) break;

                    uint8_t non_res = rec[cur_attr + 8];

                    if (a_type == 0x10 && non_res == 0) { // $STANDARD_INFORMATION
                        uint16_t val_off = *reinterpret_cast<uint16_t*>(&rec[cur_attr + 20]);
                        if (cur_attr + val_off + 48 <= record_size) {
                            const uint8_t* val = &rec[cur_attr + val_off];
                            item.created_time = *reinterpret_cast<const uint64_t*>(val + 0);
                            item.modified_time = *reinterpret_cast<const uint64_t*>(val + 8);
                            item.accessed_time = *reinterpret_cast<const uint64_t*>(val + 24);
                            item.dos_attributes = *reinterpret_cast<const uint32_t*>(val + 32);
                        }
                    } else if (a_type == 0x30 && non_res == 0) { // $FILE_NAME
                        uint16_t val_off = *reinterpret_cast<uint16_t*>(&rec[cur_attr + 20]);
                        if (cur_attr + val_off + 66 <= record_size) {
                            const uint8_t* fn = &rec[cur_attr + val_off];
                            uint64_t p_ref = *reinterpret_cast<const uint64_t*>(fn + 0) & 0x0000FFFFFFFFFFFFULL;
                            uint64_t real_sz = *reinterpret_cast<const uint64_t*>(fn + 48);
                            uint64_t alloc_sz = *reinterpret_cast<const uint64_t*>(fn + 40);
                            uint8_t fn_len = fn[64];
                            uint8_t fn_type = fn[65];

                            if (cur_attr + val_off + 66 + fn_len * 2 <= record_size) {
                                std::string fname_utf8;
                                const uint8_t* u16 = fn + 66;
                                for (size_t c = 0; c < fn_len; ++c) {
                                    uint16_t ch = static_cast<uint16_t>(u16[c * 2]) | (static_cast<uint16_t>(u16[c * 2 + 1]) << 8);
                                    if (ch == 0) break;
                                    if (ch < 0x80) fname_utf8.push_back(static_cast<char>(ch));
                                    else if (ch < 0x800) {
                                        fname_utf8.push_back(static_cast<char>(0xC0 | (ch >> 6)));
                                        fname_utf8.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                                    } else {
                                        fname_utf8.push_back(static_cast<char>(0xE0 | (ch >> 12)));
                                        fname_utf8.push_back(static_cast<char>(0x80 | ((ch >> 6) & 0x3F)));
                                        fname_utf8.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
                                    }
                                }

                                if (fn_type == 0x01 || fn_type == 0x03) {
                                    win32_name = fname_utf8;
                                } else if (fn_type == 0x02) {
                                    dos_name = fname_utf8;
                                } else if (win32_name.empty()) {
                                    win32_name = fname_utf8;
                                }

                                item.parent_record_number = p_ref;
                                if (real_sz > 0) item.file_size = real_sz;
                                if (alloc_sz > 0) item.alloc_size = alloc_sz;
                            }
                        }
                    } else if (a_type == 0x80) { // $DATA
                        if (non_res == 0) { // Resident Data
                            uint32_t val_len = *reinterpret_cast<uint32_t*>(&rec[cur_attr + 16]);
                            uint16_t val_off = *reinterpret_cast<uint16_t*>(&rec[cur_attr + 20]);
                            if (cur_attr + val_off + val_len <= record_size) {
                                item.is_resident = true;
                                item.resident_data.assign(&rec[cur_attr + val_off], &rec[cur_attr + val_off + val_len]);
                                item.file_size = val_len;
                            }
                        } else { // Non-Resident Data
                            item.is_resident = false;
                            uint16_t run_off = *reinterpret_cast<uint16_t*>(&rec[cur_attr + 0x20]);
                            uint64_t real_sz = *reinterpret_cast<uint64_t*>(&rec[cur_attr + 0x30]);
                            if (real_sz > 0) item.file_size = real_sz;
                            if (cur_attr + run_off < record_size) {
                                item.run_list = parse_run_list(&rec[cur_attr + run_off], record_size - (cur_attr + run_off));
                            }
                        }
                    }
                    cur_attr += a_len;
                }

                item.filename = !win32_name.empty() ? win32_name : dos_name;

                if (in_use && is_dir && !item.filename.empty()) {
                    dir_tree[current_rec_num] = { item.parent_record_number, item.filename };
                }

                // If record is DELETED and not an empty metadata record
                if (!in_use && !item.filename.empty() && item.filename[0] != '$') {
                    item.created_time_str = format_filetime(item.created_time);
                    item.modified_time_str = format_filetime(item.modified_time);
                    item.accessed_time_str = format_filetime(item.accessed_time);
                    item.attributes_str = format_dos_attributes(item.dos_attributes);

                    size_t dot = item.filename.find_last_of('.');
                    if (dot != std::string::npos) {
                        item.extension = item.filename.substr(dot + 1);
                        std::transform(item.extension.begin(), item.extension.end(), item.extension.begin(), ::tolower);
                    } else {
                        item.extension = "bin";
                    }

                    if (item.extension == "png" || item.extension == "jpg" || item.extension == "jpeg" || item.extension == "gif" || item.extension == "webp" || item.extension == "bmp") item.category = "images";
                    else if (item.extension == "mp4" || item.extension == "mp3" || item.extension == "wav" || item.extension == "mkv" || item.extension == "avi") item.category = "media";
                    else if (item.extension == "zip" || item.extension == "rar" || item.extension == "7z" || item.extension == "tar" || item.extension == "gz") item.category = "archives";
                    else if (item.extension == "txt" || item.extension == "pdf" || item.extension == "docx" || item.extension == "xlsx" || item.extension == "pptx" || item.extension == "csv" || item.extension == "json" || item.extension == "md") item.category = "documents";
                    else item.category = "other";

                    raw_deleted.push_back(item);
                }
            }

            total_records_scanned += actual_records;
            if (progress_callback) {
                progress_callback(total_records_scanned, raw_deleted.size());
            }
        }
    }

    CloseHandle(hVol);

    // Resolve full path for each deleted file using dir_tree
    for (auto& item : raw_deleted) {
        std::vector<std::string> path_segments;
        uint64_t curr_parent = item.parent_record_number;
        int depth = 0;

        while (curr_parent != 0 && curr_parent != 5 && depth++ < 30) {
            auto it = dir_tree.find(curr_parent);
            if (it == dir_tree.end()) break;
            path_segments.push_back(it->second.second);
            curr_parent = it->second.first;
        }

        std::string full_path = drive + "\\";
        for (auto rit = path_segments.rbegin(); rit != path_segments.rend(); ++rit) {
            full_path += *rit + "\\";
        }
        item.original_path = full_path + item.filename;

        // Scope filter
        if (!is_root_scope) {
            std::string check_path = item.original_path;
            std::transform(check_path.begin(), check_path.end(), check_path.begin(), ::tolower);
            if (check_path.find(norm_scope) == std::string::npos) {
                continue;
            }
        }

        deleted_files.push_back(item);
    }
#endif
    return deleted_files;
}

bool NtfsScanner::recover_deleted_file(const NtfsDeletedFile& file, const std::string& dest_path) {
    if (file.is_resident && !file.resident_data.empty()) {
        std::ofstream out(dest_path, std::ios::binary);
        if (!out.is_open()) return false;
        out.write(reinterpret_cast<const char*>(file.resident_data.data()), file.resident_data.size());
        return true;
    }

#ifdef _WIN32
    if (!file.run_list.empty()) {
        std::string vol_path = "\\\\.\\" + file.drive_letter;
        HANDLE hVol = CreateFileA(vol_path.c_str(), GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, 0, NULL);
        if (hVol == INVALID_HANDLE_VALUE) return false;

        NtfsBootSector boot{};
        DWORD bytesRead = 0;
        if (!ReadFile(hVol, &boot, sizeof(boot), &bytesRead, NULL) || bytesRead < sizeof(boot)) {
            CloseHandle(hVol);
            return false;
        }

        uint32_t cluster_size = boot.bytes_per_sector * boot.sectors_per_cluster;
        if (cluster_size == 0) cluster_size = 4096;

        std::ofstream out(dest_path, std::ios::binary);
        if (!out.is_open()) {
            CloseHandle(hVol);
            return false;
        }

        uint64_t remaining = file.file_size;
        std::vector<char> buf(64 * 1024);

        for (const auto& run : file.run_list) {
            if (remaining == 0) break;
            if (run.lcn == 0) {
                // Sparse run: fill with zeros
                uint64_t sparse_bytes = std::min<uint64_t>(remaining, run.cluster_count * cluster_size);
                std::vector<char> zero_buf(static_cast<size_t>(std::min<uint64_t>(sparse_bytes, 64 * 1024)), 0);
                while (sparse_bytes > 0) {
                    size_t step = std::min<size_t>(sparse_bytes, zero_buf.size());
                    out.write(zero_buf.data(), step);
                    sparse_bytes -= step;
                    remaining -= step;
                }
            } else {
                LARGE_INTEGER pos;
                pos.QuadPart = static_cast<LONGLONG>(run.lcn * cluster_size);
                SetFilePointerEx(hVol, pos, NULL, FILE_BEGIN);

                uint64_t run_bytes = std::min<uint64_t>(remaining, run.cluster_count * cluster_size);
                while (run_bytes > 0) {
                    DWORD step = static_cast<DWORD>(std::min<uint64_t>(run_bytes, buf.size()));
                    DWORD actual = 0;
                    if (!ReadFile(hVol, buf.data(), step, &actual, NULL) || actual == 0) {
                        break;
                    }
                    out.write(buf.data(), actual);
                    run_bytes -= actual;
                    remaining -= actual;
                }
            }
        }

        CloseHandle(hVol);
        return true;
    }
#endif
    return false;
}

} // namespace byterescue
