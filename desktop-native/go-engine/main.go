package main

import (
	"archive/zip"
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
)

// ===================================================
// ROOM v1.4.4 & APP.JS UYUMLU VERİ MODELLERİ
// ===================================================

type Note struct {
	ID               int64    `json:"id"`
	Title            string   `json:"title"`
	Content          string   `json:"content"`
	Color            int64    `json:"color"`
	CategoryID       *int64   `json:"categoryId"`
	IsPinned         bool     `json:"isPinned"`
	IsLocked         bool     `json:"isLocked"`
	IsArchived       bool     `json:"isArchived"`
	IsInTrash        bool     `json:"isInTrash"`
	IsDeleted        bool     `json:"isDeleted"`
	IsVoiceNote      bool     `json:"isVoiceNote"`
	VoiceAudioPath   string   `json:"voiceAudioPath"`
	VoiceDuration    int64    `json:"voiceDuration"`
	IsChecklist      bool     `json:"isChecklist"`
	ChecklistData    string   `json:"checklistData"`
	ReminderDateTime *int64   `json:"reminderDateTime"`
	ReminderTime     *int64   `json:"reminderTime"`
	MediaUris        []string `json:"mediaUris"`
	Tags             []string `json:"tags"`
	Attachments      []string `json:"attachments"`
	BackgroundImage  string   `json:"backgroundImage"`
	FolderID         *int64   `json:"folderId"`
	CreatedAt        int64    `json:"createdAt"`
	UpdatedAt        int64    `json:"updatedAt"`
	Timestamp        int64    `json:"timestamp"`
	DeletedAt        *int64   `json:"deletedAt"`
	Version          int      `json:"version"`
}

type Category struct {
	ID        int64  `json:"id"`
	Name      string `json:"name"`
	Color     int64  `json:"color"`
	CreatedAt int64  `json:"createdAt"`
	UpdatedAt int64  `json:"updatedAt"`
}

type StoreData struct {
	Notes          []Note     `json:"notes"`
	Categories     []Category `json:"categories"`
	MasterHash     string     `json:"masterHash,omitempty"` // SHA-256(salt + password) hex
	MasterSalt     string     `json:"masterSalt,omitempty"` // 16B salt hex
	Version        int        `json:"version"`
}

type SyncState struct {
	IsRunning        bool   `json:"isRunning"`
	Port             int    `json:"port"`
	PIN              string `json:"pin"`
	ConnectedClients int    `json:"connectedClients"`
	LocalIP          string `json:"localIp"`
	LastMessage      string `json:"lastMessage"`
}

// MarshalJSON hem camelCase hem snake_case alanları aynı anda JSON çıktısına ekler
func (s SyncState) MarshalJSON() ([]byte, error) {
	return json.Marshal(map[string]interface{}{
		"isRunning":         s.IsRunning,
		"is_running":        s.IsRunning,
		"port":              s.Port,
		"pin":               s.PIN,
		"pairing_pin":       s.PIN,
		"connectedClients":  s.ConnectedClients,
		"connected_clients": s.ConnectedClients,
		"localIp":           s.LocalIP,
		"local_ip":          s.LocalIP,
		"lastMessage":       s.LastMessage,
		"last_message":      s.LastMessage,
	})
}

// UnmarshalJSON hem camelCase hem snake_case anahtarları sorunsuz çözümler
func (s *SyncState) UnmarshalJSON(b []byte) error {
	var raw map[string]interface{}
	if err := json.Unmarshal(b, &raw); err != nil {
		return err
	}
	if v, ok := raw["isRunning"].(bool); ok {
		s.IsRunning = v
	} else if v, ok := raw["is_running"].(bool); ok {
		s.IsRunning = v
	}
	if v, ok := raw["port"].(float64); ok {
		s.Port = int(v)
	}
	if v, ok := raw["pin"].(string); ok {
		s.PIN = v
	} else if v, ok := raw["pairing_pin"].(string); ok {
		s.PIN = v
	}
	if v, ok := raw["connectedClients"].(float64); ok {
		s.ConnectedClients = int(v)
	} else if v, ok := raw["connected_clients"].(float64); ok {
		s.ConnectedClients = int(v)
	}
	if v, ok := raw["localIp"].(string); ok {
		s.LocalIP = v
	} else if v, ok := raw["local_ip"].(string); ok {
		s.LocalIP = v
	}
	if v, ok := raw["lastMessage"].(string); ok {
		s.LastMessage = v
	} else if v, ok := raw["last_message"].(string); ok {
		s.LastMessage = v
	}
	return nil
}

type VaultStatus struct {
	HasPassword bool `json:"hasPassword"`
	IsUnlocked  bool `json:"isUnlocked"`
}

// Bilgi Grafiği Düğüm Modeli (Knowledge Graph Node)
type GraphNode struct {
	ID        int64  `json:"id"`
	Title     string `json:"title"`
	Group     string `json:"group,omitempty"`
	Color     int64  `json:"color"`
	InDegree  int    `json:"inDegree"`
	OutDegree int    `json:"outDegree"`
	IsGhost   bool   `json:"isGhost"`
}

// Bilgi Grafiği Kenar Modeli (Knowledge Graph Edge)
type GraphEdge struct {
	Source      int64  `json:"source"`
	Target      int64  `json:"target"`
	TargetTitle string `json:"targetTitle"`
	Label       string `json:"label,omitempty"`
}

// Bilgi Grafiği Tam Veri Paketi (D3.js / Cytoscape Uyumlu)
type GraphData struct {
	Nodes []GraphNode `json:"nodes"`
	Edges []GraphEdge `json:"edges"`
}

// ===================================================
// VERİ DEPOSU (DATA STORE)
// ===================================================

type DataStore struct {
	sync.Mutex
	filePath        string
	data            StoreData
	isVaultUnlocked bool
	vaultSessionKey []byte
}

func newDataStore(path string) *DataStore {
	ds := &DataStore{
		filePath: path,
		data: StoreData{
			Notes:      make([]Note, 0),
			Categories: make([]Category, 0),
			Version:    1,
		},
	}
	ds.load()
	return ds
}

