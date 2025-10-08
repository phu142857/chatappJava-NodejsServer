const os = require('os');
const fs = require('fs');
const path = require('path');
const User = require('../models/User');
const Message = require('../models/Message');
const Call = require('../models/Call');

// @desc    Get server health and metrics
// @route   GET /api/server/health
// @access  Private (Admin)
const getServerHealth = async (req, res) => {
  try {
    // Get CPU usage (simplified)
    const cpus = os.cpus();
    const cpuUsage = process.cpuUsage();
    const cpuPercent = Math.round((cpuUsage.user + cpuUsage.system) / 1000000); // Convert to percentage

    // Get memory usage
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const usedMem = totalMem - freeMem;
    const memoryPercent = Math.round((usedMem / totalMem) * 100);

    // Get disk usage (simplified)
    const diskUsage = await getDiskUsage();
    const diskPercent = Math.round((diskUsage.used / diskUsage.total) * 100);

    // Get uptime
    const uptime = process.uptime();
    const uptimeString = formatUptime(uptime);

    // Check services status
    const services = {
      auth: true, // Assume auth service is running
      chat: true, // Assume chat service is running
      videoCall: true, // Assume video call service is running
      websocket: true // Assume websocket is running
    };

    res.json({
      success: true,
      data: {
        cpu: Math.min(cpuPercent, 100),
        memory: memoryPercent,
        disk: diskPercent,
        uptime: uptimeString,
        services
      }
    });

  } catch (error) {
    console.error('Get server health error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching health data'
    });
  }
};

// @desc    Get user statistics
// @route   GET /api/users/stats
// @access  Private (Admin)
const getUserStats = async (req, res) => {
  try {
    
    const total = await User.countDocuments();
    const active = await User.countDocuments({ isActive: true });
    
    // Online users (last seen within 5 minutes)
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
    const online = await User.countDocuments({ 
      lastSeen: { $gte: fiveMinutesAgo },
      isActive: true 
    });

    res.json({
      success: true,
      data: {
        total,
        active,
        online
      }
    });

  } catch (error) {
    console.error('Get user stats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching user statistics'
    });
  }
};

// @desc    Get message statistics
// @route   GET /api/messages/stats
// @access  Private (Admin)
const getMessageStats = async (req, res) => {
  try {
    
    const total = await Message.countDocuments();
    
    // Today's messages
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayCount = await Message.countDocuments({
      createdAt: { $gte: today }
    });
    
    // This week's messages
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const thisWeek = await Message.countDocuments({
      createdAt: { $gte: weekAgo }
    });

    res.json({
      success: true,
      data: {
        total,
        today: todayCount,
        thisWeek
      }
    });

  } catch (error) {
    console.error('Get message stats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching message statistics'
    });
  }
};

// @desc    Get call statistics
// @route   GET /api/calls/stats
// @access  Private (Admin)
const getCallStats = async (req, res) => {
  try {
    
    const total = await Call.countDocuments();
    
    // Today's calls
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayCount = await Call.countDocuments({
      startedAt: { $gte: today }
    });
    
    // Successful calls
    const successful = await Call.countDocuments({ status: 'completed' });
    
    // Failed calls
    const failed = await Call.countDocuments({ status: 'failed' });

    res.json({
      success: true,
      data: {
        total,
        today: todayCount,
        successful,
        failed
      }
    });

  } catch (error) {
    console.error('Get call stats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching call statistics'
    });
  }
};

// Helper function to get disk usage
const getDiskUsage = async () => {
  try {
    const stats = fs.statSync(process.cwd());
    // This is a simplified version - in production you'd use a proper disk usage library
    return {
      used: 1024 * 1024 * 1024, // 1GB (placeholder)
      total: 10 * 1024 * 1024 * 1024 // 10GB (placeholder)
    };
  } catch (error) {
    return {
      used: 0,
      total: 1
    };
  }
};

// Helper function to format uptime
const formatUptime = (seconds) => {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  
  return `${days}d ${hours}h ${minutes}m`;
};

module.exports = {
  getServerHealth,
  getUserStats,
  getMessageStats,
  getCallStats
};
