// cpp/src/folder_dialog.hpp
#pragma once

#include <string>

namespace byterescue {

class FolderDialog {
public:
    static std::string open_folder_dialog(const std::string& title = "Klasör Seç");
    static std::string open_file_dialog(const std::string& title = "Dosya Seç", const std::string& filter_desc = "Disk İmajları & Dosyalar (*.img;*.raw;*.*)");
};

} // namespace byterescue
