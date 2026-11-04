/**
 * Lightweight Zero-Dependency ZIP Archive Builder & Parser for Node.js
 * Uses standard Node.js zlib for Deflate/Inflate.
 */
const zlib = require('zlib');

function calculateCrc32(buf) {
  if (typeof zlib.crc32 === 'function') {
    return zlib.crc32(buf) >>> 0;
  }
  let table = [];
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[n] = c;
  }
  let crc = 0 ^ (-1);
  for (let i = 0; i < buf.length; i++) {
    crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xFF];
  }
  return (crc ^ (-1)) >>> 0;
}

function dosDateTime(date = new Date()) {
  const d = new Date(date);
  const time = ((d.getHours() << 11) | (d.getMinutes() << 5) | (Math.floor(d.getSeconds() / 2))) & 0xFFFF;
  const dateVal = (((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate()) & 0xFFFF;
  return { time, date: dateVal };
}

/**
 * Creates a valid ZIP buffer from an array of files:
 * files: [{ name: 'notes.json', data: Buffer|string }]
 * @param {Array<{name: string, data: Buffer|string}>} files
 * @param {boolean} compress - whether to compress with DEFLATE (default true)
 * @returns {Buffer}
 */
function createZip(files, compress = true) {
  const localChunks = [];
  const centralChunks = [];
  let currentOffset = 0;

  const { time: modTime, date: modDate } = dosDateTime();

  for (const file of files) {
    const rawData = Buffer.isBuffer(file.data) ? file.data : Buffer.from(file.data, 'utf-8');
    const nameBuf = Buffer.from(file.name.replace(/\\/g, '/'), 'utf-8');
    const crc = calculateCrc32(rawData);
    const uncompressedSize = rawData.length;

    let compressedData = rawData;
    let method = 0; // Store

    if (compress && uncompressedSize > 0) {
      const deflated = zlib.deflateRawSync(rawData);
      if (deflated.length < uncompressedSize) {
        compressedData = deflated;
        method = 8; // Deflate
      }
    }

    const compressedSize = compressedData.length;

    // Local file header (30 bytes + nameBuf.length)
    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0); // Signature
    localHeader.writeUInt16LE(20, 4);          // Min version
    localHeader.writeUInt16LE(0x0800, 6);      // Flags: Bit 11 UTF-8
    localHeader.writeUInt16LE(method, 8);      // Method
    localHeader.writeUInt16LE(modTime, 10);    // Mod time
    localHeader.writeUInt16LE(modDate, 12);    // Mod date
    localHeader.writeUInt32LE(crc, 14);        // CRC32
    localHeader.writeUInt32LE(compressedSize, 18);
    localHeader.writeUInt32LE(uncompressedSize, 22);
    localHeader.writeUInt16LE(nameBuf.length, 26);
    localHeader.writeUInt16LE(0, 28);          // Extra len

    const localRecord = Buffer.concat([localHeader, nameBuf, compressedData]);
    localChunks.push(localRecord);

    // Central Directory Record (46 bytes + nameBuf.length)
    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0); // Signature
    centralHeader.writeUInt16LE(20, 4);          // Version made by
    centralHeader.writeUInt16LE(20, 6);          // Version needed
    centralHeader.writeUInt16LE(0x0800, 8);      // Flags: UTF-8
    centralHeader.writeUInt16LE(method, 10);     // Method
    centralHeader.writeUInt16LE(modTime, 12);
    centralHeader.writeUInt16LE(modDate, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(compressedSize, 20);
    centralHeader.writeUInt32LE(uncompressedSize, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30);          // Extra len
    centralHeader.writeUInt16LE(0, 32);          // Comment len
    centralHeader.writeUInt16LE(0, 34);          // Disk num
    centralHeader.writeUInt16LE(0, 36);          // Internal attr
    centralHeader.writeUInt32LE(0, 38);          // External attr
    centralHeader.writeUInt32LE(currentOffset, 42); // Offset of local header

    centralChunks.push(Buffer.concat([centralHeader, nameBuf]));
    currentOffset += localRecord.length;
  }

  const centralDirBuffer = Buffer.concat(centralChunks);
  const centralDirSize = centralDirBuffer.length;
  const centralDirOffset = currentOffset;

  // End of Central Directory (22 bytes)
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4); // Disk num
  eocd.writeUInt16LE(0, 6); // Central dir disk
  eocd.writeUInt16LE(files.length, 8); // Entries on disk
  eocd.writeUInt16LE(files.length, 10); // Total entries
  eocd.writeUInt32LE(centralDirSize, 12);
  eocd.writeUInt32LE(centralDirOffset, 16);
  eocd.writeUInt16LE(0, 20); // Comment len

  return Buffer.concat([...localChunks, centralDirBuffer, eocd]);
}

