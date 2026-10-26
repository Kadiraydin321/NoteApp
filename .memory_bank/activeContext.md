# Active Context - NoteApp

## Son Yapılan İşlemler (v1.2.6 - Başlangıç İzinleri & Hatırlatıcı Bildirimleri Onarımı)
- **Uygulama Açılışında Tüm İzinlerin Toplu İstenmesi:**
  - `MainActivity.kt` içerisine `requestStartupPermissionsLauncher` (`ActivityResultContracts.RequestMultiplePermissions()`) eklendi.
  - Uygulama ilk açıldığında (`onCreate`) eksik olan izinler taranarak tek bir sistem diyaloğunda kullanıcıdan talep ediliyor:
    - `POST_NOTIFICATIONS` (Android 13+ / API 33+)
    - `RECORD_AUDIO` (Tüm Android sürümleri için sesli not kaydı)
    - `READ_MEDIA_IMAGES` & `READ_MEDIA_VISUAL_USER_SELECTED` (Android 13+ ve Android 14+ medya seçimi)
    - `READ_EXTERNAL_STORAGE` (Android 12 ve öncesi)
  - Not detay ekranında hatırlatıcı ekleme ve ses kaydı butonlarına da kullanıcı izinleri reddetmişse anlık fallback kontrolü eklendi.

- **Hatırlatıcı Bildirimlerinin Çıkmama Sorununun Giderilmesi:**
  - **Kök Neden:** Android 13+ üzerinde `POST_NOTIFICATIONS` runtime izni daha önce hiçbir yerde istenmediği için sistem tüm bildirimleri sessizce engelliyordu.
  - **Bildirim Kanalları:** `NotificationHelper.createNotificationChannels(context)` metodu static/companion hale getirilip `NoteApp.onCreate()` içerisine taşındı; böylece uygulama başlar başlamaz sistem bildirim kanalları tescil ediliyor.
  - **Exact Alarm İzinleri:** `AndroidManifest.xml` içerisine `USE_EXACT_ALARM` izni eklendi (Android 13+ sistem otomatik onayı için). `AndroidAlarmScheduler.kt` içine `SecurityException` koruması eklenerek yetki kısıtlaması olan üretici cihazlarda `setAndAllowWhileIdle` yedek mekanizması devreye sokuldu.
  - **Cihaz Yeniden Başlatma & Güncelleme Desteği:** `AlarmReceiver.kt` `@AndroidEntryPoint` ile donatılarak `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` ve `QUICKBOOT_POWERON` sinyalleri yakalandığında Room veritabanındaki aktif hatırlatıcıların otomatik olarak yeniden takvimlendirilmesi sağlandı.
  - **Öncelik ve Görünürlük:** Bildirimler `PRIORITY_MAX`, `CATEGORY_REMINDER`, `DEFAULT_ALL` (ses, titreşim, ışık) ve `VISIBILITY_PUBLIC` ile heads-up (açılır banner) formatına getirildi; küçük ikon NoteApp monochrome vektör ikonu ile güncellendi.
  - **Bildirim ID Tutarlılığı:** `NotificationHelper.getReminderNotificationId(noteId)` ile `ReminderActionReceiver` ve `NotificationHelper` arasındaki iptal/erteleme senkronizasyonu sağlandı.

- **Sürüm Güncellemesi ve Yayın (v1.2.6 - Code 26):**
  - `app/build.gradle.kts`: `versionCode = 26`, `versionName = "1.2.6"`
  - `version.json`: `versionCode: 26`, `versionName: "1.2.6"`
  - `./gradlew testDebugUnitTest` ve `./gradlew assembleRelease` başarıyla çalıştırıldı (31 unit test & 54 build task yeşil).
  - `NoteApp-release.apk` kök dizine kopyalandı ve `hermes`, `main`, `apk` dallarına pushlandı.
