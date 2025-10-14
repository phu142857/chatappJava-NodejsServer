const express = require('express');
const { body } = require('express-validator');
const {
  register,
  login,
  logout,
  getMe,
  updateProfile,
  changePassword,
  refreshToken,
  uploadAvatar,
  deleteAccount,
  requestDeleteAccountOtp,
  confirmDeleteAccountWithOtp,
  registerRequestOTP,
  verifyRegisterOTP
} = require('../controllers/authController');
const {
  requestPasswordReset,
  confirmPasswordReset,
  verifyPasswordResetOtp
} = require('../controllers/authController');
const { authMiddleware } = require('../middleware/authMiddleware');

const router = express.Router();

// Validation rules
const registerValidation = [
  body('username')
    .isLength({ min: 3, max: 30 })
    .withMessage('Username must be between 3 and 30 characters')
    .matches(/^[a-zA-Z0-9_]+$/)
    .withMessage('Username can only contain letters, numbers, and underscores'),
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Please provide a valid email'),
  body('password')
    .isLength({ min: 6 })
    .withMessage('Password must be at least 6 characters long')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('Password must contain at least one uppercase letter, one lowercase letter, and one number')
];

const loginValidation = [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Please provide a valid email'),
  body('password')
    .notEmpty()
    .withMessage('Password is required')
];

const updateProfileValidation = [
  body('username')
    .optional()
    .isLength({ min: 3, max: 30 })
    .withMessage('Username must be between 3 and 30 characters')
    .matches(/^[a-zA-Z0-9_]+$/)
    .withMessage('Username can only contain letters, numbers, and underscores'),
  body('profile.firstName')
    .optional()
    .isLength({ max: 50 })
    .withMessage('First name cannot exceed 50 characters'),
  body('profile.lastName')
    .optional()
    .isLength({ max: 50 })
    .withMessage('Last name cannot exceed 50 characters'),
  body('profile.bio')
    .optional()
    .isLength({ max: 500 })
    .withMessage('Bio cannot exceed 500 characters'),
  body('profile.phoneNumber')
    .optional()
    .custom((value) => {
      if (value && value.trim() !== '') {
        // Only validate if value is not empty
        const phoneRegex = /^[\+]?[0-9][\d]{0,15}$/;
        if (!phoneRegex.test(value.replace(/[\s\-\(\)]/g, ''))) {
          throw new Error('Please provide a valid phone number');
        }
      }
      return true;
    }),
  body('avatar')
    .optional()
    .isString()
    .withMessage('Avatar must be a string')
];

const changePasswordValidation = [
  body('currentPassword')
    .notEmpty()
    .withMessage('Current password is required'),
  body('newPassword')
    .isLength({ min: 6 })
    .withMessage('New password must be at least 6 characters long')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('New password must contain at least one uppercase letter, one lowercase letter, and one number')
];

// Public routes
router.post('/register', registerValidation, register);
router.post('/login', loginValidation, login);

// OTP Registration
router.post('/register/request-otp', [
  body('username')
    .isLength({ min: 3, max: 30 })
    .withMessage('Username must be between 3 and 30 characters')
    .matches(/^[a-zA-Z0-9_]+$/)
    .withMessage('Username can only contain letters, numbers, and underscores'),
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Please provide a valid email'),
  body('password')
    .isLength({ min: 6 })
    .withMessage('Password must be at least 6 characters long')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('Password must contain at least one uppercase letter, one lowercase letter, and one number')
], registerRequestOTP);

router.post('/register/verify-otp', [
  body('email')
    .isEmail()
    .normalizeEmail()
    .withMessage('Please provide a valid email'),
  body('otpCode')
    .isLength({ min: 6, max: 6 })
    .withMessage('OTP code must be 6 digits')
], verifyRegisterOTP);

// Password Reset
router.post('/password/request-reset', [
  body('email').isEmail().normalizeEmail().withMessage('Please provide a valid email')
], requestPasswordReset);

router.post('/password/reset', [
  body('email').isEmail().normalizeEmail().withMessage('Please provide a valid email'),
  body('otpCode').isLength({ min: 6, max: 6 }).withMessage('OTP code must be 6 digits'),
  body('newPassword')
    .isLength({ min: 6 })
    .withMessage('Password must be at least 6 characters long')
    .matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/)
    .withMessage('Password must contain at least one uppercase letter, one lowercase letter, and one number')
], confirmPasswordReset);

// Verify password reset OTP only
router.post('/password/verify-otp', [
  body('email').isEmail().normalizeEmail().withMessage('Please provide a valid email'),
  body('otpCode').isLength({ min: 6, max: 6 }).withMessage('OTP code must be 6 digits')
], verifyPasswordResetOtp);

// Protected routes
router.use(authMiddleware); // Apply auth middleware to all routes below

router.post('/logout', logout);
router.get('/me', getMe);
router.put('/profile', updateProfileValidation, updateProfile);
router.put('/change-password', changePasswordValidation, changePassword);
router.post('/refresh', refreshToken);
router.post('/upload-avatar', uploadAvatar);
router.delete('/me', deleteAccount);
// Delete account via OTP
router.post('/delete/request-otp', requestDeleteAccountOtp);
router.delete('/me/confirm', [
  body('otpCode')
    .isLength({ min: 6, max: 6 })
    .withMessage('OTP code must be 6 digits')
], confirmDeleteAccountWithOtp);

module.exports = router;
