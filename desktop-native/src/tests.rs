use crate::crypto::{constant_time_eq, CryptoManager, PBKDF2_ITERATIONS, SALT_LEN};
use crate::db::Database;
use crate::models::{Category, Note, SyncState, VaultStatus};
use crate::sync_server::{constant_time_pin_match, is_allowed_client_ip};
use serde_json::json;
use std::net::SocketAddr;

// =========================================================================
// 1. MODELS TESTS (Serde, Android v1.4.4 Uyumlu Alias'lar)
// =========================================================================
#[cfg(test)]
mod models_tests {
    use super::*;

    #[test]
    fn test_note_deserialize_android_room_aliases() {
        // Android Room v1.4.4 alan adları: isDeleted ve reminderTime
        let raw_android_json = json!({
            "id": 101,
            "title": "Android Uyumlu Not",
            "content": "Bu not Android Room veritabanından yedeklenmiştir.",
            "timestamp": 1730000000000i64,
            "color": -123456,
            "categoryId": 5,
            "isPinned": true,
            "isLocked": false,
            "isArchived": false,
            "isDeleted": true, // Android Room alias -> isInTrash
            "deletedAt": 1730000050000i64,
            "reminderTime": 1730500000000i64, // Android Room alias -> reminderDateTime
            "tags": ["android", "yedek", "v1.4.4"],
            "attachments": ["file:///sdcard/Download/photo.jpg"],
            "createdAt": 1729000000000i64,
            "updatedAt": 1730000000000i64,
            "backgroundImage": null,
            "folderId": null
        });

        let note: Note = serde_json::from_value(raw_android_json)
            .expect("Android JSON Note modeline deserialize edilemedi!");

        assert_eq!(note.id, 101);
        assert_eq!(note.title, "Android Uyumlu Not");
        assert_eq!(note.category_id, Some(5));
        assert!(note.is_pinned);
        assert!(!note.is_locked);
        assert!(!note.is_archived);

        // isDeleted -> is_in_trash ve is_deleted() alias kontrolü
        assert!(note.is_in_trash);
        assert!(note.is_deleted());
        assert_eq!(note.deleted_at, Some(1730000050000i64));

        // reminderTime -> reminder_date_time ve reminder_time() alias kontrolü
        assert_eq!(note.reminder_date_time, Some(1730500000000i64));
        assert_eq!(note.reminder_time(), Some(1730500000000i64));

        // Etiketler ve ekler
        assert_eq!(note.tags, vec!["android", "yedek", "v1.4.4"]);
        assert_eq!(note.attachments, vec!["file:///sdcard/Download/photo.jpg"]);
    }

    #[test]
    fn test_note_deserialize_desktop_format() {
        // Masaüstü native JSON formatı: isInTrash ve reminderDateTime
        let raw_desktop_json = json!({
            "id": 202,
            "title": "Masaüstü Notu",
            "content": "Rust Wry native notu",
            "timestamp": 1731000000000i64,
            "color": 0,
            "categoryId": null,
            "isPinned": false,
            "isLocked": true,
            "isArchived": false,
            "isInTrash": false,
            "deletedAt": null,
            "reminderDateTime": null,
            "tags": ["masaustu", "rust"],
            "attachments": []
        });

        let note: Note = serde_json::from_value(raw_desktop_json)
            .expect("Masaüstü JSON Note modeline deserialize edilemedi!");

        assert_eq!(note.id, 202);
        assert_eq!(note.title, "Masaüstü Notu");
        assert!(note.is_locked);
        assert!(!note.is_in_trash);
        assert!(!note.is_deleted());
        assert_eq!(note.reminder_date_time, None);
        assert_eq!(note.reminder_time(), None);
        assert_eq!(note.tags.len(), 2);
    }

