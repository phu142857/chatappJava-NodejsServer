const os = require('os');
const fs = require('fs');
const path = require('path');
const si = require('systeminformation');
const User = require('../models/User');
const Message = require('../models/Message');
const Call = require('../models/Call');

// Cache for CPU usage calculation (for KVM VPS compatibility)
let lastCpuMeasure = null;
let lastCpuTime = null;
let lastCpuPercent = 0; // Store last calculated value for smoothing
let cpuHistory = []; // Moving average history
const MIN_INTERVAL_MS = 1000; // Minimum interval between calculations (1 second)
const SMOOTHING_FACTOR = 0.8; // Smoothing factor (0-1, higher = more smoothing)
const MAX_HISTORY = 5; // Maximum history for moving average

// Helper function to read CPU stats from /proc/stat (Linux/KVM VPS)
const readProcStat = () => {
  try {
    const statContent = fs.readFileSync('/proc/stat', 'utf8');
    const lines = statContent.split('\n');
    const cpuLine = lines[0]; // First line is overall CPU stats
    
    if (cpuLine && cpuLine.startsWith('cpu ')) {
      const parts = cpuLine.split(/\s+/);
      // Format: cpu user nice system idle iowait irq softirq steal guest guest_nice
      // Index:  0   1    2    3     4     5      6   7       8      9     10
      const user = parseInt(parts[1]) || 0;
      const nice = parseInt(parts[2]) || 0;
      const system = parseInt(parts[3]) || 0;
      const idle = parseInt(parts[4]) || 0;
      const iowait = parseInt(parts[5]) || 0;
      const irq = parseInt(parts[6]) || 0;
      const softirq = parseInt(parts[7]) || 0;
      const steal = parseInt(parts[8]) || 0;
      
      // Total CPU time = user + nice + system + idle + iowait + irq + softirq + steal
      const total = user + nice + system + idle + iowait + irq + softirq + steal;
      
      return { idle, total };
    }
  } catch (error) {
    // /proc/stat not available (not Linux or permission issue)
    return null;
  }
  return null;
};

// Helper function to apply smoothing to CPU usage
const applySmoothing = (rawCpuPercent) => {
  // Apply exponential smoothing
  let smoothedPercent = rawCpuPercent;
  if (lastCpuPercent > 0) {
    // Exponential smoothing: new = old * (1 - factor) + new * factor
    smoothedPercent = lastCpuPercent * (1 - SMOOTHING_FACTOR) + rawCpuPercent * SMOOTHING_FACTOR;
  }
  
  // Add to history for moving average
  cpuHistory.push(smoothedPercent);
  if (cpuHistory.length > MAX_HISTORY) {
    cpuHistory.shift(); // Remove oldest value
  }
  
  // Calculate moving average
  const movingAvg = cpuHistory.reduce((sum, val) => sum + val, 0) / cpuHistory.length;
  
  // Use weighted average: 70% moving average, 30% current smoothed value
  const finalCpuPercent = Math.round(movingAvg * 0.7 + smoothedPercent * 0.3);
  
  // Update last value
  lastCpuPercent = finalCpuPercent;
  
  return finalCpuPercent;
};

