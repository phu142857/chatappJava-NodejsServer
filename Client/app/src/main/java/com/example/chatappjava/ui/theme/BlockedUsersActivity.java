package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.EmptyStateHelper;
import com.example.chatappjava.utils.SkeletonHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class BlockedUsersActivity extends AppCompatActivity {

    private View ivBack;
    private RecyclerView rvBlocked;
    private View listSkeleton;
    private View emptyState;

    private final List<User> blockedUsers = new ArrayList<>();
    private BlockedUsersAdapter adapter;

    private ApiClient apiClient;
    private DatabaseManager sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        apiClient = new ApiClient();
        sharedPrefs = new DatabaseManager(this);

        initViews();
        setupRecycler();
        setupClicks();
        loadBlockedUsers();
    }

    @SuppressLint("SetTextI18n")
    private void initViews() {
        View backWell = findViewById(R.id.toolbar_back_well);
        if (backWell != null) {
            backWell.setVisibility(View.VISIBLE);
        }
        ivBack = findViewById(R.id.iv_toolbar_back);
        TextView tvTitle = findViewById(R.id.tv_toolbar_title);
        rvBlocked = findViewById(R.id.rv_blocked);
        listSkeleton = findViewById(R.id.list_skeleton);
        emptyState = findViewById(R.id.empty_state);
        tvTitle.setText(R.string.title_activity_blocked_users);
        EmptyStateHelper.bind(
                emptyState,
                R.string.empty_blocked_users_title,
                R.string.empty_blocked_users_subtitle,
                R.drawable.ic_eye
        );
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
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        SkeletonHelper.setListLoading(listSkeleton, true);
        rvBlocked.setVisibility(View.GONE);
        apiClient.getBlockedUsers(token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    rvBlocked.setVisibility(View.VISIBLE);
                    Toast.makeText(BlockedUsersActivity.this, getString(R.string.error_failed_to_load), Toast.LENGTH_SHORT).show();
                });
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    rvBlocked.setVisibility(View.VISIBLE);
                    try {
                        if (!response.isSuccessful()) {
                            Toast.makeText(BlockedUsersActivity.this, getString(R.string.error_failed_code, response.code()), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONObject json = new JSONObject(body);
                        JSONArray arr = json.getJSONObject("data").getJSONArray("users");
                        int previousSize = blockedUsers.size();
                        blockedUsers.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            User user = User.fromJsonStatic(arr.getJSONObject(i));
                            blockedUsers.add(user);
                        }
                        notifyBlockedListChanged(previousSize, blockedUsers.size());
                        emptyState.setVisibility(blockedUsers.isEmpty() ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        Toast.makeText(BlockedUsersActivity.this, getString(R.string.error_parse), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void onUnblockClick(User user) {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            return;
        }
        apiClient.blockUser(token, user.getId(), "unblock", new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(BlockedUsersActivity.this, getString(R.string.error_network), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        int index = blockedUsers.indexOf(user);
                        if (index >= 0) {
                            blockedUsers.remove(index);
                            adapter.notifyItemRemoved(index);
                        }
                        emptyState.setVisibility(blockedUsers.isEmpty() ? View.VISIBLE : View.GONE);
                        Toast.makeText(BlockedUsersActivity.this, getString(R.string.msg_unblocked), Toast.LENGTH_SHORT).show();
                        // Notify home to refresh blocked list and chats
                        android.content.Intent intent = new android.content.Intent("com.example.chatappjava.ACTION_BLOCKED_USERS_CHANGED");
                        sendBroadcast(intent);
                    } else {
                        Toast.makeText(BlockedUsersActivity.this, getString(R.string.error_failed_code, response.code()), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void notifyBlockedListChanged(int previousSize, int newSize) {
        if (adapter == null) {
            return;
        }
        if (previousSize == newSize) {
            if (newSize > 0) {
                adapter.notifyItemRangeChanged(0, newSize);
            }
            return;
        }
        if (newSize > previousSize) {
            if (previousSize > 0) {
                adapter.notifyItemRangeChanged(0, previousSize);
            }
            adapter.notifyItemRangeInserted(previousSize, newSize - previousSize);
            return;
        }
        if (newSize > 0) {
            adapter.notifyItemRangeChanged(0, newSize);
        }
        adapter.notifyItemRangeRemoved(newSize, previousSize - newSize);
    }
}