    #[test]
    fn test_note_serialize_output_fields() {
        let note = Note {
            id: 303,
            title: "Serileştirme Testi".to_string(),
            content: "İçerik".to_string(),
            timestamp: 1732000000000,
            color: 42,
            category_id: Some(7),
            is_pinned: true,
            is_locked: false,
            is_archived: false,
            is_in_trash: true,
            deleted_at: Some(1732000010000),
            reminder_date_time: Some(1732500000000),
            tags: vec!["test".to_string()],
            attachments: vec![],
            created_at: 1731000000000,
            updated_at: 1732000000000,
            background_image: None,
            folder_id: None,
        };

        let val = serde_json::to_value(&note).expect("Serileştirme başarısız!");

        // Serileştirilen JSON alan isimleri şartnameye uygun olmalı
        assert_eq!(val["categoryId"], 7);
        assert_eq!(val["isPinned"], true);
        assert_eq!(val["isInTrash"], true);
        assert_eq!(val["deletedAt"], 1732000010000i64);
        assert_eq!(val["reminderDateTime"], 1732500000000i64);
        assert_eq!(val["tags"][0], "test");
    }

    #[test]
    fn test_category_and_sync_state_serde() {
        let cat = Category {
            id: 1,
            name: "İş".to_string(),
            color: -65536,
        };
        let cat_json = serde_json::to_string(&cat).unwrap();
        let cat_deser: Category = serde_json::from_str(&cat_json).unwrap();
        assert_eq!(cat_deser.name, "İş");

        let sync_state = SyncState {
            is_running: true,
            port: 8765,
            pin: "1234".to_string(),
            connected_clients: 2,
            local_ip: "192.168.1.35".to_string(),
            last_message: "Bağlandı".to_string(),
        };
        let sync_json = serde_json::to_string(&sync_state).unwrap();
        let sync_deser: SyncState = serde_json::from_str(&sync_json).unwrap();
        assert_eq!(sync_deser.pin, "1234");
        assert_eq!(sync_deser.port, 8765);

        let vault_status = VaultStatus {
            has_password: true,
            is_unlocked: false,
        };
        let v_json = serde_json::to_string(&vault_status).unwrap();
        let v_deser: VaultStatus = serde_json::from_str(&v_json).unwrap();
        assert!(v_deser.has_password);
        assert!(!v_deser.is_unlocked);
    }
}

// =========================================================================
// 2. CRYPTO TESTS (PBKDF2, AES-256-GCM, Constant-Time Comparison)
// =========================================================================
#[cfg(test)]
mod crypto_tests {
    use super::*;
    use aes_gcm::{
        aead::{Aead, KeyInit},
        Aes256Gcm, Nonce,
    };
    use base64::prelude::*;

    #[test]
    fn test_constant_time_eq() {
        // Aynı baytlar -> true
        assert!(constant_time_eq(b"SecretPassword123!", b"SecretPassword123!"));
        assert!(constant_time_eq(b"", b""));

        // Farklı baytlar -> false
        assert!(!constant_time_eq(b"SecretPassword123!", b"SecretPassword123?"));
        assert!(!constant_time_eq(b"admin", b"root_"));

        // Farklı uzunluklar -> false
        assert!(!constant_time_eq(b"123", b"1234"));
        assert!(!constant_time_eq(b"123456", b"12345"));
    }

    #[test]
    fn test_pbkdf2_key_derivation() {
        let password = "TestMasterPassword2026!";
        let salt = [0x5Au8; SALT_LEN];

        // 1. Deterministiklik Testi: Aynı parola ve salt daima aynı anahtarı üretmeli
        let key1 = CryptoManager::derive_key(password, &salt, PBKDF2_ITERATIONS)
            .expect("PBKDF2 anahtar türetilemedi");
        let key2 = CryptoManager::derive_key(password, &salt, PBKDF2_ITERATIONS)
            .expect("PBKDF2 anahtar türetilemedi");

        assert_eq!(key1, key2);
        assert_eq!(key1.len(), 32); // 256-bit

        // 2. Farklı parola farklı anahtar üretmeli
        let key_diff_pass = CryptoManager::derive_key("BaskaBirSifre", &salt, PBKDF2_ITERATIONS)
            .expect("PBKDF2 anahtar türetilemedi");
        assert_ne!(key1, key_diff_pass);

        // 3. Farklı salt farklı anahtar üretmeli
        let diff_salt = [0x7Fu8; SALT_LEN];
        let key_diff_salt = CryptoManager::derive_key(password, &diff_salt, PBKDF2_ITERATIONS)
            .expect("PBKDF2 anahtar türetilemedi");
        assert_ne!(key1, key_diff_salt);
    }

