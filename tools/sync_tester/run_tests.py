#!/usr/bin/env python3
"""
Cross-Platform LAN / Wi-Fi Sync Test Suite & Verification Framework Runner (Python 3)
Zero-dependency Python CLI
"""
import sys
import argparse
import time
import threading
from http.server import HTTPServer
from lib_py.discovery_probe import probe_target
from lib_py.security_validator import run_security_tests
from lib_py.integrity_engine import run_integrity_tests
from lib_py.stress_runner import run_stress_tests
from lib_py.reporter import banner, print_section, print_test_result, print_summary, save_reports
from mock_server import MockHandler


def start_mock_server_thread(port: int, pin: str):
    MockHandler.pairing_pin = pin
    server = HTTPServer(("127.0.0.1", port), MockHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def main():
    parser = argparse.ArgumentParser(description="LAN / Wi-Fi Senkronizasyon Test Suite")
    parser.add_argument("--host", default="127.0.0.1", help="Hedef IP adresi")
    parser.add_argument("--port", type=int, default=8765, help="Hedef Port")
    parser.add_argument("--pin", default="1234", help="Eşleşme PIN kodu")
    parser.add_argument("--mock", "-m", action="store_true", help="Dahili Mock Sunucuyu başlat ve test et (Self-Test)")
    parser.add_argument("--concurrency", type=int, default=5, help="Eşzamanlı istek sayısı")
    parser.add_argument("--report-md", default="SYNC_TEST_REPORT.md", help="Markdown rapor çıktısı")
    parser.add_argument("--report-json", default="sync_test_results.json", help="JSON rapor çıktısı")

    args = parser.parse_args()
    banner()

    target_host = args.host
    target_port = args.port
    valid_pin = args.pin
    mock_server = None

    if args.mock:
        print_section("Dahili Mock Sunucu Başlatılıyor (Python 3)")
        target_port = 8770
        valid_pin = "1234"
        mock_server = start_mock_server_thread(target_port, valid_pin)
        time.sleep(0.1)
        print(f"✅ Mock Server aktif: http://127.0.0.1:{target_port} (PIN: {valid_pin})")

    start_time = time.perf_counter()
    all_tests = []

    # Phase 1: Discovery & Probe
    print_section("Aşama 1: Keşif ve Durum Kontrolü (Probe)")
    probe = probe_target(target_host, target_port)
    probe_ok = probe["tcp_reachable"] and probe["is_sync_server"]
    probe_test = {
        "id": "DISC-01",
        "category": "KEŞİF & BAĞLANTI (PROBE)",
        "title": "Hedef Sunucu Erişilebilirliği ve /api/status Durum Sorgusu",
        "status": "PASS" if probe_ok else ("WARN" if probe["tcp_reachable"] else "FAIL"),
        "details": f"Uygulama: {probe['server_info'].get('appName')}, Notlar: {probe['server_info'].get('notesCount')}" if probe_ok else f"Hata: {probe.get('error')}",
        "latencyMs": probe["http_latency_ms"] if probe["http_latency_ms"] >= 0 else probe["tcp_latency_ms"]
    }
    all_tests.append(probe_test)
    print_test_result(probe_test)

    if not probe["tcp_reachable"]:
        print(f"\n⚠️  Sunucuya bağlanılamadı ({target_host}:{target_port}).")
        print("Dahili mock sunucuyla test etmek için: python run_tests.py --mock\n")
        sys.exit(1)

    # Phase 2: Security & PIN
    print_section("Aşama 2: Güvenlik ve Eşleşme PIN Doğrulama")
    sec_tests = run_security_tests(target_host, target_port, valid_pin)
    for t in sec_tests:
        all_tests.append(t)
        print_test_result(t)

    # Phase 3: Integrity
    print_section("Aşama 3: Çift Yönlü Veri Aktarımı ve Bütünlük (SHA-256)")
    integrity_tests = run_integrity_tests(target_host, target_port, valid_pin)
    for t in integrity_tests:
        all_tests.append(t)
        print_test_result(t)

    # Phase 4: Stress
    print_section("Aşama 4: Sınır Durumlar ve Stres Testleri")
    stress_tests = run_stress_tests(target_host, target_port, valid_pin, args.concurrency)
    for t in stress_tests:
        all_tests.append(t)
        print_test_result(t)

    if mock_server:
        mock_server.shutdown()

    # Summarize
    duration_ms = int((time.perf_counter() - start_time) * 1000)
    passed_cnt = sum(1 for t in all_tests if t["status"] == "PASS")
    failed_cnt = sum(1 for t in all_tests if t["status"] == "FAIL")
    pass_rate = int((passed_cnt / len(all_tests)) * 100) if all_tests else 0

    target_info = {
        "host": target_host,
        "port": target_port,
        "appName": probe["server_info"].get("appName") if probe["server_info"] else "Sync Server"
    }

    summary = {
        "total": len(all_tests),
        "passed": passed_cnt,
        "failed": failed_cnt,
        "passRate": pass_rate,
        "durationMs": duration_ms,
        "target": target_info
    }

    print_summary(summary)
    save_reports(summary, all_tests, args.report_md, args.report_json)

    print(f"📄 Markdown Raporu : {args.report_md}")
    print(f"💾 JSON Raporu     : {args.report_json}\n")


if __name__ == "__main__":
    main()
