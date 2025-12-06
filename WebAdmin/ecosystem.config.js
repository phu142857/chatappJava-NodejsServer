/**
 * PM2 Ecosystem Configuration for WebAdmin
 * 
 * Usage:
 *   - Production: pm2 start ecosystem.config.js --env production
 *   - Development: pm2 start ecosystem.config.js --env development
 */

/**
 * PM2 Ecosystem Configuration for WebAdmin
 * 
 * Usage:
 *   - Production: pm2 start ecosystem.config.js --env production
 *   - Development: pm2 start ecosystem.config.js --env development
 * 
 * Note: Make sure to run 'npm run build' before starting in production mode
 */

module.exports = {
  apps: [
    {
      name: 'webadmin',
      script: 'npx',
      args: 'serve -s dist -l 5173',
      instances: 1,
      exec_mode: 'fork',
      env: {
        NODE_ENV: 'production',
        PORT: 5173
      },
      env_development: {
        NODE_ENV: 'development',
        PORT: 5173
      },
      // Auto restart on crash
      autorestart: true,
      // Watch for file changes (development only)
      watch: false,
      // Max memory before restart
      max_memory_restart: '500M',
      // Log files
      error_file: './logs/webadmin-error.log',
      out_file: './logs/webadmin-out.log',
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
      // Merge logs
      merge_logs: true,
      // Time to wait before considering app as started
      min_uptime: '10s',
      // Number of unstable restarts before stopping
      max_restarts: 10,
      // Time between restarts
      restart_delay: 4000
    },
    // Alternative: Development mode with Vite
    {
      name: 'webadmin-dev',
      script: 'npm',
      args: 'run dev:ip',
      instances: 1,
      exec_mode: 'fork',
      env: {
        NODE_ENV: 'development',
        PORT: 5173
      },
      autorestart: true,
      watch: false,
      max_memory_restart: '500M',
      error_file: './logs/webadmin-dev-error.log',
      out_file: './logs/webadmin-dev-out.log',
      log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
      merge_logs: true
    }
  ]
};