    #[test]
    fn test_aes_256_gcm_roundtrip() {
        let test_key = [0x42u8; 32];
        let crypto = CryptoManager::with_session_key(test_key);

        assert!(crypto.is_unlocked());

        let original_text = "Bu çok gizli bir test notudur. AES-256-GCM ile korunmaktadır! 🔒 UTF-8: ğüşıöç";
        let encrypted = crypto.encrypt_content(original_text);

        // Şifreli içerik ENC_GCM_V1: ön ekiyle başlamalı
        assert!(encrypted.starts_with("ENC_GCM_V1:"));
        assert_ne!(encrypted, original_text);

        // Deşifre et ve doğrula
        let decrypted = crypto.decrypt_content(&encrypted);
        assert_eq!(decrypted, original_text);
    }

    #[test]
    fn test_aes_gcm_empty_string() {
        let test_key = [0x11u8; 32];
        let crypto = CryptoManager::with_session_key(test_key);

        let enc_empty = crypto.encrypt_content("");
        assert_eq!(enc_empty, "");

        let dec_empty = crypto.decrypt_content("");
        assert_eq!(dec_empty, "");
    }

    #[test]
    fn test_aes_gcm_android_compatibility_format() {
        // Android formatı: ENC_GCM_V1:<base64(iv + ciphertext + tag)>
        let key = [0x33u8; 32];
        let crypto = CryptoManager::with_session_key(key);

        let iv = [0x07u8; 12];
        let plain = b"Android Mobil Cihazdan Gelen Not";

        let cipher = Aes256Gcm::new_from_slice(&key).unwrap();
        let nonce = Nonce::from_slice(&iv);
        let ct_with_tag = cipher.encrypt(nonce, plain.as_ref()).unwrap();

        let mut combined = Vec::new();
        combined.extend_from_slice(&iv);
        combined.extend_from_slice(&ct_with_tag);

        let android_payload = format!("ENC_GCM_V1:{}", BASE64_STANDARD.encode(&combined));

        // Masaüstü motoru bu formatı deşifre edebilmeli
        let decrypted = crypto.decrypt_content(&android_payload);
        assert_eq!(decrypted, "Android Mobil Cihazdan Gelen Not");
    }

    #[test]
    fn test_crypto_manager_lock_vault() {
        let test_key = [0x99u8; 32];
        let crypto = CryptoManager::with_session_key(test_key);

        assert!(crypto.is_unlocked());
        let encrypted = crypto.encrypt_content("Kilitlenecek Not");

        // Kasayı kilitle
        crypto.lock_vault();
        assert!(!crypto.is_unlocked());

        // Kilitliyken deşifre girişimi hata mesajı dönmeli
        let result = crypto.decrypt_content(&encrypted);
        assert!(result.contains("🔒") || result.contains("Kasayı"));
    }
}

// =========================================================================
// 3. DATABASE TESTS (In-Memory SQLite, CRUD, Wikilinks, Tags, Trash)
// =========================================================================
#[cfg(test)]
mod db_tests {
    use super::*;

    fn create_test_note(title: &str, content: &str, tags: Vec<&str>) -> Note {
        Note {
            id: 0,
            title: title.to_string(),
            content: content.to_string(),
            timestamp: 1730000000000,
            color: 0,
            category_id: None,
            is_pinned: false,
            is_locked: false,
            is_archived: false,
            is_in_trash: false,
            deleted_at: None,
            reminder_date_time: None,
            tags: tags.into_iter().map(String::from).collect(),
            attachments: vec![],
            created_at: 1730000000000,
            updated_at: 1730000000000,
            background_image: None,
            folder_id: None,
        }
    }

    #[test]
    fn test_db_crud_operations() {
        let db = Database::new_in_memory();

        // 1. Create (Insert)
        let note = create_test_note("Rust Mimarisi", "Native desktop modülü", vec!["rust", "mimarisi"]);
        let inserted = db.insert_note(note).expect("Not eklenemedi");
        assert!(inserted.id > 0);
        let note_id = inserted.id;

        // 2. Read (Get by ID)
        let fetched = db.get_note_by_id(note_id).expect("Sorgu hatası");
        assert!(fetched.is_some());
        let fetched_note = fetched.unwrap();
        assert_eq!(fetched_note.title, "Rust Mimarisi");
        assert_eq!(fetched_note.tags, vec!["rust", "mimarisi"]);

        // 3. Update
        let mut to_update = fetched_note;
        to_update.title = "Rust Mimarisi v2".to_string();
        to_update.color = 0xFF5722;
        let updated = db.update_note(to_update).expect("Not güncellenemedi");
        assert_eq!(updated.title, "Rust Mimarisi v2");
        assert_eq!(updated.color, 0xFF5722);

        // 4. List Active
        let active_notes = db.get_active_notes().expect("Aktif notlar alınamadı");
        assert_eq!(active_notes.len(), 1);
        assert_eq!(active_notes[0].id, note_id);
    }