func (ds *DataStore) load() {
	ds.Lock()
	defer ds.Unlock()

	b, err := os.ReadFile(ds.filePath)
	if err != nil {
		return
	}
	var data StoreData
	if err := json.Unmarshal(b, &data); err == nil {
		if data.Notes == nil {
			data.Notes = make([]Note, 0)
		}
		if data.Categories == nil {
			data.Categories = make([]Category, 0)
		}
		// Alias normalizasyonu
		for i := range data.Notes {
			if data.Notes[i].IsInTrash {
				data.Notes[i].IsDeleted = true
			} else if data.Notes[i].IsDeleted {
				data.Notes[i].IsInTrash = true
			}
			if data.Notes[i].ReminderDateTime != nil && data.Notes[i].ReminderTime == nil {
				data.Notes[i].ReminderTime = data.Notes[i].ReminderDateTime
			} else if data.Notes[i].ReminderTime != nil && data.Notes[i].ReminderDateTime == nil {
				data.Notes[i].ReminderDateTime = data.Notes[i].ReminderTime
			}
		}
		ds.data = data
	}
}

func (ds *DataStore) saveUnsafe() error {
	dir := filepath.Dir(ds.filePath)
	if dir != "" && dir != "." {
		_ = os.MkdirAll(dir, 0755)
	}

	b, err := json.MarshalIndent(ds.data, "", "  ")
	if err != nil {
		return err
	}

	// Atomik dosya yazma (.tmp -> rename)
	tmpPath := ds.filePath + ".tmp"
	if err := os.WriteFile(tmpPath, b, 0644); err != nil {
		return err
	}
	// Windows'ta hedef dosya varsa doğrudan rename hata verebilir; kaldırıp taşıyoruz
	_ = os.Remove(ds.filePath)
	return os.Rename(tmpPath, ds.filePath)
}

func (ds *DataStore) save() error {
	ds.Lock()
	defer ds.Unlock()
	return ds.saveUnsafe()
}

// Wikilinks Regex: [[başlık]] ve [[başlık|etiket]] kalıplarını yakalar
var wikilinkRegex = regexp.MustCompile(`\[\[([^\[\]\|]+)(?:\|([^\[\]]+))?\]\]`)

// computeGraph: Notlardaki çift yönlü [[wikilink]] bağlantılarını tarayarak
// düğüm (nodes) ve kenarlardan (edges) oluşan bilgi grafiği topolojisini hesaplar.
func (ds *DataStore) computeGraph() GraphData {
	activeNotes := make([]Note, 0)
	titleToID := make(map[string]int64)

	for _, n := range ds.data.Notes {
		if !n.IsInTrash && !n.IsDeleted {
			activeNotes = append(activeNotes, n)
			cleanTitle := strings.ToLower(strings.TrimSpace(n.Title))
			if cleanTitle != "" {
				titleToID[cleanTitle] = n.ID
			}
		}
	}

	inDegrees := make(map[int64]int)
	outDegrees := make(map[int64]int)
	edges := make([]GraphEdge, 0)
	ghostNodes := make(map[string]int64)
	var ghostIDCounter int64 = -1

	type edgeKey struct {
		src int64
		tgt int64
	}
	seenEdges := make(map[edgeKey]bool)

	for _, n := range activeNotes {
		// Not içeriğindeki [[...]] kalıplarını tara
		matches := wikilinkRegex.FindAllStringSubmatch(n.Content, -1)
		for _, m := range matches {
			if len(m) < 2 {
				continue
			}
			targetTitle := strings.TrimSpace(m[1])
			if targetTitle == "" {
				continue
			}
			var alias string
			if len(m) >= 3 && m[2] != "" {
				alias = strings.TrimSpace(m[2])
			}

			targetKey := strings.ToLower(targetTitle)
			var targetID int64
			isGhost := false

			if tid, ok := titleToID[targetKey]; ok {
				targetID = tid
			} else {
				// Henüz mevcut olmayan hedef not (Ghost Node)
				if gid, ok := ghostNodes[targetKey]; ok {
					targetID = gid
				} else {
					targetID = ghostIDCounter
					ghostNodes[targetKey] = ghostIDCounter
					ghostIDCounter--
				}
				isGhost = true
			}

			ek := edgeKey{src: n.ID, tgt: targetID}
			if !seenEdges[ek] {
				seenEdges[ek] = true
				outDegrees[n.ID]++
				if !isGhost {
					inDegrees[targetID]++
				}

				edges = append(edges, GraphEdge{
					Source:      n.ID,
					Target:      targetID,
					TargetTitle: targetTitle,
					Label:       alias,
				})
			}
		}
	}

	nodes := make([]GraphNode, 0, len(activeNotes)+len(ghostNodes))
	for _, n := range activeNotes {
		nodes = append(nodes, GraphNode{
			ID:        n.ID,
			Title:     n.Title,
			Group:     "",
			Color:     n.Color,
			InDegree:  inDegrees[n.ID],
			OutDegree: outDegrees[n.ID],
			IsGhost:   false,
		})
	}

	for titleKey, gid := range ghostNodes {
		nodes = append(nodes, GraphNode{
			ID:        gid,
			Title:     titleKey,
			Group:     "uncreated",
			Color:     -7829368, // Gri hayalet düğüm rengi
			InDegree:  1,
			OutDegree: 0,
			IsGhost:   true,
		})
	}

	return GraphData{
		Nodes: nodes,
		Edges: edges,
	}
}

// ===================================================
// KRİPTO VE .NOTEVLT1 STANDARDI
// ===================================================

const notevltMagic = "NOTEVLT1"
const defaultPBKDF2Iterations = 65536

// PBKDF2-HMAC-SHA256 (RFC 2898 standardı, saf Go standart kütüphanesi)
func deriveKeyPBKDF2(password, salt []byte, iter int, keyLen int) []byte {
	prf := hmac.New(sha256.New, password)
	hLen := prf.Size()
	numBlocks := (keyLen + hLen - 1) / hLen
	var key []byte
	for block := 1; block <= numBlocks; block++ {
		prf.Reset()
		prf.Write(salt)
		var b [4]byte
		binary.BigEndian.PutUint32(b[:], uint32(block))
		prf.Write(b[:])
		u := prf.Sum(nil)
		t := make([]byte, len(u))
		copy(t, u)
		for i := 1; i < iter; i++ {
			prf.Reset()
			prf.Write(u)
			u = prf.Sum(nil)
			for j := 0; j < len(t); j++ {
				t[j] ^= u[j]
			}
		}
		key = append(key, t...)
	}
	return key[:keyLen]
}

