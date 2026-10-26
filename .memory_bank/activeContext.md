# Active Context - NoteApp

## Son Yapılan İşlemler (v1.2.9 - Kilitli Not İçeriğinin Şifreli Görünmesi Hatasının Onarımı)
- **Kilitli Not Açıldığında `ENC_GCM_V1` Şifreli Metninin Görünmesi Hatasının Giderilmesi:**
  - **Kök Nedenler:**
    1. `NoteRepositoryImpl.kt` içindeki `getNoteById(id)` ve `getNoteByTitle(title)` metodlarında `if (note.isLocked && cryptoManager.isEncrypted(note.content))` koşulu bulunuyordu. Not kilidi kaldırıldığında veya durum geçişlerinde `isLocked` false iken veritabanında henüz şifreli kalan içerik deşifre edilmeden doğrudan kullanıcıya sunuluyordu.
    2. `NoteRepositoryImpl.insertNote` ve `updateNote` fonksiyonlarında, eğer içerik zaten şifrelenmişse (`ENC_GCM_V1:` ile başlıyorsa) idempotency kontrolü olmadığı için şifreli metin tekrar tekrar şifreleniyor (çift/üç katmanlı şifreleme) ve tek katman deşifre edilse bile içteki `ENC_GCM_V1:` metni editöre yansıyordu.
    3. `getAllNotes()`, `getActiveNotesList()` gibi yedekleme ve dışa aktarma fonksiyonları ham veritabanını okuyup Keystore şifresini çözmeden aktarıyordu.
  - **Çözümler:**
    1. `NoteCryptoManager.kt` içindeki `encrypt` fonksiyonu **idempotent** yapıldı; metin zaten `ENC_GCM_V1:` ile başlıyorsa asla tekrar şifrelenmez.
    2. `NoteCryptoManager.kt` içindeki `decrypt` fonksiyonu **özyinelemeli (recursive)** hale getirildi; daha önce birden fazla kez şifrelenmiş tüm katmanları tek seferde düz metne ulaşana kadar çözer.
    3. `NoteRepositoryImpl.kt` içindeki `getNoteById`, `getNoteByTitle`, `getNotesLinkingTo`, `getAllNotes`, `getActiveNotesList`, `getPinnedNotesList` metodlarında `note.isLocked` şartına bakılmaksızın içerik şifreliyse anında otomatik olarak deşifre edilir.
    4. Notun kilidi kaldırıldığında (`updateNote`), veritabanındaki içerik de otomatik olarak düz metne çevrilerek kaydedilir.
    5. `NoteDetailViewModel.kt` başlangıç yüklemesi (`init`) ve taslak geri yükleme (`restoreDraft`) aşamalarına deşifre koruması eklendi.
- **Sürüm Güncellemesi ve Yayın (v1.2.9 - Code 29):**
  - `app/build.gradle.kts`: `versionCode = 29`, `versionName = "1.2.9"`
  - `version.json`: `versionCode: 29`, `versionName: "1.2.9"`
  - `NoteApp-release.apk` kök dizine kopyalandı ve `main`, `apk` dallarına pushlandı.
- **Hermes Sürümü ve Klasörünün Temizlenmesi:**
  - `NoteApp` yerel deposundaki `hermes` dalı silindi (`git branch -D hermes`).
  - GitHub uzak deposundaki `origin/hermes` dalı tamamen kaldırıldı (`git push origin --delete hermes`).
  - Disk üzerindeki `/home/birin/Projeler/hermes_NoteApp` klasörü kalıcı olarak silindi.
  - Aktif geliştirme dalı doğrudan ana `main` dalına alındı.
