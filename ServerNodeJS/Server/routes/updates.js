const express = require('express');
const { query } = require('express-validator');
const {
  getMessagesUpdates,
  getPostsUpdates,
  getConversationsUpdates
} = require('../controllers/updateController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const sinceValidation = [
  query('since')
    .optional()
    .isInt({ min: 0 })
    .withMessage('Since timestamp must be a non-negative integer')
];

// GET /api/updates/messages?since=timestamp
// Returns only messages updated/created after the timestamp
router.get('/messages', sinceValidation, getMessagesUpdates);

// GET /api/updates/posts?since=timestamp
// Returns only posts updated/created after the timestamp
router.get('/posts', sinceValidation, getPostsUpdates);

// GET /api/updates/conversations?since=timestamp
// Returns only conversations updated after the timestamp
router.get('/conversations', sinceValidation, getConversationsUpdates);

module.exports = router;

