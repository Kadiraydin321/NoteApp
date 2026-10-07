// engine/signatures.js
// Kapsamlı Dosya İmzaları (Magic Bytes) ve Başlık/Bitiş Kuralları

export const BUILTIN_SIGNATURES = [
  // --- GÖRSELLER & FOTOĞRAFLAR ---
  {
    id: 'jpg',
    name: 'JPEG / JPG Görsel',
    category: 'images',
    extension: 'jpg',
    mime: 'image/jpeg',
    header: Buffer.from([0xFF, 0xD8, 0xFF]),
    footer: Buffer.from([0xFF, 0xD9]),
    minSize: 100,
    maxSize: 50 * 1024 * 1024, // 50 MB
    extractSize: (buffer, offset) => {
      // JPEG EOI (End of Image) işaretçisini ara (FF D9)
      const maxSearch = Math.min(buffer.length, offset + 50 * 1024 * 1024);
      for (let i = offset + 2; i < maxSearch - 1; i++) {
        if (buffer[i] === 0xFF && buffer[i + 1] === 0xD9) {
          return (i + 2) - offset;
        }
      }
      return null;
    }
  },
  {
    id: 'png',
    name: 'PNG Görsel',
    category: 'images',
    extension: 'png',
    mime: 'image/png',
    header: Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    footer: Buffer.from([0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82]), // IEND chunk + CRC
    minSize: 64,
    maxSize: 100 * 1024 * 1024, // 100 MB
    extractSize: (buffer, offset) => {
      const footer = Buffer.from([0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82]);
      const maxSearch = Math.min(buffer.length, offset + 100 * 1024 * 1024);
      for (let i = offset + 8; i <= maxSearch - 8; i++) {
        if (buffer.subarray(i, i + 8).equals(footer)) {
          return (i + 8) - offset;
        }
      }
      return null;
    }
  },
  {
    id: 'gif',
    name: 'GIF Hareketli Görsel',
    category: 'images',
    extension: 'gif',
    mime: 'image/gif',
    header: Buffer.from([0x47, 0x49, 0x46, 0x38]), // GIF8 (GIF87a veya GIF89a)
    footer: Buffer.from([0x00, 0x3B]),
    minSize: 32,
    maxSize: 50 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      const maxSearch = Math.min(buffer.length, offset + 50 * 1024 * 1024);
      for (let i = offset + 6; i < maxSearch - 1; i++) {
        if (buffer[i] === 0x00 && buffer[i + 1] === 0x3B) {
          return (i + 2) - offset;
        }
      }
      return null;
    }
  },
  {
    id: 'webp',
    name: 'WEBP Görsel',
    category: 'images',
    extension: 'webp',
    mime: 'image/webp',
    header: Buffer.from([0x52, 0x49, 0x46, 0x46]), // RIFF
    minSize: 32,
    maxSize: 50 * 1024 * 1024,
    validate: (buffer, offset) => {
      if (offset + 12 > buffer.length) return false;
      return buffer.subarray(offset + 8, offset + 12).toString('ascii') === 'WEBP';
    },
    extractSize: (buffer, offset) => {
      if (offset + 8 > buffer.length) return null;
      const riffSize = buffer.readUInt32LE(offset + 4);
      return riffSize + 8;
    }
  },
  {
    id: 'bmp',
    name: 'BMP Bitmap Görsel',
    category: 'images',
    extension: 'bmp',
    mime: 'image/bmp',
    header: Buffer.from([0x42, 0x4D]), // BM
    minSize: 54,
    maxSize: 100 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      if (offset + 6 > buffer.length) return null;
      const size = buffer.readUInt32LE(offset + 2);
      if (size > 54 && size <= 100 * 1024 * 1024) return size;
      return null;
    }
  },

  // --- BELGELER & OFİS ---
  {
    id: 'pdf',
    name: 'PDF Belgesi',
    category: 'documents',
    extension: 'pdf',
    mime: 'application/pdf',
    header: Buffer.from([0x25, 0x50, 0x44, 0x46, 0x2D]), // %PDF-
    minSize: 128,
    maxSize: 200 * 1024 * 1024, // 200 MB
    extractSize: (buffer, offset) => {
      const eofPattern = Buffer.from('%%EOF');
      const maxSearch = Math.min(buffer.length, offset + 200 * 1024 * 1024);
      let lastMatch = null;
      // PDF dosyalarında birden fazla revizyon/%%EOF olabilir, sonuncuyu bulmak en iyisidir
      for (let i = offset + 10; i <= maxSearch - 5; i++) {
        if (buffer[i] === 0x25 && buffer[i + 1] === 0x25 &&
            buffer[i + 2] === 0x45 && buffer[i + 3] === 0x4F && buffer[i + 4] === 0x46) {
          lastMatch = (i + 5) - offset;
          // Satır sonu karakterlerini (CR/LF) de dahil et
          while (offset + lastMatch < buffer.length && 
                 (buffer[offset + lastMatch] === 0x0A || buffer[offset + lastMatch] === 0x0D)) {
            lastMatch++;
          }
          // İlk makul boyutta durdurma (veya sonuncuya kadar ilerleme)
          if (lastMatch > 5000000) break; // 5MB üzerinde ilk büyük blokta optimize et
        }
      }
      return lastMatch;
    }
  },
  {
    id: 'zip_office',
    name: 'ZIP / MS Office (DOCX/XLSX/PPTX)',
    category: 'documents',
    extension: 'zip',
    mime: 'application/zip',
    header: Buffer.from([0x50, 0x4B, 0x03, 0x04]), // PK..
    minSize: 64,
    maxSize: 300 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      // EOCD (End of Central Directory Record): 50 4B 05 06
      const eocd = Buffer.from([0x50, 0x4B, 0x05, 0x06]);
      const maxSearch = Math.min(buffer.length, offset + 300 * 1024 * 1024);
      for (let i = offset + 22; i <= maxSearch - 22; i++) {
        if (buffer.subarray(i, i + 4).equals(eocd)) {
          const commentLength = buffer.readUInt16LE(i + 20);
          return (i + 22 + commentLength) - offset;
        }
      }
      return null;
    },
    postProcess: (buffer, offset, size) => {
      // İçeriğe bakarak DOCX, XLSX veya PPTX mi olduğunu tespit et
      const searchLen = Math.min(size, 4096);
      const headerStr = buffer.subarray(offset, offset + searchLen).toString('binary');
      if (headerStr.includes('word/')) {
        return { extension: 'docx', mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', name: 'Word Belgesi (DOCX)' };
      }
      if (headerStr.includes('xl/')) {
        return { extension: 'xlsx', mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', name: 'Excel Tablosu (XLSX)' };
      }
      if (headerStr.includes('ppt/')) {
        return { extension: 'pptx', mime: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', name: 'PowerPoint (PPTX)' };
      }
      return { extension: 'zip', mime: 'application/zip', name: 'ZIP Arşivi' };
    }
  },

  // --- MEDYA & SES/VİDEO ---
  {
    id: 'mp4',
    name: 'MP4 / MOV Video',
    category: 'media',
    extension: 'mp4',
    mime: 'video/mp4',
    header: Buffer.from([0x66, 0x74, 0x79, 0x70]), // ftyp (offset 4)
    headerOffset: 4,
    minSize: 1024,
    maxSize: 1024 * 1024 * 1024, // 1 GB
    extractSize: (buffer, offset) => {
      // İlk box boyutu genelde offset 0'daki 4 baytlık Big Endian tamsayıdır
      if (offset + 4 > buffer.length) return null;
      const initialBoxSize = buffer.readUInt32BE(offset);
      if (initialBoxSize > 16 && initialBoxSize < buffer.length - offset) {
        // Box zincirini tara
        let cur = offset;
        const maxLimit = Math.min(buffer.length, offset + 1024 * 1024 * 1024);
        while (cur + 8 <= maxLimit) {
          const boxLen = buffer.readUInt32BE(cur);
          if (boxLen === 0) {
            // dosya sonuna kadar uzanır
            return Math.min(buffer.length - offset, 200 * 1024 * 1024);
          }
          if (boxLen < 8 || boxLen > 1024 * 1024 * 1024) break;
          cur += boxLen;
          if (cur - offset > 1024 * 1024 * 1024) break;
        }
        if (cur > offset + 1024) return cur - offset;
      }
      return null;
    }
  },
  {
    id: 'mkv',
    name: 'MKV / WebM Video',
    category: 'media',
    extension: 'mkv',
    mime: 'video/x-matroska',
    header: Buffer.from([0x1A, 0x45, 0xDF, 0xA3]), // EBML header
    minSize: 4096,
    maxSize: 1024 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      // Varsayılan güvenli boyut (akış halinde kurtarma için)
      return Math.min(buffer.length - offset, 50 * 1024 * 1024);
    }
  },
  {
    id: 'mp3',
    name: 'MP3 Ses Dosyası',
    category: 'media',
    extension: 'mp3',
    mime: 'audio/mpeg',
    header: Buffer.from([0x49, 0x44, 0x33]), // ID3v2
    minSize: 1024,
    maxSize: 100 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      if (offset + 10 > buffer.length) return null;
      // ID3v2 etiket boyutunu oku (Synchsafe integer)
      const b6 = buffer[offset + 6];
      const b7 = buffer[offset + 7];
      const b8 = buffer[offset + 8];
      const b9 = buffer[offset + 9];
      const tagSize = ((b6 & 0x7F) << 21) | ((b7 & 0x7F) << 14) | ((b8 & 0x7F) << 7) | (b9 & 0x7F);
      const totalEstimated = tagSize + 10 + 5 * 1024 * 1024; // etiket + yaklaşık 5MB ses verisi
      return Math.min(totalEstimated, buffer.length - offset);
    }
  },
  {
    id: 'wav',
    name: 'WAV Ses Dosyası',
    category: 'media',
    extension: 'wav',
    mime: 'audio/wav',
    header: Buffer.from([0x52, 0x49, 0x46, 0x46]), // RIFF
    minSize: 44,
    maxSize: 200 * 1024 * 1024,
    validate: (buffer, offset) => {
      if (offset + 12 > buffer.length) return false;
      return buffer.subarray(offset + 8, offset + 12).toString('ascii') === 'WAVE';
    },
    extractSize: (buffer, offset) => {
      if (offset + 8 > buffer.length) return null;
      return buffer.readUInt32LE(offset + 4) + 8;
    }
  },
  {
    id: 'flac',
    name: 'FLAC Kayıpsız Ses',
    category: 'media',
    extension: 'flac',
    mime: 'audio/flac',
    header: Buffer.from([0x66, 0x4C, 0x61, 0x43]), // fLaC
    minSize: 1024,
    maxSize: 150 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      return Math.min(buffer.length - offset, 25 * 1024 * 1024);
    }
  },

  // --- ARŞİVLER ---
  {
    id: '7z',
    name: '7-Zip Arşivi',
    category: 'archives',
    extension: '7z',
    mime: 'application/x-7z-compressed',
    header: Buffer.from([0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C]),
    minSize: 32,
    maxSize: 500 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      // 7z başlangıç başlığı 32 bayttır. Sonraki veriyi güvenli sınırla oku
      if (offset + 32 > buffer.length) return null;
      return Math.min(buffer.length - offset, 100 * 1024 * 1024);
    }
  },
  {
    id: 'rar',
    name: 'RAR Arşivi (v4/v5)',
    category: 'archives',
    extension: 'rar',
    mime: 'application/vnd.rar',
    header: Buffer.from([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07]), // Rar!\x1a\x07
    minSize: 32,
    maxSize: 500 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      return Math.min(buffer.length - offset, 50 * 1024 * 1024);
    }
  },
  {
    id: 'gz',
    name: 'GZIP Sıkıştırılmış Dosya',
    category: 'archives',
    extension: 'gz',
    mime: 'application/gzip',
    header: Buffer.from([0x1F, 0x8B, 0x08]),
    minSize: 20,
    maxSize: 300 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      return Math.min(buffer.length - offset, 20 * 1024 * 1024);
    }
  },
  {
    id: 'tar',
    name: 'TAR Arşivi (POSIX)',
    category: 'archives',
    extension: 'tar',
    mime: 'application/x-tar',
    header: Buffer.from([0x75, 0x73, 0x74, 0x61, 0x72]), // ustar (offset 257)
    headerOffset: 257,
    minSize: 512,
    maxSize: 500 * 1024 * 1024,
    extractSize: (buffer, offset) => {
      return Math.min(buffer.length - offset, 50 * 1024 * 1024);
    }
  }
];

// Kullanıcı tanımlı özel imza oluşturucu
export function parseCustomSignature(raw) {
  // raw: { name, hex, extension, category: 'custom' }
  const cleanHex = raw.hex.replace(/[\s\-,:]/g, '');
  if (!cleanHex || cleanHex.length % 2 !== 0) {
    throw new Error('Geçersiz Onaltılık (Hex) bayt dizesi. Çift sayıda karakter olmalıdır.');
  }
  const headerBuf = Buffer.from(cleanHex, 'hex');
  return {
    id: 'custom_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6),
    name: raw.name || 'Özel Dosya (' + raw.extension + ')',
    category: 'custom',
    extension: (raw.extension || 'bin').replace(/^\./, ''),
    mime: 'application/octet-stream',
    header: headerBuf,
    headerOffset: 0,
    minSize: headerBuf.length,
    maxSize: (raw.maxSizeMB || 50) * 1024 * 1024,
    extractSize: (buffer, offset) => {
      return Math.min(buffer.length - offset, (raw.maxSizeMB || 50) * 1024 * 1024);
    }
  };
}
