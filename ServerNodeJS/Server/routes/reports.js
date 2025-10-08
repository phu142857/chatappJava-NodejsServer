const express = require('express');
const router = express.Router();
const { authMiddleware, adminOnly } = require('../middleware/authMiddleware');
const reportController = require('../controllers/reportController');

// Create report (authenticated)
router.post('/', authMiddleware, reportController.createReport);

// Admin: list reports
router.get('/', authMiddleware, adminOnly, reportController.listReports);

// Admin: delete report
router.delete('/:id', authMiddleware, adminOnly, reportController.deleteReport);

module.exports = router;


