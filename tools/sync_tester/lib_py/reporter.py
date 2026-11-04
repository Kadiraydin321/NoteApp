"""
Reporting Module (Python 3)
Generates console ANSI output, Markdown and JSON reports.
"""
import sys
import json
import datetime
from typing import Dict, Any, List

# Ensure Windows terminal handles UTF-8 emojis and special characters cleanly
if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

COLORS = {
    "cyan": "\033[36m",
    "bright": "\033[1m",
    "reset": "\033[0m",
    "green": "\033[32m",
    "red": "\033[31m",
    "yellow": "\033[33m",
    "blue": "\033[34m",
    "dim": "\033[2m",
    "bgGreen": "\033[42m"
}

def banner():
    print(f"""
{COLORS['cyan']}{COLORS['bright']}========================================================================{COLORS['reset']}
{COLORS['cyan']}{COLORS['bright']}   *  CROSS-PLATFORM LAN / WI-FI SYNC TEST SUITE (PYTHON 3)    {COLORS['reset']}
{COLORS['dim']}   Enterprise-Grade Wi-Fi Synchronization Protocol Validator v2.0{COLORS['reset']}
{COLORS['cyan']}{COLORS['bright']}========================================================================{COLORS['reset']}
""")

def print_section(title: str):
    print(f"\n{COLORS['bright']}{COLORS['blue']}▶ {title}{COLORS['reset']}")
    print(f"{COLORS['dim']}{'─' * 70}{COLORS['reset']}")

def print_test_result(test: Dict[str, Any]):
    status = test["status"]
    icon = "[OK]" if status == "PASS" else ("[WARN]" if status == "WARN" else "[FAIL]")
    color = COLORS["green"] if status == "PASS" else (COLORS["yellow"] if status == "WARN" else COLORS["red"])
    
    tid = f"[{test['id']}]".ljust(9)
    latency = f"{test['latencyMs']}ms".rjust(7) if test.get("latencyMs", -1) >= 0 else "       "
    
    print(f" {icon} {COLORS['bright']}{tid}{COLORS['reset']} {color}{test['title'].ljust(52)}{COLORS['reset']} {COLORS['dim']}{latency}{COLORS['reset']}")
    if test.get("details"):
        print(f"    {COLORS['dim']}└─ {test['details']}{COLORS['reset']}")

def print_summary(summary: Dict[str, Any]):
    print(f"\n{COLORS['cyan']}{COLORS['bright']}════════════════════════════════════════════════════════════════════════{COLORS['reset']}")
    print(f"{COLORS['bright']}               📊 TEST SONUÇLARI VE PERFORMANS ÖZETİ                    {COLORS['reset']}")
    print(f"{COLORS['cyan']}{COLORS['bright']}════════════════════════════════════════════════════════════════════════{COLORS['reset']}")
    print(f" Hedef Sunucu       : {summary['target']['host']}:{summary['target']['port']} ({summary['target'].get('appName', 'N/A')})")
    print(f" Toplam Test Sayısı : {summary['total']}")
    print(f" Başarılı           : {COLORS['green']}{COLORS['bright']}{summary['passed']}{COLORS['reset']}")
    print(f" Başarısız          : {COLORS['red'] if summary['failed'] > 0 else COLORS['dim']}{summary['failed']}{COLORS['reset']}")
    print(f" Başarı Oranı       : %{summary['passRate']}")
    print(f" Toplam Süre        : {summary['durationMs']} ms")
    print(f"{COLORS['cyan']}{COLORS['bright']}════════════════════════════════════════════════════════════════════════{COLORS['reset']}\n")

def save_reports(summary: Dict[str, Any], tests: List[Dict[str, Any]], md_path: str = "SYNC_TEST_REPORT.md", json_path: str = "sync_test_results.json"):
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump({"schemaVersion": "2.0.0", "target": summary["target"], "summary": summary, "tests": tests}, f, ensure_ascii=False, indent=2)

    lines = [
        "# 📡 LAN / Wi-Fi Senkronizasyon Test ve Doğrulama Raporu (Python)",
        "",
        f"> **Tarih:** {datetime.datetime.now().strftime('%d.%m.%Y %H:%M:%S')}  ",
        f"> **Hedef:** `http://{summary['target']['host']}:{summary['target']['port']}`  ",
        f"> **Uygulama:** {summary['target'].get('appName', 'N/A')}  ",
        "",
        "## 1. Yönetici Özeti",
        "",
        "| Metrik | Değer |",
        "|---|---|",
        f"| Toplam Test | `{summary['total']}` |",
        f"| Başarılı | `{summary['passed']}` |",
        f"| Başarısız | `{summary['failed']}` |",
        f"| Başarı Oranı | **%{summary['passRate']}** |",
        f"| Toplam Süre | `{summary['durationMs']} ms` |",
        "",
        "## 2. Test Matrisi",
        "",
        "| ID | Kategori | Test Başlığı | Durum | Gecikme | Açıklama |",
        "|---|---|---|:---:|:---:|---|"
    ]

    for t in tests:
        status_icon = "PASS" if t["status"] == "PASS" else ("WARN" if t["status"] == "WARN" else "FAIL")
        lines.append(f"| **{t['id']}** | {t['category']} | {t['title']} | {status_icon} | `{t['latencyMs']}ms` | {t.get('details', '')} |")

    lines.append("")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
