const express = require('express');
const router = express.Router();
const callController = require('../controllers/callController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');
const { getCallStats } = require('../controllers/serverController');

// Apply authentication middleware to all routes
router.use(authMiddleware);

// Call management routes
router.post('/initiate', callController.initiateCall);
router.post('/:callId/join', callController.joinCall);
router.post('/:callId/decline', callController.declineCall);
router.post('/:callId/leave', callController.leaveCall);
router.post('/:callId/end', callController.endCall);

// Call information routes
router.get('/admin', callController.getAllCalls);
router.get('/stats', adminOnly, getCallStats);
router.get('/history', callController.getCallHistory);
router.get('/active', callController.getActiveCalls);
router.get('/:callId', callController.getCallDetails);

// Call settings routes
router.put('/:callId/settings', callController.updateCallSettings);

module.exports = router;
