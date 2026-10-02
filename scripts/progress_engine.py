#!/usr/bin/env python3
"""
NoteApp Autonomous Feature Progression & Benchmark Engine
-----------------------------------------------------------
Bu betik, docs/research/ altındaki araştırma dokümanlarını referans alarak
NoteApp Kotlin/Compose kod tabanını otonom olarak analiz eder, özellik tamamlanma
oranlarını ölçer, rakiplere göre olgunluk endeksini hesaplar ve projenin kendi
kendine ilerleme yol haritasını yönetir.
"""

import os
import re
import json
import subprocess
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DOCS_DIR = BASE_DIR / "docs" / "research"
SRC_DIR = BASE_DIR / "app" / "src" / "main" / "java"

BENCHMARK_FEATURES = [
    {
        "id": "F01",
        "name": "Masonry Izgara & 12 Renkli Pastel Kartlar",
        "inspired_by": ["Google Keep"],
        "category": "UI/UX",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/notes/NotesScreen.kt", r"StaggeredGrid|itemsIndexed|NoteCard"),
            (SRC_DIR / "com/example/noteapp/domain/model/Note.kt", r"val color: Int")
        ]
    },
    {
        "id": "F02",
        "name": "Geri Al / İleri Al (Undo/Redo) Geçmişi",
        "inspired_by": ["Google Keep", "Bear"],
        "category": "Editor",
        "weight": 7,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailViewModel.kt", r"undoStack|redoStack|canUndo|canRedo")
        ]
    },
    {
        "id": "F03",
        "name": "Biyometrik & Master PIN Şifreli Kasa (E2EE)",
        "inspired_by": ["Apple Notes", "Joplin"],
        "category": "Security",
        "weight": 9,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/biometric/BiometricPromptManager.kt", r"BiometricPrompt|authenticate"),
            (SRC_DIR / "com/example/noteapp/data/security/NoteCryptoManager.kt", r"AES|Cipher|SecretKey")
        ]
    },
    {
        "id": "F04",
        "name": "Ses Kaydı & Entegre Oynatıcı",
        "inspired_by": ["Google Keep", "OneNote"],
        "category": "Multimedia",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/media/AudioHelper.kt", r"AudioRecorder|AudioPlayer|MediaRecorder|MediaPlayer"),
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailViewModel.kt", r"startAudioRecording|stopAudioRecording")
        ]
    },
    {
        "id": "F05",
        "name": "Gelişmiş Serbest Çizim Kanvası",
        "inspired_by": ["Samsung Notes", "OneNote"],
        "category": "Multimedia",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/drawing/DrawingScreen.kt", r"Canvas|Stroke|Path|DrawingScreen")
        ]
    },
    {
        "id": "F06",
        "name": "Görsel Üzerine Çizim & Metin İşaretleme",
        "inspired_by": ["Samsung Notes", "Google Keep"],
        "category": "Multimedia",
        "weight": 7,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/imageedit/ImageEditScreen.kt", r"ImageEditScreen|saveEditedImage|drawWithContent")
        ]
    },
    {
        "id": "F07",
        "name": "Cihaz Üzerinde Yerel OCR Metin Çıkarma",
        "inspired_by": ["Evernote", "Apple Notes"],
        "category": "AI / Scanner",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/media/OcrHelper.kt", r"TextRecognition|recognizeText|LatinTextRecognizerOptions")
        ]
    },
    {
        "id": "F08",
        "name": "Çok Boyutlu Android Widget Ekosistemi (1x1, 3x2, Popup)",
        "inspired_by": ["Google Keep"],
        "category": "System Integration",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/widget/NotesWidgetProvider.kt", r"AppWidgetProvider|updateAppWidget"),
            (SRC_DIR / "com/example/noteapp/widget/QuickNoteWidgetProvider.kt", r"QuickNoteWidgetProvider")
        ]
    },
    {
        "id": "F09",
        "name": "Markdown Biçimlendirme & Hızlı Araç Çubuğu",
        "inspired_by": ["Obsidian", "Notion", "Bear"],
        "category": "Editor",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/components/RichTextToolbar.kt", r"RichTextToolbar|applyFormatting"),
            (SRC_DIR / "com/example/noteapp/presentation/components/MarkdownVisualTransformation.kt", r"MarkdownVisualTransformation")
        ]
    },
    {
        "id": "F10",
        "name": "Görev Yöneticisi Tam Siyah Gizlilik Kalkanı (Pitch-Black)",
        "inspired_by": ["Apple Notes"],
        "category": "Security",
        "weight": 6,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/MainActivity.kt", r"FLAG_SECURE|privacyOverlay|onUserLeaveHint")
        ]
    },
    {
        "id": "F11",
        "name": "Çift Yönlü Bağlantılar (`[[Not Başlığı]]` Wikilinks)",
        "inspired_by": ["Obsidian", "Logseq"],
        "category": "Knowledge Graph",
        "weight": 9,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailScreen.kt", r"wikilink|\[\[|navigateToNoteByTitle"),
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailViewModel.kt", r"extractWikilinks|findNoteByTitle")
        ]
    },
    {
        "id": "F12",
        "name": "Geri Bağlantılar (Backlinks) ve İlgili Notlar Listesi",
        "inspired_by": ["Obsidian", "Logseq"],
        "category": "Knowledge Graph",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/data/local/NoteDao.kt", r"getNotesLinkingTo|LIKE.*\[\["),
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailScreen.kt", r"Backlinks|Geri Bağlantılar|İlgili Notlar")
        ]
    },
    {
        "id": "F13",
        "name": "Metin İçi `#etiket` ve İç İçe Etiketleme Çıkarımı",
        "inspired_by": ["Bear", "Obsidian"],
        "category": "Organization",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/notes/NotesViewModel.kt", r"extractHashtags|allHashtags|selectedTag"),
            (SRC_DIR / "com/example/noteapp/presentation/notes/NotesScreen.kt", r"TagFilter|#|HashtagChip")
        ]
    },
    {
        "id": "F14",
        "name": "Canlı Okuma Süresi, Sözcük ve Karakter Sayacı",
        "inspired_by": ["Bear", "Notion"],
        "category": "Editor",
        "weight": 6,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailScreen.kt", r"kelime|karakter|okuma süresi|wordCount|readingTime")
        ]
    },
    {
        "id": "F15",
        "name": "Günün Notu (Daily Journal) Hızlı Oluşturma",
        "inspired_by": ["Logseq", "Obsidian"],
        "category": "Productivity",
        "weight": 7,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/notes/NotesViewModel.kt", r"openOrCreateDailyNote|Günlük Not|dailyNote"),
            (SRC_DIR / "com/example/noteapp/presentation/notes/NotesScreen.kt", r"Günün Notu|Günlük Not|daily_note|ic_daily")
        ]
    },
    {
        "id": "F16",
        "name": "Akıllı Filtreler (Kilitli, Sesli, Görselli, Hatırlatıcılı)",
        "inspired_by": ["Apple Notes", "Notion"],
        "category": "Organization",
        "weight": 7,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/notes/NotesViewModel.kt", r"NoteTypeFilter|SmartFilter|filterByAttachment|filterByReminder")
        ]
    },
    {
        "id": "F17",
        "name": "Evrensel Markdown (.md) ve Düz Metin Paylaşımı/Dışa Aktarma",
        "inspired_by": ["Joplin", "Obsidian", "Bear"],
        "category": "Portability",
        "weight": 7,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailScreen.kt", r"exportAsMarkdown|shareAsMarkdown|text/markdown")
        ]
    },
    {
        "id": "F18",
        "name": "Zaman Damgalı Ses İşaretleri (Audio Timestamps `[MM:SS]`)",
        "inspired_by": ["Microsoft OneNote", "Samsung Notes"],
        "category": "Multimedia",
        "weight": 7,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/detail/NoteDetailViewModel.kt", r"insertAudioTimestamp|jumpToAudioTimestamp")
        ]
    },
    {
        "id": "F19",
        "name": "Sürükle-Bırak Sıralamalı Dinamik Kontrol Listesi",
        "inspired_by": ["Google Keep"],
        "category": "Productivity",
        "weight": 8,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/components/KeepCheckboxIcon.kt", r"KeepCheckboxIcon|checkbox")
        ]
    },
    {
        "id": "F20",
        "name": "Vurgu Kutusu (Callout Block) Markdown Desteği",
        "inspired_by": ["Notion", "Obsidian"],
        "category": "Editor",
        "weight": 6,
        "patterns": [
            (SRC_DIR / "com/example/noteapp/presentation/components/MarkdownPreview.kt", r"NOTE|TIP|WARNING|Callout")
        ]
    }
]

