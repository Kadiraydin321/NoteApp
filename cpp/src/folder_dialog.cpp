// cpp/src/folder_dialog.cpp
#include "folder_dialog.hpp"

#ifdef _WIN32
#include <windows.h>
#include <shlobj.h>
#include <commdlg.h>
#endif

namespace byterescue {

std::string FolderDialog::open_folder_dialog(const std::string& title) {
#ifdef _WIN32
    HWND hwndOwner = GetForegroundWindow();
    if (!hwndOwner) {
        hwndOwner = GetDesktopWindow();
    }

    HRESULT hr = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);

    // Modern IFileOpenDialog
    IFileOpenDialog* pFileOpen = nullptr;
    hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_ALL, IID_IFileOpenDialog, reinterpret_cast<void**>(&pFileOpen));
    if (SUCCEEDED(hr)) {
        FILEOPENDIALOGOPTIONS opt;
        if (SUCCEEDED(pFileOpen->GetOptions(&opt))) {
            pFileOpen->SetOptions(opt | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
        }

        std::wstring wtitle(title.begin(), title.end());
        pFileOpen->SetTitle(wtitle.c_str());

        // Bring dialog to foreground window
        if (SUCCEEDED(pFileOpen->Show(hwndOwner))) {
            IShellItem* pItem = nullptr;
            if (SUCCEEDED(pFileOpen->GetResult(&pItem))) {
                PWSTR pszFilePath = nullptr;
                if (SUCCEEDED(pItem->GetDisplayName(SIGDN_FILESYSPATH, &pszFilePath))) {
                    char path_utf8[MAX_PATH * 2] = { 0 };
                    WideCharToMultiByte(CP_UTF8, 0, pszFilePath, -1, path_utf8, sizeof(path_utf8), NULL, NULL);
                    CoTaskMemFree(pszFilePath);
                    pItem->Release();
                    pFileOpen->Release();
                    CoUninitialize();
                    return std::string(path_utf8);
                }
                pItem->Release();
            }
        }
        pFileOpen->Release();
    }

    // Fallback: SHBrowseForFolderA
    BROWSEINFOA bi = { 0 };
    bi.hwndOwner = hwndOwner;
    bi.lpszTitle = title.c_str();
    bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
    LPITEMIDLIST pidl = SHBrowseForFolderA(&bi);
    if (pidl != 0) {
        char path[MAX_PATH] = { 0 };
        if (SHGetPathFromIDListA(pidl, path)) {
            CoTaskMemFree(pidl);
            CoUninitialize();
            return std::string(path);
        }
        CoTaskMemFree(pidl);
    }

    CoUninitialize();
    return "";
#else
    return "/home/birin";
#endif
}

std::string FolderDialog::open_file_dialog(const std::string& title, const std::string& filter_desc) {
#ifdef _WIN32
    HWND hwndOwner = GetForegroundWindow();
    if (!hwndOwner) {
        hwndOwner = GetDesktopWindow();
    }

    OPENFILENAMEA ofn = { 0 };
    char szFile[MAX_PATH] = { 0 };

    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = hwndOwner;
    ofn.lpstrFile = szFile;
    ofn.nMaxFile = sizeof(szFile);
    ofn.lpstrFilter = "Tüm Desteklenen Dosyalar (*.img;*.raw;*.*)\0*.img;*.raw;*.*\0Tüm Dosyalar (*.*)\0*.*\0";
    ofn.nFilterIndex = 1;
    ofn.lpstrTitle = title.c_str();
    ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST | OFN_NOCHANGEDIR;

    if (GetOpenFileNameA(&ofn) == TRUE) {
        return std::string(ofn.lpstrFile);
    }
    return "";
#else
    return "";
#endif
}

} // namespace byterescue
