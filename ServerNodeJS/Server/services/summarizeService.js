const { GoogleGenerativeAI } = require('@google/generative-ai');
const { GoogleGenAI } = require('@google/genai');
const https = require('https');

let genAI = null;
let genAIv2 = null; // New @google/genai client
let model = null;
let apiKey = null;

/**
 * Initialize Gemini AI model
 */
async function listAvailableModels(genAI) {
  try {
    // Try to list models if the method exists
    if (typeof genAI.listModels === 'function') {
      const models = await genAI.listModels();
      
      if (models && Array.isArray(models)) {
        const modelNames = models.map(m => {
          // Handle different response formats
          if (typeof m === 'string') return m;
          if (m.name) return m.name;
          if (m.model) return m.model;
          return String(m);
        }).filter(Boolean);
        return modelNames;
      }
      
      // If it's an object with a data property
      if (models && models.data && Array.isArray(models.data)) {
        const modelNames = models.data.map(m => {
          if (typeof m === 'string') return m;
          if (m.name) return m.name;
          if (m.model) return m.model;
          return String(m);
        }).filter(Boolean);
        return modelNames;
      }
    }
    return null;
  } catch (error) {
    // listModels might not be available in this version or API key doesn't have permission
    console.log('   listModels error:', error.message?.substring(0, 100));
    return null;
  }
}

function initializeGemini() {
  apiKey = process.env.GEMINI_API_KEY;
  
  if (!apiKey) {
    console.warn('GEMINI_API_KEY not found. Summarization will use fallback method.');
    return false;
  }

  // Validate API key format (should start with AIza)
  if (!apiKey.startsWith('AIza')) {
    console.warn('⚠ GEMINI_API_KEY format looks invalid (should start with AIza). Summarization will use fallback method.');
    return false;
  }

  try {
    genAI = new GoogleGenerativeAI(apiKey);
    // Also initialize new @google/genai client
    genAIv2 = new GoogleGenAI({ apiKey });
    // Don't set model here - we'll try different models when generating
    // Also store apiKey globally for REST API fallback
    console.log('✓ Gemini AI initialized (will try new SDK, old SDK, then REST API if needed)');
    return true;
  } catch (error) {
    console.error('Failed to initialize Gemini AI:', error);
    return false;
  }
}

/**
 * Call Gemini API directly via REST (like N8N does)
 * This bypasses SDK issues with model names
 */
async function callGeminiREST(prompt) {
  return new Promise((resolve, reject) => {
    // Try different API versions and model names
    // Prioritize v1 over v1beta, and gemini-pro (most common free tier model)
    const endpoints = [
      { version: 'v1', model: 'gemini-pro' },
      { version: 'v1', model: 'models/gemini-pro' },
      { version: 'v1beta', model: 'gemini-pro' },
      { version: 'v1beta', model: 'models/gemini-pro' },
      { version: 'v1', model: 'gemini-1.5-flash' },
      { version: 'v1', model: 'models/gemini-1.5-flash' },
    ];

    let currentEndpoint = 0;

    const tryEndpoint = () => {
      if (currentEndpoint >= endpoints.length) {
        reject(new Error('All REST API endpoints failed'));
        return;
      }

      const { version, model } = endpoints[currentEndpoint];
      const url = `https://generativelanguage.googleapis.com/${version}/${model}:generateContent?key=${apiKey}`;

      const postData = JSON.stringify({
        contents: [{
          parts: [{
            text: prompt
          }]
        }]
      });

      const options = {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(postData)
        }
      };

      const req = https.request(url, options, (res) => {
        let data = '';

        res.on('data', (chunk) => {
          data += chunk;
        });

        res.on('end', () => {
          if (res.statusCode === 200) {
            try {
              const response = JSON.parse(data);
              if (response.candidates && response.candidates[0] && response.candidates[0].content) {
                const text = response.candidates[0].content.parts[0].text;
                console.log(`✓ Successfully used Gemini REST API (${version}/${model})`);
                resolve(text);
              } else {
                // Try next endpoint if response format is invalid
                console.log(`⚠ Invalid response format from ${version}/${model}, trying next...`);
                currentEndpoint++;
                tryEndpoint();
              }
            } catch (parseError) {
              console.log(`⚠ Parse error from ${version}/${model}: ${parseError.message}, trying next...`);
              currentEndpoint++;
              tryEndpoint();
            }
          } else if (res.statusCode === 404) {
            // Try next endpoint
            console.log(`⚠ Endpoint ${version}/${model} returned 404, trying next...`);
            currentEndpoint++;
            tryEndpoint();
          } else {
            // For other errors, log and try next
            console.log(`⚠ Endpoint ${version}/${model} returned ${res.statusCode}, trying next...`);
            if (currentEndpoint < endpoints.length - 1) {
              currentEndpoint++;
              tryEndpoint();
            } else {
              reject(new Error(`API returned status ${res.statusCode}: ${data.substring(0, 200)}`));
            }
          }
        });
      });

      req.on('error', (error) => {
        // Network error, try next endpoint
        console.log(`⚠ Network error with ${version}/${model}: ${error.message}, trying next...`);
        if (currentEndpoint < endpoints.length - 1) {
          currentEndpoint++;
          tryEndpoint();
        } else {
          reject(error);
        }
      });

      req.setTimeout(30000, () => {
        req.destroy();
        console.log(`⚠ Timeout with ${version}/${model}, trying next...`);
        if (currentEndpoint < endpoints.length - 1) {
          currentEndpoint++;
          tryEndpoint();
        } else {
          reject(new Error('Request timeout'));
        }
      });

      req.write(postData);
      req.end();
    };

    tryEndpoint();
  });
}

