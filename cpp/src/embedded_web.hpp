// cpp/src/embedded_web.hpp
// Auto-generated from cpp/web/index.html
#pragma once

namespace byterescue {

const char EMBEDDED_INDEX_HTML[] = R"html(
<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>ByteRescue // Veri Kurtarıcı - Kadir</title>
  <style>
    :root {
      --bg-base: #090d16;
      --bg-card: #111827;
      --bg-card-hover: #162032;
      --border-color: rgba(255, 255, 255, 0.08);
      --border-focus: rgba(16, 185, 129, 0.5);
      --accent: #10b981;
      --accent-hover: #059669;
      --accent-glow: rgba(16, 185, 129, 0.25);
      --accent-blue: #06b6d4;
      --text-main: #f9fafb;
      --text-muted: #9ca3af;
      --text-dim: #6b7280;
      --danger: #ef4444;
      --warning: #f59e0b;
      --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", Helvetica, Arial, sans-serif;
      --font-mono: "JetBrains Mono", "Cascadia Code", "Consolas", monospace;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg-base);
      color: var(--text-main);
      font-family: var(--font-sans);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
    }

    /* Scrollbar */
    ::-webkit-scrollbar { width: 8px; height: 8px; }
    ::-webkit-scrollbar-track { background: var(--bg-base); }
    ::-webkit-scrollbar-thumb { background: #1f293d; border-radius: 4px; }
    ::-webkit-scrollbar-thumb:hover { background: #374151; }

    /* Layout */
    header {
      height: 64px;
      background: rgba(17, 24, 39, 0.85);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 24px;
      position: sticky;
      top: 0;
      z-index: 50;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-icon {
      width: 36px;
      height: 36px;
      background: linear-gradient(135deg, #10b981 0%, #06b6d4 100%);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 0 15px var(--accent-glow);
    }
    .brand-icon svg { width: 20px; height: 20px; fill: #ffffff; }
    .brand-title {
      font-size: 17px;
      font-weight: 700;
      letter-spacing: -0.3px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .brand-badge {
      font-size: 10px;
      font-family: var(--font-mono);
      background: rgba(16, 185, 129, 0.15);
      color: var(--accent);
      padding: 2px 7px;
      border-radius: 6px;
      border: 1px solid rgba(16, 185, 129, 0.3);
      font-weight: 600;
    }
    .brand-sub {
      font-size: 11px;
      color: var(--text-dim);
    }

    .header-center {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 14px;
      border-radius: 20px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border-color);
      font-size: 12px;
      font-family: var(--font-mono);
      font-weight: 600;
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--text-dim);
    }
    .dot.running {
      background: var(--accent);
      box-shadow: 0 0 10px var(--accent);
      animation: pulse 1.5s infinite;
    }
    .dot.paused { background: var(--warning); box-shadow: 0 0 8px var(--warning); }
    .dot.error { background: var(--danger); box-shadow: 0 0 8px var(--danger); }

    @keyframes pulse {
      0% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.4; transform: scale(1.1); }
      100% { opacity: 1; transform: scale(1); }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 8px 16px;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
      border: 1px solid transparent;
      outline: none;
    }
    .btn-primary {
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      color: #ffffff;
      box-shadow: 0 2px 10px var(--accent-glow);
    }
    .btn-primary:hover {
      background: linear-gradient(135deg, #059669 0%, #047857 100%);
      box-shadow: 0 4px 15px var(--accent-glow);
    }
    .btn-secondary {
      background: rgba(255, 255, 255, 0.05);
      border-color: var(--border-color);
      color: var(--text-main);
    }
    .btn-secondary:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.2);
    }
    .btn-danger {
      background: rgba(239, 68, 68, 0.15);
      border-color: rgba(239, 68, 68, 0.3);
      color: #fca5a5;
    }
    .btn-danger:hover {
      background: rgba(239, 68, 68, 0.25);
    }
    .btn-warning {
      background: rgba(245, 158, 11, 0.15);
      border-color: rgba(245, 158, 11, 0.3);
      color: #fde68a;
    }
    .btn-warning:hover {
      background: rgba(245, 158, 11, 0.25);
    }
    .btn-sm { padding: 5px 10px; font-size: 12px; border-radius: 6px; }
    .btn:disabled { opacity: 0.4; cursor: not-allowed; }

    /* Main Container */
    main {
      flex: 1;
      padding: 24px;
      max-width: 1440px;
      margin: 0 auto;
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* Target Selector Card */
    .card {
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 18px 20px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
    }

    .section-label {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-dim);
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .targets-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    .target-pill {
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border-color);
      color: var(--text-muted);
      padding: 8px 14px;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 7px;
      transition: all 0.15s ease;
    }
    .target-pill:hover {
      background: var(--bg-card-hover);
      color: var(--text-main);
      border-color: rgba(255, 255, 255, 0.2);
    }
    .target-pill.active {
      background: rgba(16, 185, 129, 0.12);
      border-color: var(--accent);
      color: var(--accent);
      box-shadow: 0 0 12px rgba(16, 185, 129, 0.15);
    }
    .target-pill .sub {
      font-size: 10px;
      color: var(--text-dim);
      font-weight: 400;
    }

    .active-target-bar {
      margin-top: 14px;
      padding-top: 12px;
      border-top: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .current-path-info {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
    }
    .current-path-tag {
      font-family: var(--font-mono);
      font-size: 12px;
      background: rgba(0, 0, 0, 0.35);
      border: 1px solid var(--border-color);
      padding: 4px 10px;
      border-radius: 6px;
      color: var(--accent-blue);
      word-break: break-all;
    }

    .scan-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    /* KPI Metric Cards Grid */
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 14px;
    }

    .metric-card {
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 16px 18px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      transition: border-color 0.15s ease;
    }
    .metric-card:hover {
      border-color: rgba(255, 255, 255, 0.15);
    }
    .metric-title {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-dim);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .metric-value {
      font-size: 22px;
      font-weight: 700;
      font-family: var(--font-mono);
      color: var(--text-main);
    }
    .metric-sub {
      font-size: 11px;
      color: var(--text-muted);
    }

    /* Content Area: Table & Activity */
    .content-card {
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .content-header {
      padding: 14px 20px;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .filter-tabs {
      display: flex;
      gap: 4px;
      background: rgba(0, 0, 0, 0.25);
      padding: 4px;
      border-radius: 8px;
    }
    .tab-btn {
      background: transparent;
      border: none;
      color: var(--text-dim);
      font-size: 12px;
      font-weight: 600;
      padding: 6px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.15s ease;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .tab-btn:hover { color: var(--text-main); }
    .tab-btn.active {
      background: rgba(255, 255, 255, 0.08);
      color: var(--accent);
    }
    .tab-badge {
      font-size: 10px;
      background: rgba(255, 255, 255, 0.1);
      padding: 1px 6px;
      border-radius: 10px;
      color: var(--text-muted);
    }

    .search-input {
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border-color);
      color: var(--text-main);
      padding: 7px 12px;
      border-radius: 8px;
      font-size: 12px;
      width: 220px;
      outline: none;
      transition: border-color 0.15s ease;
    }
    .search-input:focus {
      border-color: var(--accent);
    }

    /* Table */
    .table-wrapper {
      max-height: 480px;
      overflow-y: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }
    th {
      position: sticky;
      top: 0;
      background: #131c2e;
      padding: 10px 16px;
      font-size: 11px;
      font-weight: 700;
      color: var(--text-dim);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      border-bottom: 1px solid var(--border-color);
      z-index: 10;
    }
    td {
      padding: 11px 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      color: var(--text-muted);
      vertical-align: middle;
    }
    tr:hover td {
      background: rgba(255, 255, 255, 0.02);
    }

    .type-pill {
      font-family: var(--font-mono);
      font-size: 10px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
      display: inline-block;
      text-transform: uppercase;
    }
    .type-png, .type-jpg, .type-jpeg, .type-webp, .type-gif { background: rgba(6, 182, 212, 0.15); color: #22d3ee; }
    .type-pdf, .type-doc, .type-docx, .type-txt { background: rgba(239, 68, 68, 0.15); color: #f87171; }
    .type-zip, .type-rar, .type-7z { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
    .type-mp4, .type-mp3, .type-wav { background: rgba(168, 85, 247, 0.15); color: #c084fc; }

    .file-name-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .file-name {
      font-weight: 600;
      color: var(--text-main);
    }
    .file-orig-path {
      font-size: 11px;
      color: var(--text-dim);
      font-family: var(--font-mono);
    }

    .selection-bar {
      padding: 12px 20px;
      background: rgba(16, 185, 129, 0.08);
      border-top: 1px solid rgba(16, 185, 129, 0.2);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    /* Modal */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(6px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 100;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.2s ease;
    }
    .modal-backdrop.open {
      opacity: 1;
      pointer-events: auto;
    }
    .modal-dialog {
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      width: 90%;
      max-width: 780px;
      max-height: 85vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6);
      transform: translateY(20px);
      transition: transform 0.2s ease;
    }
    .modal-backdrop.open .modal-dialog {
      transform: translateY(0);
    }
    .modal-header {
      padding: 16px 20px;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .modal-title {
      font-size: 15px;
      font-weight: 700;
    }
    .modal-body {
      padding: 20px;
      overflow-y: auto;
      font-family: var(--font-mono);
      font-size: 12px;
      line-height: 1.6;
      background: #090e18;
      color: #38bdf8;
      white-space: pre;
    }
    .modal-footer {
      padding: 14px 20px;
      border-top: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;
    }

    /* Toast Notification */
    .toast {
      position: fixed;
      bottom: 24px;
      right: 24px;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      padding: 14px 18px;
      border-radius: 10px;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 12px;
      z-index: 200;
      transform: translateY(100px);
      opacity: 0;
      transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .toast.show {
      transform: translateY(0);
      opacity: 1;
    }
    .toast.success { border-color: var(--accent); }
    .toast.error { border-color: var(--danger); }
  </style>
</head>
<body>

  <!-- 1. Header -->
  <header>
    <div class="brand">
      <div class="brand-icon">
        <svg viewBox="0 0 24 24"><path d="M12 2L3 7v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V7l-9-5zm0 2.18l7 3.89v4.93c0 4.54-3.07 8.79-7 9.94-3.93-1.15-7-5.4-7-9.94V8.07l7-3.89zM11 7v6h2V7h-2zm0 8v2h2v-2h-2z"/></svg>
      </div>
      <div>
        <div class="brand-title">
          <span>ByteRescue</span>
          <span class="brand-badge">C++ MOTORU</span>
          <span class="brand-badge" id="adminBadge" style="display:none; background: rgba(6,182,212,0.15); color: #22d3ee; border-color: rgba(6,182,212,0.3);">🛡️ YÖNETİCİ</span>
        </div>
        <div class="brand-sub">Veri Kurtarıcı • Kadir</div>
      </div>
    </div>

    <div class="header-center">
      <div class="status-pill">
        <div class="dot" id="statusDot"></div>
        <span id="statusText">SİSTEM HAZIR</span>
      </div>
    </div>

    <div class="header-right">
      <button class="btn btn-secondary btn-sm" id="btnDestDir" title="Kurtarılan Dosyaların Kaydedileceği Dizin">
        <span>Hedef:</span>
        <strong id="destDirName">Masaüstü</strong>
      </button>
      <button class="btn btn-secondary btn-sm" id="btnAbout" title="Geliştirici & Bilgi">
        <span>Bilgi</span>
      </button>
      <button class="btn btn-danger btn-sm" id="btnShutdownPC" title="İşlem Bitince Bilgisayarı Kapat">
        <span>PC Kapat</span>
      </button>
      <button class="btn btn-secondary btn-sm" id="btnExitApp" title="ByteRescue Sunucusunu Kapat">
        <span>Çıkış</span>
      </button>
    </div>
  </header>

  <!-- 2. Main Area -->
  <main>
    <!-- Target Selection Card -->
    <section class="card">
      <div class="section-label">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>
        <span>Taranacak Sürücü veya Klasör</span>
      </div>

      <div class="targets-row" id="targetsList">
        <!-- Injected via JavaScript from /api/drives -->
        <button class="target-pill active" data-path="C:\\">
          <span>💻 C: Sürücüsü</span>
          <span class="sub">(Sistem)</span>
        </button>
      </div>

      <div class="active-target-bar">
        <div class="current-path-info">
          <span>Aktif Hedef:</span>
          <span class="current-path-tag" id="currentPathText">C:\</span>
        </div>

        <div class="scan-actions">
          <button class="btn btn-primary" id="btnStartScan">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
            <span>Taramayı Başlat</span>
          </button>
          <button class="btn btn-warning btn-sm" id="btnPauseScan" disabled>Duraklat</button>
          <button class="btn btn-danger btn-sm" id="btnStopScan" disabled>Durdur</button>
        </div>
      </div>
    </section>

    <!-- Metrics KPI Grid -->
    <section class="metrics-grid">
      <div class="metric-card">
        <div class="metric-title">
          <span>Taranan Veri</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path></svg>
        </div>
        <div class="metric-value" id="valBytes">0 B</div>
        <div class="metric-sub" id="valLocation">Hazır</div>
      </div>

      <div class="metric-card">
        <div class="metric-title">
          <span>Tarama Hızı</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
        </div>
        <div class="metric-value" id="valSpeed">0 MB/s</div>
        <div class="metric-sub">Gerçek zamanlı hız</div>
      </div>

      <div class="metric-card">
        <div class="metric-title">
          <span>Bulunan Dosyalar</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
        </div>
        <div class="metric-value" id="valFiles">0</div>
        <div class="metric-sub">Kurtarılabilir imza</div>
      </div>

      <div class="metric-card">
        <div class="metric-title">
          <span>Geçen Süre</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
        </div>
        <div class="metric-value" id="valElapsed">00:00</div>
        <div class="metric-sub">C++ çok kanallı tarama</div>
      </div>
    </section>

    <!-- Discovered Files Table & Filter -->
    <section class="content-card">
      <div class="content-header">
        <div class="filter-tabs">
          <button class="tab-btn active" data-cat="all">Tümü <span class="tab-badge" id="badgeAll">0</span></button>
          <button class="tab-btn" data-cat="images">Resimler <span class="tab-badge" id="badgeImages">0</span></button>
          <button class="tab-btn" data-cat="documents">Belgeler <span class="tab-badge" id="badgeDocs">0</span></button>
          <button class="tab-btn" data-cat="media">Medya <span class="tab-badge" id="badgeMedia">0</span></button>
          <button class="tab-btn" data-cat="archives">Arşivler <span class="tab-badge" id="badgeArchives">0</span></button>
        </div>

        <input type="text" class="search-input" id="searchInput" placeholder="Dosya veya uzantı ara...">
      </div>

      <div class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th width="40"><input type="checkbox" id="selectAllCheckbox"></th>
              <th width="80">Tür</th>
              <th>Dosya Adı & Konum</th>
              <th width="100">Boyut</th>
              <th width="120">Ofset</th>
              <th width="80">Güven</th>
              <th width="150" style="text-align: right;">İşlem</th>
            </tr>
          </thead>
          <tbody id="filesTableBody">
            <tr>
              <td colspan="7" style="text-align: center; padding: 40px; color: var(--text-dim);">
                Henüz tarama başlatılmadı. Taranacak sürücü veya klasörü seçip <strong>Taramayı Başlat</strong> butonuna basın.
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="selection-bar" id="selectionBar" style="display: none;">
        <div>
          <strong id="selectedCountText">0</strong> dosya seçildi
        </div>
        <button class="btn btn-primary btn-sm" id="btnRecoverSelected">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
          <span>Seçilenleri Kurtar</span>
        </button>
      </div>
    </section>
  </main>

  <!-- Hex Viewer Modal -->
  <div class="modal-backdrop" id="hexModal">
    <div class="modal-dialog">
      <div class="modal-header">
        <div class="modal-title" id="hexModalTitle">Hex İnceleme</div>
        <button class="btn btn-secondary btn-sm" id="btnCloseHexModal">Kapat</button>
      </div>
      <div class="modal-body" id="hexModalBody">Yükleniyor...</div>
      <div class="modal-footer">
        <button class="btn btn-primary btn-sm" id="btnRecoverModalFile">Bu Dosyayı Kurtar</button>
      </div>
    </div>
  </div>

  <!-- Toast Notification -->
  <div class="toast" id="toast">
    <span id="toastMessage">Bildirim mesajı</span>
  </div>

  <!-- Application Logic -->
  <script>
    let currentPath = "C:\\";
    let destDir = "C:\\Users\\Birin\\Desktop\\ByteRescue_Kurtarilan";
    let isScanning = false;
    let pollTimer = null;
    let elapsedTimer = null;
    let startTime = 0;
    let allFiles = [];
    let selectedFileIds = new Set();
    let currentFilter = "all";
    let activeHexFile = null;

    // Elements
    const statusDot = document.getElementById('statusDot');
    const statusText = document.getElementById('statusText');
    const currentPathText = document.getElementById('currentPathText');
    const destDirName = document.getElementById('destDirName');
    const btnStartScan = document.getElementById('btnStartScan');
    const btnPauseScan = document.getElementById('btnPauseScan');
    const btnStopScan = document.getElementById('btnStopScan');
    const targetsList = document.getElementById('targetsList');
    const filesTableBody = document.getElementById('filesTableBody');
    const selectAllCheckbox = document.getElementById('selectAllCheckbox');
    const selectionBar = document.getElementById('selectionBar');
    const selectedCountText = document.getElementById('selectedCountText');
    const btnRecoverSelected = document.getElementById('btnRecoverSelected');
    const searchInput = document.getElementById('searchInput');

    // Init
    window.addEventListener('DOMContentLoaded', async () => {
      await loadDefaultDest();
      await loadDrives();
      startPolling();
    });

    async function loadDefaultDest() {
      try {
        const res = await fetch('/api/default-dest');
        const data = await res.json();
        if (data && data.path) {
          destDir = data.path;
          const parts = destDir.split(/[\\/]/).filter(Boolean);
          destDirName.textContent = parts[parts.length - 1] || destDir;
        }
      } catch (_) {}
    }

    async function loadDrives() {
      try {
        const res = await fetch('/api/drives');
        const drives = await res.json();
        if (!Array.isArray(drives) || drives.length === 0) return;

        targetsList.innerHTML = '';
        drives.forEach((d, idx) => {
          const btn = document.createElement('button');
          btn.className = 'target-pill' + (idx === 0 ? ' active' : '');
          btn.dataset.path = d.path;

          let icon = '💻';
          if (d.type === 'recycle_bin') icon = '🗑️';
          else if (d.id.includes('desktop')) icon = '🖥️';
          else if (d.id.includes('downloads')) icon = '📥';
          else if (d.id.includes('drive_d')) icon = '💾';

          btn.innerHTML = `<span>${icon} ${d.name}</span><span class="sub">(${d.sizeHuman || d.badge})</span>`;
          btn.onclick = () => selectTarget(d.path, btn);
          targetsList.appendChild(btn);

          if (idx === 0) currentPath = d.path;
        });

        // Add "Klasör Seç" button
        const browseBtn = document.createElement('button');
        browseBtn.className = 'target-pill';
        browseBtn.innerHTML = `<span>📂 Klasör Seç...</span>`;
        browseBtn.onclick = handleBrowseFolder;
        targetsList.appendChild(browseBtn);

        // Add "Test Diski" button
        const testBtn = document.createElement('button');
        testBtn.className = 'target-pill';
        testBtn.innerHTML = `<span>🧪 Test Diski</span>`;
        testBtn.onclick = handleTestDisk;
        targetsList.appendChild(testBtn);

        currentPathText.textContent = currentPath;
      } catch (err) {
        console.error('Drives yüklenemedi:', err);
      }
    }

    function selectTarget(path, element) {
      if (isScanning) return;
      currentPath = path;
      currentPathText.textContent = path;
      document.querySelectorAll('.target-pill').forEach(el => el.classList.remove('active'));
      if (element) element.classList.add('active');
    }

    async function handleBrowseFolder() {
      if (isScanning) return;
      try {
        const res = await fetch('/api/browse-folder', { method: 'POST' });
        const data = await res.json();
        if (data && data.path) {
          selectTarget(data.path, null);
          showToast('Klasör seçildi: ' + data.path, 'success');
        }
      } catch (err) {
        showToast('Klasör seçimi başarısız: ' + err.message, 'error');
      }
    }

    async function handleTestDisk() {
      if (isScanning) return;
      try {
        const res = await fetch('/api/create-test-disk');
        const data = await res.json();
        if (data && data.path) {
          selectTarget(data.path, null);
          showToast('10 MB Test Diski Hazırlandı: ' + data.path, 'success');
        }
      } catch (err) {
        showToast('Test diski oluşturulamadı', 'error');
      }
    }

    btnStartScan.onclick = async () => {
      if (!currentPath) {
        showToast('Lütfen taranacak bir konum seçin', 'error');
        return;
      }

      try {
        if (isScanning) {
          await fetch('/api/scan/stop', { method: 'POST' });
          await new Promise(r => setTimeout(r, 300));
        }

        const res = await fetch('/api/scan/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sourcePath: currentPath,
            categories: ["all"],
            deepCarve: true
          })
        });
        const data = await res.json();
        if (data.success) {
          isScanning = true;
          startTime = Date.now();
          startElapsedTimer();
          updateButtonsState(true);
          showToast('Tarama başlatıldı: ' + currentPath, 'success');
        } else {
          showToast('Başlatılamadı: ' + (data.error || 'Bilinmeyen hata'), 'error');
        }
      } catch (err) {
        showToast('Hata: ' + err.message, 'error');
      }
    };

    btnPauseScan.onclick = async () => {
      const isPaused = btnPauseScan.textContent === 'Devam Et';
      await fetch(isPaused ? '/api/scan/resume' : '/api/scan/pause', { method: 'POST' });
      btnPauseScan.textContent = isPaused ? 'Duraklat' : 'Devam Et';
    };

    btnStopScan.onclick = async () => {
      await fetch('/api/scan/stop', { method: 'POST' });
      isScanning = false;
      updateButtonsState(false);
      showToast('Tarama durduruldu', 'info');
    };

    function updateButtonsState(running) {
      btnStartScan.disabled = running;
      btnPauseScan.disabled = !running;
      btnStopScan.disabled = !running;
      btnPauseScan.textContent = 'Duraklat';
    }

    function startElapsedTimer() {
      if (elapsedTimer) clearInterval(elapsedTimer);
      elapsedTimer = setInterval(() => {
        if (!isScanning) return;
        const diff = Math.floor((Date.now() - startTime) / 1000);
        const m = String(Math.floor(diff / 60)).padStart(2, '0');
        const s = String(diff % 60).padStart(2, '0');
        document.getElementById('valElapsed').textContent = `${m}:${s}`;
      }, 1000);
    }

    function startPolling() {
      if (pollTimer) clearInterval(pollTimer);
      pollTimer = setInterval(async () => {
        try {
          const res = await fetch('/api/status');
          const stats = await res.json();

          if (stats.isAdmin) {
            const ab = document.getElementById('adminBadge');
            if (ab) ab.style.display = 'inline-block';
          }

          if (stats.status === 'running') {
            statusDot.className = 'dot running';
            statusText.textContent = 'TARANIYOR...';
            isScanning = true;
            updateButtonsState(true);
          } else if (stats.status === 'paused') {
            statusDot.className = 'dot paused';
            statusText.textContent = 'DURAKLATILDI';
          } else if (stats.status === 'completed') {
            statusDot.className = 'dot';
            statusText.textContent = 'TAMAMLANDI';
            if (isScanning) {
              isScanning = false;
              updateButtonsState(false);
              showToast('Tarama başarıyla tamamlandı!', 'success');
            }
          } else {
            statusDot.className = 'dot';
            statusText.textContent = 'SİSTEM HAZIR';
            isScanning = false;
            updateButtonsState(false);
          }

          document.getElementById('valBytes').textContent = formatBytes(stats.bytesScanned || 0);
          document.getElementById('valSpeed').textContent = (stats.speedMBps ? stats.speedMBps.toFixed(1) : '0') + ' MB/s';
          document.getElementById('valFiles').textContent = stats.filesFound || 0;
          if (stats.currentLocation) {
            const parts = stats.currentLocation.split(/[\\/]/);
            document.getElementById('valLocation').textContent = parts[parts.length - 1] || 'Taranıyor...';
          }

          // Fetch files list if running or finished
          if (isScanning || stats.filesFound > allFiles.length) {
            await fetchFiles();
          }
        } catch (_) {}
      }, 800);
    }

    async function fetchFiles() {
      try {
        const res = await fetch('/api/files');
        allFiles = await res.json();
        updateCategoryBadges();
        renderFilesTable();
      } catch (_) {}
    }

    function updateCategoryBadges() {
      document.getElementById('badgeAll').textContent = allFiles.length;
      document.getElementById('badgeImages').textContent = allFiles.filter(f => f.category === 'images').length;
      document.getElementById('badgeDocs').textContent = allFiles.filter(f => f.category === 'documents').length;
      document.getElementById('badgeMedia').textContent = allFiles.filter(f => f.category === 'media').length;
      document.getElementById('badgeArchives').textContent = allFiles.filter(f => f.category === 'archives').length;
    }

    function renderFilesTable() {
      const q = searchInput.value.toLowerCase().trim();
      const filtered = allFiles.filter(f => {
        if (currentFilter !== 'all' && f.category !== currentFilter) return false;
        if (q && !f.name.toLowerCase().includes(q) && !f.type.toLowerCase().includes(q) && !f.hexOffset.toLowerCase().includes(q)) return false;
        return true;
      });

      if (filtered.length === 0) {
        filesTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 40px; color: var(--text-dim);">${isScanning ? 'Aranıyor...' : 'Seçilen kriterde dosya bulunamadı.'}</td></tr>`;
        return;
      }

      filesTableBody.innerHTML = filtered.map(f => {
        const isChecked = selectedFileIds.has(f.id) ? 'checked' : '';
        const canRecover = f.isRecoverable !== false && f.confidence > 0;
        const statusBadge = canRecover 
          ? `<span style="font-size: 10px; font-weight: 700; background: rgba(16,185,129,0.15); color: var(--accent); padding: 2px 7px; border-radius: 4px; border: 1px solid rgba(16,185,129,0.3);">%${f.confidence || 100} ${f.statusLabel || 'Kurtarılabilir'}</span>`
          : `<span style="font-size: 10px; font-weight: 700; background: rgba(239,68,68,0.15); color: #fca5a5; padding: 2px 7px; border-radius: 4px; border: 1px solid rgba(239,68,68,0.3);">${f.statusLabel || 'Veri Yok'}</span>`;

        return `
          <tr style="${canRecover ? '' : 'opacity: 0.7;'}">
            <td><input type="checkbox" class="file-chk" data-id="${f.id}" ${isChecked} ${canRecover ? '' : 'disabled'} onchange="toggleSelect('${f.id}')"></td>
            <td><span class="type-pill type-${(f.type || 'bin').toLowerCase()}">${(f.type || 'BIN').toUpperCase()}</span></td>
            <td>
              <div class="file-name-cell">
                <span class="file-name">${escapeHtml(f.name)}</span>
                ${f.originalPath ? `<span class="file-orig-path">${escapeHtml(f.originalPath)}</span>` : ''}
              </div>
            </td>
            <td><strong>${f.sizeHuman || formatBytes(f.sizeBytes)}</strong></td>
            <td><span style="font-family: var(--font-mono); color: var(--accent-blue); font-size: 11px;">${f.hexOffset}</span></td>
            <td>${statusBadge}</td>
            <td style="text-align: right;">
              <button class="btn btn-secondary btn-sm" onclick="showHexModal('${f.id}')">Hex İncele</button>
              <button class="btn btn-primary btn-sm" onclick="recoverSingle('${f.id}')" ${canRecover ? '' : 'disabled title="Bu dosyanın verisi diskten temizlenmiş"'}>Kurtar</button>
            </td>
          </tr>
        `;
      }).join('');
    }

    window.toggleSelect = (id) => {
      if (selectedFileIds.has(id)) selectedFileIds.delete(id);
      else selectedFileIds.add(id);
      updateSelectionBar();
    };

    selectAllCheckbox.onchange = () => {
      if (selectAllCheckbox.checked) {
        allFiles.forEach(f => {
          if (f.isRecoverable !== false && f.confidence > 0) {
            selectedFileIds.add(f.id);
          }
        });
      } else {
        selectedFileIds.clear();
      }
      renderFilesTable();
      updateSelectionBar();
    };

    function updateSelectionBar() {
      selectedCountText.textContent = selectedFileIds.size;
      selectionBar.style.display = selectedFileIds.size > 0 ? 'flex' : 'none';
    }

    // Filter Tabs
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        currentFilter = btn.dataset.cat;
        renderFilesTable();
      };
    });

    searchInput.oninput = () => renderFilesTable();

    // Hex & Preview Modal
    window.showHexModal = async (fileId) => {
      activeHexFile = fileId;
      document.getElementById('hexModal').classList.add('open');
      document.getElementById('hexModalBody').innerHTML = '<div style="padding: 20px; color: var(--text-dim);">Önizleme ve Hex yükleniyor...</div>';
      try {
        const res = await fetch('/api/preview?id=' + encodeURIComponent(fileId));
        const data = await res.json();
        let html = '';
        if (data.type === 'image' && data.data) {
          html += `<div style="text-align: center; margin-bottom: 16px; background: rgba(0,0,0,0.5); padding: 14px; border-radius: 8px;">
            <img src="${data.data}" style="max-height: 280px; max-width: 100%; border-radius: 6px; object-fit: contain; box-shadow: 0 4px 14px rgba(0,0,0,0.6);" alt="Kurtarılan Görsel">
          </div>`;
        } else if (data.type === 'text' && data.data) {
          html += `<div style="padding: 12px; background: rgba(255,255,255,0.04); border-radius: 8px; margin-bottom: 14px; font-family: var(--font-mono); font-size: 11px; color: #f3f4f6; max-height: 180px; overflow-y: auto; white-space: pre-wrap; border: 1px solid var(--border-color);">${escapeHtml(data.data)}</div>`;
        }
        if (data.hex) {
          html += `<div style="font-family: var(--font-mono); font-size: 11px; color: #38bdf8; white-space: pre;">${escapeHtml(data.hex)}</div>`;
        }
        document.getElementById('hexModalBody').innerHTML = html;
      } catch (err) {
        document.getElementById('hexModalBody').innerHTML = `<div style="color: var(--danger); padding: 12px;">Önizleme yüklenemedi: ${escapeHtml(err.message)}</div>`;
      }
    };

    document.getElementById('btnCloseHexModal').onclick = () => {
      document.getElementById('hexModal').classList.remove('open');
    };

    document.getElementById('btnRecoverModalFile').onclick = () => {
      if (activeHexFile) {
        recoverSingle(activeHexFile);
        document.getElementById('hexModal').classList.remove('open');
      }
    };

    // Recover Operations
    window.recoverSingle = async (fileId) => {
      await executeRecovery([fileId]);
    };

    btnRecoverSelected.onclick = async () => {
      if (selectedFileIds.size === 0) return;
      await executeRecovery(Array.from(selectedFileIds));
    };

    async function executeRecovery(fileIds) {
      if (!fileIds || fileIds.length === 0) {
        showToast('Kurtarılacak dosya seçilmedi', 'warning');
        return;
      }
      showToast(`${fileIds.length} dosya kurtarılıyor -> ${destDir}...`, 'info');
      try {
        const res = await fetch('/api/recover', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ fileIds, destDir })
        });
        const data = await res.json();
        if (data.success && data.recoveredCount > 0) {
          showToast(`✅ ${data.recoveredCount} dosya başarıyla kurtarıldı! (${destDir})`, 'success');
        } else if (data.failedCount > 0 && data.recoveredCount === 0) {
          showToast(`⚠️ Seçilen dosyanın içeriği diskten temizlenmiş / üzerine yazılmış.`, 'error');
        } else {
          showToast(`Kurtarılamadı: ${data.error || '0 dosya kurtarıldı'}`, 'error');
        }
      } catch (err) {
        showToast('Kurtarma hatası: ' + err.message, 'error');
      }
    }

    // Destination Directory Change
    document.getElementById('btnDestDir').onclick = async () => {
      try {
        const res = await fetch('/api/browse-folder', { method: 'POST' });
        const data = await res.json();
        if (data && data.path) {
          destDir = data.path;
          const parts = destDir.split(/[\\/]/).filter(Boolean);
          destDirName.textContent = parts[parts.length - 1] || destDir;
          showToast('Hedef kurtarma klasörü belirlendi: ' + destDir, 'success');
        }
      } catch (err) {
        showToast('Klasör seçilemedi: ' + err.message, 'error');
      }
    };

    // Shutdown PC Button
    const btnShutdownPC = document.getElementById('btnShutdownPC');
    if (btnShutdownPC) {
      btnShutdownPC.onclick = async () => {
        if (confirm('Bilgisayar kapatılacak. Emin misiniz?')) {
          showToast('Bilgisayar 10 saniye içinde kapatılıyor...', 'warning');
          await fetch('/api/shutdown-computer', { method: 'POST' });
        }
      };
    }

    // Exit Application Button
    const btnExitApp = document.getElementById('btnExitApp');
    if (btnExitApp) {
      btnExitApp.onclick = async () => {
        if (confirm('ByteRescue sunucusunu kapatmak istediğinize emin misiniz?')) {
          await fetch('/api/exit', { method: 'POST' });
          document.body.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;color:#9ca3af;font-size:16px;">ByteRescue başarıyla kapatıldı. Sekmeyi kapatabilirsiniz.</div>';
        }
      };
    }

    // Toast
    function showToast(msg, type = 'info') {
      const toast = document.getElementById('toast');
      const toastMessage = document.getElementById('toastMessage');
      toastMessage.textContent = msg;
      toast.className = 'toast show ' + type;
      setTimeout(() => { toast.classList.remove('show'); }, 4000);
    }

    function formatBytes(bytes) {
      if (!bytes || bytes === 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    function escapeHtml(str) {
      if (!str) return '';
      return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    document.getElementById('btnAbout').onclick = () => {
      alert("⚡ ByteRescue // Veri Kurtarıcı\n\nAltyapı: Yüksek Başarımlı Native C++ Motoru\nGeliştirici: Kadir\n\nÖzellikler:\n- Ham sektör ve blok taraması\n- Geri Dönüşüm Kutusu ($Recycle.Bin) metaveri kurtarma\n- PNG, JPG, PDF, ZIP/DOCX, MP4, MP3 vb. imza ayrıştırma\n- Çevrimdışı ve tek parça standalone çalışır.");
    };
  </script>
</body>
</html>
)html";

} // namespace byterescue
