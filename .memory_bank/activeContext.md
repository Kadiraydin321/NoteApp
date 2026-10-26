# Active Context - NoteApp

## Son Yapılan İşlemler (v1.2.8 - Dışa Aktarma & Yedeklemede Biyometrik Parmak İzi Doğrulaması)
- **Notları Dışarı Çıkarırken Parmak İzi Desteği:**
  - `BiometricPromptManager.kt` içerisine `Context.findFragmentActivity()` ve `canAuthenticate()` yardımcı fonksiyonları eklendi.
  - `SettingsScreen.kt`:
    - "Kasa Kilidi ile Yedekle (.notevault / .zip)" ve "Kasa Kilidi ile Yedeği Paylaş / Gönder" butonlarına tıklandığında cihazda parmak izi/biyometrik varsa doğrudan sistem `BiometricPrompt` diyaloğu tetikleniyor.
    - Parmak izi okutulduğunda kullanıcının kayıtlı Master PIN'i ile otomatik eşleşip dosya doğrudan kaydediliyor/paylaşılıyor.
    - Şifre giriş diyaloğu (`showExportPasswordDialog`) içerisine de tek dokunuşla çalışan "Parmak İzi ile Doğrula" butonu yerleştirildi.
  - `VaultPasswordDialogs.kt` (`ExportVaultDialog`):
    - Seçili notları veya tekil notu `.notevault` kasası olarak dışa aktarırken biyometrik destek eklendi.
    - Diyalog açıldığında Master PIN ve parmak izi tanımlıysa sistem biyometrik istemi doğrudan açılarak tek dokunuşla şifreleme ve dışa aktarma olanağı sağlandı.
    - Diyalog içine "Parmak İzi ile Onayla" butonu entegre edildi.
  - `NotesScreen.kt` ve `NoteDetailScreen.kt`:
    - Dışa aktarma diyaloglarına `masterPin` aktarımı bağlandı.
- **Sürüm Güncellemesi ve Yayın (v1.2.8 - Code 28):**
  - `app/build.gradle.kts`: `versionCode = 28`, `versionName = "1.2.8"`
  - `version.json`: `versionCode: 28`, `versionName: "1.2.8"`
  - `NoteApp-release.apk` kök dizine kopyalandı ve `hermes`, `main`, `apk` dallarına pushlandı.
