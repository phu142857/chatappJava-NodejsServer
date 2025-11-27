const Notification = require('../models/Notification');
const Post = require('../models/Post');
const User = require('../models/User');

// @desc    Get user notifications
// @route   GET /api/notifications
// @access  Private
const getNotifications = async (req, res) => {
  try {
    const userId = req.user.id;
    const { page = 1, limit = 50 } = req.query;
    
    const notifications = await Notification.getUserNotifications(userId, parseInt(page), parseInt(limit));
    
    // Format notifications for response
    const formattedNotifications = notifications.map(notification => {
      const notificationObj = notification.toObject();
      
      // Extract post preview image if available
      if (notificationObj.postId && notificationObj.postId.images && notificationObj.postId.images.length > 0) {
        notificationObj.postPreviewImage = notificationObj.postId.images[0];
      }
      
      return {
        _id: notificationObj._id,
        type: notificationObj.type,
        actor: notificationObj.actor,
        postId: notificationObj.postId ? notificationObj.postId._id : null,
        commentId: notificationObj.commentId,
        postPreviewImage: notificationObj.postPreviewImage,
        isRead: notificationObj.isRead,
        createdAt: notificationObj.createdAt
      };
    });
    
    res.json({
      success: true,
      data: {
        notifications: formattedNotifications
      }
    });
  } catch (error) {
    console.error('Get notifications error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Mark notification as read
// @route   PUT /api/notifications/:id/read
// @access  Private
const markNotificationAsRead = async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.id;
    
    const notification = await Notification.markAsRead(id, userId);
    
    if (!notification) {
      return res.status(404).json({
        success: false,
        message: 'Notification not found'
      });
    }
    
    res.json({
      success: true,
      data: {
        notification: notification
      }
    });
  } catch (error) {
    console.error('Mark notification as read error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Mark all notifications as read
// @route   PUT /api/notifications/read-all
// @access  Private
const markAllNotificationsAsRead = async (req, res) => {
  try {
    const userId = req.user.id;
    
    await Notification.markAllAsRead(userId);
    
    res.json({
      success: true,
      message: 'All notifications marked as read'
    });
  } catch (error) {
    console.error('Mark all notifications as read error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

module.exports = {
  getNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead
};

