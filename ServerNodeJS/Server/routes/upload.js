const express = require('express');
const { uploadChatImage, uploadChatFile, downloadFile, getFilePreview } = require('../controllers/uploadController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Upload chat image
router.post('/chat/:chatId/image', authMiddleware, uploadChatImage);

// Upload chat file (PDF, TXT, etc.)
router.post('/chat/:chatId/file', authMiddleware, uploadChatFile);

// Download file
router.get('/download/:chatId/:fileName', authMiddleware, downloadFile);

// Get file preview
router.get('/preview/:chatId/:fileName', authMiddleware, getFilePreview);

module.exports = router;