// ZIP Baytlarını NOTEVLT1 ikili formatında şifreler:
// MAGIC (8B: NOTEVLT1) + SALT (16B) + IV (12B) + PASSWORD HASH (32B: SHA-256(SALT + password)) + CIPHERTEXT (AES-256-GCM)
func createNoteVaultBuffer(plainZip []byte, password string) ([]byte, error) {
	if password == "" {
		return nil, fmt.Errorf("Kasa şifresi boş olamaz")
	}

	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return nil, fmt.Errorf("Rastgele salt üretilemedi: %v", err)
	}

	iv := make([]byte, 12)
	if _, err := rand.Read(iv); err != nil {
		return nil, fmt.Errorf("Rastgele IV üretilemedi: %v", err)
	}

	// Parola kontrol hash'i: SHA-256(salt + password)
	h := sha256.New()
	h.Write(salt)
	h.Write([]byte(password))
	pwdHash := h.Sum(nil)

	// PBKDF2-HMAC-SHA256 (65.536 iterasyon) ile 256-bit anahtar türetme
	key := deriveKeyPBKDF2([]byte(password), salt, defaultPBKDF2Iterations, 32)

	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("AES başlatılamadı: %v", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("AES-GCM başlatılamadı: %v", err)
	}

	ciphertext := gcm.Seal(nil, iv, plainZip, nil)

	var buf bytes.Buffer
	buf.Grow(8 + 16 + 12 + 32 + len(ciphertext))
	buf.WriteString(notevltMagic)
	buf.Write(salt)
	buf.Write(iv)
	buf.Write(pwdHash)
	buf.Write(ciphertext)

	return buf.Bytes(), nil
}

// NOTEVLT1 formatındaki baytları çözer
func decryptNoteVaultBuffer(data []byte, password string) ([]byte, error) {
	if len(data) < 68 {
		return nil, fmt.Errorf("Geçersiz dosya boyutu (minimum 68 bayt başlık gerekli)")
	}

	if string(data[:8]) != notevltMagic {
		return nil, fmt.Errorf("Geçersiz dosya formatı: 'NOTEVLT1' sihirli başlığı bulunamadı")
	}

	salt := data[8:24]
	iv := data[24:36]
	expectedHash := data[36:68]
	ciphertext := data[68:]

	// Parola hash kontrolü
	h := sha256.New()
	h.Write(salt)
	h.Write([]byte(password))
	computedHash := h.Sum(nil)

	if !hmac.Equal(expectedHash, computedHash) {
		return nil, fmt.Errorf("Hatalı kasa şifresi")
	}

	// İterasyon adayları (Android ve önceki sürümlerle geriye dönük uyumluluk)
	candidates := []int{65536, 12000, 100000, 10000, 50000, 1000, 5000, 20000}
	for _, iter := range candidates {
		key := deriveKeyPBKDF2([]byte(password), salt, iter, 32)
		block, err := aes.NewCipher(key)
		if err != nil {
			continue
		}
		gcm, err := cipher.NewGCM(block)
		if err != nil {
			continue
		}
		plaintext, err := gcm.Open(nil, iv, ciphertext, nil)
		if err == nil {
			return plaintext, nil
		}
	}

	return nil, fmt.Errorf("Kasa şifresi çözülemedi (AES-GCM doğrulaması başarısız)")
}

// ===================================================
// YEDEKLEME VE ZIP İŞLEMLERİ
// ===================================================

func createBackupZipBuffer(notes []Note, categories []Category) ([]byte, error) {
	buf := new(bytes.Buffer)
	zw := zip.NewWriter(buf)

	// 1. notes.json
	notesBytes, err := json.MarshalIndent(notes, "", "  ")
	if err != nil {
		return nil, err
	}
	fNotes, err := zw.Create("notes.json")
	if err != nil {
		return nil, err
	}
	if _, err := fNotes.Write(notesBytes); err != nil {
		return nil, err
	}

	// 2. categories.json
	catBytes, err := json.MarshalIndent(categories, "", "  ")
	if err != nil {
		return nil, err
	}
	fCat, err := zw.Create("categories.json")
	if err != nil {
		return nil, err
	}
	if _, err := fCat.Write(catBytes); err != nil {
		return nil, err
	}

	// 3. backup_info.json
	info := map[string]interface{}{
		"version":   1,
		"appName":   "NotDefterim",
		"timestamp": time.Now().UnixMilli(),
	}
	infoBytes, _ := json.MarshalIndent(info, "", "  ")
	fInfo, err := zw.Create("backup_info.json")
	if err == nil {
		_, _ = fInfo.Write(infoBytes)
	}

	if err := zw.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func importBackupZip(zipBytes []byte) ([]Note, []Category, error) {
	zr, err := zip.NewReader(bytes.NewReader(zipBytes), int64(len(zipBytes)))
	if err != nil {
		return nil, nil, fmt.Errorf("ZIP arşivi okunamadı: %v", err)
	}

	var notes []Note
	var categories []Category

	for _, f := range zr.File {
		rc, err := f.Open()
		if err != nil {
			continue
		}
		data, err := io.ReadAll(rc)
		rc.Close()
		if err != nil {
			continue
		}

		switch f.Name {
		case "notes.json":
			var parsedNotes []Note
			if err := json.Unmarshal(data, &parsedNotes); err == nil {
				notes = parsedNotes
			}
		case "categories.json":
			var parsedCats []Category
			if err := json.Unmarshal(data, &parsedCats); err == nil {
				categories = parsedCats
			}
		}
	}

	return notes, categories, nil
}

// ===================================================
// YARDIMCI VE ÇÖZÜMLEME FONKSİYONLARI
// ===================================================

func findStorePath() string {
	candidates := []string{
		"../notdefterim_store.json",
		"notdefterim_store.json",
		"desktop-native/notdefterim_store.json",
	}
	for _, c := range candidates {
		if _, err := os.Stat(c); err == nil {
			return c
		}
	}

	exePath, err := os.Executable()
	if err == nil {
		exeDir := filepath.Dir(exePath)
		p1 := filepath.Join(exeDir, "notdefterim_store.json")
		if _, err := os.Stat(p1); err == nil {
			return p1
		}
		p2 := filepath.Join(exeDir, "..", "..", "notdefterim_store.json")
		if _, err := os.Stat(p2); err == nil {
			return p2
		}
		return filepath.Join(exeDir, "notdefterim_store.json")
	}

	return "../notdefterim_store.json"
}

func findUIDir() string {
	exePath, _ := os.Executable()
	exeDir := filepath.Dir(exePath)

	candidates := []string{
		filepath.Join(exeDir, "ui"),
		filepath.Join(exeDir, "..", "ui"),
		filepath.Join(exeDir, "..", "..", "ui"),
		"../ui",
		"ui",
		"desktop-native/ui",
		"c:\\Users\\Birin\\Birin\\Projeler\\not-uygulamalari\\not-defterim\\desktop-native\\ui",
	}

	for _, dir := range candidates {
		if fi, err := os.Stat(dir); err == nil && fi.IsDir() {
			if _, err := os.Stat(filepath.Join(dir, "index.html")); err == nil {
				return dir
			}
		}
	}
	return ""
}

func findEdgePath() string {
	paths := []string{
		os.ExpandEnv("${ProgramFiles(x86)}\\Microsoft\\Edge\\Application\\msedge.exe"),
		os.ExpandEnv("${ProgramFiles}\\Microsoft\\Edge\\Application\\msedge.exe"),
		os.ExpandEnv("${LocalAppData}\\Microsoft\\Edge\\Application\\msedge.exe"),
		"C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
		"C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
	}
	for _, p := range paths {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	return ""
}

func getLocalIP() string {
	// 1. Öncelik: Varsayılan ağ geçidi üzerinden çıkış IP'sini tespit et (OS Routing Table)
	conn, err := net.DialTimeout("udp", "8.8.8.8:80", 250*time.Millisecond)
	if err == nil {
		localAddr := conn.LocalAddr().(*net.UDPAddr)
		_ = conn.Close()
		ipStr := localAddr.IP.String()
		if !localAddr.IP.IsLoopback() && (strings.HasPrefix(ipStr, "192.168.") || strings.HasPrefix(ipStr, "10.") || strings.HasPrefix(ipStr, "172.")) {
			return ipStr
		}
	}

	// 2. Öncelik: Ağ arayüzlerini tara, sanal adaptörleri (WSL, Hyper-V, VirtualBox) ele
	ifaces, err := net.Interfaces()
	if err != nil {
		return "127.0.0.1"
	}

	var tier1, tier2, tier3, tier4 string

	for _, iface := range ifaces {
		// Kapalı veya loopback arayüzleri atla
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}

		nameLower := strings.ToLower(iface.Name)
		// Sanal adaptör filtreleri
		isVirtual := strings.Contains(nameLower, "vethernet") ||
			strings.Contains(nameLower, "wsl") ||
			strings.Contains(nameLower, "virtual") ||
			strings.Contains(nameLower, "vmware") ||
			strings.Contains(nameLower, "hyper-v") ||
			strings.Contains(nameLower, "docker") ||
			strings.Contains(nameLower, "teredo") ||
			strings.Contains(nameLower, "tailscale") ||
			strings.Contains(nameLower, "pseudo")

		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}

		for _, addr := range addrs {
			ipnet, ok := addr.(*net.IPNet)
			if !ok || ipnet.IP.IsLoopback() || ipnet.IP.To4() == nil {
				continue
			}

			ipStr := ipnet.IP.String()
			isPhysical := strings.Contains(nameLower, "wi-fi") ||
				strings.Contains(nameLower, "wlan") ||
				strings.Contains(nameLower, "wireless") ||
				strings.Contains(nameLower, "ethernet") ||
				strings.Contains(nameLower, "yerel") ||
				strings.Contains(nameLower, "eth") ||
				strings.Contains(nameLower, "en")

			if strings.HasPrefix(ipStr, "192.168.") {
				if isPhysical && !isVirtual && tier1 == "" {
					tier1 = ipStr
				} else if !isVirtual && tier2 == "" {
					tier2 = ipStr
				}
			} else if strings.HasPrefix(ipStr, "10.") && !isVirtual && tier3 == "" {
				tier3 = ipStr
			} else if strings.HasPrefix(ipStr, "172.") && !isVirtual && tier4 == "" {
				tier4 = ipStr
			}
		}
	}

	if tier1 != "" {
		return tier1
	}
	if tier2 != "" {
		return tier2
	}
	if tier3 != "" {
		return tier3
	}
	if tier4 != "" {
		return tier4
	}

	// Son çare: Herhangi bir IPv4
	addrs, _ := net.InterfaceAddrs()
	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() && ipnet.IP.To4() != nil {
			return ipnet.IP.String()
		}
	}

	return "127.0.0.1"
}

