const User = require('../models/User');
const { generateToken } = require('../utils/jwt');
const RegistrationOTP = require('../models/RegistrationOTP');
const { sendMail } = require('../utils/mailer');
const PasswordReset = require('../models/PasswordReset');
const DeleteAccountOTP = require('../models/DeleteAccountOTP');
const { validationResult } = require('express-validator');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

// @desc    Register a new user
// @route   POST /api/auth/register
// @access  Public
const register = async (req, res) => {
  try {
    // Check for validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { username, email, password, profile } = req.body;

    // Check if user already exists
    const existingUser = await User.findOne({
      $or: [{ email }, { username }]
    });

    if (existingUser) {
      return res.status(400).json({
        success: false,
        message: 'User already exists with this email or username'
      });
    }

    return res.status(400).json({
      success: false,
      message: 'Direct registration is disabled. Please request OTP and verify.'
    });

  } catch (error) {
    console.error('Register error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during registration'
    });
  }
};

// @desc    Request OTP for registration
// @route   POST /api/auth/register/request-otp
// @access  Public
const registerRequestOTP = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }

    const { username, email, password } = req.body;

    const existingUser = await User.findOne({ $or: [{ email }, { username }] });
    if (existingUser) {
      return res.status(400).json({ success: false, message: 'User already exists with this email or username' });
    }

    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 60 * 1000); // 1 minute

    await RegistrationOTP.deleteMany({ email });
    await RegistrationOTP.create({ email, username, password, otpCode, expiresAt });

    const html = `
      <p>Your verification code is:</p>
      <h2 style="letter-spacing:4px;">${otpCode}</h2>
      <p>This code will expire in 1 minute.</p>
    `;
    await sendMail(email, 'Your OTP Code', html);

    return res.status(200).json({ success: true, message: 'OTP sent to email. Valid for 1 minute.' });
  } catch (error) {
    console.error('Request OTP error:', error);
    return res.status(500).json({ success: false, message: 'Server error while requesting OTP' });
  }
};

// @desc    Verify OTP and complete registration
// @route   POST /api/auth/register/verify-otp
// @access  Public
const verifyRegisterOTP = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }

    const { email, otpCode } = req.body;
    const record = await RegistrationOTP.findOne({ email }).sort({ createdAt: -1 });
    if (!record) {
      return res.status(400).json({ success: false, message: 'No OTP found or it has been used' });
    }

    if (new Date() > record.expiresAt) {
      await RegistrationOTP.deleteMany({ email });
      return res.status(400).json({ success: false, message: 'OTP expired' });
    }

    if (record.otpCode !== otpCode) {
      return res.status(400).json({ success: false, message: 'Invalid OTP' });
    }

    const existsAgain = await User.findOne({ $or: [{ email }, { username: record.username }] });
    if (existsAgain) {
      await RegistrationOTP.deleteMany({ email });
      return res.status(400).json({ success: false, message: 'User already exists' });
    }

    const user = new User({ username: record.username, email: record.email, password: record.password, profile: {} });
    await user.save();

    await RegistrationOTP.deleteMany({ email });

    const token = generateToken(user._id);
    return res.status(201).json({ success: true, message: 'User registered successfully', data: { user: user.toJSON(), token } });
  } catch (error) {
    console.error('Verify OTP error:', error);
    return res.status(500).json({ success: false, message: 'Server error while verifying OTP' });
  }
};

module.exports.registerRequestOTP = registerRequestOTP;
module.exports.verifyRegisterOTP = verifyRegisterOTP;

// @desc    Login user
// @route   POST /api/auth/login
// @access  Public
const login = async (req, res) => {
  try {
    // Check for validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { email, password } = req.body;

    // Find user by email (check both active and inactive users)
    const user = await User.findOne({ email });
    if (!user) {
      return res.status(401).json({
        success: false,
        message: 'Account not found',
        accountStatus: 'not_found',
        details: 'No account found with this email address. Please check your email or register for a new account.'
      });
    }

    // Check if user account is locked
    if (!user.isActive) {
      return res.status(403).json({
        success: false,
        message: 'Your account has been locked',
        accountStatus: 'locked',
        details: 'Your account has been locked by an administrator. Please contact support for assistance.'
      });
    }

    // Check password
    const isPasswordValid = await user.comparePassword(password);
    if (!isPasswordValid) {
      return res.status(401).json({
        success: false,
        message: 'Incorrect password',
        accountStatus: 'active',
        details: 'The password you entered is incorrect. Please try again or use the forgot password feature.'
      });
    }

    await user.updateLastSeen();

    // Generate JWT token
    const token = generateToken(user._id);

    res.json({
      success: true,
      message: 'Login successful',
      data: {
        user: user.toJSON(),
        token
      }
    });

  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during login'
    });
  }
};

