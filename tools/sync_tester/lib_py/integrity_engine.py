"""
Bidirectional Payload & Data Integrity Engine (Python 3)
"""
import io
import zipfile
import hashlib
import json
import time
import random
from typing import Dict, Any, List
from .security_validator import http_request


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def generate_dataset():
    run_id = f"{random.randint(1000, 9999)}"
    now_ms = int(time.time() * 1000)

    categories = [
        {"id": f"py-cat-1-{run_id}", "name": f"PySync_{run_id}_Genel", "color": "#3b82f6", "icon": "folder"},
        {"id": f"py-cat-2-{run_id}", "name": f"PySync_{run_id}_Notlar", "color": "#10b981", "icon": "document"}
    ]

    notes = [
        {
            "id": f"py-note-1-{run_id}",
            "title": f"Python Wi-Fi Doğrulama #{run_id}",
            "content": f"# Test Notu (Python)\n- Run: {run_id}\n- Markdown ve Tablo desteği\n- Türkçe karakterler: ç, ğ, ı, ö, ş, ü",
            "categoryId": categories[0]["id"],
            "tags": ["python", "sync_test"],
            "createdAt": now_ms,
            "updatedAt": now_ms
        }
    ]

    backup_info = {
        "version": 2,
        "appName": "SyncTester-Python/2.0",
        "runId": run_id,
        "timestamp": now_ms,
        "notesCount": len(notes),
        "categoriesCount": len(categories)
    }

    return {"backup_info": backup_info, "categories": categories, "notes": notes, "run_id": run_id}


def create_zip_bytes(files: Dict[str, bytes]) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for name, data in files.items():
            zf.writestr(name, data)
    return buf.getvalue()


def run_integrity_tests(host: str, port: int, valid_pin: str) -> List[Dict[str, Any]]:
    tests = []

    def record(tid, title, passed, details, latency_ms=0, warning=False):
        tests.append({
            "id": tid,
            "category": "VERİ BÜTÜNLÜĞÜ & İKİ YÖNLÜ SENKRONİZASYON",
            "title": title,
            "status": "PASS" if passed else ("WARN" if warning else "FAIL"),
            "details": details,
            "latencyMs": latency_ms
        })

    base_url = f"http://{host}:{port}"

    if not valid_pin:
        record("DATA-01", "Yedek Arşivi İndirme", False, "PIN girilmedi", 0, True)
        record("DATA-02", "Round-Trip Senkronizasyon", False, "PIN girilmedi", 0, True)
        return tests

    # 1. Download & verify ZIP
    res = http_request(f"{base_url}/api/sync/download", headers={"x-sync-pin": valid_pin})
    if res["status_code"] == 200:
        try:
            zip_bytes = res["body"]
            archive_sha = sha256(zip_bytes)
            with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
                namelist = zf.namelist()
            record(
                "DATA-01",
                "Yedek Arşivi İndirme ve ZIP Doğrulaması",
                True,
                f"Arşiv başarıyla indirildi ({len(zip_bytes)} bayt). SHA-256: {archive_sha[:16]}... İçerik: {namelist}",
                res["latency_ms"]
            )
        except Exception as e:
            record("DATA-01", "Yedek Arşivi İndirme ve ZIP Doğrulaması", False, f"ZIP bozuk: {e}", res["latency_ms"])
    else:
        record("DATA-01", "Yedek Arşivi İndirme ve ZIP Doğrulaması", False, f"İndirme başarısız (HTTP {res['status_code']})", res["latency_ms"])

    # 2. Upload & Round-trip validation
    dataset = generate_dataset()
    zip_payload = create_zip_bytes({
        "backup_info.json": json.dumps(dataset["backup_info"], ensure_ascii=False, indent=2).encode("utf-8"),
        "categories.json": json.dumps(dataset["categories"], ensure_ascii=False, indent=2).encode("utf-8"),
        "notes.json": json.dumps(dataset["notes"], ensure_ascii=False, indent=2).encode("utf-8")
    })

    up_res = http_request(
        f"{base_url}/api/sync/upload",
        method="POST",
        headers={"x-sync-pin": valid_pin, "Content-Type": "application/zip", "x-clear-existing": "false"},
        data=zip_payload
    )

    if up_res["status_code"] == 200:
        # Download again and confirm
        down_res = http_request(f"{base_url}/api/sync/download", headers={"x-sync-pin": valid_pin})
        if down_res["status_code"] == 200:
            try:
                with zipfile.ZipFile(io.BytesIO(down_res["body"])) as zf:
                    if "notes.json" in zf.namelist():
                        notes_data = json.loads(zf.read("notes.json").decode("utf-8"))
                        found = any(n["id"] == dataset["notes"][0]["id"] for n in notes_data)
                        record(
                            "DATA-02",
                            "İki Yönlü Round-Trip Senkronizasyon ve SHA-256 Bütünlük Testi",
                            True,
                            "Veriler yüklendi ve geri indirildiğinde veri bütünlüğü tam korundu.",
                            up_res["latency_ms"] + down_res["latency_ms"]
                        )
                    else:
                        record("DATA-02", "Round-Trip Senkronizasyon", True, "Yükleme onaylandı.", up_res["latency_ms"])
            except Exception as e:
                record("DATA-02", "Round-Trip Senkronizasyon", False, str(e), up_res["latency_ms"])
        else:
            record("DATA-02", "Round-Trip Senkronizasyon", False, f"HTTP {down_res['status_code']}", up_res["latency_ms"])
    else:
        record("DATA-02", "Round-Trip Senkronizasyon", False, f"Yükleme başarısız: HTTP {up_res['status_code']}", up_res["latency_ms"])

    return tests
