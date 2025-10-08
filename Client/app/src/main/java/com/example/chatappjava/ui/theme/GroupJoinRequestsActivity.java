package com.example.chatappjava.ui.theme;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.adapters.MessageAdapter;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GroupJoinRequestsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private MessageAdapter.RequestsAdapter adapter;
    private final List<User> requests = new ArrayList<>();
    private final List<User> allRequests = new ArrayList<>();
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefs;
    private Chat currentChat;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.chatappjava.R.layout.activity_group_join_requests);

        recyclerView = findViewById(com.example.chatappjava.R.id.rv_requests);
        progressBar = findViewById(com.example.chatappjava.R.id.progress_bar);
        etSearch = findViewById(com.example.chatappjava.R.id.et_search);
        View ivBack = findViewById(com.example.chatappjava.R.id.iv_back);
        if (ivBack != null) ivBack.setOnClickListener(v -> finish());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter.RequestsAdapter(requests, new MessageAdapter.RequestsAdapter.ActionListener() {
            @Override
            public void onApprove(User u) { respond(u, true); }
            @Override
            public void onReject(User u) { respond(u, false); }
        });
        recyclerView.setAdapter(adapter);

        apiClient = new ApiClient();
        sharedPrefs = new SharedPreferencesManager(this);

        // Read chat
        try {
            String chatJson = getIntent().getStringExtra("chat");
            if (chatJson != null) {
                currentChat = Chat.fromJson(new JSONObject(chatJson));
            }
        } catch (JSONException ignored) {}

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterRequests(s != null ? s.toString() : "");
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        loadRequests();
    }

    private void loadRequests() {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty() || currentChat == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        String targetGroupId = currentChat.getGroupId() != null && !currentChat.getGroupId().isEmpty() ? currentChat.getGroupId() : currentChat.getId();
        apiClient.authenticatedGet("/api/groups/" + targetGroupId + "/join-requests", token, new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GroupJoinRequestsActivity.this, "Failed to load requests", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.code() == 200 && json.optBoolean("success", false)) {
                            JSONArray arr = json.getJSONObject("data").optJSONArray("requests");
                            requests.clear();
                            allRequests.clear();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject r = arr.getJSONObject(i).optJSONObject("user");
                                    if (r != null) {
                                        User u = User.fromJson(r);
                                        requests.add(u);
                                        allRequests.add(u);
                                    }
                                }
                            }
                            adapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(GroupJoinRequestsActivity.this, json.optString("message", "Failed"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(GroupJoinRequestsActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void filterRequests(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        requests.clear();
        if (q.isEmpty()) {
            requests.addAll(allRequests);
        } else {
            for (User u : allRequests) {
                String displayName = u.getDisplayName() != null ? u.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
                String username = u.getUsername() != null ? u.getUsername().toLowerCase(java.util.Locale.ROOT) : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase(java.util.Locale.ROOT) : "";
                String phone = u.getPhoneNumber() != null ? u.getPhoneNumber().toLowerCase(java.util.Locale.ROOT) : "";
                if (displayName.contains(q) || username.contains(q) || email.contains(q) || phone.contains(q)) {
                    requests.add(u);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void respond(User user, boolean approve) {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty() || currentChat == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("action", approve ? "approve" : "reject");
            String targetGroupId = currentChat.getGroupId() != null && !currentChat.getGroupId().isEmpty() ? currentChat.getGroupId() : currentChat.getId();
            apiClient.authenticatedPost("/api/groups/" + targetGroupId + "/join-requests/" + user.getId(), token, body, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(GroupJoinRequestsActivity.this, "Action failed", Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.code() == 200) {
                            Toast.makeText(GroupJoinRequestsActivity.this, approve ? "Approved" : "Rejected", Toast.LENGTH_SHORT).show();
                            loadRequests();
                        } else {
                            Toast.makeText(GroupJoinRequestsActivity.this, "Action failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException ignored) {}
    }
}


