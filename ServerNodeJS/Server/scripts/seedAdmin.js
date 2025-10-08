require('dotenv').config();
const mongoose = require('mongoose');
const connectDB = require('../config/database');
const User = require('../models/User');

async function run() {
  try {
    await connectDB();

    const email = process.env.ADMIN_EMAIL;
    const username = process.env.ADMIN_USERNAME;
    const password = process.env.ADMIN_PASSWORD;

    let existing = await User.findOne({ email });
    if (existing) {
      existing.role = 'admin';
      existing.isActive = true;
      await existing.save();
      console.log(`[OK] Existing user upgraded to admin: ${email}`);
    } else {
      const admin = new User({ username, email, password, role: 'admin', isActive: true });
      await admin.save();
      console.log(`[OK] Admin created: ${email}`);
    }

    await mongoose.connection.close();
    process.exit(0);
  } catch (err) {
    console.error('[ERROR] Seeding admin failed:', err);
    await mongoose.connection.close();
    process.exit(1);
  }
}

run();


