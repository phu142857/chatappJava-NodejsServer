const express = require('express');
const { getServerHealth, getUserStats, getMessageStats, getCallStats } = require('../controllers/serverController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Apply admin middleware to all routes
router.use(adminOnly);

// Routes
router.get('/health', getServerHealth);
router.get('/users/stats', getUserStats);
router.get('/messages/stats', getMessageStats);
router.get('/calls/stats', getCallStats);

module.exports = router;
