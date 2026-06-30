const express = require('express');
const { body, param } = require('express-validator');
const {
  getChats,
  getAllChats,
  getChatById,
  createPrivateChat,
  createGroupChat,
  updateGroupChat,
  updateGroupChatSettings,
  addParticipant,
  removeParticipant,
  leaveChat,
  deleteChat,
  addMember,
  getGroupMembers,
  removeMember,
  updateMemberRole,
  uploadGroupAvatar,
  deleteChatAdmin,
  transferOwnership
} = require('../controllers/chatController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const chatIdValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid chat ID format')
];

const createPrivateChatValidation = [
  body('participantId')
    .isMongoId()
    .withMessage('Invalid participant ID format')
    .notEmpty()
    .withMessage('Participant ID is required')
];

const createGroupChatValidation = [
  body('name')
    .notEmpty()
    .withMessage('Group name is required')
    .isLength({ min: 1, max: 100 })
    .withMessage('Group name must be between 1 and 100 characters'),
  body('description')
    .optional()
    .isLength({ max: 500 })
    .withMessage('Description cannot exceed 500 characters'),
  body('participantIds')
    .isArray({ min: 1 })
    .withMessage('At least one participant is required'),
  body('participantIds.*')
    .isMongoId()
    .withMessage('Invalid participant ID format'),
  body('avatar')
    .optional()
    .isURL()
    .withMessage('Avatar must be a valid URL')
];

const updateGroupChatValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid chat ID format'),
  body('name')
    .optional()
    .isLength({ min: 1, max: 100 })
    .withMessage('Group name must be between 1 and 100 characters'),
  body('description')
    .optional()
    .isLength({ max: 500 })
    .withMessage('Description cannot exceed 500 characters'),
  body('avatar')
    .optional()
    .isURL()
    .withMessage('Avatar must be a valid URL')
];

const updateGroupChatSettingsValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid chat ID format'),
  body('settings')
    .isObject()
    .withMessage('Settings must be an object'),
  body('settings.isPublic')
    .optional()
    .isBoolean()
    .withMessage('isPublic must be a boolean')
];

const addParticipantValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid chat ID format'),
  body('participantId')
    .isMongoId()
    .withMessage('Invalid participant ID format')
    .notEmpty()
    .withMessage('Participant ID is required')
];

const removeParticipantValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid chat ID format'),
  param('participantId')
    .isMongoId()
    .withMessage('Invalid participant ID format')
];

// Routes

// Get user's chats
router.get('/', getChats);

// Get all chats (admin only)
router.get('/admin', getAllChats);

// Delete chat and all messages (admin only)
router.delete('/:id/admin', chatIdValidation, deleteChatAdmin);

// Create chats
router.post('/private', createPrivateChatValidation, createPrivateChat);
router.post('/group', createGroupChatValidation, createGroupChat);

// Chat operations
router.get('/:id', chatIdValidation, getChatById);
router.put('/:id', updateGroupChatValidation, updateGroupChat);
router.put('/:id/settings', updateGroupChatSettingsValidation, updateGroupChatSettings);
router.delete('/:id', chatIdValidation, deleteChat);

// Participant management
router.post('/:id/participants', addParticipantValidation, addParticipant);
router.delete('/:id/participants/:participantId', removeParticipantValidation, removeParticipant);

// Leave chat
router.post('/:id/leave', chatIdValidation, leaveChat);

// Member management
router.get('/:id/members', chatIdValidation, getGroupMembers);
router.post('/:id/members', chatIdValidation, addMember);
router.put('/:id/members/role', chatIdValidation, updateMemberRole);
router.delete('/:id/members', chatIdValidation, removeMember);
router.put('/:id/owner', chatIdValidation, transferOwnership);

// Group avatar upload
const multer = require('multer');
const path = require('path');

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = path.join(__dirname, '../uploads/avatars');
    // Create directory if it doesn't exist
    const fs = require('fs');
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'group-avatar-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB limit
  },
  fileFilter: (req, file, cb) => {
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Only image files are allowed'), false);
    }
  }
});

router.post('/:id/avatar', chatIdValidation, upload.single('avatar'), uploadGroupAvatar);

module.exports = router;