// @desc    Logout user
// @route   POST /api/auth/logout
// @access  Private
const logout = async (req, res) => {
  try {
    const user = await User.findById(req.user.id);
    if (user) {
      await user.updateLastSeen();
    }

    res.json({
      success: true,
      message: 'Logout successful'
    });

  } catch (error) {
    console.error('Logout error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during logout'
    });
  }
};

// @desc    Get current user profile
// @route   GET /api/auth/me
// @access  Private
const getMe = async (req, res) => {
  try {
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      data: {
        user: user.toJSON()
      }
    });

  } catch (error) {
    console.error('Get me error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error'
    });
  }
};

// @desc    Update user profile
// @route   PUT /api/auth/profile
// @access  Private
const updateProfile = async (req, res) => {
  try {
    // Check for validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { username, profile, avatar } = req.body;
    const user = await User.findById(req.user.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Check if username is being changed and if it's already taken
    if (username && username !== user.username) {
      const existingUser = await User.findOne({ username });
      if (existingUser) {
        return res.status(400).json({
          success: false,
          message: 'Username already taken'
        });
      }
      user.username = username;
    }

    // Update profile fields
    if (profile) {
      user.profile = { ...user.profile, ...profile };
    }

    if (avatar !== undefined) {
      if (avatar === '') {
        // Remove avatar
        if (user.avatar) {
          const oldAvatarPath = path.join(__dirname, '../uploads/avatars', path.basename(user.avatar));
          if (fs.existsSync(oldAvatarPath)) {
            fs.unlinkSync(oldAvatarPath);
          }
        }
        user.avatar = '';
      } else {
        user.avatar = avatar;
      }
    }

    await user.save();

    res.json({
      success: true,
      message: 'Profile updated successfully',
      data: {
        user: user.toJSON()
      }
    });

  } catch (error) {
    console.error('Update profile error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during profile update'
    });
  }
};

// @desc    Change password
// @route   PUT /api/auth/change-password
// @access  Private
const changePassword = async (req, res) => {
  try {
    // Check for validation errors
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors: errors.array()
      });
    }

    const { currentPassword, newPassword } = req.body;
    const user = await User.findById(req.user.id);

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Verify current password
    const isCurrentPasswordValid = await user.comparePassword(currentPassword);
    if (!isCurrentPasswordValid) {
      return res.status(400).json({
        success: false,
        message: 'Current password is incorrect'
      });
    }

    // Update password
    user.password = newPassword;
    await user.save();

    res.json({
      success: true,
      message: 'Password changed successfully'
    });

  } catch (error) {
    console.error('Change password error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during password change'
    });
  }
};

// @desc    Request password reset OTP
// @route   POST /api/auth/password/request-reset
// @access  Public
const requestPasswordReset = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }
    const { email } = req.body;
    const user = await User.findOne({ email });
    if (!user) {
      // To avoid user enumeration, respond success
      return res.status(200).json({ success: true, message: 'If the email exists, an OTP has been sent' });
    }

    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes
    await PasswordReset.deleteMany({ email });
    await PasswordReset.create({ email, otpCode, expiresAt });

    const html = `
      <p>Your password reset code is:</p>
      <h2 style="letter-spacing:4px;">${otpCode}</h2>
      <p>This code will expire in 10 minutes.</p>
    `;
    await sendMail(email, 'Password Reset Code', html);

    return res.status(200).json({ success: true, message: 'If the email exists, an OTP has been sent' });
  } catch (error) {
    console.error('Request password reset error:', error);
    return res.status(500).json({ success: false, message: 'Server error while requesting password reset' });
  }
};

