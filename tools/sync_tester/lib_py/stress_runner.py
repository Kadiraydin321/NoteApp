"""
Edge Cases & Stress Runner Module (Python 3)
"""
import time
import concurrent.futures
from typing import Dict, Any, List
from .security_validator import http_request
from .discovery_probe import tcp_ping


def run_stress_tests(host: str, port: int, valid_pin: str, concurrency: int = 5) -> List[Dict[str, Any]]:
    tests = []

    def record(tid, title, passed, details, latency_ms=0, warning=False):
        tests.append({
            "id": tid,
            "category": "SINIR DURUMLAR & STRES / YÜK TESTLERİ",
            "title": title,
            "status": "PASS" if passed else ("WARN" if warning else "FAIL"),
            "details": details,
            "latencyMs": latency_ms
        })

    base_url = f"http://{host}:{port}"

    # STR-01: 10 MB Oversized payload
    payload_10mb = b"A" * (10 * 1024 * 1024)
    res = http_request(
        f"{base_url}/api/sync/upload",
        method="POST",
        headers={"x-sync-pin": valid_pin or "1234", "Content-Type": "application/zip"},
        data=payload_10mb,
        timeout_sec=12.0
    )
    ping = tcp_ping(host, port, 2.5)
    server_alive = ping["reachable"]
    record(
        "STR-01",
        "Aşırı Yük Güvenlik Testi (10 MB Payload Too Large)",
        server_alive,
        f"10 MB yük sonrası sunucu kararlı kaldı (HTTP: {res['status_code']}, Ping: {ping['latency_ms']} ms)." if server_alive else "Sunucu çöktü!",
        res["latency_ms"]
    )

    # STR-02: Corrupt ZIP packet
    corrupt_data = b"PK\x03\x04\xff\xff" + (b"\x12\x34\x56\x78" * 8192)
    res = http_request(
        f"{base_url}/api/sync/upload",
        method="POST",
        headers={"x-sync-pin": valid_pin or "1234", "Content-Type": "application/zip"},
        data=corrupt_data,
        timeout_sec=5.0
    )
    ping = tcp_ping(host, port, 2.5)
    server_alive = ping["reachable"]
    record(
        "STR-02",
        "Bozuk ZIP / Geçersiz İkili Veri Paketi Testi",
        server_alive,
        f"Bozuk veri paketi sunucuyu kilitlemedi (HTTP: {res['status_code']})." if server_alive else "Sunucu çöktü!",
        res["latency_ms"]
    )

    # STR-03: Concurrency
    client_count = max(2, concurrency)
    start_time = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=client_count) as executor:
        futures = [
            executor.submit(http_request, f"{base_url}/api/status", "GET", timeout_sec=5.0)
            for _ in range(client_count)
        ]
        results = [f.result() for f in futures]

    total_time_ms = (time.perf_counter() - start_time) * 1000.0
    success_count = sum(1 for r in results if r["status_code"] in [200, 401])
    all_success = success_count == client_count
    latencies = [r["latency_ms"] for r in results if r["latency_ms"] >= 0]
    avg_latency = round(sum(latencies) / len(latencies), 2) if latencies else 0

    record(
        "STR-03",
        f"Eşzamanlı Çoklu İstemci Testi ({client_count} Paralel İstek)",
        all_success,
        f"{client_count} paralel istek {round(total_time_ms, 1)} ms sürede kilitlenmesiz tamamlandı (Ort. {avg_latency} ms)." if all_success else "Kilitlenme veya hata tespit edildi.",
        avg_latency
    )

    return tests
