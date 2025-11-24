const express = require('express');
const router = express.Router();
const { authMiddleware } = require('../middleware/authMiddleware');
const {
    initiateGroupCall,
    joinGroupCall,
    updateParticipantMedia,
    leaveGroupCall,
    getGroupCallDetails,
    getActiveGroupCall
} = require('../controllers/groupCallController');

// @route   POST /api/group-calls
// @desc    Initiate a new group call (Discord-style, no ringing)
// @access  Private
router.post('/', authMiddleware, initiateGroupCall);

// @route   POST /api/group-calls/:callId/join
// @desc    Join an existing group call
// @access  Private
router.post('/:callId/join', authMiddleware, joinGroupCall);

// @route   PATCH /api/group-calls/:callId/media
// @desc    Update participant media state (mute/unmute, video on/off, screen share)
// @access  Private
router.patch('/:callId/media', authMiddleware, updateParticipantMedia);

// @route   POST /api/group-calls/:callId/leave
// @desc    Leave a group call
// @access  Private
router.post('/:callId/leave', authMiddleware, leaveGroupCall);

// @route   GET /api/group-calls/:callId
// @desc    Get group call details
// @access  Private
router.get('/:callId', authMiddleware, getGroupCallDetails);

// @route   GET /api/group-calls/chat/:chatId/active
// @desc    Get active group call for a chat
// @access  Private
router.get('/chat/:chatId/active', authMiddleware, getActiveGroupCall);

module.exports = router;

