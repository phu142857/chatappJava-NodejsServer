const BlockedIP = require('../models/BlockedIP');
const AuditLog = require('../models/AuditLog');
const User = require('../models/User');

// Get all blocked IPs
const getBlockedIPs = async (req, res) => {
  try {
    const blockedIPs = await BlockedIP.find()
      .populate('blockedBy', 'username')
      .sort({ blockedAt: -1 });
    
    res.json({
      success: true,
      data: blockedIPs
    });
  } catch (error) {
    console.error('Error fetching blocked IPs:', error);
    res.status(500).json({
      success: false,
      message: 'Failed to fetch blocked IPs'
    });
  }
};

// Get all audit logs
const getAuditLogs = async (req, res) => {
  try {
    const auditLogs = await AuditLog.find()
      .populate('user', 'username')
      .sort({ timestamp: -1 })
      .limit(1000); // Limit to last 1000 logs
    
    res.json({
      success: true,
      data: auditLogs
    });
  } catch (error) {
    console.error('Error fetching audit logs:', error);
    res.status(500).json({
      success: false,
      message: 'Failed to fetch audit logs'
    });
  }
};

// Block an IP address
const blockIP = async (req, res) => {
  try {
    const { ip, reason } = req.body;
    const adminId = req.user.id;

    // Check if IP is already blocked
    const existingBlock = await BlockedIP.findOne({ ip });
    if (existingBlock) {
      return res.status(400).json({
        success: false,
        message: 'IP address is already blocked'
      });
    }

    // Create blocked IP record
    const blockedIP = new BlockedIP({
      ip,
      reason,
      blockedBy: adminId
    });

    await blockedIP.save();

    // Log the action
    await logAuditAction(adminId, 'BLOCK_IP', 'IP', `Blocked IP address ${ip}`, req.ip);

    res.json({
      success: true,
      message: 'IP address blocked successfully',
      data: blockedIP
    });
  } catch (error) {
    console.error('Error blocking IP:', error);
    res.status(500).json({
      success: false,
      message: 'Failed to block IP address'
    });
  }
};

// Unblock an IP address
const unblockIP = async (req, res) => {
  try {
    const { ipId } = req.params;
    const adminId = req.user.id;

    const blockedIP = await BlockedIP.findById(ipId);
    if (!blockedIP) {
      return res.status(404).json({
        success: false,
        message: 'Blocked IP record not found'
      });
    }

    // Remove from blocked IPs
    await BlockedIP.findByIdAndDelete(ipId);

    // Log the action
    await logAuditAction(adminId, 'UNBLOCK_IP', 'IP', `Unblocked IP address ${blockedIP.ip}`, req.ip);

    res.json({
      success: true,
      message: 'IP address unblocked successfully'
    });
  } catch (error) {
    console.error('Error unblocking IP:', error);
    res.status(500).json({
      success: false,
      message: 'Failed to unblock IP address'
    });
  }
};

// Helper function to log audit actions
const logAuditAction = async (userId, action, resource, details, ipAddress) => {
  try {
    const auditLog = new AuditLog({
      user: userId,
      action,
      resource,
      details,
      ipAddress
    });
    await auditLog.save();
  } catch (error) {
    console.error('Error logging audit action:', error);
  }
};

module.exports = {
  getBlockedIPs,
  getAuditLogs,
  blockIP,
  unblockIP
};
