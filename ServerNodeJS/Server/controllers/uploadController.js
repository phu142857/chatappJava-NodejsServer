const multer = require('multer');
const path = require('path');
const fs = require('fs');

// Configure multer for chat image uploads
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    const chatId = req.params.chatId;
    const uploadPath = path.join(__dirname, '../uploads/chat', chatId);
    
    // Create directory if it doesn't exist
    if (!fs.existsSync(uploadPath)) {
      fs.mkdirSync(uploadPath, { recursive: true });
    }
    
    cb(null, uploadPath);
  },
  filename: function (req, file, cb) {
    // Generate unique filename with timestamp
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    const ext = path.extname(file.originalname);
    cb(null, 'image-' + uniqueSuffix + ext);
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB limit
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

// @desc    Upload chat image
// @route   POST /api/upload/chat/:chatId
// @access  Private
const uploadChatImage = async (req, res) => {
  try {
    // Use multer middleware to handle file upload
    upload.single('image')(req, res, async (err) => {
      if (err) {
        return res.status(400).json({
          success: false,
          message: err.message
        });
      }

      if (!req.file) {
        return res.status(400).json({
          success: false,
          message: 'No image file uploaded'
        });
      }

      const chatId = req.params.chatId;
      const fileName = req.file.filename;
      
      // Construct the image URL path
      const imageUrl = `/uploads/chat/${chatId}/${fileName}`;
      
      res.json({
        success: true,
        message: 'Image uploaded successfully',
        imageUrl: imageUrl,
        fileName: fileName
      });
    });

  } catch (error) {
    console.error('Upload chat image error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during image upload'
    });
  }
};

module.exports = {
  uploadChatImage
};
