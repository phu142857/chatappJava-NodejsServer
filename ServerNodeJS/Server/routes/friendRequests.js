const express = require('express');
const { body, param } = require('express-validator');
const {
  sendFriendRequest,
  getFriendRequests,
  respondToFriendRequest,
  cancelFriendRequest
} = require('../controllers/friendRequestController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const sendFriendRequestValidation = [
  body('receiverId')
    .isMongoId()
    .withMessage('Invalid receiver ID format')
];

const respondToFriendRequestValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid friend request ID format'),
  body('action')
    .isIn(['accept', 'reject'])
    .withMessage('Action must be either accept or reject')
];

const cancelFriendRequestValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid friend request ID format')
];

// Routes
router.post('/', sendFriendRequestValidation, sendFriendRequest);
router.get('/', getFriendRequests);
router.put('/:id', respondToFriendRequestValidation, respondToFriendRequest);
router.delete('/:id', cancelFriendRequestValidation, cancelFriendRequest);

module.exports = router;
