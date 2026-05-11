package com.burhanuday.spamit;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ContextThemeWrapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWidgetService extends Service {

    private WindowManager mWindowManager;
    private View mFloatingView;
    private WindowManager.LayoutParams params;

    private Button btnStartSpam;
    private Button btnStopSpam;
    private TextView tvStatus;

    public FloatingWidgetService() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();
        setupForeground();

        try {
            // Use a ContextThemeWrapper to apply the AppTheme to the service context.
            // This is necessary because some UI components (like CardView and selectableItemBackground)
            // require a theme to resolve attributes during inflation.
            ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.AppTheme);
            mFloatingView = LayoutInflater.from(wrapper).inflate(R.layout.layout_floating_widget, null);
            Log.d("SPAMit", "Layout inflated successfully");
        } catch (Exception e) {
            Log.e("SPAMit", "Error inflating layout: " + e.getMessage());
            e.printStackTrace();
            stopSelf();
            return;
        }

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            mWindowManager.addView(mFloatingView, params);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_overlay_failed, Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        btnStartSpam = mFloatingView.findViewById(R.id.btn_start_spam);
        btnStopSpam = mFloatingView.findViewById(R.id.btn_stop_spam);
        tvStatus = mFloatingView.findViewById(R.id.tv_status);

        if (btnStartSpam == null || btnStopSpam == null || tvStatus == null) {
            Log.e("SPAMit", "Critical views missing in floating widget layout");
            stopSelf();
            return;
        }

        mFloatingView.findViewById(R.id.btn_close_widget).setOnClickListener(view -> stopSelf());

        btnStartSpam.setOnClickListener(view -> {
            if (!SpamAccessibilityService.isServiceActive()) {
                Toast.makeText(this, R.string.error_enable_accessibility, Toast.LENGTH_LONG).show();
                return;
            }
            
            // Validate message is not empty before starting
            String msg = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                    .getString(Constants.KEY_MESSAGE, "");
            if (msg == null || msg.trim().isEmpty()) {
                Toast.makeText(this, R.string.error_empty_message, Toast.LENGTH_SHORT).show();
                return;
            }
            
            updateUI(true);
            SpamAccessibilityService.startSpam(this, new SpamAccessibilityService.SpamCallback() {
                @Override
                public void onProgress(int current, int total) {
                    tvStatus.setText(getString(R.string.status_sent, current, total));
                }

                @Override
                public void onFinished() {
                    updateUI(false);
                }
            });
        });

        btnStopSpam.setOnClickListener(view -> {
            SpamAccessibilityService.stopSpam();
            updateUI(false);
        });

        // Make the entire widget draggable
        mFloatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isMoving = false;
            private static final int TOUCH_SLOP = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);

                        if (isMoving || diffX > TOUCH_SLOP || diffY > TOUCH_SLOP) {
                            isMoving = true;
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            mWindowManager.updateViewLayout(mFloatingView, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isMoving) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });


        // Ensure the drag handle icon also indicates draggability (optional but good for UX)
        View btnDrag = mFloatingView.findViewById(R.id.btn_drag);
        if (btnDrag != null) {
            btnDrag.setOnTouchListener(null); // Clear previous listener if any, root handles it
        }
    }

    private void updateUI(boolean isSpamming) {
        if (isSpamming) {
            btnStartSpam.setVisibility(View.GONE);
            btnStopSpam.setVisibility(View.VISIBLE);
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(R.string.status_sending);
        } else {
            btnStartSpam.setVisibility(View.VISIBLE);
            btnStopSpam.setVisibility(View.GONE);
            tvStatus.setVisibility(View.GONE);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Ensure widget stays within screen bounds after rotation
        if (mFloatingView != null && mWindowManager != null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
            
            // Re-calculate bounds to ensure accessibility after rotation
            int viewWidth = mFloatingView.getWidth();
            int viewHeight = mFloatingView.getHeight();
            
            if (viewWidth == 0) viewWidth = 200; // Fallback if view not measured
            if (viewHeight == 0) viewHeight = 200;

            if (params.x + viewWidth > metrics.widthPixels) {
                params.x = Math.max(0, metrics.widthPixels - viewWidth);
            }
            if (params.y + viewHeight > metrics.heightPixels) {
                params.y = Math.max(0, metrics.heightPixels - viewHeight);
            }
            mWindowManager.updateViewLayout(mFloatingView, params);
        }
    }

    private void setupForeground() {
        String channelId = "spamit_widget_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "SPAMit Widget",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.widget_notification_title))
                .setContentText(getString(R.string.widget_notification_desc))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mFloatingView);
            } catch (Exception e) {
                // View already removed or never attached
            }
        }
        SpamAccessibilityService.stopSpam();
    }
}
