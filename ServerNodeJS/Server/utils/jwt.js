const jwt = require('jsonwebtoken');

// @desc    Generate JWT token
// @param   {string} userId - User ID to encode in token
// @returns {string} JWT token
const generateToken = (userId) => {
  return jwt.sign(
    { id: userId },
    process.env.JWT_SECRET,
    {
      expiresIn: process.env.JWT_EXPIRES_IN || '7d'
    }
  );
};

// @desc    Generate refresh token
// @param   {string} userId - User ID to encode in token
// @returns {string} Refresh token
const generateRefreshToken = (userId) => {
  return jwt.sign(
    { 
      id: userId,
      type: 'refresh'
    },
    process.env.JWT_SECRET,
    {
      expiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '30d'
    }
  );
};

// @desc    Verify JWT token
// @param   {string} token - JWT token to verify
// @returns {object} Decoded token payload
const verifyToken = (token) => {
  try {
    return jwt.verify(token, process.env.JWT_SECRET);
  } catch (error) {
    throw new Error('Invalid token');
  }
};

// @desc    Decode JWT token without verification
// @param   {string} token - JWT token to decode
// @returns {object} Decoded token payload or null
const decodeToken = (token) => {
  try {
    return jwt.decode(token);
  } catch (error) {
    return null;
  }
};

// @desc    Check if token is expired
// @param   {string} token - JWT token to check
// @returns {boolean} True if token is expired
const isTokenExpired = (token) => {
  try {
    const decoded = jwt.decode(token);
    if (!decoded || !decoded.exp) {
      return true;
    }
    
    const currentTime = Math.floor(Date.now() / 1000);
    return decoded.exp < currentTime;
  } catch (error) {
    return true;
  }
};

// @desc    Get token expiration time
// @param   {string} token - JWT token
// @returns {Date|null} Expiration date or null
const getTokenExpiration = (token) => {
  try {
    const decoded = jwt.decode(token);
    if (!decoded || !decoded.exp) {
      return null;
    }
    
    return new Date(decoded.exp * 1000);
  } catch (error) {
    return null;
  }
};

// @desc    Extract user ID from token
// @param   {string} token - JWT token
// @returns {string|null} User ID or null
const getUserIdFromToken = (token) => {
  try {
    const decoded = jwt.decode(token);
    return decoded && decoded.id ? decoded.id : null;
  } catch (error) {
    return null;
  }
};

// @desc    Generate token pair (access + refresh)
// @param   {string} userId - User ID
// @returns {object} Object containing access and refresh tokens
const generateTokenPair = (userId) => {
  const accessToken = generateToken(userId);
  const refreshToken = generateRefreshToken(userId);
  
  return {
    accessToken,
    refreshToken,
    expiresIn: process.env.JWT_EXPIRES_IN || '7d'
  };
};

// @desc    Verify refresh token
// @param   {string} refreshToken - Refresh token to verify
// @returns {object} Decoded token payload
const verifyRefreshToken = (refreshToken) => {
  try {
    const decoded = jwt.verify(refreshToken, process.env.JWT_SECRET);
    
    if (decoded.type !== 'refresh') {
      throw new Error('Invalid refresh token type');
    }
    
    return decoded;
  } catch (error) {
    throw new Error('Invalid refresh token');
  }
};

// @desc    Create token from request headers
// @param   {object} req - Express request object
// @returns {string|null} Extracted token or null
const extractTokenFromRequest = (req) => {
  let token = null;
  
  // Check Authorization header
  if (req.headers.authorization && req.headers.authorization.startsWith('Bearer ')) {
    token = req.headers.authorization.substring(7);
  }
  
  // Check cookies (if using cookie-based auth)
  else if (req.cookies && req.cookies.token) {
    token = req.cookies.token;
  }
  
  // Check query parameter (for websocket connections)
  else if (req.query && req.query.token) {
    token = req.query.token;
  }
  
  return token;
};

// @desc    Generate secure random token
// @param   {number} length - Token length (default: 32)
// @returns {string} Random token
const generateSecureToken = (length = 32) => {
  const crypto = require('crypto');
  return crypto.randomBytes(length).toString('hex');
};

module.exports = {
  generateToken,
  generateRefreshToken,
  verifyToken,
  decodeToken,
  isTokenExpired,
  getTokenExpiration,
  getUserIdFromToken,
  generateTokenPair,
  verifyRefreshToken,
  extractTokenFromRequest,
  generateSecureToken
};
