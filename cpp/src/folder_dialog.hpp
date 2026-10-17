// cpp/src/folder_dialog.hpp
#pragma once

#include <string>

namespace byterescue {

class FolderDialog {
public:
    // Opens native Windows Folder Browser Dialog and returns selected path, or empty string if cancelled
    static std::string open_folder_dialog(const std::string& title = "Taranacak Klasörü Seçin");
};

} // namespace byterescue