// Helper function to get CPU usage (works on KVM VPS)
const getCpuUsage = async () => {
  const currentTime = Date.now();
  
  // Enforce minimum interval to prevent rapid fluctuations
  if (lastCpuTime && (currentTime - lastCpuTime) < MIN_INTERVAL_MS) {
    // Return last smoothed value if called too soon
    return lastCpuPercent;
  }
  
  // Method 1: Try reading from /proc/stat (most reliable on Linux/KVM VPS)
  const procStat = readProcStat();
  if (procStat) {
    if (lastCpuMeasure && lastCpuTime) {
      const timeDiff = currentTime - lastCpuTime;
      
      // Need at least 200ms for accurate calculation
      if (timeDiff >= 200) {
        const idleDiff = procStat.idle - lastCpuMeasure.idle;
        const totalDiff = procStat.total - lastCpuMeasure.total;
        
        if (totalDiff > 0) {
          const rawCpuPercent = Math.min(100, Math.max(0, Math.round(100 - (idleDiff / totalDiff) * 100)));
          
          // Apply smoothing
          const smoothedPercent = applySmoothing(rawCpuPercent);
          
          // Update cache
          lastCpuMeasure = procStat;
          lastCpuTime = currentTime;
          
          return smoothedPercent;
        }
      } else {
        // Too soon, return last smoothed value
        return lastCpuPercent;
      }
    } else {
      // First measurement - initialize cache
      lastCpuMeasure = procStat;
      lastCpuTime = currentTime;
      return 0; // First call returns 0, next call will have value
    }
  }
  
  // Method 2: Try systeminformation
  try {
    const cpuData = await si.currentLoad();
    let rawPercent = 0;
    
    // Check for currentload field (most common)
    if (cpuData.currentload !== undefined && cpuData.currentload !== null && cpuData.currentload > 0) {
      rawPercent = Math.round(cpuData.currentload);
    }
    // Check for avgload field (alternative)
    else if (cpuData.avgload !== undefined && cpuData.avgload !== null && cpuData.avgload > 0) {
      rawPercent = Math.round(cpuData.avgload);
    }
    // Check for cpus array and calculate average
    else if (cpuData.cpus && Array.isArray(cpuData.cpus) && cpuData.cpus.length > 0) {
      const validCpus = cpuData.cpus.filter(cpu => cpu.load !== undefined && cpu.load !== null && cpu.load > 0);
      if (validCpus.length > 0) {
        const avgLoad = validCpus.reduce((sum, cpu) => sum + cpu.load, 0) / validCpus.length;
        if (avgLoad > 0) {
          rawPercent = Math.round(avgLoad);
        }
      }
    }
    
    if (rawPercent > 0) {
      // Apply smoothing
      const smoothedPercent = applySmoothing(rawPercent);
      lastCpuTime = currentTime;
      return smoothedPercent;
    }
  } catch (siError) {
    console.warn('systeminformation.currentLoad() failed:', siError.message);
  }
  
  // Method 3: Fallback - Calculate from os.cpus() with 2-sample comparison
  const cpus = os.cpus();
  const numCpus = cpus.length;
  
  if (!cpus || numCpus === 0) {
    return lastCpuPercent; // Return last value if no CPUs
  }
  
  // Calculate current CPU times
  let totalIdle = 0;
  let totalTick = 0;
  
  cpus.forEach((cpu) => {
    for (const type in cpu.times) {
      totalTick += cpu.times[type];
    }
    totalIdle += cpu.times.idle;
  });
  
  const idle = totalIdle / numCpus;
  const total = totalTick / numCpus;
  
  // If we have previous measurement, calculate usage
  if (lastCpuMeasure && lastCpuTime) {
    const timeDiff = currentTime - lastCpuTime;
    
    // Need at least 200ms for accurate calculation
    if (timeDiff >= 200) {
      const idleDiff = idle - lastCpuMeasure.idle;
      const totalDiff = total - lastCpuMeasure.total;
      
      if (totalDiff > 0) {
        const rawCpuPercent = Math.min(100, Math.max(0, Math.round(100 - (idleDiff / totalDiff) * 100)));
        
        // Apply smoothing
        const smoothedPercent = applySmoothing(rawCpuPercent);
        
        // Update cache
        lastCpuMeasure = { idle, total };
        lastCpuTime = currentTime;
        
        return smoothedPercent;
      }
    }
    
    // Too soon, return last smoothed value
    return lastCpuPercent;
  }
  
  // First measurement - initialize cache
  lastCpuMeasure = { idle, total };
  lastCpuTime = currentTime;
  
  return 0; // First call returns 0, next call will have value
};

// Initialize CPU cache on module load
(function initializeCpuCache() {
  // Try /proc/stat first (Linux/KVM VPS)
  const procStat = readProcStat();
  if (procStat) {
    lastCpuMeasure = procStat;
    lastCpuTime = Date.now();
    return;
  }
  
  // Fallback to os.cpus()
  const cpus = os.cpus();
  const numCpus = cpus.length;
  
  if (cpus && numCpus > 0) {
    let totalIdle = 0;
    let totalTick = 0;
    
    cpus.forEach((cpu) => {
      for (const type in cpu.times) {
        totalTick += cpu.times[type];
      }
      totalIdle += cpu.times.idle;
    });
    
    lastCpuMeasure = {
      idle: totalIdle / numCpus,
      total: totalTick / numCpus
    };
    lastCpuTime = Date.now();
  }
})();