def check_feature(feature):
    """Kod tabanındaki desenleri tarayarak özelliğin tamamlanma durumunu saptar."""
    matched_count = 0
    total_patterns = len(feature["patterns"])

    for file_path, pattern in feature["patterns"]:
        if not file_path.exists():
            continue
        try:
            content = file_path.read_text(encoding="utf-8")
            if re.search(pattern, content):
                matched_count += 1
        except Exception:
            pass

    if matched_count == total_patterns:
        return "COMPLETED", 100
    elif matched_count > 0:
        pct = int((matched_count / total_patterns) * 100)
        return "IN_PROGRESS", pct
    else:
        return "NOT_STARTED", 0

def run_audit():
    print("=" * 70)
    print(" NOTEAPP AUTONOMOUS FEATURE PROGRESSION & AUDIT ENGINE")
    print("=" * 70)

    total_weight = sum(f["weight"] for f in BENCHMARK_FEATURES)
    achieved_weight = 0
    results = []

    for f in BENCHMARK_FEATURES:
        status, pct = check_feature(f)
        score = (f["weight"] * pct) / 100
        achieved_weight += score
        results.append({
            **f,
            "status": status,
            "progress_percent": pct,
            "score": score
        })

    overall_maturity = (achieved_weight / total_weight) * 100

    print(f"\n[+] Genel NoteApp Olgunluk Endeksi: %{overall_maturity:.1f}\n")
    print(f"{'ID':<4} | {'Özellik':<42} | {'Durum':<12} | {'Tamamlanma':<10}")
    print("-" * 75)

    for r in results:
        status_sym = "✅" if r["status"] == "COMPLETED" else ("🔄" if r["status"] == "IN_PROGRESS" else "⏳")
        print(f"{r['id']:<4} | {r['name'][:40]:<42} | {status_sym} {r['status']:<9} | %{r['progress_percent']:<3}")

    print("-" * 75)

    # Rakiplere Göre Parite Analizi
    app_scores = {}
    for r in results:
        for app in r["inspired_by"]:
            if app not in app_scores:
                app_scores[app] = {"total": 0, "achieved": 0}
            app_scores[app]["total"] += r["weight"]
            app_scores[app]["achieved"] += r["score"]

    print("\n[+] Rakiplere Göre Özellik Parite Analizi:")
    for app, data in sorted(app_scores.items(), key=lambda x: (x[1]["achieved"]/x[1]["total"]), reverse=True):
        ratio = (data["achieved"] / data["total"]) * 100 if data["total"] > 0 else 0
        bar = "█" * int(ratio / 10) + "░" * (10 - int(ratio / 10))
        print(f"  • {app:<18} [{bar}] %{ratio:.1f}")

    return overall_maturity, results

