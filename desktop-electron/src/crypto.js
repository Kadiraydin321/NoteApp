const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const SALT_LEN = 16;
const IV_LEN = 12;
const TAG_LEN = 16;
const PBKDF2_ITERATIONS = 65536;
const KEY_LEN = 32; // 256 bits
const PREFIX = 'ENC_GCM_V1:';

class CryptoManager {
  constructor(appDataDir) {
    this.appDataDir = appDataDir;
    this.configPath = path.join(appDataDir, 'vault_config.json');
    this.sessionKey = null;
    this.isVaultUnlocked = false;

    if (!fs.existsSync(this.appDataDir)) {
      fs.mkdirSync(this.appDataDir, { recursive: true });
    }
  }

  hasMasterPassword() {
    if (!fs.existsSync(this.configPath)) return false;
    try {
      const data = JSON.parse(fs.readFileSync(this.configPath, 'utf8'));
      return Boolean(data.salt && data.hash);
    } catch {
      return false;
    }
  }

  setupMasterPassword(password) {
    if (!password || password.length < 4) return false;
    try {
      const salt = crypto.randomBytes(SALT_LEN);
      const hash = crypto.createHash('sha256')
        .update(salt)
        .update(Buffer.from(password, 'utf8'))
        .digest();

      const config = {
        salt: salt.toString('base64'),
        hash: hash.toString('base64'),
        createdAt: Date.now()
      };

      fs.writeFileSync(this.configPath, JSON.stringify(config, null, 2), 'utf8');
      return this.unlockVault(password);
    } catch (e) {
      console.error('Master password setup error:', e);
      return false;
    }
  }

  unlockVault(password) {
    if (!this.hasMasterPassword()) return false;
    try {
      const data = JSON.parse(fs.readFileSync(this.configPath, 'utf8'));
      const salt = Buffer.from(data.salt, 'base64');
      const storedHash = Buffer.from(data.hash, 'base64');

      const computedHash = crypto.createHash('sha256')
        .update(salt)
        .update(Buffer.from(password, 'utf8'))
        .digest();

      if (crypto.timingSafeEqual(storedHash, computedHash)) {
        // Derive AES-256 session key using PBKDF2
        this.sessionKey = crypto.pbkdf2Sync(
          Buffer.from(password, 'utf8'),
          salt,
          PBKDF2_ITERATIONS,
          KEY_LEN,
          'sha256'
        );
        this.isVaultUnlocked = true;
        return true;
      }
    } catch (e) {
      console.error('Vault unlock error:', e);
    }
    return false;
  }

  lockVault() {
    this.sessionKey = null;
    this.isVaultUnlocked = false;
  }

  encryptContent(plainText) {
    if (!plainText || plainText.length === 0) return plainText;
    if (!this.sessionKey) return plainText;

    try {
      const iv = crypto.randomBytes(IV_LEN);
      const cipher = crypto.createCipheriv('aes-256-gcm', this.sessionKey, iv);
      const encrypted = Buffer.concat([
        cipher.update(Buffer.from(plainText, 'utf8')),
        cipher.final()
      ]);
      const authTag = cipher.getAuthTag();

      // Combine IV (12) + encrypted text + authTag (16) matching Java/Kotlin AES/GCM
      const combined = Buffer.concat([iv, encrypted, authTag]);
      return PREFIX + combined.toString('base64');
    } catch (e) {
      console.error('Encryption error:', e);
      return plainText;
    }
  }

  decryptContent(cipherText) {
    if (!cipherText || !cipherText.startsWith(PREFIX)) return cipherText;
    if (!this.sessionKey) return '🔒 Gizli Kasa İçeriği. Düzenlemek için şifreyi giriniz.';

    try {
      const base64Data = cipherText.slice(PREFIX.length);
      const combined = Buffer.from(base64Data, 'base64');

      if (combined.length < IV_LEN + TAG_LEN) {
        return '[Geçersiz Şifreli Veri]';
      }

      const iv = combined.subarray(0, IV_LEN);
      const authTag = combined.subarray(combined.length - TAG_LEN);
      const encryptedData = combined.subarray(IV_LEN, combined.length - TAG_LEN);

      const decipher = crypto.createDecipheriv('aes-256-gcm', this.sessionKey, iv);
      decipher.setAuthTag(authTag);

      const decrypted = Buffer.concat([
        decipher.update(encryptedData),
        decipher.final()
      ]);

      return decrypted.toString('utf8');
    } catch (e) {
      console.error('Decryption error:', e);
      return '[Şifreli İçerik Çözülemedi]';
    }
  }

  isEncrypted(text) {
    return typeof text === 'string' && text.startsWith(PREFIX);
  }
}

module.exports = CryptoManager;
