const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true
  },
  type: {
    type: String,
    enum: ['tagged_in_post', 'tagged_in_comment', 'friend_posted', 'friend_shared'],
    required: true
  },
  actor: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  postId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Post',
    default: null
  },
  commentId: {
    type: mongoose.Schema.Types.ObjectId,
    default: null
  },
  postPreviewImage: {
    type: String,
    default: null
  },
  isRead: {
    type: Boolean,
    default: false
  }
}, {
  timestamps: true
});

// Indexes for better performance
notificationSchema.index({ userId: 1, createdAt: -1 });
notificationSchema.index({ userId: 1, isRead: 1 });

// Static method to create notification
notificationSchema.statics.createNotification = async function(userId, type, actorId, postId, commentId = null, postPreviewImage = null) {
  // Don't create notification if user is notifying themselves
  if (userId.toString() === actorId.toString()) {
    return null;
  }
  
  // Check if notification already exists (avoid duplicates)
  const existingNotification = await this.findOne({
    userId: userId,
    type: type,
    actor: actorId,
    postId: postId,
    commentId: commentId || null,
    createdAt: {
      $gte: new Date(Date.now() - 60000) // Within last minute
    }
  });
  
  if (existingNotification) {
    return existingNotification;
  }
  
  const notification = new this({
    userId: userId,
    type: type,
    actor: actorId,
    postId: postId,
    commentId: commentId,
    postPreviewImage: postPreviewImage
  });
  
  return await notification.save();
};

// Static method to get user notifications
notificationSchema.statics.getUserNotifications = async function(userId, page = 1, limit = 50) {
  const skip = (page - 1) * limit;
  
  return await this.find({ userId: userId })
    .populate('actor', 'username avatar profile.firstName profile.lastName')
    .populate('postId', 'images')
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(limit);
};

// Static method to mark notification as read
notificationSchema.statics.markAsRead = async function(notificationId, userId) {
  return await this.findOneAndUpdate(
    { _id: notificationId, userId: userId },
    { isRead: true },
    { new: true }
  );
};

// Static method to mark all notifications as read
notificationSchema.statics.markAllAsRead = async function(userId) {
  return await this.updateMany(
    { userId: userId, isRead: false },
    { isRead: true }
  );
};

module.exports = mongoose.model('Notification', notificationSchema);