/**
 * Parses a ZIP archive buffer and returns a map of entries:
 * { "backup_info.json": Buffer, "notes.json": Buffer, ... }
 * @param {Buffer} zipBuffer
 * @returns {Record<string, Buffer>}
 */
function readZip(zipBuffer) {
  if (!Buffer.isBuffer(zipBuffer) || zipBuffer.length < 22) {
    throw new Error('Geçersiz ZIP arşivi: Boyut çok küçük.');
  }

  // Find EOCD by scanning from end of buffer
  let eocdOffset = -1;
  for (let i = zipBuffer.length - 22; i >= Math.max(0, zipBuffer.length - 65557); i--) {
    if (zipBuffer.readUInt32LE(i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }

  if (eocdOffset === -1) {
    throw new Error('Geçersiz ZIP arşivi: EOCD imzası bulunamadı.');
  }

  const totalEntries = zipBuffer.readUInt16LE(eocdOffset + 10);
  const centralDirSize = zipBuffer.readUInt32LE(eocdOffset + 12);
  const centralDirOffset = zipBuffer.readUInt32LE(eocdOffset + 16);

  const result = {};
  let cur = centralDirOffset;

  for (let idx = 0; idx < totalEntries; idx++) {
    if (cur + 46 > zipBuffer.length) break;
    const sig = zipBuffer.readUInt32LE(cur);
    if (sig !== 0x02014b50) break;

    const method = zipBuffer.readUInt16LE(cur + 10);
    const compressedSize = zipBuffer.readUInt32LE(cur + 20);
    const uncompressedSize = zipBuffer.readUInt32LE(cur + 24);
    const nameLen = zipBuffer.readUInt16LE(cur + 28);
    const extraLen = zipBuffer.readUInt16LE(cur + 30);
    const commentLen = zipBuffer.readUInt16LE(cur + 32);
    const localHeaderOffset = zipBuffer.readUInt32LE(cur + 42);

    const name = zipBuffer.toString('utf-8', cur + 46, cur + 46 + nameLen);
    cur += 46 + nameLen + extraLen + commentLen;

    // Read local header to find exact payload offset
    const localSig = zipBuffer.readUInt32LE(localHeaderOffset);
    if (localSig !== 0x04034b50) {
      throw new Error(`Bozuk ZIP: '${name}' için yerel başlık bulunamadı.`);
    }
    const localNameLen = zipBuffer.readUInt16LE(localHeaderOffset + 26);
    const localExtraLen = zipBuffer.readUInt16LE(localHeaderOffset + 28);
    const dataStart = localHeaderOffset + 30 + localNameLen + localExtraLen;
    const rawCompressedData = zipBuffer.subarray(dataStart, dataStart + compressedSize);

    let extractedData;
    if (method === 0) {
      extractedData = Buffer.from(rawCompressedData);
    } else if (method === 8) {
      extractedData = zlib.inflateRawSync(rawCompressedData);
    } else {
      throw new Error(`Desteklenmeyen sıkıştırma yöntemi (${method}) dosya: ${name}`);
    }

    result[name] = extractedData;
  }

  return result;
}

module.exports = {
  createZip,
  readZip,
  calculateCrc32
};