/**
 * Try new @google/genai SDK with gemini-2.5-flash
 */
async function tryNewGenAI(prompt) {
  if (!genAIv2) return null;
  
  try {
    const response = await genAIv2.models.generateContent({
      model: 'gemini-2.5-flash',
      contents: prompt,
    });
    
    // Check for text in response
    if (response && response.text) {
      console.log('✓ Successfully used @google/genai SDK with gemini-2.5-flash');
      return response.text;
    } else if (response && typeof response === 'string') {
      // Sometimes response is directly a string
      console.log('✓ Successfully used @google/genai SDK with gemini-2.5-flash');
      return response;
    }
  } catch (error) {
    const errorMsg = error.message || String(error);
    console.log(`⚠ New SDK (gemini-2.5-flash) failed: ${errorMsg.substring(0, 200)}`);
    // Log full error for debugging
    if (error.stack) {
      console.log(`   Stack: ${error.stack.substring(0, 300)}`);
    }
  }
  
  return null;
}

/**
 * Generate AI summary using Gemini
 */
async function generateAISummary(messages, chatContext = {}) {
  // Initialize if not already done
  if (!genAI && !genAIv2 && !initializeGemini()) {
    return null; // Fallback to rule-based
  }

  // Format messages for AI
  const formattedMessages = messages.map(msg => {
    const sender = msg.sender?.username || 'User';
    const content = msg.content || `[${msg.type} message]`;
    const time = new Date(msg.createdAt).toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit'
    });
    return `${sender} (${time}): ${content}`;
  }).join('\n');

  // Build prompt for professional conversation summary
  const prompt = `Bạn là một trợ lý tóm tắt hội thoại chuyên nghiệp và súc tích. Nhiệm vụ của bạn là phân tích đoạn hội thoại dưới đây và cung cấp một bản tóm tắt ngắn gọn nhưng đầy đủ thông tin.

**1. Vai trò:**

Hãy tập trung vào các điểm chính:

* **Ai là người nói?** (Các bên tham gia)

* **Chủ đề chính là gì?**

* **Những quyết định/hành động đã được chốt/thống nhất?**

* **Các chi tiết quan trọng (thời gian, địa điểm, con số) là gì?**

**2. Đoạn hội thoại cần tóm tắt:**

${formattedMessages}

${chatContext.type === 'group' ? `Đây là một nhóm chat với ${chatContext.participantCount || 'nhiều'} thành viên.` : 'Đây là một cuộc trò chuyện riêng tư giữa hai người.'}

**3. Định dạng đầu ra mong muốn:**

Vui lòng cung cấp tóm tắt của bạn theo cấu trúc sau, sử dụng ngôn ngữ tự nhiên:

**Tóm Tắt Hội Thoại:**

* **Các bên:** [Liệt kê tên những người tham gia]

* **Chủ đề:** [Nêu chủ đề bao quát của cuộc nói chuyện]

* **Nội dung Chính:** [Tóm tắt chi tiết các quyết định, đề xuất, hoặc thông tin quan trọng. Sử dụng bullet points (•) nếu có nhiều điểm. Nhóm các chủ đề liên quan lại với nhau.]

* **Hành động/Quyết định Đã Chốt:** [Liệt kê các việc cần làm, lịch hẹn, hoặc thỏa thuận đã được xác nhận. Sử dụng bullet points (•) cho mỗi hành động/quyết định.]

Lưu ý:
- Viết bằng tiếng Việt nếu cuộc hội thoại bằng tiếng Việt, ngược lại viết bằng tiếng Anh
- Giữ tóm tắt ngắn gọn nhưng đầy đủ thông tin
- Tập trung vào thông tin quan trọng và có thể hành động được
- Nếu có tin nhắn media (hình ảnh, file), đề cập trong phần Nội dung Chính
- Sử dụng ngôn ngữ tự nhiên, dễ hiểu

Hãy cung cấp tóm tắt theo đúng format trên:`;

  // First, try new @google/genai SDK with gemini-2.5-flash
  const newSDKResult = await tryNewGenAI(prompt);
  if (newSDKResult) {
    return newSDKResult.trim();
  }

  // Fallback to old SDK - Try different model names - prioritize free tier models
  // Free tier typically supports: gemini-pro (legacy) and gemini-1.5-flash
  // Note: Free tier has rate limits (e.g., 15 requests per minute for gemini-1.5-flash)
  let modelNames = [
    'gemini-1.5-flash',         // Flash model (free tier, fast) - RECOMMENDED for free tier
    'gemini-pro',                // Standard model (legacy, free tier)
    'gemini-1.5-flash-latest',  // Latest flash model
    'models/gemini-1.5-flash',  // With models/ prefix
    'models/gemini-pro',
  ];

  // Try to get available models (if API supports it)
  try {
    const availableModels = await listAvailableModels(genAI);
    if (availableModels && availableModels.length > 0) {
      console.log(`✓ Found ${availableModels.length} available models:`, availableModels.slice(0, 5).join(', '));
      // Prepend available models to the list (remove duplicates)
      const uniqueModels = [...new Set([...availableModels, ...modelNames])];
      modelNames = uniqueModels;
    } else {
      console.log('⚠ Could not list models, using default model names');
    }
  } catch (err) {
    // listModels not available, continue with default list
    console.log('⚠ Could not list models:', err.message?.substring(0, 100));
    console.log('   Using default model names');
  }

  for (const modelName of modelNames) {
    try {
      // Remove 'models/' prefix if present for getGenerativeModel
      const cleanModelName = modelName.replace(/^models\//, '');
      const currentModel = genAI.getGenerativeModel({ model: cleanModelName });
      
      // Try to generate content
      const result = await currentModel.generateContent(prompt);
      const response = await result.response;
      const summary = response.text();
      
      // Cache the working model for next time
      model = currentModel;
      console.log(`✓ Successfully used Gemini model: ${cleanModelName}`);
      
      return summary.trim();
    } catch (error) {
      // Log full error for debugging (especially for first model)
      const errorMsg = error.message || String(error);
      const errorCode = error.code || '';
      const errorStatus = error.status || error.response?.status || '';
      
      // Log detailed error for first model to help debug
      if (modelNames.indexOf(modelName) === 0) {
        console.log(`🔍 Debug - First model (${modelName}) error:`);
        console.log(`   Message: ${errorMsg.substring(0, 300)}`);
        console.log(`   Code: ${errorCode || 'N/A'}`);
        console.log(`   Status: ${errorStatus || 'N/A'}`);
        if (error.response) {
          console.log(`   Response: ${JSON.stringify(error.response).substring(0, 200)}`);
        }
      }
      
      // If this is a 404 (model not found), try next model
      if (errorMsg.includes('404') || errorMsg.includes('not found') || errorCode === '404' || errorCode === 404 || errorStatus === 404) {
        // Only log if it's not the last model
        if (modelNames.indexOf(modelName) < modelNames.length - 1) {
          console.log(`⚠ Model ${modelName} not available (404), trying next...`);
        }
        continue;
      }
      
      // For API key or permission errors
      if (errorMsg.includes('API key') || errorMsg.includes('permission') || errorMsg.includes('403') || errorCode === '403' || errorCode === 403 || errorStatus === 403) {
        console.error(`❌ API key issue with model ${modelName}: ${errorMsg.substring(0, 200)}`);
        // Don't continue if it's an API key issue - all models will fail
        break;
      }
      
      // For quota/rate limit errors
      if (errorMsg.includes('quota') || errorMsg.includes('rate limit') || errorMsg.includes('RESOURCE_EXHAUSTED')) {
        console.error(`❌ Rate limit exceeded for model ${modelName}. Please wait and try again.`);
        // Continue to try other models
        continue;
      }
      
      // For other errors, log and try next
      if (modelNames.indexOf(modelName) < modelNames.length - 1) {
        console.log(`⚠ Error with model ${modelName}: ${errorMsg.substring(0, 150)}`);
      }
      continue;
    }
  }

  // All SDK models failed, try REST API directly (like N8N does)
  console.log('⚠ SDK models failed, trying REST API directly...');
  try {
    const summary = await callGeminiREST(prompt);
    return summary.trim();
  } catch (restError) {
    console.error('❌ REST API also failed:', restError.message?.substring(0, 200));
  }

  // All methods failed
  console.error('❌ All Gemini API methods failed. Using fallback summarization.');
  console.error('   For FREE API key users:');
  console.error('   1. Ensure "Generative Language API" is enabled in Google Cloud Console');
  console.error('   2. Free tier supports: gemini-1.5-flash and gemini-pro');
  console.error('   3. Check rate limits (15 req/min for flash, 2 req/min for pro)');
  console.error('   4. Visit: https://makersuite.google.com/app/apikey to verify your API key');
  console.error('   5. Enable API: https://console.cloud.google.com/apis/library/generativelanguage.googleapis.com');
  return null; // Fallback to rule-based
}

