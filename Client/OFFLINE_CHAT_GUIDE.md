# Hướng Dẫn Sử Dụng Offline Chat với SQLite

## Tổng Quan

Tính năng Offline Chat cho phép người dùng:
- Gửi tin nhắn khi không có mạng (lưu vào SQLite với trạng thái PENDING)
- Xem lịch sử chat từ database local
- Tự động đồng bộ tin nhắn khi có mạng trở lại

## 1. Schema SQLite

### Bảng `conversations` (Hội thoại)
Lưu trữ thông tin các cuộc trò chuyện:
- `id`: ID của hội thoại
- `type`: Loại ("private" hoặc "group")
- `name`: Tên hội thoại
- `last_message`: Tin nhắn cuối cùng
- `last_message_time`: Thời gian tin nhắn cuối
- `unread_count`: Số tin nhắn chưa đọc
- Và các trường khác...

### Bảng `messages` (Tin nhắn)
Lưu trữ tất cả tin nhắn:
- `id`: ID tin nhắn (có thể là temp ID cho tin nhắn offline)
- `chat_id`: ID hội thoại
- `sender_id`: ID người gửi
- `content`: Nội dung tin nhắn
- `type`: Loại tin nhắn ("text", "image", "file", "voice")
- `timestamp`: Thời gian gửi
- `sync_status`: Trạng thái đồng bộ ("synced", "pending", "failed")
- `client_nonce`: ID tạm thời để tránh trùng lặp
- Và các trường khác...

## 2. Workflow Gửi Tin Nhắn Offline

### Luồng xử lý khi gửi tin nhắn:

```
1. Người dùng nhập tin nhắn và nhấn gửi
   ↓
2. Kiểm tra kết nối mạng
   ↓
3a. CÓ MẠNG:
    - Gửi tin nhắn lên server ngay
    - Lưu vào DB với sync_status = "synced"
    - Cập nhật UI
   
3b. KHÔNG CÓ MẠNG:
    - Tạo Message object với temp ID (temp_UUID)
    - Lưu vào DB với sync_status = "pending"
    - Hiển thị trong UI với icon "pending" (⏳)
    - Tin nhắn sẽ được đồng bộ tự động khi có mạng
```

## 3. Code Examples

### 3.1. Gửi Tin Nhắn (Với Hỗ Trợ Offline)

```java
import com.example.chatappjava.utils.MessageRepository;
import com.example.chatappjava.utils.OfflineMessageSyncManager;
import com.example.chatappjava.models.Message;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ChatActivity extends AppCompatActivity {
    private MessageRepository messageRepository;
    private OfflineMessageSyncManager syncManager;
    private DatabaseManager databaseManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        messageRepository = new MessageRepository(this);
        syncManager = new OfflineMessageSyncManager(this);
        databaseManager = new DatabaseManager(this);
    }
    
    /**
     * Gửi tin nhắn với hỗ trợ offline
     */
    private void sendMessage(String content, String chatId) {
        // Tạo Message object
        Message message = new Message();
        message.setContent(content);
        message.setChatId(chatId);
        message.setSenderId(databaseManager.getUserId());
        message.setType("text");
        message.setChatType("private"); // hoặc "group"
        message.setTimestamp(System.currentTimeMillis());
        
        // Kiểm tra kết nối mạng
        if (isNetworkAvailable()) {
            // CÓ MẠNG: Gửi lên server ngay
            sendMessageToServer(message);
        } else {
            // KHÔNG CÓ MẠNG: Lưu vào DB với status PENDING
            message.setId(null); // Không có ID = tin nhắn mới
            Message savedMessage = messageRepository.saveMessage(message);
            
            // Hiển thị trong UI với trạng thái pending
            displayMessage(savedMessage, true); // true = pending
            
            Toast.makeText(this, "Tin nhắn sẽ được gửi khi có mạng", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Gửi tin nhắn lên server
     */
    private void sendMessageToServer(Message message) {
        String token = databaseManager.getToken();
        
        try {
            JSONObject messageJson = new JSONObject();
            messageJson.put("chatId", message.getChatId());
            messageJson.put("content", message.getContent());
            messageJson.put("type", message.getType());
            messageJson.put("timestamp", message.getTimestamp());
            
            // Generate clientNonce để tránh trùng lặp
            String clientNonce = UUID.randomUUID().toString();
            message.setClientNonce(clientNonce);
            messageJson.put("clientNonce", clientNonce);
            
            apiClient.sendMessage(token, messageJson, new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response.body().string());
                            if (jsonResponse.optBoolean("success", false)) {
                                JSONObject data = jsonResponse.optJSONObject("data");
                                String serverMessageId = data.optString("_id", "");
                                
                                // Lưu vào DB với server ID
                                message.setId(serverMessageId);
                                messageRepository.saveMessage(message);
                                
                                // Cập nhật UI
                                runOnUiThread(() -> {
                                    displayMessage(message, false); // false = synced
                                });
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        // Gửi thất bại -> lưu với status PENDING
                        message.setId(null);
                        messageRepository.saveMessage(message);
                        runOnUiThread(() -> {
                            displayMessage(message, true);
                            Toast.makeText(ChatActivity.this, 
                                "Gửi thất bại, sẽ thử lại sau", 
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                
                @Override
                public void onFailure(Call call, IOException e) {
                    // Lỗi kết nối -> lưu với status PENDING
                    message.setId(null);
                    messageRepository.saveMessage(message);
                    runOnUiThread(() -> {
                        displayMessage(message, true);
                        Toast.makeText(ChatActivity.this, 
                            "Không có kết nối, tin nhắn sẽ được gửi sau", 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Kiểm tra kết nối mạng
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }
}
```

