const express = require('express');
const { body, query, param } = require('express-validator');
const {
  getGroups,
  getGroupById,
  updateStatus,
  getContacts,
  searchGroups,
  getPublicGroups,
  createGroup,
  updateGroup,
  joinGroup,
  leaveGroup,
  addMember,
  removeMember,
  deleteGroup,
  getGroupMembers
} = require('../controllers/groupController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const updateStatusValidation = [
  body('status')
    .isIn(['active', 'inactive', 'archived'])
    .withMessage('Status must be one of: active, inactive, archived'),
  body('groupId')
    .isMongoId()
    .withMessage('Invalid group ID format')
];

const searchValidation = [
  query('q')
    .isLength({ min: 2 })
    .withMessage('Search query must be at least 2 characters')
];

const groupIdValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid group ID format')
];

const memberIdValidation = [
  param('memberId')
    .isMongoId()
    .withMessage('Invalid member ID format')
];

const createGroupValidation = [
  body('name')
    .notEmpty()
    .withMessage('Group name is required')
    .isLength({ min: 3, max: 50 })
    .withMessage('Group name must be between 3 and 50 characters'),
  body('description')
    .optional()
    .isLength({ max: 500 })
    .withMessage('Description cannot exceed 500 characters'),
  body('memberIds')
    .optional()
    .isArray()
    .withMessage('Member IDs must be an array'),
  body('memberIds.*')
    .optional()
    .isMongoId()
    .withMessage('Invalid member ID format'),
  body('avatar')
    .optional()
    .isString()
    .withMessage('Avatar must be a string'),
  body('settings')
    .optional()
    .isObject()
    .withMessage('Settings must be an object')
];

const updateGroupValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid group ID format'),
  body('name')
    .optional()
    .isLength({ min: 3, max: 50 })
    .withMessage('Group name must be between 3 and 50 characters'),
  body('description')
    .optional()
    .isLength({ max: 500 })
    .withMessage('Description cannot exceed 500 characters'),
  body('avatar')
    .optional()
    .isString()
    .withMessage('Avatar must be a string'),
  body('settings')
    .optional()
    .isObject()
    .withMessage('Settings must be an object')
];

const addMemberValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid group ID format'),
  body('userId')
    .isMongoId()
    .withMessage('Invalid user ID format')
    .notEmpty()
    .withMessage('User ID is required'),
  body('role')
    .optional()
    .isIn(['admin', 'moderator', 'member'])
    .withMessage('Role must be one of: admin, moderator, member')
];

const removeMemberValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid group ID format'),
  param('memberId')
    .isMongoId()
    .withMessage('Invalid member ID format')
];

// Routes

// Get groups
router.get('/', getGroups);
router.get('/contacts', getContacts);
router.get('/public', getPublicGroups);
router.get('/search', searchValidation, searchGroups);

// Create group (admin)
router.post('/', adminOnly, createGroupValidation, createGroup);

// Update group status
router.put('/status', updateStatusValidation, updateStatus);

// Group operations
router.get('/:id', groupIdValidation, getGroupById);
router.put('/:id', updateGroupValidation, updateGroup);
router.delete('/:id', groupIdValidation, deleteGroup);

// Group membership
router.post('/:id/join', groupIdValidation, joinGroup);
router.post('/:id/leave', groupIdValidation, leaveGroup);

// Member management
router.get('/:id/members', groupIdValidation, getGroupMembers);
router.post('/:id/members', addMemberValidation, addMember);
router.delete('/:id/members/:memberId', removeMemberValidation, removeMember);

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

// Upload group avatar
router.post('/:id/avatar', groupIdValidation, upload.single('avatar'), async (req, res) => {
  try {
    const { id } = req.params;
    const Group = require('../models/Group');

    if (!req.file) {
      return res.status(400).json({
        success: false,
        message: 'No image file provided'
      });
    }

    const group = await Group.findById(id);
    if (!group || !group.isActive) {
      return res.status(404).json({
        success: false,
        message: 'Group not found'
      });
    }

    // Check if user has permission to upload avatar
    if (!group.hasPermission(req.user.id)) {
      return res.status(403).json({
        success: false,
        message: 'Only group admins/moderators can upload avatar'
      });
    }

    // Delete old avatar if exists
    if (group.avatar) {
      const fs = require('fs');
      const path = require('path');
      const oldAvatarPath = path.join(__dirname, '../uploads/avatars', path.basename(group.avatar));
      if (fs.existsSync(oldAvatarPath)) {
        fs.unlinkSync(oldAvatarPath);
      }
    }

    // Update group avatar
    group.avatar = `/uploads/avatars/${req.file.filename}`;
    await group.updateLastActivity();

    res.json({
      success: true,
      message: 'Group avatar uploaded successfully',
      data: {
        avatarUrl: group.avatar
      }
    });

  } catch (error) {
    console.error('Upload group avatar error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

module.exports = router;
