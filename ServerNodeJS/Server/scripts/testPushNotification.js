/**
 * Script to test push notifications
 * 
 * Usage:
 * node scripts/testPushNotification.js <userId> <fcmToken>
 * 
 * Or if user already has FCM token in database:
 * node scripts/testPushNotification.js <userId>
 */

require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');
const { sendNotification, sendNotificationToUser, initializeFirebase } = require('../services/fcmService');
const connectDB = require('../config/database');

async function testPushNotification() {
  try {
    // Connect to database
    await connectDB();
    console.log('✓ Connected to database');

    // Initialize Firebase
    initializeFirebase();
    console.log('✓ Firebase initialized');

    // Get arguments
    const args = process.argv.slice(2);
    if (args.length < 1) {
      console.error('Usage: node scripts/testPushNotification.js <userId> [fcmToken]');
      console.error('Example: node scripts/testPushNotification.js 507f1f77bcf86cd799439011');
      process.exit(1);
    }

    const userId = args[0];
    const fcmToken = args[1]; // Optional

    // Find user
    const user = await User.findById(userId);
    if (!user) {
      console.error(`✗ User not found: ${userId}`);
      process.exit(1);
    }

    console.log(`✓ Found user: ${user.username} (${user.email})`);

    // If FCM token provided, add it to user
    if (fcmToken) {
      await user.addFCMToken(fcmToken, null, 'android');
      console.log(`✓ Added FCM token to user`);
    }

    // Check if user has FCM tokens
    const tokens = user.fcmTokens || [];
    if (tokens.length === 0) {
      console.error('✗ User has no FCM tokens registered');
      console.error('  Please register FCM token first by:');
      console.error('  1. Login to Android app');
      console.error('  2. Or provide FCM token as second argument');
      process.exit(1);
    }

    console.log(`✓ User has ${tokens.length} FCM token(s)`);

    // Test 1: Send simple notification
    console.log('\n📱 Test 1: Sending simple notification...');
    const result1 = await sendNotificationToUser(
      user,
      'Test Notification',
      'This is a test push notification from server!',
      {
        type: 'test',
        message: 'Hello from test script'
      }
    );

    if (result1.success) {
      console.log('✓ Notification sent successfully!');
      if (result1.successCount) {
        console.log(`  Success: ${result1.successCount} device(s)`);
      }
      if (result1.failureCount > 0) {
        console.log(`  Failed: ${result1.failureCount} device(s)`);
      }
    } else {
      console.error('✗ Failed to send notification:', result1.error);
    }

    // Test 2: Send notification to specific token
    if (tokens.length > 0) {
      console.log('\n📱 Test 2: Sending to first token...');
      const firstToken = tokens[0].token;
      const result2 = await sendNotification(
        firstToken,
        'Direct Token Test',
        'This notification was sent directly to your FCM token',
        {
          type: 'test',
          testId: 'direct-token-test'
        }
      );

      if (result2.success) {
        console.log('✓ Direct token notification sent!');
        console.log(`  Message ID: ${result2.messageId}`);
      } else {
        console.error('✗ Failed:', result2.error);
        if (result2.code === 'messaging/invalid-registration-token') {
          console.error('  Token is invalid. User needs to re-register FCM token.');
        }
      }
    }

    console.log('\n✅ Test completed!');
    console.log('\nNext steps:');
    console.log('1. Check your Android device for notifications');
    console.log('2. If no notification received, check:');
    console.log('   - App is installed and logged in');
    console.log('   - FCM token is registered (check database)');
    console.log('   - Device has internet connection');
    console.log('   - Notification permissions are granted');

    process.exit(0);
  } catch (error) {
    console.error('✗ Error:', error);
    process.exit(1);
  }
}

// Run test
testPushNotification();

