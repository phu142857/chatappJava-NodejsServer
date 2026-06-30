const express = require('express');
const { getUserStatistics, getMessageStatistics, getCallStatistics, getTopUsers } = require('../controllers/statisticsController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Apply admin middleware to all routes
router.use(adminOnly);

// Routes
router.get('/users', getUserStatistics);
router.get('/messages', getMessageStatistics);
router.get('/calls', getCallStatistics);
router.get('/top-users', getTopUsers);

module.exports = router;
