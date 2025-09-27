const express = require('express');
const { body, query, param } = require('express-validator');
const {
  getUsers,
  getUserById,
  updateStatus,
  getContacts,
  searchUsers,
  getOnlineUsers,
  toggleBlockUser,
  reportUser,
  getUserFriends,
  removeFriend,
  setActive
} = require('../controllers/userController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');
const { createUser, updateUser: adminUpdateUser, deleteUser: adminDeleteUser, setRole, resetPassword } = require('../controllers/adminUserController');
const { getUserStats } = require('../controllers/serverController');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const updateStatusValidation = [
  body('status')
    .isIn(['online', 'offline', 'away'])
    .withMessage('Status must be one of: online, offline, away')
];

const searchValidation = [
  query('q')
    .isLength({ min: 2 })
    .withMessage('Search query must be at least 2 characters')
];

const userIdValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid user ID format')
];

const blockUserValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid user ID format'),
  body('action')
    .isIn(['block', 'unblock'])
    .withMessage('Action must be either block or unblock')
];

const reportUserValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid user ID format'),
  body('reason')
    .notEmpty()
    .withMessage('Reason is required')
    .isIn(['spam', 'harassment', 'inappropriate_content', 'fake_account', 'other'])
    .withMessage('Invalid report reason'),
  body('description')
    .optional()
    .isLength({ max: 500 })
    .withMessage('Description cannot exceed 500 characters')
];

// Routes
router.get('/', getUsers);
router.get('/contacts', getContacts);
router.get('/friends', getUserFriends);
router.get('/online', getOnlineUsers);
router.get('/search', searchValidation, searchUsers);

// Admin stats route
router.get('/stats', adminOnly, getUserStats);

router.put('/status', updateStatusValidation, updateStatus);

// Admin-only CRUD & role/password management (placed before param routes to avoid ambiguity)
router.post('/', adminOnly, [
  body('username').isLength({ min: 3, max: 30 }),
  body('email').isEmail(),
  body('password').isLength({ min: 6 }),
  body('role').optional().isIn(['user', 'admin', 'moderator'])
], createUser);

router.put('/:id', userIdValidation, adminOnly, adminUpdateUser);
router.delete('/:id', userIdValidation, adminOnly, adminDeleteUser);
router.put('/:id/role', userIdValidation, adminOnly, [
  body('role').isIn(['user', 'admin', 'moderator'])
], setRole);
router.put('/:id/reset-password', userIdValidation, adminOnly, [
  body('newPassword').isLength({ min: 6 })
], resetPassword);

router.get('/:id', userIdValidation, getUserById);
router.put('/:id/block', blockUserValidation, toggleBlockUser);
router.post('/:id/report', reportUserValidation, reportUser);
router.delete('/:id/friends', userIdValidation, removeFriend);
// Admin-only: activate/deactivate user
router.put('/:id/active', userIdValidation, adminOnly, setActive);

module.exports = router;