    #[test]
    fn test_trash_and_restore() {
        let db = Database::new_in_memory();

        let note = create_test_note("Çöp Kutusu Testi", "Silinecek içerik", vec!["cop"]);
        let inserted = db.insert_note(note).unwrap();
        let note_id = inserted.id;

        // Çöp kutusuna taşı
        db.move_to_trash(note_id).expect("Çöp kutusuna taşınamadı");

        // Aktif notlarda görünmemeli
        let active = db.get_active_notes().unwrap();
        assert!(active.is_empty());

        // Çöp kutusunda görünmeli
        let trash = db.get_trash_notes().expect("Çöp kutusu notları alınamadı");
        assert_eq!(trash.len(), 1);
        assert_eq!(trash[0].id, note_id);
        assert!(trash[0].is_in_trash);
        assert!(trash[0].deleted_at.is_some());

        // Geri yükle (Restore)
        db.restore_from_trash(note_id).expect("Geri yüklenemedi");

        let trash_after = db.get_trash_notes().unwrap();
        assert!(trash_after.is_empty());

        let active_after = db.get_active_notes().unwrap();
        assert_eq!(active_after.len(), 1);
        assert_eq!(active_after[0].id, note_id);
        assert!(!active_after[0].is_in_trash);
        assert_eq!(active_after[0].deleted_at, None);

        // Kalıcı sil (Permanent Delete)
        db.delete_permanently(note_id).expect("Kalıcı silinemedi");
        let not_found = db.get_note_by_id(note_id).unwrap();
        assert!(not_found.is_none());
    }

    #[test]
    fn test_wikilinks_bidirectional() {
        let db = Database::new_in_memory();

        // Hedef Not
        let target_note = create_test_note("Hedef Sayfa", "Hedef içerik", vec![]);
        let target_inserted = db.insert_note(target_note).unwrap();

        // Kaynak Not 1: Standart [[Hedef Sayfa]] wikilink bağlantısı
        let source1 = create_test_note(
            "Kaynak Not 1",
            "Bu not [[Hedef Sayfa]] konusuna referans verir.",
            vec![],
        );
        let src1_inserted = db.insert_note(source1).unwrap();

        // Kaynak Not 2: Boru etiketli [[Hedef Sayfa|Özel Etiket]] wikilink bağlantısı
        let source2 = create_test_note(
            "Kaynak Not 2",
            "Ayrıca [[Hedef Sayfa|Görünen İsim]] bağlantısı da var.",
            vec![],
        );
        let src2_inserted = db.insert_note(source2).unwrap();

        // Bağlantısız Not
        let unrelated = create_test_note("Alakasız Not", "Burada hiç bağlantı yok.", vec![]);
        db.insert_note(unrelated).unwrap();

        // Çift yönlü arama: 'Hedef Sayfa'ya referans veren notları bul
        let backlinks = db
            .get_notes_linking_to("Hedef Sayfa", None)
            .expect("Wikilinks araması başarısız");

        assert_eq!(backlinks.len(), 2);
        let backlink_ids: Vec<i64> = backlinks.iter().map(|n| n.id).collect();
        assert!(backlink_ids.contains(&src1_inserted.id));
        assert!(backlink_ids.contains(&src2_inserted.id));

        // exclude_id testi: src1 hariç tutulunca yalnızca src2 dönmeli
        let backlinks_excluded = db
            .get_notes_linking_to("Hedef Sayfa", Some(src1_inserted.id))
            .unwrap();
        assert_eq!(backlinks_excluded.len(), 1);
        assert_eq!(backlinks_excluded[0].id, src2_inserted.id);

        // Olmayan bir başlık için arama -> Boş dönmeli
        let empty_links = db.get_notes_linking_to("Bilinmeyen Baslik", None).unwrap();
        assert!(empty_links.is_empty());
    }