// @desc    Confirm password reset with OTP
// @route   POST /api/auth/password/reset
// @access  Public
const confirmPasswordReset = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }

    const { email, otpCode, newPassword } = req.body;
    const record = await PasswordReset.findOne({ email }).sort({ createdAt: -1 });
    if (!record) {
      return res.status(400).json({ success: false, message: 'OTP not found or already used' });
    }
    if (new Date() > record.expiresAt) {
      await PasswordReset.deleteMany({ email });
      return res.status(400).json({ success: false, message: 'OTP expired' });
    }
    if (record.otpCode !== otpCode) {
      return res.status(400).json({ success: false, message: 'Invalid OTP' });
    }

    const user = await User.findOne({ email });
    if (!user) {
      await PasswordReset.deleteMany({ email });
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    user.password = newPassword;
    await user.save();
    await PasswordReset.deleteMany({ email });

    return res.status(200).json({ success: true, message: 'Password has been reset successfully' });
  } catch (error) {
    console.error('Confirm password reset error:', error);
    return res.status(500).json({ success: false, message: 'Server error while resetting password' });
  }
};

module.exports.requestPasswordReset = requestPasswordReset;
module.exports.confirmPasswordReset = confirmPasswordReset;

// @desc    Verify password reset OTP (no password change)
// @route   POST /api/auth/password/verify-otp
// @access  Public
const verifyPasswordResetOtp = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }

    const { email, otpCode } = req.body;
    const record = await PasswordReset.findOne({ email }).sort({ createdAt: -1 });
    if (!record) {
      return res.status(400).json({ success: false, message: 'OTP not found or already used' });
    }
    if (new Date() > record.expiresAt) {
      await PasswordReset.deleteMany({ email });
      return res.status(400).json({ success: false, message: 'OTP expired' });
    }
    if (record.otpCode !== otpCode) {
      return res.status(400).json({ success: false, message: 'Invalid OTP' });
    }

    return res.status(200).json({ success: true, message: 'OTP is valid' });
  } catch (error) {
    console.error('Verify password reset OTP error:', error);
    return res.status(500).json({ success: false, message: 'Server error while verifying OTP' });
  }
};

module.exports.verifyPasswordResetOtp = verifyPasswordResetOtp;

// @desc    Refresh token
// @route   POST /api/auth/refresh
// @access  Private
const refreshToken = async (req, res) => {
  try {
    const user = await User.findById(req.user.id);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Generate new token
    const token = generateToken(user._id);

    res.json({
      success: true,
      message: 'Token refreshed successfully',
      data: {
        token
      }
    });

  } catch (error) {
    console.error('Refresh token error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during token refresh'
    });
  }
};

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadDir = path.join(__dirname, '../uploads/avatars');
    // Create directory if it doesn't exist
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    // Generate unique filename with timestamp
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'avatar-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB limit
  },
  fileFilter: function (req, file, cb) {
    // Check if file is an image
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Only image files are allowed'), false);
    }
  }
});

// @desc    Upload user avatar
// @route   POST /api/auth/upload-avatar
// @access  Private
const uploadAvatar = async (req, res) => {
  try {
    // Use multer middleware to handle file upload
    upload.single('avatar')(req, res, async (err) => {
      if (err) {
        return res.status(400).json({
          success: false,
          message: err.message
        });
      }

      if (!req.file) {
        return res.status(400).json({
          success: false,
          message: 'No file uploaded'
        });
      }

      const userId = req.user.id;
      
      // Delete old avatar if exists
      const user = await User.findById(userId);
      if (user && user.avatar) {
        const oldAvatarPath = path.join(__dirname, '../uploads/avatars', path.basename(user.avatar));
        if (fs.existsSync(oldAvatarPath)) {
          fs.unlinkSync(oldAvatarPath);
        }
      }

      // Update user's avatar URL
      const avatarUrl = `/uploads/avatars/${req.file.filename}`;
      await User.findByIdAndUpdate(userId, { avatar: avatarUrl });

      res.json({
        success: true,
        message: 'Avatar uploaded successfully',
        data: {
          avatarUrl: avatarUrl
        }
      });
    });
  } catch (error) {
    console.error('Avatar upload error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during avatar upload'
    });
  }
};

