// scripts/make-icon.cjs
const { app, nativeImage } = require('electron');
const fs = require('fs');
const path = require('path');

app.whenReady().then(() => {
  const srcJpg = '/home/birin/.gemini/antigravity/brain/de8bc470-109d-4a3b-bfc7-b41fbc65dbf2/byterescue_cyber_icon_1791624124340.jpg';
  
  if (!fs.existsSync(srcJpg)) {
    console.error('Kaynak resim bulunamadı:', srcJpg);
    app.exit(1);
    return;
  }

  const baseImg = nativeImage.createFromPath(srcJpg);
  fs.mkdirSync('build', { recursive: true });
  fs.mkdirSync('public', { recursive: true });

  // 1. Yüksek çözünürlüklü 512x512 ve 256x256 PNG üret
  const png512 = baseImg.resize({ width: 512, height: 512, quality: 'best' }).toPNG();
  const png256 = baseImg.resize({ width: 256, height: 256, quality: 'best' }).toPNG();
  const png128 = baseImg.resize({ width: 128, height: 128, quality: 'best' }).toPNG();
  const png64 = baseImg.resize({ width: 64, height: 64, quality: 'best' }).toPNG();
  const png48 = baseImg.resize({ width: 48, height: 48, quality: 'best' }).toPNG();
  const png32 = baseImg.resize({ width: 32, height: 32, quality: 'best' }).toPNG();
  const png16 = baseImg.resize({ width: 16, height: 16, quality: 'best' }).toPNG();

  fs.writeFileSync('build/icon.png', png512);
  fs.writeFileSync('public/icon.png', png512);
  fs.writeFileSync('src/assets/icon.png', png512);

  // 2. Çoklu Çözünürlüklü Windows ICO Dosyası Üret
  const images = [
    { size: 256, buf: png256 },
    { size: 128, buf: png128 },
    { size: 64, buf: png64 },
    { size: 48, buf: png48 },
    { size: 32, buf: png32 },
    { size: 16, buf: png16 }
  ];

  const headerSize = 6;
  const dirEntrySize = 16;
  const numImages = images.length;
  let offset = headerSize + numImages * dirEntrySize;

  const icoBuffer = Buffer.alloc(offset + images.reduce((acc, img) => acc + img.buf.length, 0));

  // ICONDIR
  icoBuffer.writeUInt16LE(0, 0); // reserved
  icoBuffer.writeUInt16LE(1, 2); // type 1 = icon
  icoBuffer.writeUInt16LE(numImages, 4); // count

  let entryPos = headerSize;
  for (const img of images) {
    const dim = img.size === 256 ? 0 : img.size;
    icoBuffer.writeUInt8(dim, entryPos); // width
    icoBuffer.writeUInt8(dim, entryPos + 1); // height
    icoBuffer.writeUInt8(0, entryPos + 2); // palette
    icoBuffer.writeUInt8(0, entryPos + 3); // reserved
    icoBuffer.writeUInt16LE(1, entryPos + 4); // planes
    icoBuffer.writeUInt16LE(32, entryPos + 6); // bpp
    icoBuffer.writeUInt32LE(img.buf.length, entryPos + 8); // size
    icoBuffer.writeUInt32LE(offset, entryPos + 12); // offset

    img.buf.copy(icoBuffer, offset);
    offset += img.buf.length;
    entryPos += dirEntrySize;
  }

  fs.writeFileSync('build/icon.ico', icoBuffer);
  fs.writeFileSync('public/favicon.ico', icoBuffer);

  console.log('✅ build/icon.png ve build/icon.ico başarıyla oluşturuldu!');
  console.log(`   PNG Boyutu: ${png512.length} bayt, ICO Boyutu: ${icoBuffer.length} bayt (${numImages} çözünürlük)`);

  app.exit(0);
});