### 3.2. Lấy Lịch Sử Chat Từ Database

```java
/**
 * Load messages từ database local
 */
private void loadMessagesFromDatabase(String chatId) {
    // Lấy tất cả tin nhắn cho chat này
    List<Message> messages = messageRepository.getMessagesForChat(chatId, 0);
    
    // Hiển thị trong RecyclerView
    messageAdapter.setMessages(messages);
    messageAdapter.notifyDataSetChanged();
    
    // Scroll xuống tin nhắn cuối
    recyclerView.scrollToPosition(messages.size() - 1);
}

/**
 * Load thêm tin nhắn cũ (pagination)
 */
private void loadOlderMessages(String chatId, long beforeTimestamp) {
    List<Message> olderMessages = messageRepository.getMessagesBefore(
        chatId, 
        beforeTimestamp, 
        50 // Load 50 tin nhắn cũ
    );
    
    // Thêm vào đầu danh sách
    messages.addAll(0, olderMessages);
    messageAdapter.notifyItemRangeInserted(0, olderMessages.size());
}
```

### 3.3. Đồng Bộ Tin Nhắn Khi Có Mạng

```java
/**
 * Lắng nghe sự kiện kết nối mạng
 */
private void setupNetworkListener() {
    // Sử dụng BroadcastReceiver hoặc NetworkCallback
    ConnectivityManager connectivityManager = 
        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    
    NetworkRequest request = new NetworkRequest.Builder().build();
    ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            // Mạng đã có -> đồng bộ tin nhắn pending
            runOnUiThread(() -> {
                syncManager.syncPendingMessages();
            });
        }
    };
    
    connectivityManager.registerNetworkCallback(request, callback);
}

/**
 * Hoặc gọi trực tiếp khi app mở lại
 */
@Override
protected void onResume() {
    super.onResume();
    
    // Kiểm tra và đồng bộ tin nhắn pending
    if (syncManager.isNetworkAvailable()) {
        syncManager.syncPendingMessages();
    }
}
```

### 3.4. INSERT Tin Nhắn Vào Database

```java
// Tạo Message object
Message message = new Message();
message.setContent("Xin chào!");
message.setChatId("chat123");
message.setSenderId("user456");
message.setType("text");
message.setTimestamp(System.currentTimeMillis());

// Lưu vào database
MessageRepository repository = new MessageRepository(context);
Message savedMessage = repository.saveMessage(message);

// Nếu message không có ID, nó sẽ được tạo temp ID và status = PENDING
// Nếu message có ID từ server, status = SYNCED
```

