package com.burhanuday.spamit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText messageInput;
    private TextInputEditText countInput;
    private TextInputLayout messageLayout;
    private TextInputLayout countLayout;
    private TextView historyLabel;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode setting before super.onCreate
        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean followSystem = prefs.getBoolean(Constants.KEY_FOLLOW_SYSTEM, true);
        if (followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean isDarkMode = prefs.getBoolean(Constants.KEY_DARK_MODE, true);
            AppCompatDelegate.setDefaultNightMode(
                    isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setElevation(0);
        }

        messageLayout = findViewById(R.id.til_message);
        countLayout = findViewById(R.id.til_count);
        messageInput = findViewById(R.id.et_message);
        countInput = findViewById(R.id.et_count);
        historyLabel = findViewById(R.id.tv_history_label);

        // Load saved values
        messageInput.setText(prefs.getString(Constants.KEY_MESSAGE, Constants.DEFAULT_MESSAGE));
        countInput.setText(String.valueOf(prefs.getInt(Constants.KEY_COUNT, Constants.DEFAULT_COUNT)));

        setupChangeListeners();
        loadMessageHistory();

        findViewById(R.id.btn_launch_widget).setOnClickListener(v -> launchWidget());
    }

    private void launchWidget() {
        if (!checkOverlayPermission()) {
            requestOverlayPermission();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission();
            return;
        }

        String msg = messageInput.getText().toString().trim();
        if (!msg.isEmpty()) {
            saveToHistory(msg);
        }

        ContextCompat.startForegroundService(this, new Intent(this, FloatingWidgetService.class));
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, R.string.toast_enable_overlay, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + SpamAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    this.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // Ignore
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    this.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, R.string.toast_enable_accessibility, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, Pref.class));
            return true;
        } else if (id == R.id.menu_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupChangeListeners() {
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                String msg = editable.toString().trim();
                if (msg.isEmpty()) {
                    messageLayout.setError(getString(R.string.error_empty_message));
                } else {
                    messageLayout.setError(null);
                    prefs.edit().putString(Constants.KEY_MESSAGE, msg).apply();
                }
            }
        });

        countInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                String text = editable.toString().trim();
                
                // Allow empty temporarily while typing, but if left empty it's handled below
                if (text.isEmpty()) {
                    return; 
                }

                try {
                    int value = Integer.parseInt(text);
                    if (value < Constants.MIN_COUNT) {
                        countLayout.setError(getString(R.string.error_min_count, Constants.MIN_COUNT));
                        prefs.edit().putInt(Constants.KEY_COUNT, Constants.MIN_COUNT).apply();
                    } else if (value > Constants.MAX_COUNT) {
                        countLayout.setError(getString(R.string.error_max_count, Constants.MAX_COUNT));
                        prefs.edit().putInt(Constants.KEY_COUNT, Constants.MAX_COUNT).apply();
                    } else {
                        countLayout.setError(null);
                        prefs.edit().putInt(Constants.KEY_COUNT, value).apply();
                    }
                } catch (NumberFormatException e) {
                    countLayout.setError(getString(R.string.error_invalid_number));
                    // Automatically visually reset it so the UI matches the backend
                    countInput.setText(String.valueOf(Constants.DEFAULT_COUNT));
                    countInput.setSelection(countInput.getText().length());
                    prefs.edit().putInt(Constants.KEY_COUNT, Constants.DEFAULT_COUNT).apply();
                }
            }
        });
        
        // Handle empty case when they leave focus
        countInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && countInput.getText().toString().trim().isEmpty()) {
                countLayout.setError(getString(R.string.error_empty_count));
                countInput.setText(String.valueOf(Constants.DEFAULT_COUNT));
                prefs.edit().putInt(Constants.KEY_COUNT, Constants.DEFAULT_COUNT).apply();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        boolean isFirstRun = prefs.getBoolean(Constants.KEY_FIRST_RUN, true);
        boolean permissionsGranted = checkOverlayPermission() && isAccessibilityServiceEnabled();

        if (isFirstRun) {
            if (permissionsGranted) {
                // Permissions already set up, skip tutorial
                prefs.edit().putBoolean(Constants.KEY_FIRST_RUN, false).apply();
            } else {
                // Show tutorial
                Intent opentut = new Intent(this, Tutorial.class);
                startActivity(opentut);
                finish();
                return;
            }
        }
        
        loadMessageHistory();
    }

    /**
     * "How To Use" button click handler
     */
    public void htu(View view) {
        startActivity(new Intent(this, Tutorial.class));
    }

    // --- Message History ---

    private void saveToHistory(String message) {
        List<String> history = getHistory();
        // Remove duplicates
        history.remove(message);
        // Add to front
        history.add(0, message);
        // Trim to max
        while (history.size() > Constants.MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        // Save
        SharedPreferences.Editor editor = prefs.edit();
        // Clear previous history entries to avoid polluted prefs
        int oldCount = prefs.getInt(Constants.KEY_HISTORY_COUNT, 0);
        for (int i = 0; i < oldCount; i++) {
            editor.remove(Constants.KEY_HISTORY_PREFIX + i);
        }

        for (int i = 0; i < history.size(); i++) {
            editor.putString(Constants.KEY_HISTORY_PREFIX + i, history.get(i));
        }
        editor.putInt(Constants.KEY_HISTORY_COUNT, history.size());
        editor.apply();
    }

    private List<String> getHistory() {
        List<String> history = new ArrayList<>();
        int count = prefs.getInt(Constants.KEY_HISTORY_COUNT, 0);
        for (int i = 0; i < count && i < Constants.MAX_HISTORY; i++) {
            String msg = prefs.getString(Constants.KEY_HISTORY_PREFIX + i, null);
            if (msg != null && !msg.isEmpty()) {
                history.add(msg);
            }
        }
        return history;
    }

    private void loadMessageHistory() {
        List<String> history = getHistory();
        if (history.isEmpty()) {
            if (historyLabel != null) historyLabel.setVisibility(View.GONE);
            return;
        }
        if (historyLabel != null) historyLabel.setVisibility(View.VISIBLE);

        // Find the history container and populate it with chips/buttons
        android.widget.LinearLayout historyContainer = findViewById(R.id.history_container);
        if (historyContainer == null) return;

        historyContainer.removeAllViews();
        for (String msg : history) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(msg.length() > 25 ? msg.substring(0, 25) + "…" : msg);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                messageInput.setText(msg);
                messageInput.setSelection(msg.length());
                prefs.edit().putString(Constants.KEY_MESSAGE, msg).apply();
            });
            historyContainer.addView(chip);
        }
    }
}
