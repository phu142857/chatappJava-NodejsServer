package com.example.chatappjava.ui.theme;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.adapters.SelectableUserAdapter;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;
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
    private Button btnCreate;
    private ProgressBar progressBar;
    private SelectableUserAdapter adapter;
    private final List<User> friends = new ArrayList<>();
    private final List<String> selectedUserIds = new ArrayList<>();
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        etGroupName = findViewById(R.id.et_group_name);
        etDescription = findViewById(R.id.et_group_description);
        rvFriends = findViewById(R.id.rv_friends);
        btnCreate = findViewById(R.id.btn_create_group);
        progressBar = findViewById(R.id.progress_bar);

        apiClient = new ApiClient();
        sharedPrefs = new SharedPreferencesManager(this);

        adapter = new SelectableUserAdapter(friends, this);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);

        btnCreate.setOnClickListener(v -> createGroup());

        loadFriends();
    }

    private void loadFriends() {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        apiClient.authenticatedGet("/api/users/friends", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(CreateGroupActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.code() == 200 && json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray arr = data.getJSONArray("friends");
                            friends.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                User u = User.fromJson(arr.getJSONObject(i));
                                friends.add(u);
                            }
                            adapter.notifyDataSetChanged();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(CreateGroupActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void createGroup() {
        String name = etGroupName.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Group name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedUserIds.size() < 2) { // at least 2 others + creator = 3
            Toast.makeText(this, "Select at least 2 friends", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("name", name);
            if (!desc.isEmpty()) payload.put("description", desc);
            JSONArray ids = new JSONArray();
            for (String id : selectedUserIds) ids.put(id);
            payload.put("participantIds", ids);

            progressBar.setVisibility(View.VISIBLE);
            apiClient.createGroupChat(token, payload, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(CreateGroupActivity.this, "Failed to create group", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        try {
                            JSONObject json = new JSONObject(body);
                            if ((response.code() == 200 || response.code() == 201) && json.optBoolean("success", false)) {
                                Toast.makeText(CreateGroupActivity.this, "Group created", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                String msg = json.optString("message", "Create group failed");
                                Toast.makeText(CreateGroupActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(CreateGroupActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException ignored) {}
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


