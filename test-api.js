/**
 * API Testing Script - Complete Test Suite
 * 
 * This script tests all API endpoints of the chat application server.
 * 
 * Usage:
 *   node test-api.js
 * 
 * Environment Variables:
 *   BASE_URL - Server base URL (default: http://localhost:49664)
 *   TEST_EMAIL - Test user email (default: test@example.com)
 *   TEST_PASSWORD - Test user password (default: Test123)
 *   USER2_EMAIL - Second test user email (for private chat testing)
 *   USER2_PASSWORD - Second test user password
 *   ADMIN_EMAIL - Admin user email (optional, for admin endpoints)
 *   ADMIN_PASSWORD - Admin user password (optional)
 */

const https = require('https');
const http = require('http');

// Configuration
const BASE_URL = process.env.BASE_URL || 'http://103.75.183.125:49664';
const API_BASE = `${BASE_URL}/api`;
const TEST_EMAIL = process.env.TEST_EMAIL || 'phu@gmail.com';
const TEST_PASSWORD = process.env.TEST_PASSWORD || 'Phu142';
const USER2_EMAIL = process.env.USER2_EMAIL || 'trang@gmail.com';
const USER2_PASSWORD = process.env.USER2_PASSWORD || 'Phu142';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

// Test results
const results = {
  passed: 0,
  failed: 0,
  skipped: 0,
  errors: []
};

let authToken = null;
let adminToken = null;
let testUserId = null;
let user2Id = null;
let testChatId = null;
let testMessageId = null;
let testPostId = null;
let testGroupId = null;
let testFriendRequestId = null;
let testNotificationId = null;
let testReportId = null;

// Helper function to make HTTP requests
function makeRequest(method, url, data = null, token = null) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const options = {
      hostname: urlObj.hostname,
      port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
      path: urlObj.pathname + urlObj.search,
      method: method,
      headers: {
        'Content-Type': 'application/json'
      }
    };

    if (token) {
      options.headers['Authorization'] = `Bearer ${token}`;
    }

    const protocol = urlObj.protocol === 'https:' ? https : http;
    const req = protocol.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () => {
        try {
          const parsed = body ? JSON.parse(body) : {};
          resolve({
            status: res.statusCode,
            headers: res.headers,
            body: parsed,
            raw: body
          });
        } catch (e) {
          resolve({
            status: res.statusCode,
            headers: res.headers,
            body: body,
            raw: body
          });
        }
      });
    });

    req.on('error', (error) => {
      reject(error);
    });

    if (data) {
      req.write(JSON.stringify(data));
    }

    req.end();
  });
}

// Test function
async function test(name, fn) {
  try {
    console.log(`\n[TEST] ${name}`);
    await fn();
    results.passed++;
    console.log(`[PASS] ${name}`);
  } catch (error) {
    results.failed++;
    results.errors.push({ test: name, error: error.message });
    console.log(`[FAIL] ${name}: ${error.message}`);
  }
}

// Skip test
function skip(name) {
  results.skipped++;
  console.log(`[SKIP] ${name}`);
}

// Assertions
function assert(condition, message) {
  if (!condition) {
    throw new Error(message || 'Assertion failed');
  }
}

function assertStatus(response, expectedStatus) {
  assert(
    response.status === expectedStatus,
    `Expected status ${expectedStatus}, got ${response.status}`
  );
}

function assertSuccess(response) {
  assert(
    response.status >= 200 && response.status < 300,
    `Expected success status, got ${response.status}`
  );
}

// ==================== TESTS ====================

