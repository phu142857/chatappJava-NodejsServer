const mongoose = require('mongoose');

const deleteAccountOtpSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  email: { type: String, required: true, lowercase: true, trim: true },
  otpCode: { type: String, required: true },
  expiresAt: { type: Date, required: true, index: true }
}, {
  timestamps: true
});

deleteAccountOtpSchema.index({ userId: 1 }, { unique: false });

module.exports = mongoose.model('DeleteAccountOTP', deleteAccountOtpSchema);

// Removed: DeleteAccountOTP model (feature reverted)


