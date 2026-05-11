package com.burhanuday.spamit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class Tutorial extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_setup);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        boolean isFirstRun = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.KEY_FIRST_RUN, true);

        boolean allGranted = checkPermissions();
        
        if (allGranted && isFirstRun) {
            con_app(null);
            return;
        }
        
        updateUI();
    }

    private void updateUI() {
        com.google.android.material.button.MaterialButton btnAccess = findViewById(R.id.button_accessibility);
        com.google.android.material.button.MaterialButton btnOverlay = findViewById(R.id.button_overlay);

        int white = getResources().getColor(android.R.color.white);

        if (isAccessibilityServiceEnabled(this)) {
            btnAccess.setText("Accessibility Granted");
            btnAccess.setEnabled(false);
            btnAccess.setIconResource(R.drawable.ic_check_circle);
            btnAccess.setIconTintResource(android.R.color.white);
            btnAccess.setTextColor(white);
        } else {
            btnAccess.setText(R.string.btn_enable_accessibility);
            btnAccess.setEnabled(true);
            btnAccess.setIconResource(android.R.drawable.ic_menu_preferences);
            btnAccess.setIconTint(null);
        }

        boolean overlayGranted = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            overlayGranted = Settings.canDrawOverlays(this);
        }
        
        if (overlayGranted) {
            btnOverlay.setText("Overlay Granted");
            btnOverlay.setEnabled(false);
            btnOverlay.setIconResource(R.drawable.ic_check_circle);
            btnOverlay.setIconTintResource(android.R.color.white);
            btnOverlay.setTextColor(white);
        } else {
            btnOverlay.setText(R.string.btn_enable_overlay);
            btnOverlay.setEnabled(true);
            btnOverlay.setIconResource(android.R.drawable.ic_menu_sort_by_size);
            btnOverlay.setIconTint(null);
        }
    }

    private boolean checkPermissions() {
        boolean overlay = true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            overlay = Settings.canDrawOverlays(this);
        }
        return overlay && isAccessibilityServiceEnabled(this);
    }

    private boolean isAccessibilityServiceEnabled(Context context) {
        int accessibilityEnabled = 0;
        final String service = context.getPackageName() + "/" + SpamAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // Ignore
        }
        android.text.TextUtils.SimpleStringSplitter mStringColonSplitter = new android.text.TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
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

    /**
     * Step 1: Open accessibility settings
     */
    public void openAccessibilitySettings(View view) {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, R.string.toast_enable_accessibility, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_settings_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Step 2: Open overlay settings
     */
    public void openOverlaySettings(View view) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, R.string.toast_enable_overlay, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.toast_overlay_not_required, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_settings_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Continue to main app
     */
    public void con_app(View view) {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(Constants.KEY_FIRST_RUN, false).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
