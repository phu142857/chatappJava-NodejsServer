const express = require('express');
const router = express.Router();
const { authMiddleware } = require('../middleware/authMiddleware');
const { getActiveGroupCall } = require('../controllers/groupCallController');

// @route   GET /api/group-calls/chat/:chatId/active
// @desc    Get active group call for a chat
// @access  Private
router.get('/chat/:chatId/active', authMiddleware, getActiveGroupCall);

module.exports = router;
