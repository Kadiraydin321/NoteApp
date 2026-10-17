# Novel to MoonReader EPUB Downloader

Bu branch yalnızca optimize edilmiş ve güvenlik açıklarından arındırılmış **NovelToMoonReader.apk** dosyasını barındırır.

### APK Özellikleri:
- **Boyut:** ~1.9 MB (R8 ProGuard küçültme ve kaynak ağaç budaması uygulandı).
- **Format:** Sadece Moon+ Reader uyumlu `.epub` indirir.
- **Güvenlik Sertleştirmeleri:**
  - `allowBackup="false"`: Özel veri veya geçmiş yedekleme sızıntılarına karşı kapatıldı.
  - `usesCleartextTraffic="false"`: HTTP engellendi, zorunlu TLS/HTTPS.
  - `FileProvider`: Dışa aktarılan dosya yolları sadece uygulama dahili depolama klasörüne sınırlandı.
  - `Girdi Temizliği (Sanitization)`: Path traversal ve komut enjeksiyonuna karşı bölüm ve arama girdileri filtrelenir.
  - `XML Escaping`: EPUB oluşturulurken XML entity enjeksiyonu engellendi.
