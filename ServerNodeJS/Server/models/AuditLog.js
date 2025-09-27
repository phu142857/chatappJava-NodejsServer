const mongoose = require('mongoose');

const auditLogSchema = new mongoose.Schema({
  user: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  action: {
    type: String,
    required: true,
    enum: [
      'LOGIN',
      'LOGOUT',
      'CREATE_USER',
      'UPDATE_USER',
      'DELETE_USER',
      'BLOCK_USER',
      'UNBLOCK_USER',
      'LOCK_USER',
      'UNLOCK_USER',
      'BLOCK_IP',
      'UNBLOCK_IP',
      'CHANGE_ROLE',
      'RESET_PASSWORD',
      'CREATE_CHAT',
      'DELETE_CHAT',
      'SEND_MESSAGE',
      'DELETE_MESSAGE',
      'CREATE_GROUP',
      'DELETE_GROUP',
      'JOIN_GROUP',
      'LEAVE_GROUP',
      'UPLOAD_FILE',
      'DELETE_FILE',
      'DELETE_ACCOUNT',
      'OTHER'
    ]
  },
  resource: {
    type: String,
    required: true
  },
  details: {
    type: String,
    required: true
  },
  timestamp: {
    type: Date,
    default: Date.now
  },
  ipAddress: {
    type: String,
    required: true
  }
}, {
  timestamps: true
});

// Index for efficient queries
auditLogSchema.index({ user: 1 });
auditLogSchema.index({ timestamp: -1 });
auditLogSchema.index({ action: 1 });

module.exports = mongoose.model('AuditLog', auditLogSchema);
