const Report = require('../models/Report');
const User = require('../models/User');

// Create a report
exports.createReport = async (req, res) => {
  try {
    const senderId = req.user.id;
    const { targetUserId, content } = req.body;

    if (!targetUserId || !content) {
      return res.status(400).json({ success: false, message: 'targetUserId and content are required' });
    }

    // word limit: 100 words
    const words = content.trim().split(/\s+/);
    if (words.length > 100) {
      return res.status(400).json({ success: false, message: 'Report exceeds 100 words limit' });
    }

    const target = await User.findById(targetUserId);
    if (!target) {
      return res.status(404).json({ success: false, message: 'Target user not found' });
    }

    const report = await Report.create({ sender: senderId, target: targetUserId, content });
    return res.status(201).json({ success: true, data: { report } });
  } catch (err) {
    console.error('createReport error:', err);
    return res.status(500).json({ success: false, message: 'Server error creating report' });
  }
};

// List reports (admin)
exports.listReports = async (req, res) => {
  try {
    const reports = await Report.find().populate('sender', 'username email').populate('target', 'username email').sort({ createdAt: -1 });
    return res.status(200).json({ success: true, data: { reports } });
  } catch (err) {
    console.error('listReports error:', err);
    return res.status(500).json({ success: false, message: 'Server error listing reports' });
  }
};

// Delete a report (admin)
exports.deleteReport = async (req, res) => {
  try {
    const { id } = req.params;
    const report = await Report.findByIdAndDelete(id);
    if (!report) {
      return res.status(404).json({ success: false, message: 'Report not found' });
    }
    return res.status(200).json({ success: true, message: 'Report deleted' });
  } catch (err) {
    console.error('deleteReport error:', err);
    return res.status(500).json({ success: false, message: 'Server error deleting report' });
  }
};


