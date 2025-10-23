const User = require('../models/User');
const Message = require('../models/Message');
const Call = require('../models/Call');

// @desc    Get user statistics
// @route   GET /api/statistics/users
// @access  Private (Admin)
const getUserStatistics = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    // Build date filter
    let dateFilter = {};
    if (startDate && endDate) {
      dateFilter.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(endDate)
      };
    }

    // Total users
    const total = await User.countDocuments();
    
    // Active users
    const active = await User.countDocuments({ isActive: true });
    
    // New users in date range
    const newInRange = await User.countDocuments(dateFilter);
    
    // New users today
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const newToday = await User.countDocuments({
      createdAt: { $gte: today }
    });
    
    // New users this week
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const newThisWeek = await User.countDocuments({
      createdAt: { $gte: weekAgo }
    });
    
    // New users this month
    const monthAgo = new Date();
    monthAgo.setMonth(monthAgo.getMonth() - 1);
    const newThisMonth = await User.countDocuments({
      createdAt: { $gte: monthAgo }
    });
    
    // Users by role
    const byRole = await User.aggregate([
      {
        $group: {
          _id: '$role',
          count: { $sum: 1 }
        }
      }
    ]);
    
    const roleStats = {
      user: 0,
      moderator: 0,
      admin: 0
    };
    
    byRole.forEach(role => {
      roleStats[role._id] = role.count;
    });

    res.json({
      success: true,
      data: {
        total,
        active,
        newToday,
        newThisWeek,
        newThisMonth,
        newInRange,
        byRole: roleStats
      }
    });

  } catch (error) {
    console.error('Get user statistics error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching user statistics'
    });
  }
};

// @desc    Get message statistics
// @route   GET /api/statistics/messages
// @access  Private (Admin)
const getMessageStatistics = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    // Build date filter
    let dateFilter = {};
    if (startDate && endDate) {
      dateFilter.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(endDate)
      };
    }

    // Total messages
    const total = await Message.countDocuments();
    
    // Messages in date range
    const inRange = await Message.countDocuments(dateFilter);
    
    // Messages today
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayCount = await Message.countDocuments({
      createdAt: { $gte: today }
    });
    
    // Messages this week
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const thisWeek = await Message.countDocuments({
      createdAt: { $gte: weekAgo }
    });
    
    // Messages this month
    const monthAgo = new Date();
    monthAgo.setMonth(monthAgo.getMonth() - 1);
    const thisMonth = await Message.countDocuments({
      createdAt: { $gte: monthAgo }
    });
    
    // Messages by type
    const byType = await Message.aggregate([
      {
        $group: {
          _id: '$messageType',
          count: { $sum: 1 }
        }
      }
    ]);
    
    const typeStats = {
      text: 0,
      image: 0,
      file: 0,
      audio: 0,
      video: 0
    };
    
    byType.forEach(type => {
      if (type._id && typeStats.hasOwnProperty(type._id)) {
        typeStats[type._id] = type.count;
      } else {
        typeStats.text += type.count; // Default to text if unknown type
      }
    });
    
    // Hourly distribution for the last 24 hours
    const hourly = await Message.aggregate([
      {
        $match: {
          createdAt: { $gte: new Date(Date.now() - 24 * 60 * 60 * 1000) }
        }
      },
      {
        $group: {
          _id: { $hour: '$createdAt' },
          count: { $sum: 1 }
        }
      },
      { $sort: { _id: 1 } }
    ]);
    
    const hourlyStats = Array.from({ length: 24 }, (_, i) => ({
      hour: i,
      count: 0
    }));
    
    hourly.forEach(h => {
      hourlyStats[h._id].count = h.count;
    });

    res.json({
      success: true,
      data: {
        total,
        inRange,
        today: todayCount,
        thisWeek,
        thisMonth,
        byType: typeStats,
        hourly: hourlyStats
      }
    });

  } catch (error) {
    console.error('Get message statistics error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching message statistics'
    });
  }
};

// @desc    Get call statistics
// @route   GET /api/statistics/calls
// @access  Private (Admin)
const getCallStatistics = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    // Build date filter
    let dateFilter = {};
    if (startDate && endDate) {
      dateFilter.startedAt = {
        $gte: new Date(startDate),
        $lte: new Date(endDate)
      };
    }

    // Total calls
    const total = await Call.countDocuments();
    
    // Calls in date range
    const inRange = await Call.countDocuments(dateFilter);
    
    // Calls today
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayCount = await Call.countDocuments({
      startedAt: { $gte: today }
    });
    
    // Calls this week
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const thisWeek = await Call.countDocuments({
      startedAt: { $gte: weekAgo }
    });
    
    // Calls this month
    const monthAgo = new Date();
    monthAgo.setMonth(monthAgo.getMonth() - 1);
    const thisMonth = await Call.countDocuments({
      startedAt: { $gte: monthAgo }
    });
    
    // Calls by status
    const byStatus = await Call.aggregate([
      {
        $group: {
          _id: '$status',
          count: { $sum: 1 }
        }
      }
    ]);
    
    const statusStats = {
      completed: 0,
      failed: 0,
      canceled: 0
    };
    
    byStatus.forEach(status => {
      if (statusStats.hasOwnProperty(status._id)) {
        statusStats[status._id] = status.count;
      }
    });
    
    // Average duration
    const avgDurationResult = await Call.aggregate([
      { $match: { status: 'completed', duration: { $exists: true, $gt: 0 } } },
      {
        $group: {
          _id: null,
          avgDuration: { $avg: '$duration' }
        }
      }
    ]);
    
    const averageDuration = avgDurationResult.length > 0 
      ? Math.round(avgDurationResult[0].avgDuration / 60) // Convert to minutes
      : 0;
    
    // Success rate
    const successRate = total > 0 ? Math.round((statusStats.completed / total) * 100) : 0;

    res.json({
      success: true,
      data: {
        total,
        inRange,
        today: todayCount,
        thisWeek,
        thisMonth,
        byStatus: statusStats,
        averageDuration,
        successRate
      }
    });

  } catch (error) {
    console.error('Get call statistics error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching call statistics'
    });
  }
};

// @desc    Get top active users
// @route   GET /api/statistics/top-users
// @access  Private (Admin)
const getTopUsers = async (req, res) => {
  try {
    const { limit = 10 } = req.query;
    
    // Get users with message and call counts
    const topUsers = await User.aggregate([
      {
        $lookup: {
          from: 'messages',
          localField: '_id',
          foreignField: 'sender',
          as: 'messages'
        }
      },
      {
        $lookup: {
          from: 'calls',
          localField: '_id',
          foreignField: 'participants.userId',
          as: 'calls'
        }
      },
      {
        $project: {
          _id: 1,
          username: 1,
          lastSeen: 1,
          messageCount: { $size: '$messages' },
          callCount: { $size: '$calls' }
        }
      },
      {
        $sort: { messageCount: -1, callCount: -1 }
      },
      {
        $limit: parseInt(limit)
      }
    ]);

    res.json({
      success: true,
      data: topUsers
    });

  } catch (error) {
    console.error('Get top users error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching top users'
    });
  }
};

module.exports = {
  getUserStatistics,
  getMessageStatistics,
  getCallStatistics,
  getTopUsers
};
