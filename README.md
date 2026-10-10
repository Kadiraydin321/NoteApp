# ⚡ ByteRescue // Veri Kurtarıcı

**Geliştirici:** Kadir  
**Sürüm:** 1.2.0  
**Platform:** Windows 10/11 & Linux  

Modern, yüksek başarımlı ve siber temalı masaüstü veri kurtarma (Raw Sector Carver & Deep Computer Scanner) uygulaması.

---

## 💾 Windows Kurulumu ve Başka Bilgisayara Taşıma (.EXE)

Uygulama başka bilgisayarlarda hiçbir Node.js, kütüphane veya geliştirme ortamı gerektirmeden doğrudan çalışacak şekilde derlenmiştir:

1. **Windows Kurulum Sihirbazı (Installer):**
   - Dosya: `release/ByteRescue Setup 1.2.0.exe` (Ayrıca Windows Masaüstünüzde: `C:\Users\Birin\Desktop\ByteRescue\ByteRescue Setup 1.2.0.exe`)
   - Masaüstü kısayolu, Başlat menüsü simgesi ve tam kaldırma (uninstaller) desteği ile tek tıkla kurulur.
2. **Taşınabilir Sürüm (Portable .exe):**
   - Dosya: `release/ByteRescue 1.2.0.exe` (Ayrıca Windows Masaüstünüzde: `C:\Users\Birin\Desktop\ByteRescue\ByteRescue 1.2.0.exe`)
   - USB belleğe veya harici diske atıp herhangi bir Windows bilgisayarda çift tıklayarak kurulumsuz çalıştırabilirsiniz.
3. **Geliştirici / Konsol Başlatıcı:**
   - Proje ana dizinindeki `Baslat_ByteRescue.bat` dosyasına çift tıklayarak da çalıştırabilirsiniz.

---

## 🚀 Öne Çıkan Özellikler

1. **Bilgisayar & Disk Taraması (Deep Drive Scanner):**
   - Yerel sürücüleri (C:\, D:\), Masaüstü, İndirilenler, Belgeler ve **Geri Dönüşüm Kutusu (Recycle Bin)** gibi hedefleri tek tıkla doğrudan tarayabilme.
   - Harici USB sürücüler, SD kartlar ve ham disk imajları (.dd, .raw, .img, .bin).

2. **Ham Sektör ve Bayt Taraması (Carver Engine):**
   - Dosya sistemi (NTFS/FAT/ext4) indeksleri ve metaverileri silinmiş dahi olsa doğrudan ham sektörleri bayt bayt analiz eder.
   - 64 KB kayan örtüşme tamponu (Sliding Window Overlap) ile sektör sınırlarında parça kaybını önler.

3. **Geniş Dosya Formatı & Özel İmza Desteği:**
   - **Görseller:** PNG, JPG/JPEG, GIF (87a/89a), WEBP, BMP
   - **Belgeler & Ofis:** PDF, DOCX, XLSX, PPTX, TXT
   - **Medya (Ses & Video):** MP4/MOV, MKV/WebM, MP3, WAV, FLAC
   - **Arşivler:** ZIP, 7Z, RAR (v4/v5), TAR, GZ
   - **Özel İmza (Custom Signatures):** Kendi belirlediğiniz Hex Magic Byte dizilerini tanımlayıp tarayabilme.

4. **Siber Ses Efektleri & Radar Görselleştirici:**
   - Web Audio API ile sıfır harici bağımlılıkla üretilen siber tarama, tespit ve kurtarma ses efektleri (sağ üstten açıp kapatılabilir).
   - Tarama anında dönen neon radar tarayıcısı ve anlık sektör akışı.

5. **Anlık Önizleme & İnteraktif Hex İnceleyici (Hex Inspector):**
   - Bulunan fotoğrafları kurtarmadan önce minik resim (thumbnail) ve modal önizleme ile görme.
   - Hex/ASCII canlı arama filtreli sektör dökümü inceleyicisi.

6. **Güvenli Kurtarma Koruması (Safe Recovery Guard):**
   - Kurtarılan dosyaların kaynak sürücünün üzerine yazılmasını denetleyerek veri kaybını önler.

7. **Tek Tıkla Sıfır Riskli Test İmajı (Synthetic Test Disk):**
   - Harici disk olmadan denemek için 10 MB'lık örnek silinmiş dosyalar barındıran sanal disk oluşturucu.

---

## 🛠️ Mimari & Teknoloji

- **Arayüz:** Svelte 5, Tailwind CSS, Lucide Icons, Web Audio API.
- **Masaüstü Motoru:** Electron 33, Node.js raw file descriptor streams (`fs.readSync`, `Buffer.allocUnsafe`), SHA-256 doğrulayıcı.
- **Paketleyici:** Electron Builder 24 (NSIS + Portable Windows x64).

---

## 🖥️ Geliştirici Ortamında Çalıştırma ve Derleme

```bash
# Bağımlılıkları yükleme
npm install

# Arayüz derleme ve başlatma
npm start

# Windows Kurulum Sihirbazı ve Portable EXE Paketleme
npm run dist:win

# Test Motorunu Çalıştırma
npm test
```
