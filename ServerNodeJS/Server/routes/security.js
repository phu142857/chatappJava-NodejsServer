const express = require('express');
const {
  getBlockedIPs,
  getAuditLogs,
  blockIP,
  unblockIP
} = require('../controllers/securityController');
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');

const router = express.Router();

// Apply auth middleware to all routes
router.use(authMiddleware);

// Apply admin middleware to all routes
router.use(adminOnly);

// Security routes
router.get('/blocked-ips', getBlockedIPs);
router.get('/audit-logs', getAuditLogs);

router.post('/block-ip', blockIP);

router.delete('/blocked-ips/:ipId', unblockIP);

module.exports = router;
