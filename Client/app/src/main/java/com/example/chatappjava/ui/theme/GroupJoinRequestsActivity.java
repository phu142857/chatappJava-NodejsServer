package com.example.chatappjava.ui.theme;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
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
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefs;
    private Chat currentChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.chatappjava.R.layout.activity_group_join_requests);

        recyclerView = findViewById(com.example.chatappjava.R.id.rv_requests);
        progressBar = findViewById(com.example.chatappjava.R.id.progress_bar);
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
        apiClient.authenticatedGet("/api/groups/" + currentChat.getId() + "/join-requests", token, new okhttp3.Callback() {
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
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject r = arr.getJSONObject(i).optJSONObject("user");
                                    if (r != null) {
                                        requests.add(User.fromJson(r));
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

    private void respond(User user, boolean approve) {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty() || currentChat == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("action", approve ? "approve" : "reject");
            apiClient.authenticatedPost("/api/groups/" + currentChat.getId() + "/join-requests/" + user.getId(), token, body, new okhttp3.Callback() {
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