// @desc    Request OTP to delete account
// @route   POST /api/auth/delete/request-otp
// @access  Private
const requestDeleteAccountOtp = async (req, res) => {
  try {
    const userId = req.user.id;
    const user = await User.findById(userId);
    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    const otpCode = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

    await DeleteAccountOTP.deleteMany({ userId });
    await DeleteAccountOTP.create({ userId, email: user.email, otpCode, expiresAt });

    const html = `
      <p>Your delete account verification code is:</p>
      <h2 style="letter-spacing:4px;">${otpCode}</h2>
      <p>This code will expire in 10 minutes.</p>
    `;
    await sendMail(user.email, 'Delete Account Verification Code', html);

    return res.status(200).json({ success: true, message: 'OTP sent to your email.' });
  } catch (error) {
    console.error('Request delete account OTP error:', error);
    return res.status(500).json({ success: false, message: 'Server error while requesting delete OTP' });
  }
};

// @desc    Confirm account deletion with OTP
// @route   DELETE /api/auth/me/confirm
// @access  Private
const confirmDeleteAccountWithOtp = async (req, res) => {
  try {
    const userId = req.user.id;
    const { otpCode } = req.body;

    if (!otpCode || typeof otpCode !== 'string') {
      return res.status(400).json({ success: false, message: 'OTP code is required' });
    }

    const record = await DeleteAccountOTP.findOne({ userId }).sort({ createdAt: -1 });
    if (!record) {
      return res.status(400).json({ success: false, message: 'OTP not found or already used' });
    }
    if (new Date() > record.expiresAt) {
      await DeleteAccountOTP.deleteMany({ userId });
      return res.status(400).json({ success: false, message: 'OTP expired' });
    }
    if (record.otpCode !== otpCode) {
      return res.status(400).json({ success: false, message: 'Invalid OTP' });
    }

    const user = await User.findById(userId);
    if (!user) {
      await DeleteAccountOTP.deleteMany({ userId });
      return res.status(404).json({ success: false, message: 'User not found' });
    }

    await User.findByIdAndDelete(userId);
    await DeleteAccountOTP.deleteMany({ userId });

    await logAuditAction(userId, 'DELETE_ACCOUNT', 'User', `User ${user.username} deleted their own account via OTP`, req.ip);

    return res.status(200).json({ success: true, message: 'Account deleted successfully' });
  } catch (error) {
    console.error('Confirm delete account with OTP error:', error);
    return res.status(500).json({ success: false, message: 'Server error while deleting account' });
  }
};

// @desc    Delete current user account
// @route   DELETE /api/auth/me
// @access  Private
const deleteAccount = async (req, res) => {
  try {
    const userId = req.user.id;
    const { password } = req.body;

    // Find user to verify password and get username for audit log
    const user = await User.findById(userId);
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Verify password if provided
    if (password) {
      const isPasswordValid = await user.comparePassword(password);
      if (!isPasswordValid) {
        return res.status(400).json({
          success: false,
          message: 'Invalid password'
        });
      }
    }

    // Delete user account
    await User.findByIdAndDelete(userId);

    // Log the action
    await logAuditAction(userId, 'DELETE_ACCOUNT', 'User', `User ${user.username} deleted their own account`, req.ip);

    res.json({
      success: true,
      message: 'Account deleted successfully'
    });

  } catch (error) {
    console.error('Delete account error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during account deletion'
    });
  }
};

// Helper function to log audit actions
const logAuditAction = async (userId, action, resource, details, ipAddress) => {
  try {
    const AuditLog = require('../models/AuditLog');
    const auditLog = new AuditLog({
      user: userId,
      action,
      resource,
      details,
      ipAddress
    });
    await auditLog.save();
  } catch (error) {
    console.error('Error logging audit action:', error);
  }
};

module.exports = {
  register,
  registerRequestOTP,
  verifyRegisterOTP,
  requestPasswordReset,
  confirmPasswordReset,
  verifyPasswordResetOtp,
  login,
  logout,
  getMe,
  updateProfile,
  changePassword,
  refreshToken,
  uploadAvatar,
  deleteAccount,
  requestDeleteAccountOtp,
  confirmDeleteAccountWithOtp
};