// UDP LAN Otomatik Keşif Dinleyicisi (UDP Auto-Discovery Responder)
// Android cihazdan broadcast veya unicast olarak "NOTEDEFTERIM_DISCOVER" paketi geldiğinde
// masaüstünün IP, Port ve PIN bilgilerini anında geri döner.
func startUDPDiscoveryResponder(httpPort int, syncState *SyncState, mu *sync.Mutex) {
	pc, err := net.ListenPacket("udp4", ":8765")
	if err != nil {
		// Port meşgulse sessizce bekle
		return
	}
	defer pc.Close()

	buf := make([]byte, 2048)
	for {
		n, clientAddr, err := pc.ReadFrom(buf)
		if err != nil {
			time.Sleep(500 * time.Millisecond)
			continue
		}

		msg := strings.TrimSpace(string(buf[:n]))
		if strings.Contains(msg, "NOTEDEFTERIM_DISCOVER") {
			mu.Lock()
			currentPin := syncState.PIN
			localIP := syncState.LocalIP
			mu.Unlock()

			if localIP == "" || localIP == "127.0.0.1" {
				localIP = getLocalIP()
			}

			// Yanıt paketi (hem JSON hem NOTEDEFTERIM_DISCOVER_RESPONSE: prefix'i desteklenir)
			respPayload := map[string]interface{}{
				"status":      "ok",
				"appName":     "NotDefterim Masaüstü",
				"version":     "1.4.4",
				"ip":          localIP,
				"localIp":     localIP,
				"local_ip":    localIP,
				"port":        httpPort,
				"pin":         currentPin,
				"pairing_pin": currentPin,
				"requiresPin": true,
			}
			jsonBytes, _ := json.Marshal(respPayload)
			responseMsg := "NOTEDEFTERIM_DISCOVER_RESPONSE:" + string(jsonBytes)

			// Göndericiye UDP üzerinden yanıt dön
			_, _ = pc.WriteTo([]byte(responseMsg), clientAddr)
		}
	}
}

func generatePIN() string {
	n, err := rand.Int(rand.Reader, big.NewInt(9000))
	if err != nil {
		return "1234"
	}
	return fmt.Sprintf("%04d", n.Int64()+1000)
}

func constantTimePinMatch(a, b string) bool {
	return hmac.Equal([]byte(strings.TrimSpace(a)), []byte(strings.TrimSpace(b)))
}

func extractPin(r *http.Request) string {
	if p := r.Header.Get("X-Sync-PIN"); p != "" {
		return p
	}
	if p := r.Header.Get("X-Pairing-PIN"); p != "" {
		return p
	}
	return r.URL.Query().Get("pin")
}

