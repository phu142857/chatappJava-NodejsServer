/**
 * Server configuration management
 * Centralizes all configuration logic and provides validation
 */

const dotenv = require('dotenv');

// Load environment variables
dotenv.config();

/**
 * Validates required environment variables
 * @param {string[]} requiredVars - Array of required variable names
 */
function validateRequired(requiredVars) {
    const missing = requiredVars.filter(varName => !process.env[varName]);
    if (missing.length > 0) {
        throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
    }
}

/**
 * Server configuration object
 */
const serverConfig = {
    // Server settings
    port: parseInt(process.env.PORT) || 49664,
    nodeEnv: process.env.NODE_ENV || 'development',
    protocol: process.env.SERVER_PROTOCOL || 'http',
    host: process.env.SERVER_HOST || 'localhost',
    
    // Get server base URL
    get baseUrl() {
        return `${this.protocol}://${this.host}:${this.port}`;
    },
    
    // Database settings
    database: {
        uri: process.env.MONGODB_URI || 'mongodb://localhost:27017/chatapp',
        name: process.env.DB_NAME || 'chatapp'
    },
    
    // JWT settings
    jwt: {
        secret: process.env.JWT_SECRET,
        expiresIn: process.env.JWT_EXPIRES_IN || '7d',
        refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '30d'
    },
    
    // Client URLs
    client: {
        webAdminUrl: process.env.WEBADMIN_URL || 'http://localhost:5173',
        androidUrl: process.env.CLIENT_URL || `http://localhost:${this.port}`
    },
    
    // Socket.IO settings
    socket: {
        corsOrigin: process.env.SOCKET_CORS_ORIGIN || '*'
    },
    
    // Security settings
    security: {
        bcryptSaltRounds: parseInt(process.env.BCRYPT_SALT_ROUNDS) || 12
    },
    
    // File upload settings
    upload: {
        maxFileSize: process.env.MAX_FILE_SIZE || '10mb',
        uploadDir: process.env.UPLOAD_DIR || './uploads'
    },
    
    // Email settings
    email: {
        host: process.env.SMTP_HOST,
        port: parseInt(process.env.SMTP_PORT) || 587,
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS,
        secure: process.env.SMTP_SECURE === 'true',
        from: process.env.SMTP_FROM || '"Chat App" <no-reply@chatapp.com>'
    },
    
    // AI settings
    ai: {
        geminiApiKey: process.env.GEMINI_API_KEY
    },
    
    // Admin settings
    admin: {
        email: process.env.ADMIN_EMAIL,
        username: process.env.ADMIN_USERNAME,
        password: process.env.ADMIN_PASSWORD
    }
};

/**
 * Validates configuration and throws error if invalid
 */
function validateConfig() {
    const requiredVars = ['JWT_SECRET'];
    
    if (serverConfig.nodeEnv === 'production') {
        requiredVars.push('MONGODB_URI');
    }
    
    validateRequired(requiredVars);
    
    // Validate port
    if (isNaN(serverConfig.port) || serverConfig.port < 1 || serverConfig.port > 65535) {
        throw new Error('Invalid PORT configuration');
    }
    
    // Validate JWT secret length
    if (serverConfig.jwt.secret && serverConfig.jwt.secret.length < 32) {
        console.warn('JWT_SECRET should be at least 32 characters long for security');
    }
    
    return true;
}

/**
 * Get configuration for specific environment
 */
function getEnvConfig() {
    return {
        isDevelopment: serverConfig.nodeEnv === 'development',
        isProduction: serverConfig.nodeEnv === 'production',
        isTest: serverConfig.nodeEnv === 'test'
    };
}

// Validate configuration on module load
try {
    validateConfig();
} catch (error) {
    console.error('Configuration validation failed:', error.message);
    if (serverConfig.nodeEnv === 'production') {
        process.exit(1);
    }
}

module.exports = {
    serverConfig,
    validateConfig,
    getEnvConfig
};
