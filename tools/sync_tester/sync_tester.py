#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Cross-Platform LAN / Wi-Fi Sync Tester (Python Edition)
Sıfır harici kütüphane bağımlılığı - Standart Python 3.8+ kütüphaneleri (urllib, json, hashlib) ile çalışır.
"""

import sys
import os
import json
import socket
import argparse
import time
import hashlib
from urllib import request, error

class PythonSyncTester:
    def __init__(self, host="127.0.0.1", port=8765, pin=None):
        self.host = host
        self.port = port
        self.pin = pin
        self.base_url = f"http://{host}:{port}"
        self.results = []

    def check_port(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2.0)
        try:
            s.connect((self.host, self.port))
            s.close()
            return True
        except Exception:
            return False

    def request(self, method, endpoint, headers=None, body=None, timeout=5.0):
        url = f"{self.base_url}{endpoint if endpoint.startswith('/') else '/' + endpoint}"
        hdrs = headers or {}
        req = request.Request(url, data=body, headers=hdrs, method=method)
        t0 = time.time()
        try:
            with request.urlopen(req, timeout=timeout) as resp:
                data = resp.read()
                latency = int((time.time() - t0) * 1000)
                try:
                    parsed_json = json.loads(data.decode("utf-8"))
                except Exception:
                    parsed_json = None
                return {"status": resp.status, "data": data, "json": parsed_json, "latency": latency}
        except error.HTTPError as e:
            data = e.read()
            latency = int((time.time() - t0) * 1000)
            return {"status": e.code, "data": data, "json": None, "latency": latency}
        except Exception as e:
            latency = int((time.time() - t0) * 1000)
            return {"status": 0, "error": str(e), "latency": latency}

    def run(self):
        print(f"\n========================================================")
        print(f"🚀 Python Wi-Fi Senkronizasyon Test Aracı")
        print(f"Hedef: {self.base_url}")
        print(f"========================================================\n")

        if not self.check_port():
            print(f"❌ [HATA] {self.host}:{self.port} portuna bağlanılamadı!")
            return False

        print("✅ Port açık ve yanıt veriyor.")

        # Status Test
        res = self.request("GET", "/api/status")
        if res.get("status") == 200 and res.get("json"):
            info = res["json"]
            print(f"✅ Sunucu çevrimiçi: {info.get('appName', 'Bilinmiyor')} (Not Sayısı: {info.get('notesCount', 0)}, Gecikme: {res['latency']} ms)")
        else:
            print(f"⚠️ /api/status yerine /status deneniyor...")
            res2 = self.request("GET", "/status")
            print(f"   Yanıt: HTTP {res2.get('status')}")

        # Auth Test
        res_no_pin = self.request("GET", "/api/sync/download")
        if res_no_pin.get("status") == 401:
            print("✅ PIN Koruması: Eksik PIN başarıyla HTTP 401 ile engellendi.")
        else:
            print(f"⚠️ PIN Koruması: Beklenen HTTP 401, alınan: {res_no_pin.get('status')}")

        if self.pin:
            res_pin = self.request("GET", "/api/sync/download", headers={"X-Sync-PIN": self.pin})
            if res_pin.get("status") == 200:
                size = len(res_pin.get("data", b""))
                sha = hashlib.sha256(res_pin.get("data", b"")).hexdigest()
                print(f"✅ Başarılı PIN Yetkilendirmesi: {size} bayt veri alındı. (SHA-256: {sha[:16]}...)")
            else:
                print(f"❌ PIN Yetkilendirmesi Başarısız: HTTP {res_pin.get('status')}")

        print(f"\n========================================================")
        print("🎉 Python Test Döngüsü Tamamlandı.")
        print(f"========================================================\n")
        return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Cross-Platform Wi-Fi Sync Tester (Python Edition)")
    parser.add_argument("--host", default="127.0.0.1", help="Hedef IP adresi")
    parser.add_argument("--port", type=int, default=8765, help="Hedef Port")
    parser.add_argument("--pin", default=None, help="Eşleşme PIN kodu")
    args = parser.parse_args()

    tester = PythonSyncTester(host=args.host, port=args.port, pin=args.pin)
    success = tester.run()
    sys.exit(0 if success else 1)
