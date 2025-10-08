const mongoose = require('mongoose');

const passwordResetSchema = new mongoose.Schema({
  email: { type: String, required: true, index: true, lowercase: true, trim: true },
  otpCode: { type: String, required: true },
  expiresAt: { type: Date, required: true, index: true }
}, {
  timestamps: true
});

module.exports = mongoose.model('PasswordReset', passwordResetSchema);