def generate_docs(overall_maturity, results):
    """Durumu PROGRESS_STATUS.md dosyasına kaydeder."""
    status_file = DOCS_DIR / "PROGRESS_STATUS.md"
    
    lines = [
        "# NoteApp Canlı İlerleme ve Özellik Gerçekleşme Durumu",
        f"**Son Güncelleme:** Otomatik Denetim Motoru",
        f"**Genel Olgunluk Endeksi:** %{overall_maturity:.1f}",
        "",
        "## 1. Özellik Gerçekleşme Tablosu",
        "",
        "| ID | Özellik | Esinlenme | Durum | İlerleme |",
        "|:---|:---|:---|:---:|:---:|"
    ]

    for r in results:
        status_str = "Tamamlandı ✅" if r["status"] == "COMPLETED" else ("Geliştiriliyor 🔄" if r["status"] == "IN_PROGRESS" else "Planlandı ⏳")
        apps = ", ".join(r["inspired_by"])
        lines.append(f"| {r['id']} | {r['name']} | {apps} | {status_str} | %{r['progress_percent']} |")

    lines.extend([
        "",
        "## 2. Sıradaki Otonom Hedefler (Next Autonomous Milestones)",
        "",
    ])

    pending = [r for r in results if r["status"] != "COMPLETED"]
    if pending:
        # En yüksek ağırlıklı bekleyen özellik
        pending.sort(key=lambda x: x["weight"], reverse=True)
        for p in pending[:5]:
            lines.append(f"- **[{p['id']}] {p['name']}** (Ağırlık: {p['weight']}, Esinlenme: {', '.join(p['inspired_by'])})")
    else:
        lines.append("Tüm kıyaslama özellikleri NoteApp kod tabanında başarıyla hayata geçirildi! 🎉")

    status_file.write_text("\n".join(lines), encoding="utf-8")
    print(f"\n[✓] İlerleme durumu kaydedildi: {status_file}")

if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else "--audit"
    maturity, res = run_audit()
    generate_docs(maturity, res)