### 3.5. SELECT Lịch Sử Chat

```java
// Lấy tất cả tin nhắn của một chat
MessageRepository repository = new MessageRepository(context);
List<Message> messages = repository.getMessagesForChat("chat123", 0);

// Lấy tin nhắn với giới hạn
List<Message> recentMessages = repository.getMessagesForChat("chat123", 50);

// Lấy tin nhắn cũ hơn (pagination)
List<Message> olderMessages = repository.getMessagesBefore(
    "chat123", 
    System.currentTimeMillis() - 86400000, // 24 giờ trước
    20
);

// Lấy tin nhắn pending cần đồng bộ
List<Message> pendingMessages = repository.getPendingMessages();
```

### 3.6. Cập Nhật Trạng Thái Sau Khi Đồng Bộ

```java
// Sau khi gửi thành công lên server
String tempId = message.getId(); // "temp_xxx"
String serverId = responseData.getString("_id"); // "server_xxx"

// Cập nhật trong database
messageRepository.updateSyncStatus(
    tempId,        // ID cũ (temp)
    serverId,      // ID mới từ server
    "synced",      // Trạng thái mới
    null           // Không có lỗi
);

// Nếu thất bại
messageRepository.updateSyncStatus(
    tempId,
    null,
    "failed",
    "Network error" // Thông báo lỗi
);
```

## 4. Tích Hợp Vào BaseChatActivity

Để tích hợp vào app hiện tại, cần sửa `BaseChatActivity.sendMessage()`:

```java
protected void sendMessage(String content) {
    // ... validation code ...
    
    // Tạo Message object
    Message message = new Message();
    message.setContent(content);
    message.setChatId(currentChat.getId());
    message.setSenderId(databaseManager.getUserId());
    message.setType("text");
    message.setChatType(currentChat.isGroupChat() ? "group" : "private");
    message.setTimestamp(System.currentTimeMillis());
    
    // Lưu vào database trước (với status pending nếu offline)
    MessageRepository messageRepo = new MessageRepository(this);
    boolean isOnline = isNetworkAvailable();
    
    if (!isOnline) {
        // Offline: Lưu với temp ID
        message.setId(null);
        Message saved = messageRepo.saveMessage(message);
        messages.add(saved);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        Toast.makeText(this, "Tin nhắn sẽ được gửi khi có mạng", Toast.LENGTH_SHORT).show();
        return;
    }
    
    // Online: Gửi lên server và lưu vào DB
    // ... existing send code ...
}
```

## 5. Lưu Ý Quan Trọng

1. **Temp ID**: Tin nhắn offline được tạo với ID dạng `temp_UUID` để tránh trùng lặp
2. **Client Nonce**: Sử dụng để server tránh xử lý trùng lặp khi có echo từ socket
3. **Sync Status**: 
   - `synced`: Đã gửi thành công lên server
   - `pending`: Chờ đồng bộ
   - `failed`: Đồng bộ thất bại (sẽ thử lại)
4. **Deduplication**: Khi server trả về tin nhắn với ID mới, cần cập nhật temp ID thành server ID
5. **UI Feedback**: Hiển thị icon ⏳ cho tin nhắn pending, ✓ cho tin nhắn đã gửi

## 6. Testing

```java
// Test gửi tin nhắn offline
// 1. Tắt WiFi/Data
// 2. Gửi tin nhắn
// 3. Kiểm tra database: SELECT * FROM messages WHERE sync_status = 'pending'
// 4. Bật mạng lại
// 5. Kiểm tra tin nhắn đã được gửi và sync_status = 'synced'
```

## 7. Database Location

Database được lưu tại:
```
/data/data/com.example.chatappjava/databases/ChatApp.db
```

Xem bằng Android Studio Device File Explorer hoặc:
```bash
adb shell
run-as com.example.chatappjava
cd databases
sqlite3 ChatApp.db
.tables
SELECT * FROM messages WHERE sync_status = 'pending';
```

