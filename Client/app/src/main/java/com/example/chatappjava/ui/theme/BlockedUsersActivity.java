package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class BlockedUsersActivity extends AppCompatActivity {

    private ImageView ivBack;
    private RecyclerView rvBlocked;
    private ProgressBar progressBar;
    private View emptyState;

    private final List<User> blockedUsers = new ArrayList<>();
    private BlockedUsersAdapter adapter;

    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        apiClient = new ApiClient();
        sharedPrefs = new SharedPreferencesManager(this);

        initViews();
        setupRecycler();
        setupClicks();
        loadBlockedUsers();
    }

    @SuppressLint("SetTextI18n")
    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        TextView tvTitle = findViewById(R.id.tv_title);
        rvBlocked = findViewById(R.id.rv_blocked);
        progressBar = findViewById(R.id.progress_bar);
        emptyState = findViewById(R.id.empty_state);
        tvTitle.setText("Blocked Users");
    }

    private void setupRecycler() {
        adapter = new BlockedUsersAdapter(blockedUsers, this::onUnblockClick);
        rvBlocked.setLayoutManager(new LinearLayoutManager(this));
        rvBlocked.setAdapter(adapter);
    }

    private void setupClicks() {
        ivBack.setOnClickListener(v -> finish());
    }

    private void loadBlockedUsers() {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        apiClient.getBlockedUsers(token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BlockedUsersActivity.this, "Failed to load", Toast.LENGTH_SHORT).show();
                });
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        if (!response.isSuccessful()) {
                            Toast.makeText(BlockedUsersActivity.this, "Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONObject json = new JSONObject(body);
                        JSONArray arr = json.getJSONObject("data").getJSONArray("users");
                        blockedUsers.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            blockedUsers.add(User.fromJson(arr.getJSONObject(i)));
                        }
                        adapter.notifyDataSetChanged();
                        emptyState.setVisibility(blockedUsers.isEmpty() ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        Toast.makeText(BlockedUsersActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void onUnblockClick(User user) {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.blockUser(token, user.getId(), "unblock", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(BlockedUsersActivity.this, "Network error", Toast.LENGTH_SHORT).show());
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        blockedUsers.remove(user);
                        adapter.notifyDataSetChanged();
                        emptyState.setVisibility(blockedUsers.isEmpty() ? View.VISIBLE : View.GONE);
                        Toast.makeText(BlockedUsersActivity.this, "Unblocked", Toast.LENGTH_SHORT).show();
                        // Notify home to refresh blocked list and chats
                        android.content.Intent intent = new android.content.Intent("com.example.chatappjava.ACTION_BLOCKED_USERS_CHANGED");
                        sendBroadcast(intent);
                    } else {
                        Toast.makeText(BlockedUsersActivity.this, "Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}


