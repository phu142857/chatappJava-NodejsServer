package com.example.chatappjava.ui.theme;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.adapters.MessageAdapter;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.SkeletonHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GroupJoinRequestsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View listSkeleton;
    private MessageAdapter.RequestsAdapter adapter;
    private final List<User> requests = new ArrayList<>();
    private final List<User> allRequests = new ArrayList<>();
    private ApiClient apiClient;
    private DatabaseManager databaseManager;
    private Chat currentChat;
    private EditText etSearch;
    private ImageButton ivClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.example.chatappjava.R.layout.activity_group_join_requests);

        recyclerView = findViewById(com.example.chatappjava.R.id.rv_requests);
        listSkeleton = findViewById(com.example.chatappjava.R.id.list_skeleton);
        etSearch = findViewById(com.example.chatappjava.R.id.et_search);
        ivClear = findViewById(com.example.chatappjava.R.id.iv_clear);
        if (etSearch != null) {
            etSearch.setHint(com.example.chatappjava.R.string.search_requests_hint);
        }
        if (ivClear != null) {
            ivClear.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.setText("");
                }
            });
        }
        View backWell = findViewById(R.id.toolbar_back_well);
        if (backWell != null) {
            backWell.setVisibility(View.VISIBLE);
        }
        View ivBack = findViewById(R.id.iv_toolbar_back);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }
        TextView tvTitle = findViewById(R.id.tv_toolbar_title);
        if (tvTitle != null) {
            tvTitle.setText(R.string.title_activity_group_join_requests);
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter.RequestsAdapter(requests, new MessageAdapter.RequestsAdapter.ActionListener() {
            @Override
            public void onApprove(User u) { respond(u, true); }
            @Override
            public void onReject(User u) { respond(u, false); }
        });
        recyclerView.setAdapter(adapter);

        apiClient = new ApiClient();
        databaseManager = new DatabaseManager(this);

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
                    if (ivClear != null) {
                        ivClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    filterRequests(s != null ? s.toString() : "");
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        loadRequests();
    }

    private void loadRequests() {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty() || currentChat == null) {
            Toast.makeText(this, getString(R.string.msg_missing_data), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        SkeletonHelper.setListLoading(listSkeleton, true);
        recyclerView.setVisibility(View.GONE);
        String targetGroupId = currentChat.getGroupId() != null && !currentChat.getGroupId().isEmpty() ? currentChat.getGroupId() : currentChat.getId();
        apiClient.authenticatedGet("/api/groups/" + targetGroupId + "/join-requests", token, new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    recyclerView.setVisibility(View.VISIBLE);
                    Toast.makeText(GroupJoinRequestsActivity.this, getString(R.string.error_failed_to_load_requests), Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    recyclerView.setVisibility(View.VISIBLE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.code() == 200 && json.optBoolean("success", false)) {
                            JSONArray arr = json.getJSONObject("data").optJSONArray("requests");
                            int previousSize = requests.size();
                            requests.clear();
                            allRequests.clear();
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject r = arr.getJSONObject(i).optJSONObject("user");
                                    if (r != null) {
                                        User u = User.fromJsonStatic(arr.getJSONObject(i));
                                        requests.add(u);
                                        allRequests.add(u);
                                    }
                                }
                            }
                            notifyRequestsListChanged(previousSize, requests.size());
                        } else {
                            Toast.makeText(GroupJoinRequestsActivity.this,
                                    json.optString("message", getString(R.string.error_request_failed)),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(GroupJoinRequestsActivity.this, getString(R.string.error_parse), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void filterRequests(String query) {
        int previousSize = requests.size();
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
        notifyRequestsListChanged(previousSize, requests.size());
    }

    private void notifyRequestsListChanged(int previousSize, int newSize) {
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

    private void respond(User user, boolean approve) {
        String token = databaseManager.getToken();
        if (token == null || token.isEmpty() || currentChat == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("action", approve ? "approve" : "reject");
            String targetGroupId = currentChat.getGroupId() != null && !currentChat.getGroupId().isEmpty() ? currentChat.getGroupId() : currentChat.getId();
            apiClient.authenticatedPost("/api/groups/" + targetGroupId + "/join-requests/" + user.getId(), token, body, new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(GroupJoinRequestsActivity.this, getString(R.string.error_action_failed), Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (response.code() == 200) {
                            Toast.makeText(GroupJoinRequestsActivity.this,
                                    approve ? getString(R.string.success_join_request_approved)
                                            : getString(R.string.success_join_request_rejected),
                                    Toast.LENGTH_SHORT).show();
                            loadRequests();
                        } else {
                            Toast.makeText(GroupJoinRequestsActivity.this, getString(R.string.error_action_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException ignored) {}
    }
}


