# Active Context - NoteApp

## Son Yapılan İşlemler (v1.2.7 - Okuma/Sunum Modu Üst Çubuk Çakışma Düzeltmesi)
- **Okuma/Sunum Modunda Başlığın Butonların Üzerine Çıkması Sorununun Giderilmesi:**
  - `NoteDetailScreen.kt` içinde `isPresentationMode` açıkken üst çubukta (`TopAppBar`) `title` alanına `state.title` verilmesi, Material3'ün title alanını `(screenWidth - navIconWidth)` kadar genişletmesi sebebiyle sağdaki 5 adet okuma eylem butonunun (`~X dk` okuma süresi, `1.3x/1.5x/1.8x` satır aralığı, tek/çift sütun okuma butonu, `A-` ve `A+` font butonları) üzerine taşmasına ve üst üste binmesine sebep oluyordu.
  - Normal düzenleme modunda olduğu gibi `TopAppBar(title = { })` yapılarak üst çubuk tamamen kontrollere ve kapatma ikonuna tahsis edildi.
  - Not başlığı ise zaten içerik alanının en üstünde büyük ve zarif bir başlık stili (`headlineMedium`, `presentationFontSize + 8f`) ile görüntülendiğinden, arayüz sadeleştirildi ve buton çakışması kalıcı olarak çözüldü.
- **Sürüm Güncellemesi ve Yayın (v1.2.7 - Code 27):**
  - `app/build.gradle.kts`: `versionCode = 27`, `versionName = "1.2.7"`
  - `version.json`: `versionCode: 27`, `versionName: "1.2.7"`
  - `NoteApp-release.apk` kök dizine kopyalandı ve `hermes`, `main`, `apk` dallarına pushlandı.
