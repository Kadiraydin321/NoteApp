"""
Discovery & Ping Probe Module (Python 3)
Zero-dependency network scanner and server identity prober.
"""
import socket
import urllib.request
import urllib.error
import json
import time
from typing import Dict, Any, List, Optional


def tcp_ping(host: str, port: int, timeout_sec: float = 2.5) -> Dict[str, Any]:
    """Measures socket connection latency in milliseconds."""
    start = time.perf_counter()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout_sec)
    try:
        sock.connect((host, port))
        latency_ms = (time.perf_counter() - start) * 1000.0
        return {"reachable": True, "latency_ms": round(latency_ms, 2)}
    except socket.timeout:
        return {"reachable": False, "latency_ms": -1.0, "error": "Connection timed out"}
    except Exception as e:
        return {"reachable": False, "latency_ms": -1.0, "error": str(e)}
    finally:
        sock.close()


def probe_target(host: str = "127.0.0.1", port: int = 8765, timeout_sec: float = 3.0) -> Dict[str, Any]:
    """Probes status endpoint and parses app name, notes count, and pin requirement."""
    res = {
        "host": host,
        "port": port,
        "tcp_reachable": False,
        "tcp_latency_ms": -1.0,
        "http_status": None,
        "http_latency_ms": -1.0,
        "server_info": None,
        "is_sync_server": False,
        "error": None
    }

    ping = tcp_ping(host, port, timeout_sec)
    res["tcp_reachable"] = ping["reachable"]
    res["tcp_latency_ms"] = ping["latency_ms"]

    if not ping["reachable"]:
        res["error"] = f"TCP Ping Hatası: {ping.get('error')}"
        return res

    endpoints = ["/api/status", "/status"]
    for ep in endpoints:
        url = f"http://{host}:{port}{ep}"
        start = time.perf_counter()
        req = urllib.request.Request(url, headers={"User-Agent": "SyncTester-Probe/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
                diff_ms = (time.perf_counter() - start) * 1000.0
                res["http_status"] = resp.status
                res["http_latency_ms"] = round(diff_ms, 2)
                body = resp.read().decode("utf-8")
                try:
                    data = json.loads(body)
                    res["server_info"] = {
                        "appName": data.get("appName", "Unknown Sync Server"),
                        "status": data.get("status", "ok"),
                        "notesCount": data.get("notesCount", 0),
                        "requiresPin": data.get("requiresPin", True),
                        "version": data.get("version", "1.0"),
                        "raw": data
                    }
                    res["is_sync_server"] = True
                    return res
                except Exception:
                    res["server_info"] = {"raw": body[:100]}
        except urllib.error.HTTPError as e:
            res["http_status"] = e.code
        except Exception as e:
            res["error"] = str(e)

    return res
