// server.js
const express = require('express');
const bodyParser = require('body-parser');
const puppeteer = require('puppeteer');
const admin = require('firebase-admin');
const path = require('path');

// Подключаем ключ доступа Firebase (файл serviceAccountKey.json должен лежать в этой же папке)
const serviceAccount = require(path.join(__dirname, 'serviceAccountKey.json'));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  // Добавляем имя бакета вручную, так как его нет в serviceAccountKey.json
  storageBucket: 'noten-9cb9f.appspot.com'
});

const app = express();
app.use(bodyParser.json({ limit: '10mb' })); // Если HTML большой, можно увеличить лимит

// Эндпоинт для конвертации HTML в PDF
app.post('/generate-pdf', async (req, res) => {
  const { html } = req.body;
  if (!html) {
    return res.status(400).json({ error: 'HTML-контент не передан' });
  }
  try {
    // Запускаем Puppeteer для рендеринга HTML в PDF
    const browser = await puppeteer.launch({
      args: ['--no-sandbox', '--disable-setuid-sandbox'] // Необходимые параметры для работы на сервере
    });
    const page = await browser.newPage();
    await page.setContent(html, { waitUntil: 'networkidle0' });
    const pdfBuffer = await page.pdf({
      format: 'A4',
      printBackground: true
    });
    await browser.close();

    // Сохраняем PDF в Firebase Storage
    const bucket = admin.storage().bucket();
    const fileName = `pdf_files/${Date.now()}.pdf`;
    const file = bucket.file(fileName);
    await file.save(pdfBuffer, {
      metadata: { contentType: 'application/pdf' }
    });
    // Делаем файл публичным (или можно настроить генерацию временной ссылки)
    await file.makePublic();
    const publicUrl = file.publicUrl();

    // Отправляем URL PDF в ответе
    return res.status(200).json({ url: publicUrl });
  } catch (error) {
    console.error('Ошибка при генерации PDF:', error);
    return res.status(500).json({ error: error.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Сервер запущен на порту ${PORT}`);
});
