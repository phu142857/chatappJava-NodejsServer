const express = require('express');
const { body, param, query } = require('express-validator');
const {
  getMessages,
  getAllMessages,
  sendMessage,
  editMessage,
  deleteMessage,
  addReaction,
  removeReaction,
  markAsRead,
  searchMessages
} = require('../controllers/messageController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');
const { getMessageStats } = require('../controllers/serverController');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const chatIdValidation = [
  param('chatId')
    .isMongoId()
    .withMessage('Invalid chat ID format')
];

const messageIdValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid message ID format')
];

const sendMessageValidation = [
  body('chatId')
    .isMongoId()
    .withMessage('Invalid chat ID format')
    .notEmpty()
    .withMessage('Chat ID is required'),
  body('content')
    .optional()
    .isLength({ max: 5000 })
    .withMessage('Message content cannot exceed 5000 characters'),
  body('type')
    .optional()
    .isIn(['text', 'image', 'file', 'video', 'audio'])
    .withMessage('Invalid message type'),
  body('replyTo')
    .optional()
    .isMongoId()
    .withMessage('Invalid reply message ID format'),
  body('attachments')
    .optional()
    .isArray()
    .withMessage('Attachments must be an array'),
  body('attachments.*.filename')
    .optional()
    .notEmpty()
    .withMessage('Attachment filename is required'),
  body('attachments.*.originalName')
    .optional()
    .notEmpty()
    .withMessage('Attachment original name is required'),
  body('attachments.*.mimeType')
    .optional()
    .notEmpty()
    .withMessage('Attachment mime type is required'),
  body('attachments.*.size')
    .optional()
    .isNumeric()
    .withMessage('Attachment size must be a number'),
  body('attachments.*.url')
    .optional()
    .isURL()
    .withMessage('Attachment URL must be valid')
];

const editMessageValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid message ID format'),
  body('content')
    .notEmpty()
    .withMessage('Message content is required')
    .isLength({ max: 5000 })
    .withMessage('Message content cannot exceed 5000 characters')
];

const reactionValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid message ID format'),
  body('emoji')
    .notEmpty()
    .withMessage('Emoji is required')
    .isLength({ max: 10 })
    .withMessage('Emoji cannot exceed 10 characters')
];

const markAsReadValidation = [
  param('chatId')
    .isMongoId()
    .withMessage('Invalid chat ID format'),
  body('messageIds')
    .optional()
    .isArray()
    .withMessage('Message IDs must be an array'),
  body('messageIds.*')
    .optional()
    .isMongoId()
    .withMessage('Invalid message ID format')
];

const searchValidation = [
  param('chatId')
    .isMongoId()
    .withMessage('Invalid chat ID format'),
  query('q')
    .isLength({ min: 2 })
    .withMessage('Search query must be at least 2 characters')
];

// Routes

// Get all messages (admin only)
router.get('/admin', getAllMessages);

// Get message statistics (admin only)
router.get('/stats', adminOnly, getMessageStats);

// Get messages for a chat
router.get('/:chatId', chatIdValidation, getMessages);

// Search messages in a chat
router.get('/:chatId/search', searchValidation, searchMessages);

// Send message
router.post('/', sendMessageValidation, sendMessage);

// Mark messages as read
router.put('/:chatId/read', markAsReadValidation, markAsRead);

// Message operations
router.put('/:id', editMessageValidation, editMessage);
router.delete('/:id', messageIdValidation, deleteMessage);

// Reactions
router.post('/:id/reactions', reactionValidation, addReaction);
router.delete('/:id/reactions', reactionValidation, removeReaction);

module.exports = router;
