"""
Authentication & PIN Security Validator Module (Python 3)
"""
import urllib.request
import urllib.error
import time
import concurrent.futures
from typing import Dict, Any, List, Optional


def http_request(
    url: str,
    method: str = "GET",
    headers: Optional[Dict[str, str]] = None,
    data: Optional[bytes] = None,
    timeout_sec: float = 4.0
) -> Dict[str, Any]:
    req_headers = {"User-Agent": "SyncTester-Security/1.0", "Accept": "*/*"}
    if headers:
        req_headers.update(headers)

    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            latency_ms = (time.perf_counter() - start) * 1000.0
            return {
                "ok": True,
                "status_code": resp.status,
                "headers": dict(resp.headers),
                "body": resp.read(),
                "latency_ms": round(latency_ms, 2)
            }
    except urllib.error.HTTPError as e:
        latency_ms = (time.perf_counter() - start) * 1000.0
        return {
            "ok": True,
            "status_code": e.code,
            "headers": dict(e.headers),
            "body": e.read(),
            "latency_ms": round(latency_ms, 2)
        }
    except Exception as e:
        return {
            "ok": False,
            "status_code": 0,
            "headers": {},
            "body": b"",
            "error": str(e),
            "latency_ms": -1.0
        }


def run_security_tests(host: str, port: int, valid_pin: str) -> List[Dict[str, Any]]:
    tests = []

    def record(tid, title, passed, details, latency_ms=0, warning=False):
        tests.append({
            "id": tid,
            "category": "GÜVENLİK & KİMLİK DOĞRULAMA",
            "title": title,
            "status": "PASS" if passed else ("WARN" if warning else "FAIL"),
            "details": details,
            "latencyMs": latency_ms
        })

    base_url = f"http://{host}:{port}"

    # SEC-01: No PIN
    res = http_request(f"{base_url}/api/sync/download")
    passed = res["status_code"] == 401
    record(
        "SEC-01",
        "PIN Başlığı Olmadan İndirme Reddi (HTTP 401)",
        passed,
        "PIN olmadan yapılan istek başarıyla HTTP 401 ile reddedildi." if passed else f"Beklenen 401, Alınan: {res['status_code']}",
        res["latency_ms"]
    )

    # SEC-02: Invalid PIN
    wrong_pin = "0000" if valid_pin != "0000" else "9999"
    res = http_request(f"{base_url}/api/sync/download", headers={"x-sync-pin": wrong_pin})
    passed = res["status_code"] == 401
    record(
        "SEC-02",
        "Geçersiz PIN ile İndirme Reddi (X-Sync-PIN: 0000)",
        passed,
        "Hatalı PIN güvenli şekilde HTTP 401 ile engellendi." if passed else f"Beklenen 401, Alınan: {res['status_code']}",
        res["latency_ms"]
    )

    # SEC-03: Valid PIN
    if valid_pin:
        res = http_request(f"{base_url}/api/sync/download", headers={"x-sync-pin": valid_pin})
        passed = res["status_code"] == 200
        record(
            "SEC-03",
            "Doğru PIN ile Başarılı Yetkilendirme (X-Sync-PIN)",
            passed,
            f"Geçerli PIN ({valid_pin}) onaylandı. İçerik boyutu: {len(res['body'])} bayt." if passed else f"Yetki hatası: HTTP {res['status_code']}",
            res["latency_ms"]
        )
    else:
        record("SEC-03", "Doğru PIN Testi", False, "PIN girilmedi", 0, True)

    # SEC-04: Query param pin
    if valid_pin:
        res = http_request(f"{base_url}/api/sync/download?pin={valid_pin}")
        passed = res["status_code"] == 200
        record(
            "SEC-04",
            "URL Parametresi ile Yetkilendirme (?pin=...) Doğrulaması",
            passed,
            "Query parametresi ile indirme başarılı (HTTP 200)." if passed else f"HTTP: {res['status_code']}",
            res["latency_ms"]
        )

    # SEC-05: Alternative Header X-Pairing-PIN
    if valid_pin:
        res = http_request(f"{base_url}/api/sync/download", headers={"x-pairing-pin": valid_pin})
        passed = res["status_code"] == 200
        record(
            "SEC-05",
            "Alternatif Başlık Desteği (X-Pairing-PIN)",
            passed,
            "'X-Pairing-PIN' başlığı destekleniyor (HTTP 200)." if passed else f"HTTP: {res['status_code']}",
            res["latency_ms"],
            not passed
        )

    # SEC-06: Boundary & Injection
    injections = ["", "   ", "-1", "999999999", "' OR 1=1 --", "<script>"]
    sec_passed = 0
    for inj in injections:
        r = http_request(f"{base_url}/api/sync/download", headers={"x-sync-pin": inj})
        if r["status_code"] == 401:
            sec_passed += 1

    record(
        "SEC-06",
        "Sınır Değer ve SQL/XSS Enjeksiyon Koruması",
        sec_passed == len(injections),
        f"{len(injections)} enjeksiyon/sınır denemesinden {sec_passed} tanesi HTTP 401 ile durduruldu.",
        0
    )

    # SEC-07: Rapid requests / brute force resilience
    attempts = 10
    start_time = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        futures = [
            executor.submit(http_request, f"{base_url}/api/sync/download", "GET", {"x-sync-pin": str(1000 + i)})
            for i in range(attempts)
        ]
        results = [f.result() for f in futures]

    total_time_ms = (time.perf_counter() - start_time) * 1000.0
    all_401 = all(r["status_code"] == 401 for r in results)
    avg_latency = round(total_time_ms / attempts, 2)

    record(
        "SEC-07",
        "Seri Hatalı PIN / Brute-Force Dayanıklılık Testi",
        all_401,
        f"{attempts} seri istek {round(total_time_ms, 1)} ms içinde tamamlandı. Kilitlenme: 0 (Ort. {avg_latency} ms)." if all_401 else "Hata alındı.",
        avg_latency
    )

    return tests