// Payload'dan esnek Note parse etme
func parseNoteFromPayload(raw json.RawMessage) (*Note, error) {
	if len(raw) == 0 {
		return nil, fmt.Errorf("Boş payload")
	}

	var wrapper struct {
		Note *Note `json:"note"`
	}
	if err := json.Unmarshal(raw, &wrapper); err == nil && wrapper.Note != nil {
		normalizeNote(wrapper.Note)
		return wrapper.Note, nil
	}

	var note Note
	if err := json.Unmarshal(raw, &note); err == nil {
		normalizeNote(&note)
		return &note, nil
	}

	return nil, fmt.Errorf("Not verisi çözümlenemedi")
}

func normalizeNote(n *Note) {
	if n.IsInTrash {
		n.IsDeleted = true
	} else if n.IsDeleted {
		n.IsInTrash = true
	}
	if n.ReminderDateTime != nil && n.ReminderTime == nil {
		n.ReminderTime = n.ReminderDateTime
	} else if n.ReminderTime != nil && n.ReminderDateTime == nil {
		n.ReminderDateTime = n.ReminderTime
	}
}

func parseIDFromPayload(raw json.RawMessage) int64 {
	if len(raw) == 0 {
		return 0
	}
	var wrapper struct {
		ID int64 `json:"id"`
	}
	if err := json.Unmarshal(raw, &wrapper); err == nil && wrapper.ID != 0 {
		return wrapper.ID
	}
	var direct int64
	if err := json.Unmarshal(raw, &direct); err == nil {
		return direct
	}
	// Bazen string id gelebilir
	var strWrapper struct {
		ID string `json:"id"`
	}
	if err := json.Unmarshal(raw, &strWrapper); err == nil {
		if idVal, err := strconv.ParseInt(strWrapper.ID, 10, 64); err == nil {
			return idVal
		}
	}
	return 0
}

func parsePasswordFromPayload(raw json.RawMessage) string {
	if len(raw) == 0 {
		return ""
	}
	var wrapper struct {
		Password string `json:"password"`
	}
	if err := json.Unmarshal(raw, &wrapper); err == nil && wrapper.Password != "" {
		return wrapper.Password
	}
	var direct string
	if err := json.Unmarshal(raw, &direct); err == nil {
		return direct
	}
	return ""
}

// ===================================================
// ANA SUNUCU VE İŞLEYİCİLER
// ===================================================

