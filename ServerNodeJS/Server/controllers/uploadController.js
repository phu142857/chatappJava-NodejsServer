const multer = require('multer');
const path = require('path');
const fs = require('fs');

// Configure multer for chat image uploads
const imageStorage = multer.diskStorage({
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

// Configure multer for post image uploads
const postImageStorage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadPath = path.join(__dirname, '../uploads/posts');
    
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
    cb(null, 'post-image-' + uniqueSuffix + ext);
  }
});

// Configure multer for comment media uploads
const commentMediaStorage = multer.diskStorage({
  destination: function (req, file, cb) {
    const uploadPath = path.join(__dirname, '../uploads/comments');
    
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
    cb(null, 'comment-media-' + uniqueSuffix + ext);
  }
});

// Configure multer for file uploads (PDF, TXT, etc.)
const fileStorage = multer.diskStorage({
  destination: function (req, file, cb) {
    const chatId = req.params.chatId;
    const uploadPath = path.join(__dirname, '../uploads/files', chatId);
    
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
    cb(null, 'file-' + uniqueSuffix + ext);
  }
});

const imageUpload = multer({
  storage: imageStorage,
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

const postImageUpload = multer({
  storage: postImageStorage,
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

const commentMediaUpload = multer({
  storage: commentMediaStorage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5MB limit for comments
  },
  fileFilter: function (req, file, cb) {
    // Allow images and GIFs
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Only image files (including GIFs) are allowed'), false);
    }
  }
});

const fileUpload = multer({
  storage: fileStorage,
  limits: {
    fileSize: 50 * 1024 * 1024 // 50MB limit for files
  },
  fileFilter: function (req, file, cb) {
    // Allow PDF, TXT, DOC, DOCX, XLS, XLSX, PPT, PPTX, and audio files for voice messages
    const allowedTypes = [
      'application/pdf',
      'text/plain',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/vnd.ms-excel',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-powerpoint',
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      // Audio file types for voice messages
      'audio/mp4',
      'audio/mpeg',
      'audio/mp3',
      'audio/wav',
      'audio/wave',
      'audio/x-wav',
      'audio/aac',
      'audio/ogg',
      'audio/webm',
      'audio/3gpp',
      'audio/amr',
      'audio/x-m4a'
    ];
    
    // Also allow if mime type starts with 'audio/' for other audio formats
    if (allowedTypes.includes(file.mimetype) || file.mimetype.startsWith('audio/')) {
      cb(null, true);
    } else {
      cb(new Error('Only PDF, TXT, DOC, DOCX, XLS, XLSX, PPT, PPTX, and audio files are allowed'), false);
    }
  }
});

// @desc    Upload chat image
// @route   POST /api/upload/chat/:chatId/image
// @access  Private
const uploadChatImage = async (req, res) => {
  try {
    // Use multer middleware to handle file upload
    imageUpload.single('image')(req, res, async (err) => {
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
        fileName: fileName,
        fileSize: req.file.size,
        mimeType: req.file.mimetype
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

// @desc    Upload chat file (PDF, TXT, etc.)
// @route   POST /api/upload/chat/:chatId/file
// @access  Private
const uploadChatFile = async (req, res) => {
  try {
    // Use multer middleware to handle file upload
    fileUpload.single('file')(req, res, async (err) => {
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

      const chatId = req.params.chatId;
      const fileName = req.file.filename;
      const originalName = req.file.originalname;
      
      // Construct the file URL path
      const fileUrl = `/uploads/files/${chatId}/${fileName}`;
      
      res.json({
        success: true,
        message: 'File uploaded successfully',
        fileUrl: fileUrl,
        fileName: fileName,
        originalName: originalName,
        fileSize: req.file.size,
        mimeType: req.file.mimetype
      });
    });

  } catch (error) {
    console.error('Upload chat file error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during file upload'
    });
  }
};

// @desc    Download file
// @route   GET /api/upload/download/:chatId/:fileName
// @access  Private
const downloadFile = async (req, res) => {
  try {
    const { chatId, fileName } = req.params;
    const filePath = path.join(__dirname, '../uploads/files', chatId, fileName);
    
    // Check if file exists
    if (!fs.existsSync(filePath)) {
      return res.status(404).json({
        success: false,
        message: 'File not found'
      });
    }

    // Get file stats
    const stats = fs.statSync(filePath);
    const fileSize = stats.size;
    
    // Set appropriate headers
    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader('Content-Disposition', `attachment; filename="${fileName}"`);
    res.setHeader('Content-Length', fileSize);
    
    // Stream the file
    const fileStream = fs.createReadStream(filePath);
    fileStream.pipe(res);
    
  } catch (error) {
    console.error('Download file error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during file download'
    });
  }
};

// @desc    Get file preview (for PDF and TXT)
// @route   GET /api/upload/preview/:chatId/:fileName
// @access  Private
const getFilePreview = async (req, res) => {
  try {
    const { chatId, fileName } = req.params;
    const filePath = path.join(__dirname, '../uploads/files', chatId, fileName);
    
    // Check if file exists
    if (!fs.existsSync(filePath)) {
      return res.status(404).json({
        success: false,
        message: 'File not found'
      });
    }

    const ext = path.extname(fileName).toLowerCase();
    
    if (ext === '.txt') {
      // For TXT files, return first 500 characters
      const content = fs.readFileSync(filePath, 'utf8');
      const preview = content.substring(0, 500);
      
      res.json({
        success: true,
        preview: preview,
        fullLength: content.length,
        type: 'text'
      });
    } else if (ext === '.pdf') {
      // For PDF files, we'll return metadata for now
      // In a real implementation, you might want to use a PDF parsing library
      const stats = fs.statSync(filePath);
      
      res.json({
        success: true,
        preview: 'PDF file preview not available',
        fileSize: stats.size,
        type: 'pdf'
      });
    } else {
      res.json({
        success: true,
        preview: 'File preview not available for this file type',
        type: 'other'
      });
    }
    
  } catch (error) {
    console.error('Get file preview error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during file preview'
    });
  }
};

// @desc    Upload post image
// @route   POST /api/upload/posts/image
// @access  Private
const uploadPostImage = async (req, res) => {
  try {
    postImageUpload.single('image')(req, res, async (err) => {
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

      const fileName = req.file.filename;
      const imageUrl = `/uploads/posts/${fileName}`;
      
      res.json({
        success: true,
        message: 'Image uploaded successfully',
        imageUrl: imageUrl
      });
    });
  } catch (error) {
    console.error('Upload post image error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during image upload'
    });
  }
};

// @desc    Upload comment media (image/GIF)
// @route   POST /api/upload/comments/image
// @access  Private
const uploadCommentImage = async (req, res) => {
  try {
    commentMediaUpload.single('image')(req, res, async (err) => {
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

      const fileName = req.file.filename;
      const imageUrl = `/uploads/comments/${fileName}`;
      
      res.json({
        success: true,
        message: 'Comment media uploaded successfully',
        imageUrl: imageUrl,
        fileName: fileName,
        fileSize: req.file.size,
        mimeType: req.file.mimetype
      });
    });
  } catch (error) {
    console.error('Upload comment image error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error during comment media upload'
    });
  }
};

module.exports = {
  uploadChatImage,
  uploadChatFile,
  uploadPostImage,
  uploadCommentImage,
  downloadFile,
  getFilePreview
};