/**
 * Fallback rule-based summarization (when AI is not available)
 */
function generateFallbackSummary(messages) {
  const textMessages = messages.filter(msg => msg.type === 'text' && msg.content);
  const mediaMessages = messages.filter(msg => 
    ['image', 'file', 'video', 'audio'].includes(msg.type)
  );

  if (textMessages.length === 0 && mediaMessages.length > 0) {
    return `• ${mediaMessages.length} media message${mediaMessages.length > 1 ? 's' : ''} shared`;
  }

  if (textMessages.length === 0) {
    return 'No new messages to summarize.';
  }

  // Get participant names
  const participants = [...new Set(textMessages.map(msg => msg.sender?.username || 'User'))];
  const participantList = participants.length === 1 
    ? participants[0] 
    : participants.slice(0, -1).join(', ') + ' và ' + participants[participants.length - 1];

  // Build summary in the new format
  let summary = `**Tóm Tắt Hội Thoại:**\n\n`;
  
  // Các bên
  summary += `* **Các bên:** ${participantList}\n\n`;

  // Group messages by sender to identify main topics
  const messagesBySender = {};
  textMessages.forEach(msg => {
    const senderName = msg.sender?.username || 'User';
    if (!messagesBySender[senderName]) {
      messagesBySender[senderName] = [];
    }
    messagesBySender[senderName].push({
      content: msg.content,
      timestamp: msg.createdAt
    });
  });

  // Chủ đề (simplified - based on message count and content)
  const totalMessages = textMessages.length;
  summary += `* **Chủ đề:** Cuộc trò chuyện giữa ${participantList} về các vấn đề khác nhau\n\n`;

  // Nội dung Chính
  summary += `* **Nội dung Chính:**\n`;
  
  Object.keys(messagesBySender).forEach(sender => {
    const senderMessages = messagesBySender[sender];
    const messageCount = senderMessages.length;
    
    if (messageCount === 1) {
      const content = senderMessages[0].content;
      const preview = content.length > 120 ? content.substring(0, 120) + '...' : content;
      summary += `  • ${sender}: ${preview}\n`;
    } else {
      // Show first 2 key messages
      const keyMessages = senderMessages.slice(0, Math.min(2, senderMessages.length));
      keyMessages.forEach(msg => {
        const text = msg.content;
        const preview = text.length > 100 ? text.substring(0, 100) + '...' : text;
        summary += `  • ${sender}: ${preview}\n`;
      });
      
      if (messageCount > 2) {
        summary += `  • ${sender}: ... và ${messageCount - 2} tin nhắn khác\n`;
      }
    }
  });

  // Add media messages if any
  if (mediaMessages.length > 0) {
    summary += `  • ${mediaMessages.length} tin nhắn media được chia sẻ\n`;
  }

  summary += `\n`;

  // Hành động/Quyết định Đã Chốt
  summary += `* **Hành động/Quyết định Đã Chốt:**\n`;
  
  // Try to identify action items from messages (simplified)
  const actionKeywords = ['hẹn', 'sẽ', 'cần', 'phải', 'nhớ', 'đừng quên', 'meet', 'meeting', 'schedule'];
  const actionMessages = textMessages.filter(msg => {
    const content = msg.content.toLowerCase();
    return actionKeywords.some(keyword => content.includes(keyword));
  });

  if (actionMessages.length > 0) {
    actionMessages.slice(0, 3).forEach(msg => {
      const preview = msg.content.length > 80 ? msg.content.substring(0, 80) + '...' : msg.content;
      summary += `  • ${preview}\n`;
    });
  } else {
    summary += `  • Không có hành động cụ thể nào được chốt trong đoạn hội thoại này\n`;
  }

  return summary.trim();
}

/**
 * Main function to summarize messages
 */
async function summarizeMessages(messages, chatContext = {}) {
  // Try AI first, fallback to rule-based if not available
  const aiSummary = await generateAISummary(messages, chatContext);
  
  if (aiSummary) {
    return aiSummary;
  }

  return generateFallbackSummary(messages);
}

module.exports = {
  summarizeMessages,
  initializeGemini
};

