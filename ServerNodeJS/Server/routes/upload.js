const express = require('express');
const { uploadChatImage, uploadChatFile, uploadPostImage, uploadCommentImage, downloadFile, getFilePreview } = require('../controllers/uploadController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Upload chat image
router.post('/chat/:chatId/image', authMiddleware, uploadChatImage);

// Upload chat file (PDF, TXT, etc.)
router.post('/chat/:chatId/file', authMiddleware, uploadChatFile);

// Upload post image
router.post('/posts/image', authMiddleware, uploadPostImage);

// Upload comment media (image/GIF)
router.post('/comments/image', authMiddleware, uploadCommentImage);

// Download file
router.get('/download/:chatId/:fileName', authMiddleware, downloadFile);

// Get file preview
router.get('/preview/:chatId/:fileName', authMiddleware, getFilePreview);

module.exports = router;
