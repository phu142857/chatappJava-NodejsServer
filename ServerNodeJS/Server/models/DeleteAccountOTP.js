const mongoose = require('mongoose');

const deleteAccountOtpSchema = new mongoose.Schema({
  email: { type: String, required: true, index: true, lowercase: true, trim: true },
  otpCode: { type: String, required: true },
  expiresAt: { type: Date, required: true, index: true }
}, {
  timestamps: true
});

module.exports = mongoose.model('DeleteAccountOTP', deleteAccountOtpSchema);


