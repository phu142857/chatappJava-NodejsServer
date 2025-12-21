const express = require('express');
const { body, query, param } = require('express-validator');
const {
  getUsers,
  getUserById,
  getContacts,
  getBlockedUsers,
  searchUsers,
  toggleBlockUser,
  getUserFriends,
  getFriendsByUserId,
  removeFriend,
  setActive,
  updateRole,
  adminAddFriendship,
  adminRemoveFriendship,
  registerFCMToken,
  removeFCMToken
} = require('../controllers/userController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');
const { createUser, updateUser: adminUpdateUser, deleteUser: adminDeleteUser, setRole, resetPassword } = require('../controllers/adminUserController');
const { getUserStats } = require('../controllers/serverController');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
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


// Routes
router.get('/', getUsers);
router.get('/contacts', getContacts);
router.get('/blocked', getBlockedUsers);
router.get('/me/blocked', getBlockedUsers);
router.get('/friends', getUserFriends);
router.get('/search', searchValidation, searchUsers);

// FCM token management
router.post('/me/fcm-token', [
  body('token').notEmpty().withMessage('FCM token is required')
], registerFCMToken);
router.delete('/me/fcm-token', [
  body('token').notEmpty().withMessage('FCM token is required')
], removeFCMToken);

// User can change their own role (with restrictions)
router.put('/me/role', [
  body('role').isIn(['user', 'admin', 'moderator']).withMessage('Invalid role')
], updateRole);

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
router.get('/:id/friends', userIdValidation, getFriendsByUserId);
router.put('/:id/block', blockUserValidation, toggleBlockUser);
router.delete('/:id/friends', userIdValidation, removeFriend);
// Admin-only: add/remove friendship by UUIDs
router.post('/admin/friendship', [
  body('userId1').isMongoId().withMessage('Invalid userId1'),
  body('userId2').isMongoId().withMessage('Invalid userId2')
], adminOnly, adminAddFriendship);
router.delete('/admin/friendship', [
  body('userId1').isMongoId().withMessage('Invalid userId1'),
  body('userId2').isMongoId().withMessage('Invalid userId2')
], adminOnly, adminRemoveFriendship);
// Admin-only: activate/deactivate user
router.put('/:id/active', userIdValidation, adminOnly, setActive);

module.exports = router;
