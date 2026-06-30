const User = require('../models/User');
const { validationResult } = require('express-validator');

const createUser = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }

    const { username, email, password, role = 'user', profile } = req.body;

    const exists = await User.findOne({ $or: [{ email }, { username }] });
    if (exists) {
      return res.status(400).json({ success: false, message: 'User exists with email or username' });
    }

    const user = new User({ username, email, password, role, profile: profile || {}, isActive: true });
    await user.save();
    return res.status(201).json({ success: true, message: 'User created', data: { user: user.toJSON() } });
  } catch (err) {
    console.error('Create user error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
};

const updateUser = async (req, res) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ success: false, message: 'Validation failed', errors: errors.array() });
    }

    const { id } = req.params;
    const { username, email, profile, isActive } = req.body;
    const user = await User.findById(id);
    if (!user) return res.status(404).json({ success: false, message: 'User not found' });

    if (username && username !== user.username) {
      const u = await User.findOne({ username });
      if (u) return res.status(400).json({ success: false, message: 'Username already taken' });
      user.username = username;
    }
    if (email && email !== user.email) {
      const u = await User.findOne({ email });
      if (u) return res.status(400).json({ success: false, message: 'Email already in use' });
      user.email = email;
    }
    if (typeof isActive === 'boolean') {
      user.isActive = isActive;
    }
    if (profile) {
      user.profile = { ...user.profile, ...profile };
    }
    await user.save();
    return res.json({ success: true, message: 'User updated', data: { user: user.toJSON() } });
  } catch (err) {
    console.error('Update user error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
};

const deleteUser = async (req, res) => {
  try {
    const { id } = req.params;
    const user = await User.findById(id);
    if (!user) return res.status(404).json({ success: false, message: 'User not found' });
    // prevent deleting yourself (admin safety)
    if (req.user && req.user._id.toString() === id) {
      return res.status(400).json({ success: false, message: 'Cannot delete the currently authenticated user' });
    }
    await User.deleteOne({ _id: id });
    return res.json({ success: true, message: 'User deleted' });
  } catch (err) {
    console.error('Delete user error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
};

const setRole = async (req, res) => {
  try {
    const { id } = req.params;
    const { role } = req.body; // 'user' | 'admin' | 'moderator'
    if (!['user', 'admin', 'moderator'].includes(role)) {
      return res.status(400).json({ success: false, message: 'Invalid role' });
    }
    const user = await User.findById(id);
    if (!user) return res.status(404).json({ success: false, message: 'User not found' });
    user.role = role;
    await user.save();
    // Return updated list-friendly projection to avoid stale caches on client
    const updated = await User.findById(id).select('username email avatar status profile lastSeen isActive createdAt role');
    return res.json({ success: true, message: 'Role updated', data: { user: updated } });
  } catch (err) {
    console.error('Set role error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
};

const resetPassword = async (req, res) => {
  try {
    const { id } = req.params;
    const { newPassword } = req.body;
    if (!newPassword || newPassword.length < 6) {
      return res.status(400).json({ success: false, message: 'Password must be at least 6 characters' });
    }
    const user = await User.findById(id);
    if (!user) return res.status(404).json({ success: false, message: 'User not found' });
    user.password = newPassword;
    await user.save();
    return res.json({ success: true, message: 'Password reset successfully' });
  } catch (err) {
    console.error('Reset password error:', err);
    return res.status(500).json({ success: false, message: 'Server error' });
  }
};

module.exports = { createUser, updateUser, deleteUser, setRole, resetPassword };


