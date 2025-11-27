const express = require('express');
const { body, param, query } = require('express-validator');
const {
  createPost,
  getUserPosts,
  getFeedPosts,
  getPostById,
  updatePost,
  deletePost,
  hidePost,
  toggleLike,
  addComment,
  editComment,
  deleteComment,
  addReactionToComment,
  removeReactionFromComment,
  sharePost
} = require('../controllers/postController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Validation rules
const createPostValidation = [
  body('content')
    .optional()
    .isLength({ max: 5000 })
    .withMessage('Post content cannot exceed 5000 characters'),
  body('images')
    .optional()
    .isArray()
    .withMessage('Images must be an array')
    .custom((images) => {
      if (images && images.length > 20) {
        throw new Error('Maximum 20 images allowed per post');
      }
      return true;
    }),
  body('images.*')
    .optional()
    .isString()
    .withMessage('Image must be a string (URL or ID)'),
  body('privacySetting')
    .optional()
    .isIn(['public', 'friends', 'only_me'])
    .withMessage('Privacy setting must be one of: public, friends, only_me'),
  body('location')
    .optional()
    .isString()
    .trim()
    .withMessage('Location must be a string'),
  body('tags')
    .optional()
    .isArray()
    .withMessage('Tags must be an array'),
  body('tags.*')
    .optional()
    .isMongoId()
    .withMessage('Each tag must be a valid user ID')
];

const updatePostValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format'),
  body('content')
    .optional()
    .isLength({ max: 5000 })
    .withMessage('Post content cannot exceed 5000 characters'),
  body('images')
    .optional()
    .isArray()
    .withMessage('Images must be an array')
    .custom((images) => {
      if (images && images.length > 20) {
        throw new Error('Maximum 20 images allowed per post');
      }
      return true;
    }),
  body('images.*')
    .optional()
    .isString()
    .withMessage('Image must be a string (URL or ID)'),
  body('privacySetting')
    .optional()
    .isIn(['public', 'friends', 'only_me'])
    .withMessage('Privacy setting must be one of: public, friends, only_me'),
  body('location')
    .optional()
    .isString()
    .trim()
    .withMessage('Location must be a string'),
  body('tags')
    .optional()
    .isArray()
    .withMessage('Tags must be an array'),
  body('tags.*')
    .optional()
    .isMongoId()
    .withMessage('Each tag must be a valid user ID')
];

const postIdValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format')
];

const userIdValidation = [
  param('userId')
    .isMongoId()
    .withMessage('Invalid user ID format')
];

const addCommentValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format'),
  body('content')
    .notEmpty()
    .withMessage('Comment content is required')
    .isLength({ max: 1000 })
    .withMessage('Comment cannot exceed 1000 characters')
    .trim(),
  body('parentCommentId')
    .optional()
    .isMongoId()
    .withMessage('Invalid parent comment ID format'),
  body('mediaUrl')
    .optional()
    .isString()
    .withMessage('Media URL must be a string')
];

const editCommentValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format'),
  param('commentId')
    .isMongoId()
    .withMessage('Invalid comment ID format'),
  body('content')
    .notEmpty()
    .withMessage('Comment content is required')
    .isLength({ max: 1000 })
    .withMessage('Comment cannot exceed 1000 characters')
    .trim()
];

const reactionValidation = [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format'),
  param('commentId')
    .isMongoId()
    .withMessage('Invalid comment ID format'),
  body('type')
    .isIn(['like', 'love', 'haha', 'wow', 'sad', 'angry'])
    .withMessage('Reaction type must be one of: like, love, haha, wow, sad, angry')
];

// Routes

// Create post
router.post('/', createPostValidation, createPost);

// Get feed posts (public and friends' posts)
router.get('/feed', [
  query('page')
    .optional()
    .isInt({ min: 1 })
    .withMessage('Page must be a positive integer'),
  query('limit')
    .optional()
    .isInt({ min: 1, max: 100 })
    .withMessage('Limit must be between 1 and 100')
], getFeedPosts);

// Get user's posts
router.get('/user/:userId', userIdValidation, [
  query('page')
    .optional()
    .isInt({ min: 1 })
    .withMessage('Page must be a positive integer'),
  query('limit')
    .optional()
    .isInt({ min: 1, max: 100 })
    .withMessage('Limit must be between 1 and 100')
], getUserPosts);

// Get post by ID (with comment pagination)
router.get('/:id', [
  postIdValidation,
  query('page')
    .optional()
    .isInt({ min: 1 })
    .withMessage('Page must be a positive integer'),
  query('limit')
    .optional()
    .isInt({ min: 1, max: 100 })
    .withMessage('Limit must be between 1 and 100'),
  query('sortBy')
    .optional()
    .isIn(['recent', 'relevant'])
    .withMessage('SortBy must be either "recent" or "relevant"')
], getPostById);

// Update post
router.put('/:id', updatePostValidation, updatePost);

// Delete post
router.delete('/:id', postIdValidation, deletePost);

// Hide post from user's feed
router.post('/:id/hide', postIdValidation, hidePost);

// Like/Unlike post
router.post('/:id/like', postIdValidation, toggleLike);

// Add comment
router.post('/:id/comments', addCommentValidation, addComment);

// Edit comment
router.put('/:id/comments/:commentId', editCommentValidation, editComment);

// Delete comment
router.delete('/:id/comments/:commentId', [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format'),
  param('commentId')
    .isMongoId()
    .withMessage('Invalid comment ID format')
], deleteComment);

// Add reaction to comment
router.post('/:id/comments/:commentId/reactions', reactionValidation, addReactionToComment);

// Remove reaction from comment
router.delete('/:id/comments/:commentId/reactions', [
  param('id')
    .isMongoId()
    .withMessage('Invalid post ID format'),
  param('commentId')
    .isMongoId()
    .withMessage('Invalid comment ID format')
], removeReactionFromComment);

// Share post
router.post('/:id/share', postIdValidation, sharePost);

module.exports = router;

