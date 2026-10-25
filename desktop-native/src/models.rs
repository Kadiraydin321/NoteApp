use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Note {
    pub id: i64,
    pub title: String,
    pub content: String,
    pub timestamp: i64,
    pub color: i32,
    #[serde(rename = "categoryId")]
    pub category_id: Option<i64>,
    #[serde(rename = "isPinned")]
    pub is_pinned: bool,
    #[serde(rename = "isArchived")]
    pub is_archived: bool,
    #[serde(rename = "isDeleted")]
    pub is_deleted: bool,
    #[serde(rename = "deletedAt")]
    pub deleted_at: Option<i64>,
    #[serde(rename = "isLocked")]
    pub is_locked: bool,
    #[serde(rename = "reminderTime")]
    pub reminder_time: Option<i64>,
    pub attachments: Vec<String>,
    #[serde(rename = "createdAt")]
    pub created_at: i64,
    #[serde(rename = "updatedAt")]
    pub updated_at: i64,
    #[serde(rename = "backgroundImage")]
    pub background_image: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: i64,
    pub name: String,
    pub color: i32,
}
