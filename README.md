# ⚡ ByteRescue // Ham Sektör Veri Kurtarma Laboratuvarı

Modern, yüksek başarımlı ve siber temalı masaüstü veri kurtarma (Raw Sector Carver) uygulaması.

---

## 🚀 Öne Çıkan Özellikler

1. **Ham Sektör ve Bayt Taraması (Carver Engine):**
   - Dosya sistemi (FAT/NTFS/ext4) metaverileri silinmiş veya bozulmuş olsa dahi, doğrudan ham disk sektörlerini ve bloklarını bayt bayt tarar.
   - Dosya Başlık (Header) ve Bitiş (Footer / Magic Bytes) imzalarını kullanarak kayıp dosyaları kurtarır.
   - Parça sınırlarında kayıp yaşanmaması için **64 KB Kayan Örtüşme Tamponu (Sliding Window Overlap)** kullanır.

2. **Geniş Dosya Formatı & Özel İmza Desteği:**
   - **Görseller:** PNG, JPG/JPEG, GIF (87a/89a), WEBP, BMP
   - **Belgeler & Ofis:** PDF, DOCX, XLSX, PPTX, TXT
   - **Medya (Ses & Video):** MP4/MOV, MKV/WebM, MP3, WAV, FLAC
   - **Arşivler:** ZIP, 7Z, RAR (v4/v5), TAR, GZ
   - **Özel İmza (Custom Signatures):** Kullanıcının kendi belirlediği Onaltılık (Hex) Magic Byte dizilerini tanımlayıp tarayabilmesi.

3. **Güvenli Kurtarma Koruması (Safe Recovery Guard):**
   - Kurtarılan dosyaların, taranan sürücü veya bölümün üzerine kaydedilmesini denetler.
   - Silinen verilerin üzerine kazara yazılarak kalıcı veri kaybına yol açılmasını önleyen otomatik uyarı ve koruma kalkanı.

4. **Anlık Önizleme & Ham Hex İnceleyici (Hex Inspector):**
   - Bulunan görselleri (JPG, PNG, GIF, WEBP) ve metin/PDF içeriklerini kurtarmadan önce anında görsel olarak önizleyin.
   - Herhangi bir dosyanın disk üzerindeki ham 16-baytlık sektör dökümünü ve ASCII karşılığını (Hex Dump) interaktif olarak inceleyin.

5. **Gerçek Zamanlı Siber Dashboard:**
   - Anlık okuma hızı (MB/s), taranan boyut, ilerleme yüzdesi ve tahmini kalan süre (ETA).
   - Taramayı Duraklatma (Pause), Devam Ettirme (Resume) ve Durdurma (Stop) kontrolleri.
   - Canlı siber aktivite akışı (Sector log stream).

6. **Tek Tıkla Sıfır Riskli Test İmajı (Synthetic Test Disk):**
   - Harici bir USB bellek veya disk bağlamaya gerek kalmadan, uygulama içerisindeki **"Test İmajı Oluştur"** butonuna basarak silinmiş örnek PNG, JPG, PDF, ZIP ve WEBP dosyaları içeren 10 MB'lık sanal bir disk oluşturup hemen test edebilirsiniz!

---

## 🛠️ Mimari & Teknoloji Tercihi

- **Arayüz (Frontend):** Svelte 5 + Tailwind CSS (Cyber Dark Theme, JetBrains Mono tipografi, neon parlamalar).
- **Masaüstü Altyapısı:** Electron (Linux Wayland / X11 tam uyumlu, ek derleyici veya root izni gerektirmez).
- **Kurtarma Motoru:** Node.js akışlı ham dosya tanımlayıcıları (`fs.readSync`, `Buffer.allocUnsafe`), yüksek verimli I/O ve SHA-256 bütünlük doğrulayıcı.

---

## 🖥️ Çalıştırma

Uygulama dizininde:

```bash
# Doğrudan başlatıcı ile:
./start.sh

# Veya NPM komutu ile:
npm start
```

### Motor Testlerini Çalıştırma:

```bash
npm run test-engine
```
Test suite sentetik diski oluşturur, tarar, 5/5 dosyayı tespit eder, hex dökümünü çıkarır ve SHA-256 doğrulamasını yapar.
