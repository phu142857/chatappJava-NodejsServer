const mongoose = require('mongoose');

const blockedIPSchema = new mongoose.Schema({
  ip: {
    type: String,
    required: true,
    validate: {
      validator: function(v) {
        return /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/.test(v);
      },
      message: 'Invalid IP address format'
    }
  },
  reason: {
    type: String,
    required: true,
    enum: ['brute_force', 'spam', 'abuse', 'suspicious', 'other']
  },
  blockedAt: {
    type: Date,
    default: Date.now
  },
  blockedBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  }
}, {
  timestamps: true
});

// Index for efficient queries
blockedIPSchema.index({ ip: 1 });
blockedIPSchema.index({ blockedAt: -1 });

module.exports = mongoose.model('BlockedIP', blockedIPSchema);
