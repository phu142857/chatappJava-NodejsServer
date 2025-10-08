const mongoose = require('mongoose');

const registrationOTPSchema = new mongoose.Schema({
  email: { type: String, required: true, lowercase: true, trim: true, index: true },
  username: { type: String, required: true, trim: true },
  password: { type: String, required: true },
  otpCode: { type: String, required: true },
  expiresAt: { type: Date, required: true, index: true }
}, {
  timestamps: true
});

registrationOTPSchema.index({ email: 1 }, { unique: false });
registrationOTPSchema.index({ username: 1 }, { unique: false });

module.exports = mongoose.model('RegistrationOTP', registrationOTPSchema);