func main() {
	storePath := findStorePath()
	store := newDataStore(storePath)

	uiDir := findUIDir()

	// Wi-Fi Senkronizasyon Durumu
	localIP := getLocalIP()
	syncState := &SyncState{
		IsRunning:        true,
		Port:             8765,
		PIN:              generatePIN(),
		ConnectedClients: 0,
		LocalIP:          localIP,
		LastMessage:      "Hazır - Bağlantı Bekleniyor",
	}
	var syncMu sync.Mutex

	// Port 8765'i dinle (Android LAN Sync & Native UI Portu)
	// Hem yerel (127.0.0.1) hem de LAN üzerinden telefon bağlantılarını kabul etmek için tüm arayüzleri (:8765) dinle
	listener, err := net.Listen("tcp", ":8765")
	if err != nil {
		// Port :8765 meşgulse yerel 127.0.0.1:8765 veya dinamik porta geç
		listener, err = net.Listen("tcp", "127.0.0.1:8765")
		if err != nil {
			listener, err = net.Listen("tcp", "127.0.0.1:0")
			if err != nil {
				fmt.Printf("TCP port açılamadı: %v\n", err)
				return
			}
		}
	}
	port := listener.Addr().(*net.TCPAddr).Port
	syncState.Port = port
	appURL := fmt.Sprintf("http://127.0.0.1:%d", port)

	// Arka planda UDP LAN Otomatik Keşif Dinleyicisini Başlat
	go startUDPDiscoveryResponder(port, syncState, &syncMu)

	mux := http.NewServeMux()

	// 1. Statik Dosya Sunucusu (Desktop UI)
	if uiDir != "" {
		fs := http.FileServer(http.Dir(uiDir))
		mux.Handle("/", fs)
	} else {
		mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			fmt.Fprintf(w, "<h2>Not Defterim UI Dizini Bulunamadı</h2><p>Lütfen desktop-native/ui klasörünü kontrol edin.</p>")
		})
	}

	// 2. Wi-Fi Sync Durum Kontrolü (Android DesktopSyncClient)
	statusHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Access-Control-Allow-Origin", "*")

		store.Lock()
		count := len(store.data.Notes)
		store.Unlock()

		resp := map[string]interface{}{
			"status":      "ok",
			"appName":     "NotDefterim Masaüstü",
			"version":     "1.4.4",
			"notesCount":  count,
			"port":        port,
			"requiresPin": true,
		}
		json.NewEncoder(w).Encode(resp)
	}
	mux.HandleFunc("/api/status", statusHandler)
	mux.HandleFunc("/status", statusHandler)

	// 3. Wi-Fi Sync ZIP İndirme (Android -> Desktop Download)
	syncDownloadHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")

		// PIN Doğrulama
		syncMu.Lock()
		curPin := syncState.PIN
		syncMu.Unlock()

		reqPin := extractPin(r)
		if !constantTimePinMatch(reqPin, curPin) {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusUnauthorized)
			json.NewEncoder(w).Encode(map[string]string{"error": "Geçersiz veya eksik PIN kodu"})
			return
		}

		store.Lock()
		notesCopy := make([]Note, len(store.data.Notes))
		copy(notesCopy, store.data.Notes)
		catsCopy := make([]Category, len(store.data.Categories))
		copy(catsCopy, store.data.Categories)
		store.Unlock()

		zipBytes, err := createBackupZipBuffer(notesCopy, catsCopy)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			json.NewEncoder(w).Encode(map[string]string{"error": "ZIP yedeği oluşturulamadı"})
			return
		}

		syncMu.Lock()
		syncState.ConnectedClients++
		syncState.LastMessage = fmt.Sprintf("%d not ZIP olarak telefona aktarıldı", len(notesCopy))
		syncMu.Unlock()

		w.Header().Set("Content-Type", "application/zip")
		w.Header().Set("Content-Disposition", "attachment; filename=\"NoteApp_Sync.zip\"")
		w.WriteHeader(http.StatusOK)
		w.Write(zipBytes)
	}
	mux.HandleFunc("/api/sync/download", syncDownloadHandler)
	mux.HandleFunc("/sync/download", syncDownloadHandler)

	// 4. Wi-Fi Sync ZIP / JSON Yükleme (Android -> Desktop Upload)
	syncUploadHandler := func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Content-Type", "application/json; charset=utf-8")

		if r.Method != http.MethodPost {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}

		// PIN Doğrulama
		syncMu.Lock()
		curPin := syncState.PIN
		syncMu.Unlock()

		reqPin := extractPin(r)
		if !constantTimePinMatch(reqPin, curPin) {
			w.WriteHeader(http.StatusUnauthorized)
			json.NewEncoder(w).Encode(map[string]string{"error": "Geçersiz veya eksik PIN kodu"})
			return
		}

		bodyBytes, err := io.ReadAll(io.LimitReader(r.Body, 50*1024*1024))
		if err != nil {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": "Gövde okunamadı"})
			return
		}

		var importedNotes []Note
		var importedCats []Category

		// ZIP mi yoksa JSON mı kontrol et (PK\x03\x04)
		if len(bodyBytes) > 4 && bodyBytes[0] == 0x50 && bodyBytes[1] == 0x4B {
			notes, cats, err := importBackupZip(bodyBytes)
			if err != nil {
				w.WriteHeader(http.StatusBadRequest)
				json.NewEncoder(w).Encode(map[string]string{"error": "ZIP açılamadı: " + err.Error()})
				return
			}
			importedNotes = notes
			importedCats = cats
		} else {
			// JSON formatı: { "notes": [...], "categories": [...] } veya doğrudan []Note
			var bundle struct {
				Notes      []Note     `json:"notes"`
				Categories []Category `json:"categories"`
			}
			if err := json.Unmarshal(bodyBytes, &bundle); err == nil && len(bundle.Notes) > 0 {
				importedNotes = bundle.Notes
				importedCats = bundle.Categories
			} else {
				var directNotes []Note
				if err := json.Unmarshal(bodyBytes, &directNotes); err == nil {
					importedNotes = directNotes
				}
			}
		}

		if len(importedNotes) == 0 && len(importedCats) == 0 {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": "İçe aktarılacak geçerli veri bulunamadı"})
			return
		}

		store.Lock()
		// Notları birleştir (Merge by ID)
		noteMap := make(map[int64]int)
		for i, n := range store.data.Notes {
			noteMap[n.ID] = i
		}
		for _, n := range importedNotes {
			normalizeNote(&n)
			if idx, ok := noteMap[n.ID]; ok {
				if n.UpdatedAt >= store.data.Notes[idx].UpdatedAt {
					store.data.Notes[idx] = n
				}
			} else {
				store.data.Notes = append(store.data.Notes, n)
			}
		}

		// Kategorileri birleştir
		catMap := make(map[int64]int)
		for i, c := range store.data.Categories {
			catMap[c.ID] = i
		}
		for _, c := range importedCats {
			if idx, ok := catMap[c.ID]; ok {
				store.data.Categories[idx] = c
			} else {
				store.data.Categories = append(store.data.Categories, c)
			}
		}
		_ = store.saveUnsafe()
		store.Unlock()

		syncMu.Lock()
		syncState.ConnectedClients++
		syncState.LastMessage = fmt.Sprintf("%d not başarıyla içe aktarıldı", len(importedNotes))
		syncMu.Unlock()

		json.NewEncoder(w).Encode(map[string]interface{}{
			"success":  true,
			"imported": len(importedNotes),
			"message":  "Senkronizasyon tamamlandı",
		})
	}
	mux.HandleFunc("/api/sync/upload", syncUploadHandler)
	mux.HandleFunc("/sync/upload", syncUploadHandler)
	mux.HandleFunc("/api/sync", syncUploadHandler)

	// 5. Birleşik IPC & JSON-RPC Köprüsü (/api)
	mux.HandleFunc("/api", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Access-Control-Allow-Origin", "*")

		if r.Method == http.MethodOptions {
			w.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-Sync-PIN, X-Pairing-PIN")
			w.WriteHeader(http.StatusOK)
			return
		}

		body, _ := io.ReadAll(r.Body)
		var req struct {
			Cmd       string          `json:"cmd"`
			Action    string          `json:"action"`
			Payload   json.RawMessage `json:"payload"`
			RequestID string          `json:"requestId"`
		}
		_ = json.Unmarshal(body, &req)

		cmd := req.Cmd
		if cmd == "" {
			cmd = req.Action
		}

		sendOK := func(res interface{}) {
			json.NewEncoder(w).Encode(map[string]interface{}{
				"requestId": req.RequestID,
				"success":   true,
				"result":    res,
				"data":      res,
			})
		}
		sendErr := func(msg string) {
			json.NewEncoder(w).Encode(map[string]interface{}{
				"requestId": req.RequestID,
				"success":   false,
				"error":     msg,
			})
		}

		store.Lock()
		defer store.Unlock()

		switch cmd {

		// ----------------------------------------------------
		// NOTLARI LİSTELEME
		// ----------------------------------------------------
		case "db:getNotes", "getNotes", "notes:list":
			var active, archived, trash []Note
			for _, n := range store.data.Notes {
				if n.IsInTrash || n.IsDeleted {
					trash = append(trash, n)
				} else if n.IsArchived {
					archived = append(archived, n)
				} else {
					active = append(active, n)
				}
			}
			sendOK(map[string]interface{}{
				"active":          active,
				"archived":        archived,
				"trash":           trash,
				"categories":      store.data.Categories,
				"isVaultUnlocked": store.isVaultUnlocked,
			})

		// ----------------------------------------------------
		// NOT OLUŞTURMA & KAYDETME
		// ----------------------------------------------------
		case "db:createNote", "createNote", "db:saveNote", "saveNote", "notes:save":
			note, err := parseNoteFromPayload(req.Payload)
			if err != nil {
				sendErr("Geçersiz not verisi: " + err.Error())
				return
			}
			now := time.Now().UnixMilli()
			if note.ID == 0 {
				note.ID = now
				note.CreatedAt = now
				note.UpdatedAt = now
				note.Timestamp = now
				note.Version = 1
				store.data.Notes = append([]Note{*note}, store.data.Notes...)
			} else {
				note.UpdatedAt = now
				note.Timestamp = now
				found := false
				for i, n := range store.data.Notes {
					if n.ID == note.ID {
						note.CreatedAt = n.CreatedAt
						note.Version = n.Version + 1
						store.data.Notes[i] = *note
						found = true
						break
					}
				}
				if !found {
					store.data.Notes = append([]Note{*note}, store.data.Notes...)
				}
			}
			_ = store.saveUnsafe()
			sendOK(note)

		// ----------------------------------------------------
		// ÇÖP KUTUSUNA TAŞIMA
		// ----------------------------------------------------
		case "db:moveToTrash", "moveToTrash", "deleteNote", "notes:delete":
			id := parseIDFromPayload(req.Payload)
			now := time.Now().UnixMilli()
			for i, n := range store.data.Notes {
				if n.ID == id {
					store.data.Notes[i].IsInTrash = true
					store.data.Notes[i].IsDeleted = true
					store.data.Notes[i].DeletedAt = &now
					store.data.Notes[i].UpdatedAt = now
					break
				}
			}
			_ = store.saveUnsafe()
			sendOK(true)

		// ----------------------------------------------------
		// ÇÖP KUTUSUNDAN GERİ YÜKLEME
		// ----------------------------------------------------
		case "db:restoreFromTrash", "restoreFromTrash", "restoreNote":
			id := parseIDFromPayload(req.Payload)
			now := time.Now().UnixMilli()
			for i, n := range store.data.Notes {
				if n.ID == id {
					store.data.Notes[i].IsInTrash = false
					store.data.Notes[i].IsDeleted = false
					store.data.Notes[i].DeletedAt = nil
					store.data.Notes[i].UpdatedAt = now
					break
				}
			}
			_ = store.saveUnsafe()
			sendOK(true)

		// ----------------------------------------------------
		// KALICI SİLME & ÇÖP KUTUSUNU BOŞALTMA
		// ----------------------------------------------------
		case "db:deletePermanently", "deletePermanently":
			id := parseIDFromPayload(req.Payload)
			for i, n := range store.data.Notes {
				if n.ID == id {
					store.data.Notes = append(store.data.Notes[:i], store.data.Notes[i+1:]...)
					break
				}
			}
			_ = store.saveUnsafe()
			sendOK(true)

		case "db:emptyTrash", "emptyTrash":
			var remaining []Note
			for _, n := range store.data.Notes {
				if !n.IsInTrash && !n.IsDeleted {
					remaining = append(remaining, n)
				}
			}
			store.data.Notes = remaining
			_ = store.saveUnsafe()
			sendOK(true)

		// ----------------------------------------------------
		// ARŞİVLEME & SABİTLEME (TOGGLE)
		// ----------------------------------------------------
		case "db:archiveNote", "archiveNote":
			id := parseIDFromPayload(req.Payload)
			now := time.Now().UnixMilli()
			for i, n := range store.data.Notes {
				if n.ID == id {
					store.data.Notes[i].IsArchived = !store.data.Notes[i].IsArchived
					store.data.Notes[i].UpdatedAt = now
					break
				}
			}
			_ = store.saveUnsafe()
			sendOK(true)

		case "db:togglePin", "togglePin":
			id := parseIDFromPayload(req.Payload)
			now := time.Now().UnixMilli()
			for i, n := range store.data.Notes {
				if n.ID == id {
					store.data.Notes[i].IsPinned = !store.data.Notes[i].IsPinned
					store.data.Notes[i].UpdatedAt = now
					break
				}
			}
			_ = store.saveUnsafe()
			sendOK(true)

		// ----------------------------------------------------
		// KATEGORİ İŞLEMLERİ
		// ----------------------------------------------------
		case "db:getCategories", "getCategories", "categories:list":
			sendOK(store.data.Categories)

		case "db:addCategory", "addCategory", "db:saveCategory", "saveCategory", "categories:save":
			var cat Category
			var wrapper struct {
				Name  string `json:"name"`
				Color int64  `json:"color"`
			}
			if err := json.Unmarshal(req.Payload, &wrapper); err == nil && wrapper.Name != "" {
				cat.Name = wrapper.Name
				cat.Color = wrapper.Color
			} else {
				_ = json.Unmarshal(req.Payload, &cat)
			}

			now := time.Now().UnixMilli()
			if cat.ID == 0 {
				cat.ID = now
				cat.CreatedAt = now
				cat.UpdatedAt = now
				store.data.Categories = append(store.data.Categories, cat)
			} else {
				cat.UpdatedAt = now
				found := false
				for i, c := range store.data.Categories {
					if c.ID == cat.ID {
						store.data.Categories[i] = cat
						found = true
						break
					}
				}
				if !found {
					store.data.Categories = append(store.data.Categories, cat)
				}
			}
			_ = store.saveUnsafe()
			sendOK(cat)

		case "db:deleteCategory", "deleteCategory", "categories:delete":
			id := parseIDFromPayload(req.Payload)
			for i, c := range store.data.Categories {
				if c.ID == id {
					store.data.Categories = append(store.data.Categories[:i], store.data.Categories[i+1:]...)
					break
				}
			}
			// İlgili notlardaki kategori bağlantısını temizle
			for i := range store.data.Notes {
				if store.data.Notes[i].CategoryID != nil && *store.data.Notes[i].CategoryID == id {
					store.data.Notes[i].CategoryID = nil
				}
			}
			_ = store.saveUnsafe()
			sendOK(true)

		// ----------------------------------------------------
		// GÜVENLİ KASA (VAULT) İŞLEMLERİ
		// ----------------------------------------------------
		case "vault:getStatus", "vaultStatus":
			sendOK(VaultStatus{
				HasPassword: store.data.MasterHash != "",
				IsUnlocked:  store.isVaultUnlocked,
			})

		case "vault:setup", "vaultSetup":
			pwd := parsePasswordFromPayload(req.Payload)
			if pwd == "" {
				sendErr("Kasa şifresi boş olamaz")
				return
			}
			salt := make([]byte, 16)
			rand.Read(salt)

			h := sha256.New()
			h.Write(salt)
			h.Write([]byte(pwd))
			hashBytes := h.Sum(nil)

			store.data.MasterSalt = hex.EncodeToString(salt)
			store.data.MasterHash = hex.EncodeToString(hashBytes)
			store.isVaultUnlocked = true
			store.vaultSessionKey = deriveKeyPBKDF2([]byte(pwd), salt, defaultPBKDF2Iterations, 32)
			_ = store.saveUnsafe()
			sendOK(true)

		case "vault:unlock", "vaultUnlock":
			pwd := parsePasswordFromPayload(req.Payload)
			if store.data.MasterHash == "" {
				sendErr("Henüz kasa şifresi tanımlanmamış")
				return
			}
			salt, err := hex.DecodeString(store.data.MasterSalt)
			if err != nil {
				sendErr("Kasa tuz verisi bozuk")
				return
			}
			h := sha256.New()
			h.Write(salt)
			h.Write([]byte(pwd))
			hashBytes := h.Sum(nil)

			expectedHash, _ := hex.DecodeString(store.data.MasterHash)
			if !hmac.Equal(expectedHash, hashBytes) {
				sendErr("Hatalı kasa şifresi")
				return
			}
			store.isVaultUnlocked = true
			store.vaultSessionKey = deriveKeyPBKDF2([]byte(pwd), salt, defaultPBKDF2Iterations, 32)
			sendOK(true)

		case "vault:lock", "vaultLock":
			store.isVaultUnlocked = false
			store.vaultSessionKey = nil
			sendOK(true)

		// ----------------------------------------------------
		// YEDEKLEME & DIŞA/İÇE AKTARMA (EXPORT/IMPORT)
		// ----------------------------------------------------
		case "backup:export", "backupExport", "exportVault":
			pwd := parsePasswordFromPayload(req.Payload)
			zipBytes, err := createBackupZipBuffer(store.data.Notes, store.data.Categories)
			if err != nil {
				sendErr("Yedek ZIP arşivi oluşturulamadı: " + err.Error())
				return
			}

			if pwd != "" {
				// NOTEVLT1 ikili formatında şifrele
				vaultBytes, err := createNoteVaultBuffer(zipBytes, pwd)
				if err != nil {
					sendErr("Kasa şifreleme hatası: " + err.Error())
					return
				}
				sendOK(map[string]interface{}{
					"format":   "notevault",
					"fileName": "NotDefterim_Vault.notevault",
					"size":     len(vaultBytes),
					"data":     hex.EncodeToString(vaultBytes),
				})
			} else {
				sendOK(map[string]interface{}{
					"format":   "zip",
					"fileName": "NoteApp_Backup.zip",
					"size":     len(zipBytes),
					"data":     hex.EncodeToString(zipBytes),
				})
			}

		case "backup:import", "backupImport", "importVault":
			var importPayload struct {
				Data     string `json:"data"`     // hex veya base64 string
				Password string `json:"password"` // opsiyonel kasa şifresi
			}
			_ = json.Unmarshal(req.Payload, &importPayload)

			var rawBytes []byte
			var err error
			if rawBytes, err = hex.DecodeString(importPayload.Data); err != nil {
				sendErr("Yedek verisi hex formatında çözülemedi")
				return
			}

			var zipBytes []byte
			if len(rawBytes) >= 8 && string(rawBytes[:8]) == notevltMagic {
				// Şifreli NOTEVLT1
				if importPayload.Password == "" {
					sendErr("Bu yedek dosyası şifrelidir, lütfen şifreyi girin")
					return
				}
				plainZip, err := decryptNoteVaultBuffer(rawBytes, importPayload.Password)
				if err != nil {
					sendErr(err.Error())
					return
				}
				zipBytes = plainZip
			} else {
				zipBytes = rawBytes
			}

			notes, cats, err := importBackupZip(zipBytes)
			if err != nil {
				sendErr("Yedek ZIP içeriği okunamadı: " + err.Error())
				return
			}

			// Birleştir
			for _, n := range notes {
				normalizeNote(&n)
				store.data.Notes = append(store.data.Notes, n)
			}
			for _, c := range cats {
				store.data.Categories = append(store.data.Categories, c)
			}
			_ = store.saveUnsafe()
			sendOK(map[string]interface{}{
				"success":        true,
				"notesCount":     len(notes),
				"categoryCount": len(cats),
			})

		// ----------------------------------------------------
		// LAN SENKRONİZASYON IPC KONTROLLERİ
		// ----------------------------------------------------
		case "sync:getState", "syncGetState", "sync:status":
			syncMu.Lock()
			st := *syncState
			syncMu.Unlock()
			sendOK(st)

		case "sync:start", "syncStart":
			syncMu.Lock()
			syncState.IsRunning = true
			syncState.LastMessage = "Çalışıyor"
			st := *syncState
			syncMu.Unlock()
			sendOK(st)

		case "sync:stop", "syncStop":
			syncMu.Lock()
			syncState.IsRunning = false
			syncState.LastMessage = "Durduruldu"
			st := *syncState
			syncMu.Unlock()
			sendOK(st)

		case "sync:refreshPin", "syncRefreshPin":
			syncMu.Lock()
			syncState.PIN = generatePIN()
			st := *syncState
			syncMu.Unlock()
			sendOK(st)

		// ----------------------------------------------------
		// EKRAN GÖRÜNTÜSÜ KORUMASI (ANTI-SCREENSHOT)
		// ----------------------------------------------------
		case "window:setAntiScreenshot", "setAntiScreenshot":
			// Windows WebView2 veya Edge App modunda kabul edilir
			sendOK(true)

		// ----------------------------------------------------
		// BİLGİ GRAFİĞİ (KNOWLEDGE GRAPH TOPOLOJİSİ)
		// ----------------------------------------------------
		case "graph:getGraph", "getGraph", "graph:data":
			graph := store.computeGraph()
			sendOK(graph)

		// ----------------------------------------------------
		// YAPIŞKAN NOT PENCERESİ (STICKY NOTE)
		// ----------------------------------------------------
		case "window:openSticky", "openSticky":
			id := parseIDFromPayload(req.Payload)
			stickyURL := fmt.Sprintf("%s/?mode=sticky&id=%d", appURL, id)
			edgePath := findEdgePath()

			if edgePath != "" {
				stickyProfile := filepath.Join(os.Getenv("APPDATA"), "NotDefterim", "StickyProfile")
				_ = os.MkdirAll(stickyProfile, 0755)
				stickyCmd := exec.Command(edgePath,
					fmt.Sprintf("--app=%s", stickyURL),
					"--window-size=380,440",
					fmt.Sprintf("--user-data-dir=%s", stickyProfile),
				)
				go func() {
					_ = stickyCmd.Start()
				}()
			} else if runtime.GOOS == "windows" {
				go func() {
					_ = exec.Command("cmd", "/c", "start", stickyURL).Start()
				}()
			} else {
				go func() {
					_ = exec.Command("xdg-open", stickyURL).Start()
				}()
			}
			sendOK(true)

		default:
			sendOK(nil)
		}
	})

	server := &http.Server{Handler: mux}
	go func() {
		_ = server.Serve(listener)
	}()

	// 6. Yerel Windows Edge App Modunu Başlat
	edgePath := findEdgePath()
	var cmd *exec.Cmd

	if edgePath != "" {
		userDataDir := filepath.Join(os.Getenv("APPDATA"), "NotDefterim", "webview-profile")
		_ = os.MkdirAll(userDataDir, 0755)
		cmd = exec.Command(edgePath,
			fmt.Sprintf("--app=%s", appURL),
			fmt.Sprintf("--user-data-dir=%s", userDataDir),
			"--window-size=1280,820",
			"--app-id=NotDefterim",
		)
	} else if runtime.GOOS == "windows" {
		cmd = exec.Command("cmd", "/c", "start", appURL)
	} else {
		cmd = exec.Command("xdg-open", appURL)
	}

	if err := cmd.Run(); err != nil {
		time.Sleep(1 * time.Second)
	}
}
