package com.burhanuday.spamit;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class Pref extends AppCompatActivity {

    private SwitchMaterial vibrateSwitch;
    private SwitchMaterial lastMessageSwitch;
    private SwitchMaterial darkModeSwitch;
    private SwitchMaterial followSystemSwitch;
    private Slider delaySlider;
    private com.google.android.material.textfield.TextInputEditText delayInput;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Set up toolbar with back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_settings);
        }

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);

        vibrateSwitch = findViewById(R.id.switch_vibrate);
        lastMessageSwitch = findViewById(R.id.switch_share);
        darkModeSwitch = findViewById(R.id.switch_dark_mode);
        followSystemSwitch = findViewById(R.id.switch_system_theme);
        delaySlider = findViewById(R.id.slider_delay);
        delayInput = findViewById(R.id.et_delay_input);

        initialise();
        setChangeListeners();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initialise() {
        vibrateSwitch.setChecked(prefs.getBoolean(Constants.KEY_VIBRATE, true));
        lastMessageSwitch.setChecked(prefs.getBoolean(Constants.KEY_LAST_MESSAGE, true));
        darkModeSwitch.setChecked(prefs.getBoolean(Constants.KEY_DARK_MODE, true));
        
        boolean followSystem = prefs.getBoolean(Constants.KEY_FOLLOW_SYSTEM, true);
        followSystemSwitch.setChecked(followSystem);
        darkModeSwitch.setEnabled(!followSystem);
        darkModeSwitch.setAlpha(followSystem ? 0.5f : 1.0f);

        // Delay slider and input
        int delayMs = prefs.getInt(Constants.KEY_DELAY_MS, Constants.DEFAULT_DELAY_MS);
        
        if (delayMs < Constants.MIN_DELAY_MS) delayMs = Constants.MIN_DELAY_MS;
        if (delayMs > Constants.MAX_DELAY_MS) delayMs = Constants.MAX_DELAY_MS;

        delaySlider.setValueFrom(Constants.MIN_DELAY_MS);
        delaySlider.setValueTo(Constants.MAX_DELAY_MS);
        delaySlider.setStepSize(5);
        
        try {
            delaySlider.setValue((float) delayMs);
        } catch (IllegalArgumentException e) {
            delaySlider.setValue((float) Constants.DEFAULT_DELAY_MS);
            delayMs = Constants.DEFAULT_DELAY_MS;
        }

        delayInput.setText(String.valueOf(delayMs));
    }

    private void setChangeListeners() {
        vibrateSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(Constants.KEY_VIBRATE, isChecked).apply());

        lastMessageSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(Constants.KEY_LAST_MESSAGE, isChecked).apply());

        followSystemSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(Constants.KEY_FOLLOW_SYSTEM, isChecked).apply();
            darkModeSwitch.setEnabled(!isChecked);
            darkModeSwitch.setAlpha(isChecked ? 0.5f : 1.0f);
            
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            } else {
                boolean isDarkMode = prefs.getBoolean(Constants.KEY_DARK_MODE, true);
                AppCompatDelegate.setDefaultNightMode(
                        isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(Constants.KEY_DARK_MODE, isChecked).apply();
            if (!followSystemSwitch.isChecked()) {
                AppCompatDelegate.setDefaultNightMode(
                        isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        delaySlider.addOnChangeListener((slider, value, fromUser) -> {
            int delay = (int) value;
            if (fromUser) {
                delayInput.setText(String.valueOf(delay));
                prefs.edit().putInt(Constants.KEY_DELAY_MS, delay).apply();
            }
        });

        delayInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String val = s.toString();
                if (val.isEmpty()) return;

                try {
                    int delay = Integer.parseInt(val);
                    if (delay >= Constants.MIN_DELAY_MS && delay <= Constants.MAX_DELAY_MS) {
                        // Avoid infinite loop by checking if value actually changed
                        if (delaySlider.getValue() != (float) delay) {
                            // Slider value must be a multiple of stepSize
                            float snappedDelay = Math.round(delay / 5.0f) * 5.0f;
                            delaySlider.setValue(snappedDelay);
                        }
                        prefs.edit().putInt(Constants.KEY_DELAY_MS, delay).apply();
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}
