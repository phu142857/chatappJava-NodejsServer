# Google Gemini AI Setup for Chat Summarization

## Overview
The chat summarization feature uses Google Gemini AI (free tier) to generate intelligent summaries of chat conversations. If the API key is not configured, the system will automatically fall back to a rule-based summarization method.

## Getting Your Free API Key

1. **Visit Google AI Studio**
   - Go to: https://makersuite.google.com/app/apikey
   - Sign in with your Google account

2. **Create API Key**
   - Click "Create API Key" button
   - Select or create a Google Cloud project (free tier is available)
   - Copy your API key

3. **Add to Environment Variables**
   - Add the following to your `.env` file:
   ```
   GEMINI_API_KEY=your_api_key_here
   ```

## Free Tier Limits

- **Free tier includes:**
  - 60 requests per minute
  - 1,500 requests per day
  - Sufficient for most chat applications

## Features

- ✅ **Intelligent Summarization**: AI understands context and creates meaningful summaries
- ✅ **Automatic Fallback**: If API key is missing, uses rule-based summarization
- ✅ **No Credit Card Required**: Free tier doesn't require payment
- ✅ **Privacy**: Messages are sent to Google's API for processing

## Testing

After adding the API key, restart your server. You should see:
```
✓ Gemini AI initialized for chat summarization
```

If the key is missing, you'll see:
```
⚠ Gemini AI not available - using fallback summarization
```

Both methods work, but AI summarization provides better quality summaries.