// @desc    Get server health and metrics
// @route   GET /api/server/health
// @access  Private (Admin)
const getServerHealth = async (req, res) => {
  try {
    // Get CPU usage (works on KVM VPS)
    const cpuPercent = await getCpuUsage();

    // Get memory usage
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const usedMem = totalMem - freeMem;
    const memoryPercent = Math.round((usedMem / totalMem) * 100);

    // Get disk usage (simplified)
    const diskUsage = await getDiskUsage();
    const diskPercent = Math.round((diskUsage.used / diskUsage.total) * 100);

    // Get uptime
    const uptime = process.uptime();
    const uptimeString = formatUptime(uptime);

    // Check services status
    const services = {
      auth: true, // Assume auth service is running
      chat: true, // Assume chat service is running
      videoCall: true, // Assume video call service is running
      websocket: true // Assume websocket is running
    };

    res.json({
      success: true,
      data: {
        cpu: Math.min(cpuPercent, 100),
        memory: memoryPercent,
        disk: diskPercent,
        uptime: uptimeString,
        services
      }
    });

  } catch (error) {
    console.error('Get server health error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching health data'
    });
  }
};

// @desc    Get user statistics
// @route   GET /api/users/stats
// @access  Private (Admin)
const getUserStats = async (req, res) => {
  try {
    
    const total = await User.countDocuments();
    const active = await User.countDocuments({ isActive: true });
    
    // Online users (last seen within 5 minutes)
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
    const online = await User.countDocuments({ 
      lastSeen: { $gte: fiveMinutesAgo },
      isActive: true 
    });

    res.json({
      success: true,
      data: {
        total,
        active,
        online
      }
    });

  } catch (error) {
    console.error('Get user stats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching user statistics'
    });
  }
};

// @desc    Get message statistics
// @route   GET /api/messages/stats
// @access  Private (Admin)
const getMessageStats = async (req, res) => {
  try {
    
    const total = await Message.countDocuments();
    
    // Today's messages
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayCount = await Message.countDocuments({
      createdAt: { $gte: today }
    });
    
    // This week's messages
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const thisWeek = await Message.countDocuments({
      createdAt: { $gte: weekAgo }
    });

    res.json({
      success: true,
      data: {
        total,
        today: todayCount,
        thisWeek
      }
    });

  } catch (error) {
    console.error('Get message stats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching message statistics'
    });
  }
};

// @desc    Get call statistics
// @route   GET /api/calls/stats
// @access  Private (Admin)
const getCallStats = async (req, res) => {
  try {
    
    const total = await Call.countDocuments();
    
    // Today's calls
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayCount = await Call.countDocuments({
      startedAt: { $gte: today }
    });
    
    // Successful calls
    const successful = await Call.countDocuments({ status: 'completed' });
    
    // Failed calls
    const failed = await Call.countDocuments({ status: 'failed' });

    res.json({
      success: true,
      data: {
        total,
        today: todayCount,
        successful,
        failed
      }
    });

  } catch (error) {
    console.error('Get call stats error:', error);
    res.status(500).json({
      success: false,
      message: 'Server error while fetching call statistics'
    });
  }
};

// Helper function to get disk usage
const getDiskUsage = async () => {
  try {
    const stats = fs.statSync(process.cwd());
    // This is a simplified version - in production you'd use a proper disk usage library
    return {
      used: 1024 * 1024 * 1024, // 1GB (placeholder)
      total: 10 * 1024 * 1024 * 1024 // 10GB (placeholder)
    };
  } catch (error) {
    return {
      used: 0,
      total: 1
    };
  }
};

// Helper function to format uptime
const formatUptime = (seconds) => {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  
  return `${days}d ${hours}h ${minutes}m`;
};

module.exports = {
  getServerHealth,
  getUserStats,
  getMessageStats,
  getCallStats
};