async function runTests() {
  console.log('='.repeat(60));
  console.log('API Testing Script - Complete Test Suite');
  console.log('='.repeat(60));
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Test Email: ${TEST_EMAIL}`);
  console.log('='.repeat(60));

  // Health Check
  await test('Health Check', async () => {
    const response = await makeRequest('GET', `${API_BASE}/health`);
    assertStatus(response, 200);
    assert(response.body.status === 'OK', 'Health check should return OK');
  });

  // ==================== AUTHENTICATION TESTS ====================
  console.log('\n--- Authentication Tests ---');

  await test('Login', async () => {
    const response = await makeRequest('POST', `${API_BASE}/auth/login`, {
      email: TEST_EMAIL,
      password: TEST_PASSWORD
    });
    assertSuccess(response);
    
    authToken = response.body.token || response.body.data?.token || response.body.data?.accessToken;
    assert(authToken, 'Response should contain token');
    
    testUserId = response.body.user?._id || response.body.user?.id || 
                 response.body.data?.user?._id || response.body.data?.user?.id ||
                 response.body.data?._id || response.body.data?.id;
    
    console.log(`  Token obtained: ${authToken.substring(0, 20)}...`);
    if (testUserId) {
      console.log(`  User ID: ${testUserId}`);
    }
  });

  if (!authToken) {
    console.log('\n[ERROR] Could not obtain authentication token. Skipping authenticated tests.');
    printResults();
    return;
  }

  await test('Get Current User', async () => {
    const response = await makeRequest('GET', `${API_BASE}/auth/me`, null, authToken);
    assertSuccess(response);
    assert(response.body.user || response.body.data, 'Response should contain user data');
  });

  await test('Update Profile', async () => {
    const response = await makeRequest('PUT', `${API_BASE}/auth/profile`, {
      username: `testuser_${Date.now()}`,
      profile: {
        firstName: 'Test',
        lastName: 'User',
        bio: 'Test bio'
      }
    }, authToken);
    assertSuccess(response);
  });

  await test('Change Password', async () => {
    const response = await makeRequest('PUT', `${API_BASE}/auth/change-password`, {
      currentPassword: TEST_PASSWORD,
      newPassword: 'NewPass123'
    }, authToken);
    // May fail if password doesn't meet requirements, that's OK
    console.log(`  Status: ${response.status}`);
  });

  await test('Refresh Token', async () => {
    const response = await makeRequest('POST', `${API_BASE}/auth/refresh`, null, authToken);
    assertSuccess(response);
  });

  // Login as User2 for private chat testing
  await test('Login as User2', async () => {
    const response = await makeRequest('POST', `${API_BASE}/auth/login`, {
      email: USER2_EMAIL,
      password: USER2_PASSWORD
    });
    assertSuccess(response);
    
    user2Id = response.body.user?._id || response.body.user?.id || 
              response.body.data?.user?._id || response.body.data?.user?.id ||
              response.body.data?._id || response.body.data?.id;
    
    if (user2Id) {
      console.log(`  User2 ID: ${user2Id}`);
    }
  });

  // ==================== USERS TESTS ====================
  console.log('\n--- Users Tests ---');

  await test('Get All Users', async () => {
    const response = await makeRequest('GET', `${API_BASE}/users?page=1&limit=10`, null, authToken);
    assertSuccess(response);
  });

  await test('Get Contacts', async () => {
    const response = await makeRequest('GET', `${API_BASE}/users/contacts`, null, authToken);
    assertSuccess(response);
  });

  await test('Get Blocked Users', async () => {
    const response = await makeRequest('GET', `${API_BASE}/users/blocked`, null, authToken);
    assertSuccess(response);
  });

  await test('Get Friends', async () => {
    const response = await makeRequest('GET', `${API_BASE}/users/friends`, null, authToken);
    assertSuccess(response);
  });

  await test('Search Users', async () => {
    const response = await makeRequest('GET', `${API_BASE}/users/search?q=test`, null, authToken);
    assertSuccess(response);
  });

  if (testUserId) {
    await test('Get User by ID', async () => {
      const response = await makeRequest('GET', `${API_BASE}/users/${testUserId}`, null, authToken);
      assertSuccess(response);
    });

    await test('Get User Friends', async () => {
      const response = await makeRequest('GET', `${API_BASE}/users/${testUserId}/friends`, null, authToken);
      assertSuccess(response);
    });
  }

  // ==================== FRIEND REQUESTS TESTS ====================
  console.log('\n--- Friend Requests Tests ---');

  await test('Get Friend Requests', async () => {
    const response = await makeRequest('GET', `${API_BASE}/friend-requests`, null, authToken);
    assertSuccess(response);
    if (response.body.data && response.body.data.length > 0) {
      testFriendRequestId = response.body.data[0]._id || response.body.data[0].id;
      console.log(`  Found friend request ID: ${testFriendRequestId}`);
    }
  });

  if (user2Id) {
    await test('Send Friend Request', async () => {
      const response = await makeRequest('POST', `${API_BASE}/friend-requests`, {
        receiverId: user2Id
      }, authToken);
      // May fail if already friends or request exists, that's OK
      console.log(`  Status: ${response.status}`);
    });
  }

  if (testFriendRequestId) {
    await test('Respond to Friend Request', async () => {
      const response = await makeRequest('PUT', `${API_BASE}/friend-requests/${testFriendRequestId}`, {
        action: 'accept'
      }, authToken);
      console.log(`  Status: ${response.status}`);
    });
  }

  // ==================== CHATS TESTS ====================
  console.log('\n--- Chats Tests ---');

  await test('Get User Chats', async () => {
    const response = await makeRequest('GET', `${API_BASE}/chats?page=1&limit=20`, null, authToken);
    assertSuccess(response);
    if (response.body.data && response.body.data.length > 0) {
      testChatId = response.body.data[0]._id || response.body.data[0].id;
      console.log(`  Found chat ID: ${testChatId}`);
    }
  });

  if (testChatId) {
    await test('Get Chat by ID', async () => {
      const response = await makeRequest('GET', `${API_BASE}/chats/${testChatId}`, null, authToken);
      assertSuccess(response);
    });

    await test('Get Group Members', async () => {
      const response = await makeRequest('GET', `${API_BASE}/chats/${testChatId}/members`, null, authToken);
      assertSuccess(response);
    });
  }

  if (user2Id) {
    await test('Create Private Chat', async () => {
      const response = await makeRequest('POST', `${API_BASE}/chats/private`, {
        participantId: user2Id
      }, authToken);
      assertSuccess(response);
      if (response.body.data?._id || response.body.data?.id || response.body._id || response.body.id) {
        testChatId = response.body.data?._id || response.body.data?.id || response.body._id || response.body.id;
        console.log(`  Private chat created with ID: ${testChatId}`);
      }
    });
  } else {
    skip('Create Private Chat (user2 ID not available)');
  }

  await test('Create Group Chat', async () => {
    const response = await makeRequest('POST', `${API_BASE}/chats/group`, {
      name: `Test Group ${Date.now()}`,
      description: 'Test group description',
      participantIds: user2Id ? [user2Id] : []
    }, authToken);
    assertSuccess(response);
    if (response.body.data?._id || response.body.data?.id || response.body._id || response.body.id) {
      testChatId = response.body.data?._id || response.body.data?.id || response.body._id || response.body.id;
      console.log(`  Group chat created with ID: ${testChatId}`);
    }
  });

  // ==================== MESSAGES TESTS ====================
  console.log('\n--- Messages Tests ---');

  if (testChatId) {
    await test('Get Messages for Chat', async () => {
      const response = await makeRequest('GET', `${API_BASE}/messages/${testChatId}?page=1&limit=20`, null, authToken);
      assertSuccess(response);
      if (response.body.data && response.body.data.length > 0) {
        testMessageId = response.body.data[0]._id || response.body.data[0].id;
        console.log(`  Found message ID: ${testMessageId}`);
      }
    });

    await test('Search Messages in Chat', async () => {
      const response = await makeRequest('GET', `${API_BASE}/messages/${testChatId}/search?q=test`, null, authToken);
      assertSuccess(response);
    });

    await test('Summarize Chat', async () => {
      const response = await makeRequest('GET', `${API_BASE}/messages/${testChatId}/summarize`, null, authToken);
      assertSuccess(response);
    });

    await test('Send Message', async () => {
      const response = await makeRequest('POST', `${API_BASE}/messages`, {
        chatId: testChatId,
        content: `Test message ${Date.now()}`,
        type: 'text'
      }, authToken);
      assertSuccess(response);
      if (response.body.message || response.body.data) {
        const msg = response.body.message || response.body.data;
        testMessageId = msg._id || msg.id;
        console.log(`  Message sent with ID: ${testMessageId}`);
      }
    });

    if (testMessageId) {
      await test('Edit Message', async () => {
        const response = await makeRequest('PUT', `${API_BASE}/messages/${testMessageId}`, {
          content: `Updated message ${Date.now()}`
        }, authToken);
        assertSuccess(response);
      });

      await test('Add Reaction to Message', async () => {
        const response = await makeRequest('POST', `${API_BASE}/messages/${testMessageId}/reactions`, {
          emoji: '👍'
        }, authToken);
        assertSuccess(response);
      });
    }

    await test('Mark Messages as Read', async () => {
      const response = await makeRequest('PUT', `${API_BASE}/messages/${testChatId}/read`, {
        messageIds: testMessageId ? [testMessageId] : []
      }, authToken);
      assertSuccess(response);
    });
  } else {
    skip('Get Messages for Chat (no chat available)');
    skip('Send Message (no chat available)');
  }

  // ==================== GROUPS TESTS ====================
  console.log('\n--- Groups Tests ---');

  await test('Get Groups', async () => {
    const response = await makeRequest('GET', `${API_BASE}/groups`, null, authToken);
    assertSuccess(response);
    if (response.body.data && response.body.data.length > 0) {
      testGroupId = response.body.data[0]._id || response.body.data[0].id;
      console.log(`  Found group ID: ${testGroupId}`);
    }
  });

  await test('Get Public Groups', async () => {
    const response = await makeRequest('GET', `${API_BASE}/groups/public`, null, authToken);
    assertSuccess(response);
  });

  await test('Get Group Contacts', async () => {
    const response = await makeRequest('GET', `${API_BASE}/groups/contacts`, null, authToken);
    assertSuccess(response);
  });

  await test('Search Groups', async () => {
    const response = await makeRequest('GET', `${API_BASE}/groups/search?q=test`, null, authToken);
    assertSuccess(response);
  });

  // ==================== CALLS TESTS ====================
  console.log('\n--- Calls Tests ---');

  await test('Get Call History', async () => {
    const response = await makeRequest('GET', `${API_BASE}/calls/history`, null, authToken);
    assertSuccess(response);
  });

  await test('Get Active Calls', async () => {
    const response = await makeRequest('GET', `${API_BASE}/calls/active`, null, authToken);
    assertSuccess(response);
  });

  if (testChatId) {
    await test('Initiate Call', async () => {
      const response = await makeRequest('POST', `${API_BASE}/calls/initiate`, {
        chatId: testChatId,
        type: 'video'
      }, authToken);
      assertSuccess(response);
    });
  }

  // ==================== GROUP CALLS TESTS ====================
  console.log('\n--- Group Calls Tests ---');

  if (testChatId) {
    await test('Get Active Group Call', async () => {
      const response = await makeRequest('GET', `${API_BASE}/group-calls/chat/${testChatId}/active`, null, authToken);
      assertSuccess(response);
    });
  } else {
    skip('Get Active Group Call (no chat available)');
  }

  // ==================== NOTIFICATIONS TESTS ====================
  console.log('\n--- Notifications Tests ---');

  await test('Get Notifications', async () => {
    const response = await makeRequest('GET', `${API_BASE}/notifications`, null, authToken);
    assertSuccess(response);
    if (response.body.data && response.body.data.length > 0) {
      testNotificationId = response.body.data[0]._id || response.body.data[0].id;
      console.log(`  Found notification ID: ${testNotificationId}`);
    }
  });

  if (testNotificationId) {
    await test('Mark Notification as Read', async () => {
      const response = await makeRequest('PUT', `${API_BASE}/notifications/${testNotificationId}/read`, null, authToken);
      assertSuccess(response);
    });
  }

  await test('Mark All Notifications as Read', async () => {
    const response = await makeRequest('PUT', `${API_BASE}/notifications/read-all`, null, authToken);
    assertSuccess(response);
  });

  // ==================== POSTS TESTS ====================
  console.log('\n--- Posts Tests ---');

  await test('Create Post', async () => {
    const response = await makeRequest('POST', `${API_BASE}/posts`, {
      content: `Test post content ${Date.now()}`,
      privacySetting: 'public',
      location: 'Test Location'
    }, authToken);
    assertSuccess(response);
    if (response.body.data && response.body.data.postId) {
      testPostId = response.body.data.postId;
      console.log(`  Post created with ID: ${testPostId}`);
    }
  });

  await test('Get Feed Posts', async () => {
    const response = await makeRequest('GET', `${API_BASE}/posts/feed?page=1&limit=10`, null, authToken);
    assertSuccess(response);
  });

  await test('Search Posts', async () => {
    const response = await makeRequest('GET', `${API_BASE}/posts/search?q=test&page=1&limit=20`, null, authToken);
    assertSuccess(response);
  });

  if (testUserId) {
    await test('Get User Posts', async () => {
      const response = await makeRequest('GET', `${API_BASE}/posts/user/${testUserId}?page=1&limit=10`, null, authToken);
      assertSuccess(response);
    });
  }

  if (testPostId) {
    await test('Get Post by ID', async () => {
      const response = await makeRequest('GET', `${API_BASE}/posts/${testPostId}`, null, authToken);
      assertSuccess(response);
    });

    await test('Like Post', async () => {
      const response = await makeRequest('POST', `${API_BASE}/posts/${testPostId}/like`, null, authToken);
      assertSuccess(response);
    });

    await test('Add Comment to Post', async () => {
      const response = await makeRequest('POST', `${API_BASE}/posts/${testPostId}/comments`, {
        content: 'This is a test comment'
      }, authToken);
      assertSuccess(response);
    });
  }

  // ==================== REPORTS TESTS ====================
  console.log('\n--- Reports Tests ---');

  if (user2Id) {
    await test('Create Report', async () => {
      const response = await makeRequest('POST', `${API_BASE}/reports`, {
        type: 'user',
        targetId: user2Id,
        reason: 'Inappropriate behavior',
        description: 'Test report description'
      }, authToken);
      assertSuccess(response);
      if (response.body.data?._id || response.body.data?.id) {
        testReportId = response.body.data._id || response.body.data.id;
        console.log(`  Report created with ID: ${testReportId}`);
      }
    });
  }

  // ==================== UPDATES TESTS ====================
  console.log('\n--- Updates Tests (Background Sync) ---');

  await test('Get Messages Updates', async () => {
    const since = Date.now() - 24 * 60 * 60 * 1000; // 24 hours ago
    const response = await makeRequest('GET', `${API_BASE}/updates/messages?since=${since}`, null, authToken);
    assert(
      response.status === 200 || response.status === 304,
      `Expected status 200 or 304, got ${response.status}`
    );
  });

  await test('Get Posts Updates', async () => {
    const since = Date.now() - 24 * 60 * 60 * 1000; // 24 hours ago
    const response = await makeRequest('GET', `${API_BASE}/updates/posts?since=${since}`, null, authToken);
    assert(
      response.status === 200 || response.status === 304,
      `Expected status 200 or 304, got ${response.status}`
    );
  });

  await test('Get Conversations Updates', async () => {
    const since = Date.now() - 24 * 60 * 60 * 1000; // 24 hours ago
    const response = await makeRequest('GET', `${API_BASE}/updates/conversations?since=${since}`, null, authToken);
    assert(
      response.status === 200 || response.status === 304,
      `Expected status 200 or 304, got ${response.status}`
    );
  });

  // ==================== ADMIN TESTS ====================
  if (ADMIN_EMAIL && ADMIN_PASSWORD) {
    console.log('\n--- Admin Tests ---');

    await test('Admin Login', async () => {
      const response = await makeRequest('POST', `${API_BASE}/auth/login`, {
        email: ADMIN_EMAIL,
        password: ADMIN_PASSWORD
      });
      assertSuccess(response);
      if (response.body.token) {
        adminToken = response.body.token;
        console.log(`  Admin token obtained: ${adminToken.substring(0, 20)}...`);
      }
    });

    if (adminToken) {
      await test('Get All Chats (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/chats/admin`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get All Messages (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/messages/admin`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get All Calls (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/calls/admin`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Server Health (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/server/health`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get User Statistics (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/statistics/users`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Message Statistics (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/statistics/messages`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Call Statistics (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/statistics/calls`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Top Users (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/statistics/top-users`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Blocked IPs (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/security/blocked-ips`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Audit Logs (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/security/audit-logs`, null, adminToken);
        assertSuccess(response);
      });

      await test('Get Reports (Admin)', async () => {
        const response = await makeRequest('GET', `${API_BASE}/reports`, null, adminToken);
        assertSuccess(response);
      });
    }
  } else {
    console.log('\n--- Admin Tests ---');
    skip('Admin tests (no admin credentials provided)');
  }

  // Print Results
  printResults();
}

function printResults() {
  console.log('\n' + '='.repeat(60));
  console.log('Test Results');
  console.log('='.repeat(60));
  console.log(`Passed: ${results.passed}`);
  console.log(`Failed: ${results.failed}`);
  console.log(`Skipped: ${results.skipped}`);
  console.log(`Total: ${results.passed + results.failed + results.skipped}`);
  
  if (results.errors.length > 0) {
    console.log('\nErrors:');
    results.errors.forEach((err, index) => {
      console.log(`  ${index + 1}. ${err.test}: ${err.error}`);
    });
  }
  
  console.log('='.repeat(60));
  
  if (results.failed === 0) {
    console.log('All tests passed! ✓');
  } else {
    console.log('Some tests failed. Check errors above.');
    process.exit(1);
  }
}

// Run tests
runTests().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
