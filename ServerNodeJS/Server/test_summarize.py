#!/usr/bin/env python3
"""
Test script for chat summarization feature
Tests the summarize API with sample chat messages
"""

import requests
import json
from datetime import datetime, timedelta

# Server configuration
BASE_URL = "http://localhost:49664"  # Change if your server runs on different port
API_ENDPOINT = f"{BASE_URL}/api/messages"

# Test users
USER1 = {
    "email": "nguyentaiphu980@gmail.com",
    "password": "Phu142"
}

USER2 = {
    "email": "trang@gmail.com", 
    "password": "Phu142"
}

# Sample chat messages (one-way from An to Binh)
SAMPLE_MESSAGES = [
    {
        "sender": "An",
        "content": "Hey Bình, xác nhận lịch hẹn Chủ Nhật 3h chiều nha!",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=5)
    },
    {
        "sender": "An",
        "content": "Sớm thế! Cậu là fan cứng của phim hành động à? Xem xong nhớ review cho tớ nha.",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=4, minutes=30)
    },
    {
        "sender": "An",
        "content": "Tớ tìm trên Maps xem trước.",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=4)
    },
    {
        "sender": "An",
        "content": "[Quán Đọc Sách Yên Tĩnh] - Nghe nói chỗ đó có món trà cam quế rất ngon! 🤤",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=3, minutes=30)
    },
    {
        "sender": "An",
        "content": "Đúng rồi! Cà phê/ trà ở đó ngon mà không gian lại yên tĩnh, rất hợp để \"sạc pin\" cuối tuần.",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=3)
    },
    {
        "sender": "An",
        "content": "Ừ, tớ đang tưởng tượng cảnh ngồi đọc sách dưới ánh nắng chiều... Thư thái thật!",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=2, minutes=30)
    },
    {
        "sender": "Bình",
        "content": "cậu đây!",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=2)
    },
    {
        "sender": "An",
        "content": "Haha, cám ơn nha! Dọn xong là thấy nhẹ nhõm liền!",
        "type": "text",
        "timestamp": datetime.now() - timedelta(hours=1, minutes=30)
    }
]


def login_user(email, password):
    """Login and get JWT token and user info"""
    try:
        response = requests.post(
            f"{BASE_URL}/api/auth/login",
            json={"email": email, "password": password},
            headers={"Content-Type": "application/json"}
        )
        
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                data_obj = data.get("data", {})
                user_data = data_obj.get("user", {})
                token = data_obj.get("token")
                
                if not token:
                    print(f"❌ No token in response: {response.text}")
                    return None
                
                username = user_data.get("username", "Unknown")
                user_id = user_data.get("_id", "")
                
                if not user_id:
                    print(f"❌ No user ID in response: {response.text}")
                    return None
                
                return {
                    "token": token,
                    "username": username,
                    "user_id": user_id,
                    "email": email
                }
        
        print(f"❌ Login failed: {response.status_code}")
        print(f"   Response: {response.text}")
        return None
    except Exception as e:
        print(f"❌ Login error: {e}")
        import traceback
        traceback.print_exc()
        return None


def create_chat(token, participant_id):
    """Create a private chat"""
    try:
        response = requests.post(
            f"{BASE_URL}/api/chats/private",
            json={"participantId": participant_id},
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}"
            }
        )
        
        if response.status_code == 200 or response.status_code == 201:
            data = response.json()
            if data.get("success"):
                chat_data = data.get("data", {}).get("chat", {})
                # Handle both object and dict
                if isinstance(chat_data, dict):
                    chat_id = chat_data.get("_id") or chat_data.get("id")
                else:
                    chat_id = str(chat_data) if chat_data else None
                return chat_id
        print(f"❌ Create chat failed: {response.status_code}")
        print(f"   Response: {response.text}")
        return None
    except Exception as e:
        print(f"❌ Create chat error: {e}")
        import traceback
        traceback.print_exc()
        return None


def send_message(token, chat_id, content, sender_name):
    """Send a message to chat"""
    try:
        response = requests.post(
            f"{BASE_URL}/api/messages",
            json={
                "chatId": chat_id,
                "content": content,
                "type": "text"
            },
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}"
            }
        )
        
        if response.status_code == 200 or response.status_code == 201:
            data = response.json()
            if data.get("success"):
                print(f"✓ Sent: {sender_name}: {content[:50]}...")
                return True
        print(f"Send message failed: {response.status_code}")
        return False
    except Exception as e:
        print(f"Send message error: {e}")
        return False


def summarize_chat(token, chat_id):
    """Call summarize API"""
    try:
        response = requests.get(
            f"{BASE_URL}/api/messages/{chat_id}/summarize",
            headers={
                "Authorization": f"Bearer {token}"
            }
        )
        
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return data.get("data", {})
        print(f"Summarize failed: {response.status_code} - {response.text}")
        return None
    except Exception as e:
        print(f"Summarize error: {e}")
        return None


def test_summarization():
    """Main test function"""
    print("=" * 60)
    print("Testing Chat Summarization Feature")
    print("=" * 60)
    
    # Step 1: Login users
    print("\n[1] Logging in users...")
    user1_info = login_user(USER1["email"], USER1["password"])
    user2_info = login_user(USER2["email"], USER2["password"])
    
    if not user1_info or not user2_info:
        print("❌ Failed to login users. Make sure users exist in database.")
        print("   You may need to register users first or use existing accounts.")
        return
    
    token1 = user1_info["token"]
    token2 = user2_info["token"]
    user1_id = user1_info["user_id"]
    user2_id = user2_info["user_id"]
    username1 = user1_info["username"]
    username2 = user2_info["username"]
    
    print(f"✓ User1 logged in: {username1} (ID: {user1_id})")
    print(f"✓ User2 logged in: {username2} (ID: {user2_id})")
    
    # Step 3: Create chat
    print("\n[3] Creating private chat...")
    chat_id = create_chat(token1, user2_id)
    if not chat_id:
        print("❌ Failed to create chat")
        return
    print(f"✓ Chat created: {chat_id}")
    
    # Step 4: Send sample messages
    print("\n[4] Sending sample messages...")
    import time
    for msg in SAMPLE_MESSAGES:
        # Use token1 for An, token2 for Binh
        token = token1 if msg["sender"] == "An" else token2
        send_message(token, chat_id, msg["content"], msg["sender"])
        time.sleep(0.5)  # Small delay between messages
    
    print(f"\n✓ Sent {len(SAMPLE_MESSAGES)} messages")
    
    # Step 4: Wait a bit for messages to be processed
    print("\n[4] Waiting for messages to be processed...")
    time.sleep(2)
    
    # Step 5: Test summarization
    print("\n[5] Testing summarization...")
    print("-" * 60)
    summary_data = summarize_chat(token1, chat_id)
    
    if summary_data:
        print("\n" + "=" * 60)
        print("SUMMARY RESULT:")
        print("=" * 60)
        print(f"\nMessage Count: {summary_data.get('message_count', 'N/A')}")
        print(f"\nSummary:\n{summary_data.get('summary', 'N/A')}")
        print("\n" + "=" * 60)
    else:
        print("❌ Failed to get summary")
    
    print("\n✓ Test completed!")


if __name__ == "__main__":
    print("\nNote: Make sure the server is running on", BASE_URL)
    print("Press Ctrl+C to cancel, or Enter to continue...")
    try:
        input()
    except KeyboardInterrupt:
        print("\nCancelled.")
        exit(0)
    
    test_summarization()


