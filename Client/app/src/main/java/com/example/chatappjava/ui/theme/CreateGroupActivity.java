package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.Switch;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.adapters.SelectableUserAdapter;
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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class CreateGroupActivity extends AppCompatActivity implements SelectableUserAdapter.OnSelectionChangedListener {

    private EditText etGroupName, etDescription;
    private RecyclerView rvFriends;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchPublic;
    private View listSkeleton;
    private SelectableUserAdapter adapter;
    private final List<User> friends = new ArrayList<>();
    private final List<String> selectedUserIds = new ArrayList<>();
    private ApiClient apiClient;
    private DatabaseManager sharedPrefs;
    private EditText etSearch;
    private ImageButton ivClear;
    private TextView tvCreate;
    private final List<User> allFriends = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        etGroupName = findViewById(R.id.et_group_name);
        etDescription = findViewById(R.id.et_group_description);
        rvFriends = findViewById(R.id.rv_friends);
        switchPublic = findViewById(R.id.switch_public);
        listSkeleton = findViewById(R.id.list_skeleton);
        etSearch = findViewById(R.id.et_search);
        ivClear = findViewById(R.id.iv_clear);
        if (etSearch != null) {
            etSearch.setHint(R.string.group_search_friends_hint);
        }
        if (ivClear != null) {
            ivClear.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.setText("");
                }
            });
        }
        tvCreate = findViewById(R.id.tv_create);

        apiClient = new ApiClient();
        sharedPrefs = new DatabaseManager(this);

        adapter = new SelectableUserAdapter(friends, this);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);

        // Top bar "Create" action
        if (tvCreate != null) tvCreate.setOnClickListener(v -> createGroup());
        
        // Back button
        View backWell = findViewById(R.id.toolbar_back_well);
        if (backWell != null) {
            backWell.setVisibility(View.VISIBLE);
        }
        View back = findViewById(R.id.iv_toolbar_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        TextView toolbarTitle = findViewById(R.id.tv_toolbar_title);
        if (toolbarTitle != null) {
            toolbarTitle.setText(R.string.title_activity_create_group);
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (ivClear != null) {
                        ivClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    filterFriends(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        loadFriends();
    }

    private void loadFriends() {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        SkeletonHelper.setListLoading(listSkeleton, true);
        rvFriends.setVisibility(View.GONE);
        apiClient.authenticatedGet("/api/users/friends", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    rvFriends.setVisibility(View.VISIBLE);
                    Toast.makeText(CreateGroupActivity.this, getString(R.string.error_load_friends), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    SkeletonHelper.setListLoading(listSkeleton, false);
                    rvFriends.setVisibility(View.VISIBLE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.code() == 200 && json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray arr = data.getJSONArray("friends");
                            int previousSize = friends.size();
                            friends.clear();
                            allFriends.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                User fetched = User.fromJsonStatic(arr.getJSONObject(i));
                                friends.add(fetched);
                                allFriends.add(fetched);
                            }
                            String currentQuery = etSearch != null && etSearch.getText() != null
                                    ? etSearch.getText().toString() : "";
                            filterFriends(currentQuery, previousSize);
                        }
                    } catch (JSONException e) {
                        Toast.makeText(CreateGroupActivity.this, getString(R.string.error_parse), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void filterFriends(String query) {
        filterFriends(query, friends.size());
    }

    private void filterFriends(String query, int previousSize) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        friends.clear();
        if (q.isEmpty()) {
            friends.addAll(allFriends);
        } else {
            for (User u : allFriends) {
                String displayName = u.getDisplayName() != null ? u.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
                String username = u.getUsername() != null ? u.getUsername().toLowerCase(java.util.Locale.ROOT) : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase(java.util.Locale.ROOT) : "";
                String phone = u.getPhoneNumber() != null ? u.getPhoneNumber().toLowerCase(java.util.Locale.ROOT) : "";
                if (displayName.contains(q) || username.contains(q) || email.contains(q) || phone.contains(q)) {
                    friends.add(u);
                }
            }
        }
        notifyFriendsListChanged(previousSize, friends.size());
    }

    private void notifyFriendsListChanged(int previousSize, int newSize) {
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

    private void createGroup() {
        String name = etGroupName.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_group_name_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedUserIds.size() < 2) { // at least 2 others + creator = 3
            Toast.makeText(this, getString(R.string.error_select_two_friends), Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_please_login_again), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = getJsonObject(name, desc);

            if (tvCreate != null) {
                tvCreate.setEnabled(false);
            }
            apiClient.createGroupChat(token, payload, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        if (tvCreate != null) {
                            tvCreate.setEnabled(true);
                        }
                        Toast.makeText(CreateGroupActivity.this, getString(R.string.error_create_group), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        if (tvCreate != null) {
                            tvCreate.setEnabled(true);
                        }
                        try {
                            JSONObject json = new JSONObject(body);
                            if ((response.code() == 200 || response.code() == 201) && json.optBoolean("success", false)) {
                                Toast.makeText(CreateGroupActivity.this, getString(R.string.group_created_success), Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                String msg = json.optString("message", "Create group failed");
                                Toast.makeText(CreateGroupActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(CreateGroupActivity.this, getString(R.string.error_parse), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException ignored) {}
    }

    @NonNull
    private JSONObject getJsonObject(String name, String desc) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("name", name);
        if (!desc.isEmpty()) payload.put("description", desc);
        JSONArray ids = new JSONArray();
        for (String id : selectedUserIds) ids.put(id);
        payload.put("participantIds", ids);

        // settings.isPublic from toggle
        JSONObject settings = new JSONObject();
        settings.put("isPublic", switchPublic != null && switchPublic.isChecked());
        payload.put("settings", settings);
        return payload;
    }

    @Override
    public void onSelectionChanged(String userId, boolean selected) {
        if (selected) {
            if (!selectedUserIds.contains(userId)) selectedUserIds.add(userId);
        } else {
            selectedUserIds.remove(userId);
        }
    }
}


