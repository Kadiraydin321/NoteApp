use std::path::Path;

#[cfg(windows)]
pub fn create_shortcut(
    target_path: &Path,
    shortcut_path: &Path,
    description: &str,
    icon_path: Option<&Path>,
) -> Result<(), String> {
    use windows::core::{Interface, HSTRING};
    use windows::Win32::System::Com::{
        CoCreateInstance, CoInitializeEx, CoUninitialize, CLSCTX_INPROC_SERVER,
        COINIT_APARTMENTTHREADED, IPersistFile,
    };
    use windows::Win32::UI::Shell::{IShellLinkW, ShellLink};

    unsafe {
        let hr = CoInitializeEx(None, COINIT_APARTMENTTHREADED);

        let link: IShellLinkW = CoCreateInstance(&ShellLink, None, CLSCTX_INPROC_SERVER)
            .map_err(|e| format!("COM ShellLink oluşturulamadı: {}", e))?;

        link.SetPath(&HSTRING::from(target_path.as_os_str()))
            .map_err(|e| format!("SetPath hatası: {}", e))?;

        link.SetDescription(&HSTRING::from(description))
            .map_err(|e| format!("SetDescription hatası: {}", e))?;

        if let Some(working_dir) = target_path.parent() {
            link.SetWorkingDirectory(&HSTRING::from(working_dir.as_os_str()))
                .ok();
        }

        if let Some(icon) = icon_path {
            link.SetIconLocation(&HSTRING::from(icon.as_os_str()), 0)
                .ok();
        }

        let persist: IPersistFile = link
            .cast()
            .map_err(|e| format!("IPersistFile cast hatası: {}", e))?;

        persist
            .Save(&HSTRING::from(shortcut_path.as_os_str()), true)
            .map_err(|e| format!("Kısayol kaydedilemedi: {}", e))?;

        if hr.is_ok() {
            CoUninitialize();
        }
        Ok(())
    }
}

#[cfg(not(windows))]
pub fn create_shortcut(
    _target_path: &Path,
    _shortcut_path: &Path,
    _description: &str,
    _icon_path: Option<&Path>,
) -> Result<(), String> {
    Ok(())
}

pub fn remove_shortcut(shortcut_path: &Path) {
    if shortcut_path.exists() {
        std::fs::remove_file(shortcut_path).ok();
    }
}
