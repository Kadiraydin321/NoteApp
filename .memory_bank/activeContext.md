# Active Context - NoteApp

## Son Yapılan İşlemler (UI Çakışmaları, Broken Feature Düzeltmeleri & v1.1.5 Release Yayını)
- **NoteDetailScreen Alt Yüzen Kapsül Dok Barı (Overlap Düzeltmesi):**
  - Sol taraftaki 5 adet aksiyon butonu (Checklist, Medya, Biçimlendir, Renk, Kategori) `Modifier.weight(1f, fill = false)` ile esnek genişliğe kavuşturuldu.
  - Sağ taraftaki istatistik rozeti (`Surface`), ekranda aynı anda checklist halkası veya kelime hedefi rozeti varken otomatik olarak kompakt formata (`"$wordCount k"`) geçecek şekilde uyarlandı.
  - İstatistik metinlerine `maxLines = 1` ve `overflow = TextOverflow.Ellipsis` uygulandı; 320dp - 411dp arası tüm ekran boyutlarında elemanların birbirinin üstüne binmesi ve taşma tamamen engellendi.
  - Medya araç kutusu butonları `Modifier.horizontalScroll(rememberScrollState())` ve `PaddingValues(10.dp, 6.dp)` ile sarılarak dar ekranlarda butonların sıkışıp kırılması önlendi.
  - Odaklanma Modu (Zen) butonu kompakt ekranlarda (`<400dp`) üst çubuktan kaldırılarak 3-nokta menüsüne taşındı; böylece üst başlık ve otomatik kayıt göstergesi ile aksiyon butonları arasındaki çakışma giderildi.
- **Find & Replace Toolbar Boşluk Düzeltmesi:**
  - `FindAndReplaceBar` açıkken içerik sütununun üstüne eklenen Spacer yüksekliği 56.dp'den 112.dp'ye çıkarılarak, değiştir (replace) paneli açıkken dahi not başlığı ve kategori etiketinin araç çubuğu altında kalması önlendi.
- **NotesScreen Çoklu Seçim Modu ve BatchActionsBottomBar Sadeleştirmesi:**
  - Çoklu seçim modunda `TopAppBar` üzerinde yer alan ve alttaki `BatchActionsBottomBar` ile birebir mükerrer olan `PushPin`, `Palette`, `CallMerge` butonları kaldırıldı.
  - Seçim üst çubuğunda sadece Kapat (Close), seçilen not adedi ve özet bilgisi (`maxLines = 1`, `TextOverflow.Ellipsis`) ve Tümünü Seç/Kaldır aksiyonu bırakıldı.
  - `BatchActionsBottomBar` butonlarına eşit ağırlık (`Modifier.weight(1f)`), daraltılmış yatay padding ve `maxLines = 1, softWrap = false, TextOverflow.Ellipsis` kazandırılarak butonların alt panelde üst üste binmesi engellendi.
- **Release APK & Auto-Update Hazırlığı (v1.1.5 - Code 16):**
  - `app/build.gradle.kts` versionCode 16 ve versionName 1.1.5 olarak güncellendi.
  - `./gradlew assembleRelease` çalıştırılarak yeni `NoteApp-release.apk` üretildi.
  - `version.json` güncellenerek GitHub `apk` dalına aktarıldı, böylece kurulu NoteApp uygulaması doğrudan uygulama içinden tek tıkla kendini güncelleyebilir.
- **Build & Test Doğrulaması:**
  - `./gradlew testDebugUnitTest assembleRelease` sıfır hata ile tamamlandı.
