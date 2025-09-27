const express = require('express');
const { uploadChatImage } = require('../controllers/uploadController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Upload chat image
router.post('/chat/:chatId', authMiddleware, uploadChatImage);

module.exports = router;
