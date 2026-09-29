package com.example.noteapp.data.sample

import android.content.Context
import android.graphics.*
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleDataLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NoteRepository
) {

    /**
     * Veritabanında hiç not yoksa veya force=true ise 25 adet zengin özellikli örnek not yükler.
     */
    suspend fun populateIfEmpty(force: Boolean = false) = withContext(Dispatchers.IO) {
        val existingNotes = repository.getAllNotes()
        if (existingNotes.isNotEmpty() && !force) {
            return@withContext
        }

        // 1. Kategorileri Oluştur
        val catBusiness = Category(name = "💼 İş & Projeler", color = 0xFF1E88E5.toInt())
        val catIdeas = Category(name = "💡 Fikirler & Tasarım", color = 0xFFFB8C00.toInt())
        val catShopping = Category(name = "🛒 Alışveriş & Günlük", color = 0xFF43A047.toInt())
        val catPersonal = Category(name = "🔒 Özel & Kişisel", color = 0xFFE53935.toInt())
        val catBooks = Category(name = "📚 Kitap & Gelişim", color = 0xFF8E24AA.toInt())
        val catTravel = Category(name = "🎯 Hedefler & Seyahat", color = 0xFF00ACC1.toInt())

        val catList = listOf(catBusiness, catIdeas, catShopping, catPersonal, catBooks, catTravel)
        repository.insertCategories(catList)
        val savedCats = repository.getAllCategoriesList()
        val cBusinessId = savedCats.getOrNull(0)?.id
        val cIdeasId = savedCats.getOrNull(1)?.id
        val cShoppingId = savedCats.getOrNull(2)?.id
        val cPersonalId = savedCats.getOrNull(3)?.id
        val cBooksId = savedCats.getOrNull(4)?.id
        val cTravelId = savedCats.getOrNull(5)?.id

        // 2. Örnek Çizim ve Görsel Dosyaları Oluştur (Dosya sistemine kaydet)
        val drawingArchitecturePath = createSampleDrawingFile(
            fileName = "DRAW_sample_architecture.png",
            title = "Sistem Mimarisi Çizimi"
        )
        val drawingSketchPath = createSampleDrawingFile(
            fileName = "DRAW_sample_sketch.png",
            title = "Oda Yerleşim Taslağı"
        )
        val imagePalettePath = createSampleImageFile(
            fileName = "IMG_sample_palette.jpg"
        )

        val now = System.currentTimeMillis()
        val oneHour = 3600000L
        val oneDay = 86400000L

        // 3. 25 Adet Gerçekçi ve Zengin Not Tanımla
        val sampleNotes = listOf(
            // 1. Not (Sabitli, İş, Markdown zengin formatı)
            Note(
                title = "2026 Q4 Strateji ve Yol Haritası",
                content = """
                    # 🚀 Çeyrek Dönem Hedefleri
                    
                    Bu çeyrekte odaklanacağımız **üç temel stratejik alan** bulunmaktadır:
                    
                    - **Performans Optimizasyonu:** Uygulama açılış süresini %40 hızlandırmak.
                    - **Kullanıcı Deneyimi:** *Karanlık mod* ve *dinamik renk geçişlerini* mükemmelleştirmek.
                    - ==Veri Güvenliği:== Biyometrik koruma ve yerel şifrelemeyi güçlendirmek.
                    
                    > "Mükemmellik bir eylem değil, bir alışkanlıktır."
                    
                    `val architecture = "CleanArchitecture + MVI"`
                """.trimIndent(),
                timestamp = now,
                color = 0xFFBBDEFB.toInt(), // Mavi pastel
                categoryId = cBusinessId,
                isPinned = true
            ),

            // 2. Not (Alışveriş, Kontrol Kutuları)
            Note(
                title = "Haftalık Süpermarket Listesi",
                content = """
                    - [x] Organik Yumurta (10'lu)
                    - [x] Tam Yağlı Süt (2 Litre)
                    - [ ] Filtre Kahve Çekirdeği
                    - [ ] Taze Fesleğen & Biberiye
                    - [ ] Zeytinyağı (Soğuk Sıkım)
                    - [ ] Avokado (Yumuşak)
                    - [ ] Kaşar Peyniri & Tulum
                    - [ ] Tam Buğday Ekmeği
                """.trimIndent(),
                timestamp = now - (oneHour * 2),
                color = 0xFFFFF9C4.toInt(), // Sarı pastel
                categoryId = cShoppingId
            ),

            // 3. Not (Çizim Ekli Not)
            Note(
                title = "Mobil Uygulama Sistem Mimarisi",
                content = """
                    Yeni sürüm için hazırladığım katmanlı mimari diyagramı ve akış şeması ektedir.
                    
                    ### Katmanlar:
                    - **Presentation:** Jetpack Compose UI & StateFlow
                    - **Domain:** UseCase modelleri ve soyut repolar
                    - **Data:** Room Database & SQLite yerel depolama
                    
                    *Detaylı bağlantı şeması aşağıdaki çizimdedir:*
                """.trimIndent(),
                timestamp = now - (oneHour * 5),
                color = 0xFFB2DFDB.toInt(), // Teal pastel
                categoryId = cBusinessId,
                isPinned = true,
                attachments = listOf(drawingArchitecturePath)
            ),

            // 4. Not (Kilitli Not - Biyometrik Koruma)
            Note(
                title = "Banka & Yatırım Giriş Bilgileri",
                content = """
                    🔒 **Gizli Finansal Notlar**
                    
                    - Ana Vadeli Hesap: Garanti BBVA TR12 3456 ...
                    - Borsa Portföy Şifresi: P@ssw0rd#2026
                    - Donanım Cüzdanı Yedek Kelimeleri: Güvenli çelik kasada saklanıyor.
                    
                    ==Önemli:== Bu şifreleri kimseyle paylaşmayın ve ekran görüntüsü almayın.
                """.trimIndent(),
                timestamp = now - (oneHour * 8),
                color = 0xFFFFCDD2.toInt(), // Kırmızı pastel
                categoryId = cPersonalId,
                isLocked = true
            ),

            // 5. Not (Seyahat Planı, Sabitli)
            Note(
                title = "Ege Kıyı Turu & Gezi Rehberi",
                content = """
                    # 🏖️ 7 Günlük Rota
                    
                    1. **Gün 1:** Ayvalık & Cunda Adası (Girit mutfağı, taş kahve)
                    2. **Gün 2:** Foça & Eski Foça sokakları
                    3. **Gün 3:** Urla Bağ Yolu & Sanat Sokağı
                    4. **Gün 4:** Alaçatı & Ilıca Plajı
                    5. **Gün 5:** Sığacık Kalesi & Teos Antik Kenti
                    6. **Gün 6:** Akyaka & Azmak Nehri tekne turu
                    7. **Gün 7:** Datça Eski Şehir & Palamutbükü
                    
                    *Rezervasyonlar tamamlandı, müzekart yanımıza alınacak.*
                """.trimIndent(),
                timestamp = now - (oneHour * 12),
                color = 0xFFC5CAE9.toInt(), // İndigo pastel
                categoryId = cTravelId,
                isPinned = true
            ),

            // 6. Not (Kitap Alıntıları)
            Note(
                title = "Okuduğum Kitaplardan Çarpıcı Alıntılar",
                content = """
                    > "Kendine ait bir odası ve biraz parası olmalı kadının, eğer roman yazacaksa." — Virginia Woolf
                    
                    > "İnsan en çok kaçtığı şeyden asla kurtulamıyor." — Stefan Zweig
                    
                    > "Sorgulanmayan bir hayat, yaşanmaya değer değildir." — Sokrates
                    
                    Kitap Adı: **Düşünceler ve Yankılar**
                    Bitirme Tarihi: *Eylül 2026*
                """.trimIndent(),
                timestamp = now - (oneHour * 20),
                color = 0xFFE1BEE7.toInt(), // Lavanta pastel
                categoryId = cBooksId
            ),

            // 7. Not (Oda Yerleşim Çizimi Ekli)
            Note(
                title = "Çalışma Odası Yerleşimi ve Ölçüler",
                content = """
                    Masa ve kitaplık için tasarlanan yeni oda planı aşağıda çizilmiştir.
                    
                    - Çalışma Masası: 160x80 cm (Pencere kenarına gelecek)
                    - Ergonomik Koltuk mesafesi: 90 cm boşluk
                    - Kitaplık: Duvar boyu raylı sistem
                """.trimIndent(),
                timestamp = now - oneDay,
                color = 0xFFFFE0B2.toInt(), // Şeftali pastel
                categoryId = cIdeasId,
                attachments = listOf(drawingSketchPath)
            ),

            // 8. Not (Spor ve Diyet Programı)
            Note(
                title = "Haftalık Antrenman ve Fitness Planı",
                content = """
                    ### Pazartesi: Göğüs & Arka Kol
                    - Bench Press: 4 set x 10 tekrar
                    - Incline Dumbbell Press: 3 set x 12 tekrar
                    - Triceps Pushdown: 4 set x 15 tekrar
                    
                    ### Çarşamba: Sırt & Ön Kol
                    - Barfiks: 4 set tükenişe kadar
                    - Barbell Row: 4 set x 10 tekrar
                    - Biceps Curl: 3 set x 12 tekrar
                    
                    ==Günlük 3 Litre Su İçmeyi Unutma!==
                """.trimIndent(),
                timestamp = now - (oneDay + oneHour),
                color = 0xFFC8E6C9.toInt(), // Yeşil pastel
                categoryId = cPersonalId
            ),

            // 9. Not (Görsel Ekli Not)
            Note(
                title = "UI/UX Tasarım Renk Paleti İncelemesi",
                content = """
                    Yeni arayüz tasarımımız için seçilen Material 3 renk tonları ve kontrast oranları görselde test edilmiştir.
                    
                    - **Primary:** Deep Iris Blue `#1E88E5`
                    - **Secondary:** Warm Tangerine `#FB8C00`
                    - **Surface:** Crisp Minimalist White
                """.trimIndent(),
                timestamp = now - (oneDay * 2),
                color = 0xFFCFD8DC.toInt(),
                categoryId = cIdeasId,
                attachments = listOf(imagePalettePath)
            ),

            // 10. Not (Kilitli Kişisel Günlük)
            Note(
                title = "Kişisel Günlük & Kararlar",
                content = """
                    🔒 **29 Eylül 2026**
                    
                    Bugün hayatımla ilgili önemli bir dönüm noktasına geldim. Yazılım projelerine daha fazla odaklanmaya, gereksiz dikkat dağıtıcıları hayatımdan çıkarmaya karar verdim.
                    
                    Kendime verdiğim sözler:
                    - Her gün en az 30 sayfa kitap oku.
                    - Saat 23:00'ten sonra ekran kullanımını bırak.
                    - Yeni teknolojileri korkmadan dene.
                """.trimIndent(),
                timestamp = now - (oneDay * 2 + oneHour * 3),
                color = 0xFF1E1E1E.toInt(), // Gece siyahı tema
                categoryId = cPersonalId,
                isLocked = true
            ),

            // 11. Not (İş Fikirleri & Girişim)
            Note(
                title = "Yeni Startup & Yazılım Fikirleri",
                content = """
                    - ~~Fikir 1: Yapay zeka ile yemek tarifi önerici~~ (Pazar çok doymuş)
                    - ~~Fikir 2: Dijital kartvizit platformu~~ (Rakipler güçlü)
                    - **Fikir 3: Minimalist ve Çevrimdışı Not & Çizim Asistanı** (==Seçilen Fikir!==)
                    
                    Öne çıkan avantajlar:
                    1. Sıfır sunucu maliyeti, %100 yerel gizlilik
                    2. Hızlı ana ekran widget aksiyonları
                    3. Gelişmiş el çizimi tuvali
                """.trimIndent(),
                timestamp = now - (oneDay * 3),
                color = 0xFFFFF9C4.toInt(),
                categoryId = cIdeasId
            ),

            // 12. Not (Yemek Tarifi)
            Note(
                title = "İtalyan Usulü Pizza Hamuru Tarifi",
                content = """
                    # 🍕 48 Saat Fermente Pizza Hamuru
                    
                    ### Malzemeler:
                    - [ ] 500 gr Tip 00 İtalyan Unu
                    - [ ] 325 ml Soğuk Su (%65 Hidrasyon)
                    - [ ] 1.5 gr Kuru Maya
                    - [ ] 12 gr İnce Deniz Tuzu
                    - [ ] 10 ml Sızma Zeytinyağı
                    
                    ### Yapılışı:
                    1. Un ile suyu karıştırıp 30 dk dinlendirin (*Otoliz*).
                    2. Maya ve tuzu ekleyip pürüzsüz olana dek yoğurun.
                    3. Buzdolabında en az 24 saat soğuk fermantasyona bırakın.
                """.trimIndent(),
                timestamp = now - (oneDay * 3 + oneHour * 6),
                color = 0xFFFFE0B2.toInt(),
                categoryId = cShoppingId
            ),

            // 13. Not (Dil Öğrenme)
            Note(
                title = "Fransızca Temel Cümle Kalıpları",
                content = """
                    - **Bonjour, comment allez-vous?** -> *Merhaba, nasılsınız?*
                    - **Je voudrais un café, s'il vous plaît.** -> *Bir kahve rica ediyorum.*
                    - ==C'est magnifique!== -> *Bu harika!*
                    - **Où se trouve la gare?** -> *Tren istasyonu nerede?*
                    - **Merci beaucoup!** -> *Çok teşekkürler!*
                """.trimIndent(),
                timestamp = now - (oneDay * 4),
                color = 0xFFF8BBD0.toInt(),
                categoryId = cBooksId
            ),

            // 14. Not (Araç Bakımı & Hatırlatıcı)
            Note(
                title = "Araç Periyodik Bakım Kaydı",
                content = """
                    - Son Bakım Kilometresi: **84.500 km**
                    - Değişen Parçalar: Motor yağı (5W-30), Yağ filtresi, Hava filtresi, Polen filtresi.
                    - Bir Sonraki Bakım: **99.500 km** veya *Eylül 2027*.
                    - Akü durumu: İyi durumda (%88 sağlık).
                """.trimIndent(),
                timestamp = now - (oneDay * 4 + oneHour * 2),
                color = 0xFFBBDEFB.toInt(),
                categoryId = cPersonalId
            ),

            // 15. Not (Finansal Hedefler - Kilitli)
            Note(
                title = "2026-2027 Tasarruf & Birikim Hedefleri",
                content = """
                    🔒 **Yıllık Birikim Planı**
                    
                    - Acil Durum Fonu Hedefi: 6 Aylık sabit gider karşılayacak miktar
                    - Eurobond & Altın Sepeti: Aylık gelirin %20'si
                    - Teknoloji Fonları: Düzenli BES katkısı
                    
                    *Borçsuz ve bağımsız bir gelecek için harcamaları kontrol altında tut!*
                """.trimIndent(),
                timestamp = now - (oneDay * 5),
                color = 0xFFFFF9C4.toInt(),
                categoryId = cPersonalId,
                isLocked = true
            ),

            // 16. Not (Film Listesi)
            Note(
                title = "İzlenecek Başyapıt Filmler",
                content = """
                    - [x] Interstellar (Christopher Nolan) ★★★★★
                    - [x] Whiplash (Damien Chazelle) ★★★★★
                    - [ ] 12 Angry Men (Sidney Lumet)
                    - [ ] Parasite (Bong Joon-ho)
                    - [ ] Cinema Paradiso (Giuseppe Tornatore)
                    - [ ] Grand Budapest Hotel (Wes Anderson)
                """.trimIndent(),
                timestamp = now - (oneDay * 5 + oneHour * 4),
                color = 0xFFE1BEE7.toInt(),
                categoryId = cPersonalId
            ),

            // 17. Not (Bitki Bakımı)
            Note(
                title = "Ev İçi Salon Bitkileri Bakım Takvimi",
                content = """
                    - **Monstera (Deve Tabanı):** Haftada 1 kez su ver, yapraklarını nemli bezle sil.
                    - **Paşa Kılıcı:** 2 haftada bir çok az su (fazla suyu hiç sevmez).
                    - **Barış Çiçeği (Spathiphyllum):** Toprağı kurudukça sula, direkt güneşten koru.
                    - **Pothos Sarmaşık:** Aydınlık köşede hızla büyür.
                """.trimIndent(),
                timestamp = now - (oneDay * 6),
                color = 0xFFC8E6C9.toInt(),
                categoryId = cPersonalId
            ),

            // 18. Not (Podcast Fikirleri)
            Note(
                title = "Yazılım & Teknoloji Podcast Konuları",
                content = """
                    ### Bölüm 1: Yapay Zeka Çağında Yazılımcı Olmak
                    - Kod yazma araçlarının evrimi
                    - Problem çözme becerisinin önemi
                    
                    ### Bölüm 2: Mobil Dünyanın Geleceği
                    - Native vs Cross-platform karşılaştırması
                    - Kotlin Multiplatform ve Jetpack Compose
                """.trimIndent(),
                timestamp = now - (oneDay * 6 + oneHour * 8),
                color = 0xFFFFE0B2.toInt(),
                categoryId = cIdeasId
            ),

            // 19. Not (Hediye Fikirleri)
            Note(
                title = "Sevdiklerim İçin Hediye Fikirleri",
                content = """
                    - Anneme: Özel tasarım seramik fincan takımı
                    - Babama: Kaliteli deri cüzdan veya kitap seti
                    - Kardeşime: Kablosuz gürültü engelleyici kulaklık
                    - Kendime: Mekanik klavye veya tablet kalemi
                """.trimIndent(),
                timestamp = now - (oneDay * 7),
                color = 0xFFF8BBD0.toInt(),
                categoryId = cPersonalId
            ),

            // 20. Not (Kod Notları)
            Note(
                title = "Kotlin Coroutines & Flow İpuçları",
                content = """
                    ```kotlin
                    // StateFlow kullanımı
                    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
                    
                    // Lifecycle ile güvenli dinleme
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.uiState.collect { ... }
                        }
                    }
                    ```
                    
                    *Not: Dispatchers.Main UI içindir, ağır işler Dispatchers.IO'da yapılmalıdır.*
                """.trimIndent(),
                timestamp = now - (oneDay * 7 + oneHour * 5),
                color = 0xFFBBDEFB.toInt(),
                categoryId = cBusinessId
            ),

            // 21. Not (Evrak Listesi)
            Note(
                title = "Yurt Dışı Vize Başvuru Evrakları",
                content = """
                    - [x] Pasaport (Geçerlilik süresi en az 1 yıl)
                    - [x] Biyometrik Fotoğraf (2 adet)
                    - [x] Uçak ve Otel Rezervasyonları
                    - [ ] Banka Hesap Dökümü (Son 3 aylık, kaşeli)
                    - [ ] Seyahat Sağlık Sigortası (30.000 € teminatlı)
                    - [ ] İşyeri İzin Yazısı ve SGK Hizmet Dökümü
                """.trimIndent(),
                timestamp = now - (oneDay * 8),
                color = 0xFFD7CCC8.toInt(),
                categoryId = cTravelId
            ),

            // 22. Not (Gitar Akorları)
            Note(
                title = "Gitar Şarkı Akorları & Repertuar",
                content = """
                    **Akdeniz Akşamları**
                    Am - Dm - G - C - F - E - Am
                    
                    **Caddelerde Rüzgar**
                    Em - Am - D - G - C - B7 - Em
                    
                    *Ritim: A - Y - Y - A - Y*
                """.trimIndent(),
                timestamp = now - (oneDay * 8 + oneHour * 7),
                color = 0xFFE1BEE7.toInt(),
                categoryId = cPersonalId
            ),

            // 23. Not (Acil Durum Numaraları - Sabitli)
            Note(
                title = "Önemli İletişim & Acil Durum Rehberi",
                content = """
                    - **Tek Acil Çağrı Numarası:** `112`
                    - Doğalgaz Acil: `187`
                    - Elektrik Arıza: `186`
                    - Su Arıza: `185`
                    
                    Aile Hekimliği: Merkez Sağlık Ocağı (Dr. Ahmet Yılmaz)
                    Ev Sahibi İletişim: +90 555 123 4567
                """.trimIndent(),
                timestamp = now - (oneDay * 9),
                color = 0xFFFFCDD2.toInt(),
                categoryId = cPersonalId,
                isPinned = true
            ),

            // 24. Not (Günün Düşüncesi)
            Note(
                title = "Günün Sözü ve Motivasyon",
                content = """
                    > "Gelecek, bugünden ona hazırlananlara aittir." — Malcolm X
                    
                    Küçük adımlar, zamanla devasa sonuçlar doğurur. Her gün kendine %1 yatırım yap.
                """.trimIndent(),
                timestamp = now - (oneDay * 9 + oneHour * 3),
                color = 0xFFFFF9C4.toInt(),
                categoryId = cBooksId
            ),

            // 25. Not (Kişisel Şifreler - Kilitli)
            Note(
                title = "Kasa Şifreleri ve Güvenlik Kodları",
                content = """
                    🔒 **Biyometrik Korumalı Alan**
                    
                    - Ev Giriş Kapısı Şifresi: `#9824*`
                    - Bina Giriş Manyetik Kod: `3841`
                    - E-Devlet İki Aşamalı Doğrulama: Google Authenticator üzerinde aktif
                """.trimIndent(),
                timestamp = now - (oneDay * 10),
                color = 0xFF37474F.toInt(), // Koyu gri tema
                categoryId = cPersonalId,
                isLocked = true
            )
        )

        repository.insertNotes(sampleNotes)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    private fun createSampleDrawingFile(fileName: String, title: String): String {
        val file = File(context.filesDir, fileName)
        if (file.exists()) return file.absolutePath

        val width = 900
        val height = 650
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Arka plan
        canvas.drawColor(android.graphics.Color.WHITE)

        val gridPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#E0E7FF")
            strokeWidth = 2f
        }
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += 50f
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += 50f
        }

        // Başlık Kutusu
        val boxPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1E88E5")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawRoundRect(100f, 80f, 800f, 200f, 24f, 24f, boxPaint)

        // Alt kutular
        val subBoxPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#43A047")
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawRoundRect(100f, 320f, 400f, 480f, 20f, 20f, subBoxPaint)
        canvas.drawRoundRect(500f, 320f, 800f, 480f, 20f, 20f, subBoxPaint)

        // Bağlantı okları
        val arrowPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#FB8C00")
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(250f, 200f, 250f, 320f, arrowPaint)
        canvas.drawLine(650f, 200f, 650f, 320f, arrowPaint)

        // Başlık Metni
        val textPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1D1B20")
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, 450f, 150f, textPaint)
        canvas.drawText("Presentation", 250f, 410f, textPaint.apply { textSize = 26f })
        canvas.drawText("Domain & Data", 650f, 410f, textPaint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    private fun createSampleImageFile(fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (file.exists()) return file.absolutePath

        val width = 800
        val height = 500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = listOf(
            android.graphics.Color.parseColor("#1E88E5"),
            android.graphics.Color.parseColor("#43A047"),
            android.graphics.Color.parseColor("#FDD835"),
            android.graphics.Color.parseColor("#E53935"),
            android.graphics.Color.parseColor("#8E24AA")
        )

        val colWidth = width / colors.size.toFloat()
        val paint = Paint()
        colors.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawRect(index * colWidth, 0f, (index + 1) * colWidth, height.toFloat(), paint)
        }

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Material You Renk Paleti", width / 2f, height / 2f, textPaint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }
}
