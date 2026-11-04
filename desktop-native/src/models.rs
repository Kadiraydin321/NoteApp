use serde::{Deserialize, Serialize};

fn default_timestamp() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

/// Android Room v1.4.4 ile %100 uyumlu Note modeli
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Note {
    /// Room Primary Key (0 ise yeni kayıt)
    pub id: i64,

    /// Not başlığı
    pub title: String,

    /// Not içeriği (Kasa kilitli ise 'ENC_GCM_V1:...' şifreli metin)
    pub content: String,

    /// Son güncelleme Unix epoch milisaniye
    pub timestamp: i64,

    /// Android ARGB Int renk kodu (Varsayılan: 0)
    pub color: i32,

    /// Bağlı kategori ID'si (Foreign Key)
    #[serde(rename = "categoryId")]
    pub category_id: Option<i64>,

    /// Başa tutturulmuş not bayrağı
    #[serde(rename = "isPinned")]
    pub is_pinned: bool,

    /// Şifreli kasa notu bayrağı
    #[serde(rename = "isLocked")]
    pub is_locked: bool,

    /// Arşivlenmiş not bayrağı
    #[serde(rename = "isArchived")]
    pub is_archived: bool,

    /// Çöp kutusu bayrağı (Android Room 'isDeleted' ile çift yönlü alias)
    #[serde(alias = "isDeleted", rename = "isInTrash")]
    pub is_in_trash: bool,

    /// Çöp kutusuna atılma zamanı (Unix ms)
    #[serde(rename = "deletedAt")]
    pub deleted_at: Option<i64>,

    /// Hatırlatıcı tarihi (Android 'reminderTime' ile çift yönlü alias)
    #[serde(alias = "reminderTime", rename = "reminderDateTime")]
    pub reminder_date_time: Option<i64>,

    /// Etiket listesi (Android v1.4.4 etiket desteği)
    #[serde(default)]
    pub tags: Vec<String>,

    /// Ek dosya ve görsel yolları
    #[serde(default)]
    pub attachments: Vec<String>,

    /// Oluşturulma tarihi Unix ms
    #[serde(rename = "createdAt", default = "default_timestamp")]
    pub created_at: i64,

    /// Güncellenme tarihi Unix ms
    #[serde(rename = "updatedAt", default = "default_timestamp")]
    pub updated_at: i64,

    /// Arka plan görsel yolu veya URI
    #[serde(rename = "backgroundImage")]
    pub background_image: Option<String>,

    /// Klasör ID'si (Opsiyonel)
    #[serde(rename = "folderId")]
    pub folder_id: Option<i64>,
}

impl Note {
    /// Geriye dönük uyumluluk yardımcısı (isDeleted alias)
    #[inline]
    pub fn is_deleted(&self) -> bool {
        self.is_in_trash
    }

    /// Geriye dönük uyumluluk yardımcısı (reminderTime alias)
    #[inline]
    pub fn reminder_time(&self) -> Option<i64> {
        self.reminder_date_time
    }
}

/// Kategori Modeli
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: i64,
    pub name: String,
    pub color: i32,
}

/// Android LAN Senkronizasyon Durum Modeli
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncState {
    #[serde(rename = "isRunning")]
    pub is_running: bool,

    pub port: u16, // Sabit 8765

    pub pin: String, // 4 haneli rastgele eşleştirme PIN kodu

    #[serde(rename = "connectedClients")]
    pub connected_clients: usize,

    #[serde(rename = "localIp")]
    pub local_ip: String,

    #[serde(rename = "lastMessage")]
    pub last_message: String,
}

/// Güvenli Kasa Durum Modeli
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultStatus {
    #[serde(rename = "hasPassword")]
    pub has_password: bool,

    #[serde(rename = "isUnlocked")]
    pub is_unlocked: bool,
}

/// IPC İstek Zarfı
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IpcRequest {
    pub action: String,

    #[serde(default)]
    pub payload: serde_json::Value,

    #[serde(rename = "requestId")]
    pub request_id: String,
}

/// IPC Hata Nesnesi
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IpcError {
    pub code: String,
    pub message: String,
}

/// IPC Yanıt Zarfı
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IpcResponse<T = serde_json::Value> {
    #[serde(rename = "requestId")]
    pub request_id: String,

    pub success: bool,

    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,

    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<IpcError>,
}

impl<T: Serialize> IpcResponse<T> {
    pub fn ok(request_id: impl Into<String>, data: T) -> Self {
        Self {
            request_id: request_id.into(),
            success: true,
            data: Some(data),
            error: None,
        }
    }

    pub fn err(
        request_id: impl Into<String>,
        code: impl Into<String>,
        message: impl Into<String>,
    ) -> IpcResponse<serde_json::Value> {
        IpcResponse {
            request_id: request_id.into(),
            success: false,
            data: None,
            error: Some(IpcError {
                code: code.into(),
                message: message.into(),
            }),
        }
    }
}

/// IPC Asenkron Olay Bildirimi (Rust -> WebView)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IpcEvent<T = serde_json::Value> {
    pub event: String,
    pub payload: T,
}
