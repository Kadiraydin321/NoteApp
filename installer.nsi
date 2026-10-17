; installer.nsi - ByteRescue Windows Kurulum Paketi
; Geliştirici: Kadir

!include "MUI2.nsh"
!include "FileFunc.nsh"

; Genel Yapılandırma
Name "ByteRescue - Veri Kurtarıcı"
OutFile "ByteRescue_Kurulum.exe"
InstallDir "$PROGRAMFILES64\ByteRescue"
InstallDirRegKey HKLM "Software\ByteRescue" "InstallDir"
RequestExecutionLevel admin

; Arayüz Ayarları
!define MUI_ABORTWARNING
!define MUI_ICON "build\icon.ico"
!define MUI_UNICON "build\icon.ico"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH

; Kurulum Sayfaları
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\ByteRescue.exe"
!define MUI_FINISHPAGE_RUN_TEXT "ByteRescue Veri Kurtarıcıyı Başlat"
!insertmacro MUI_PAGE_FINISH

; Kaldırma Sayfaları
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

; Diller
!insertmacro MUI_LANGUAGE "Turkish"

Section "Ana Program" SecMain
    SetOutPath "$INSTDIR"
    File "ByteRescue.exe"
    File "build\icon.ico"

    ; Kısayollar
    CreateDirectory "$SMPROGRAMS\ByteRescue"
    CreateShortcut "$SMPROGRAMS\ByteRescue\ByteRescue.lnk" "$INSTDIR\ByteRescue.exe" "" "$INSTDIR\icon.ico"
    CreateShortcut "$SMPROGRAMS\ByteRescue\Kaldır.lnk" "$INSTDIR\Uninstall.exe"
    CreateShortcut "$DESKTOP\ByteRescue.lnk" "$INSTDIR\ByteRescue.exe" "" "$INSTDIR\icon.ico"

    ; Kayıt Defteri Bilgileri
    WriteRegStr HKLM "Software\ByteRescue" "InstallDir" "$INSTDIR"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ByteRescue" "DisplayName" "ByteRescue - Veri Kurtarıcı (Kadir)"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ByteRescue" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ByteRescue" "DisplayIcon" '"$INSTDIR\icon.ico"'
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ByteRescue" "Publisher" "Kadir"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ByteRescue" "DisplayVersion" "1.3.0"

    ; Kaldırıcı Oluştur
    WriteUninstaller "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Uninstall"
    Delete "$DESKTOP\ByteRescue.lnk"
    Delete "$SMPROGRAMS\ByteRescue\ByteRescue.lnk"
    Delete "$SMPROGRAMS\ByteRescue\Kaldır.lnk"
    RMDir "$SMPROGRAMS\ByteRescue"

    Delete "$INSTDIR\ByteRescue.exe"
    Delete "$INSTDIR\icon.ico"
    Delete "$INSTDIR\Uninstall.exe"
    RMDir "$INSTDIR"

    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ByteRescue"
    DeleteRegKey HKLM "Software\ByteRescue"
SectionEnd