    #[test]
    fn test_tag_filtering() {
        let db = Database::new_in_memory();

        let n1 = create_test_note("Not 1", "İçerik 1", vec!["rust", "backend"]);
        let n2 = create_test_note("Not 2", "İçerik 2", vec!["android", "mobile"]);
        let n3 = create_test_note("Not 3", "İçerik 3", vec!["rust", "desktop"]);

        let id1 = db.insert_note(n1).unwrap().id;
        let id2 = db.insert_note(n2).unwrap().id;
        let id3 = db.insert_note(n3).unwrap().id;

        // 'rust' etiketine sahip notlar (Not 1 ve Not 3)
        let rust_notes = db.get_notes_by_tag("rust").expect("Etiket sorgusu başarısız");
        assert_eq!(rust_notes.len(), 2);
        let rust_ids: Vec<i64> = rust_notes.iter().map(|n| n.id).collect();
        assert!(rust_ids.contains(&id1));
        assert!(rust_ids.contains(&id3));

        // 'android' etiketine sahip notlar (Not 2)
        let android_notes = db.get_notes_by_tag("android").unwrap();
        assert_eq!(android_notes.len(), 1);
        assert_eq!(android_notes[0].id, id2);

        // Bulunmayan etiket
        let flutter_notes = db.get_notes_by_tag("flutter").unwrap();
        assert!(flutter_notes.is_empty());
    }
}

// =========================================================================
// 4. SYNC TESTS (Constant-Time PIN Matching, Subnet IP Filtering)
// =========================================================================
#[cfg(test)]
mod sync_tests {
    use super::*;

    #[test]
    fn test_constant_time_pin_matching() {
        // Tam eşleşme
        assert!(constant_time_pin_match("7821", "7821"));

        // Boşluklu girişler (trim güvenliği)
        assert!(constant_time_pin_match("  7821 ", "7821"));
        assert!(constant_time_pin_match("7821", " 7821\n"));

        // Hatalı PIN
        assert!(!constant_time_pin_match("7821", "7822"));
        assert!(!constant_time_pin_match("0000", "1234"));

        // Farklı uzunluk
        assert!(!constant_time_pin_match("782", "7821"));
        assert!(!constant_time_pin_match("78210", "7821"));

        // Boş PIN
        assert!(constant_time_pin_match("", ""));
        assert!(!constant_time_pin_match("1234", ""));
    }

    #[test]
    fn test_allowed_client_ip() {
        // Loopback (İzinli)
        let loopback: SocketAddr = "127.0.0.1:8765".parse().unwrap();
        assert!(is_allowed_client_ip(&loopback));

        // RFC 1918 Class C (İzinli)
        let class_c: SocketAddr = "192.168.1.42:54321".parse().unwrap();
        assert!(is_allowed_client_ip(&class_c));

        // RFC 1918 Class A (İzinli)
        let class_a: SocketAddr = "10.0.0.15:12345".parse().unwrap();
        assert!(is_allowed_client_ip(&class_a));

        // RFC 1918 Class B (İzinli)
        let class_b_1: SocketAddr = "172.16.0.1:8765".parse().unwrap();
        let class_b_2: SocketAddr = "172.31.255.254:8765".parse().unwrap();
        assert!(is_allowed_client_ip(&class_b_1));
        assert!(is_allowed_client_ip(&class_b_2));

        // RFC 3927 Link-Local (İzinli)
        let link_local: SocketAddr = "169.254.120.3:8765".parse().unwrap();
        assert!(is_allowed_client_ip(&link_local));

        // Genel İnternet IP'leri (KESİNLİKLE ENGELLİ)
        let public_dns: SocketAddr = "8.8.8.8:8765".parse().unwrap();
        assert!(!is_allowed_client_ip(&public_dns));

        let cloudflare: SocketAddr = "1.1.1.1:8765".parse().unwrap();
        assert!(!is_allowed_client_ip(&cloudflare));

        let public_ext: SocketAddr = "203.0.113.195:8765".parse().unwrap();
        assert!(!is_allowed_client_ip(&public_ext));
    }
}
