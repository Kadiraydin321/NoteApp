"""
Reference Mock Wi-Fi Sync Server (Python 3)
Zero-dependency HTTP server with in-memory store and PIN authentication.
"""
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.parse
import json
import io
import zipfile
import threading
import sys
import time

PORT = 8765
PIN = "1234"
MAX_PAYLOAD = 8 * 1024 * 1024

class MockHandler(BaseHTTPRequestHandler):
    categories = [
        {"id": "cat-py-1", "name": "Genel", "color": "#3b82f6", "icon": "folder"},
        {"id": "cat-py-2", "name": "Projeler", "color": "#10b981", "icon": "briefcase"}
    ]
    notes = [
        {"id": "note-py-1", "title": "Hoş Geldiniz", "content": "Not Defterim Wi-Fi Senkronizasyon", "categoryId": "cat-py-1"}
    ]
    pairing_pin = PIN

    def log_message(self, format, *args):
        # Suppress noisy standard HTTP logs
        return

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        # 1. Status API
        if path in ["/api/status", "/status"]:
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            data = {
                "status": "ok",
                "appName": "Not Defterim Mock Sync Server (Python)",
                "notesCount": len(self.notes),
                "requiresPin": True,
                "version": "2.1.0"
            }
            self.wfile.write(json.dumps(data).encode("utf-8"))
            return

        # 2. Download ZIP
        if path == "/api/sync/download":
            pin = self.headers.get("x-sync-pin") or self.headers.get("x-pairing-pin") or query.get("pin", [None])[0]
            if pin != self.pairing_pin:
                self.send_response(401)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.end_headers()
                self.wfile.write("Geçersiz Eşleşme PIN kodu!".encode("utf-8"))
                return

            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
                info = {"version": 2, "notesCount": len(self.notes), "categoriesCount": len(self.categories)}
                zf.writestr("backup_info.json", json.dumps(info, indent=2))
                zf.writestr("categories.json", json.dumps(self.categories, indent=2))
                zf.writestr("notes.json", json.dumps(self.notes, indent=2))

            zip_bytes = buf.getvalue()
            self.send_response(200)
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Disposition", 'attachment; filename="NoteApp_Sync.zip"')
            self.send_header("Content-Length", str(len(zip_bytes)))
            self.end_headers()
            self.wfile.write(zip_bytes)
            return

        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/sync/upload":
            pin = self.headers.get("x-sync-pin") or self.headers.get("x-pairing-pin")
            if pin != self.pairing_pin:
                self.send_response(401)
                self.end_headers()
                self.wfile.write(b"Unauthorized")
                return

            length = int(self.headers.get("Content-Length", 0))
            if length > MAX_PAYLOAD:
                self.send_response(413)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": False, "error": "Payload Too Large"}).encode("utf-8"))
                return

            body = self.rfile.read(length)
            try:
                with zipfile.ZipFile(io.BytesIO(body)) as zf:
                    if "notes.json" in zf.namelist():
                        new_notes = json.loads(zf.read("notes.json").decode("utf-8"))
                        self.notes.extend(new_notes)
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": True, "notesCount": len(self.notes)}).encode("utf-8"))
            except Exception as e:
                self.send_response(400)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"success": False, "error": f"Bozuk ZIP: {e}"}).encode("utf-8"))
            return

        self.send_response(404)
        self.end_headers()


def run_server(port=8765, pin="1234"):
    MockHandler.pairing_pin = pin
    server = HTTPServer(("0.0.0.0", port), MockHandler)
    print(f"[MockServer-Py] 🚀 Aktif: http://127.0.0.1:{port} (PIN: {pin})")
    server.serve_forever()


if __name__ == "__main__":
    p = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    c = sys.argv[2] if len(sys.argv) > 2 else "1234"
    run_server(p, c)
