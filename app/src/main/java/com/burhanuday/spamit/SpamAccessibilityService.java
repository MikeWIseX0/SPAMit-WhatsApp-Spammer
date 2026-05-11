package com.burhanuday.spamit;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import android.content.Intent;
import android.annotation.SuppressLint;
import java.util.List;

public class SpamAccessibilityService extends AccessibilityService {

    private static SpamAccessibilityService instance;
    private boolean isSpamming = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler backgroundHandler;
    private HandlerThread handlerThread;
    private int counter = 0;
    private int totalToSend = 0;
    private String message = "";
    private SpamCallback callback;
    
    private AccessibilityNodeInfo activeRoot;
    private AccessibilityNodeInfo activeEditText;
    private AccessibilityNodeInfo activeSendButton;
    
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private int delayMs = Constants.DEFAULT_DELAY_MS;
    private boolean shouldVibrate = true;
    private boolean sendReferral = false;

    public interface SpamCallback {
        void onProgress(int current, int total);
        void onFinished();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handlerThread = new HandlerThread("SpamEngine");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mainHandler.removeCallbacksAndMessages(null);
        if (backgroundHandler != null) backgroundHandler.removeCallbacksAndMessages(null);
        stopSpamming();
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        instance = null;
        callback = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopSpamming();
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        instance = null;
        callback = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used actively since we poll the UI in our loop
    }

    @Override
    public void onInterrupt() {
        stopSpamming();
    }

    public static boolean isServiceActive() {
        return instance != null;
    }

    public static void startSpam(Context context, SpamCallback callback) {
        if (instance != null && !instance.isSpamming) {
            instance.startSpamming(context, callback);
        }
    }

    public static void stopSpam() {
        if (instance != null) {
            instance.stopSpamming();
        }
    }

    private void startSpamming(Context context, SpamCallback cb) {
        if (isSpamming) return;

        prefs = context.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        message = prefs.getString(Constants.KEY_MESSAGE, Constants.DEFAULT_MESSAGE);
        totalToSend = prefs.getInt(Constants.KEY_COUNT, Constants.DEFAULT_COUNT);
        delayMs = prefs.getInt(Constants.KEY_DELAY_MS, Constants.DEFAULT_DELAY_MS);
        shouldVibrate = prefs.getBoolean(Constants.KEY_VIBRATE, true);
        sendReferral = prefs.getBoolean(Constants.KEY_LAST_MESSAGE, true);
        
        counter = 0;
        callback = cb;
        isSpamming = true;

        backgroundHandler.post(spamRunnable);
    }

    private void stopSpamming() {
        if (!isSpamming) return; // Prevent double-stop
        isSpamming = false;
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacks(spamRunnable);
        }
        mainHandler.removeCallbacksAndMessages(null);
        recycleActiveNodes();
        if (callback != null) {
            callback.onFinished();
            callback = null;
        }
    }

    private void recycleActiveNodes() {
        if (activeEditText != null) {
            activeEditText.recycle();
            activeEditText = null;
        }
        if (activeSendButton != null) {
            activeSendButton.recycle();
            activeSendButton = null;
        }
        if (activeRoot != null) {
            activeRoot.recycle();
            activeRoot = null;
        }
    }

    private void sendReferralMessage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            stopSpamming();
            return;
        }

        AccessibilityNodeInfo editText = findEditText(root);
        AccessibilityNodeInfo sendButton = findSendButton(root);

        if (editText != null && sendButton != null) {
            activeRoot = root;
            activeEditText = editText;
            activeSendButton = sendButton;

            Bundle arguments = new Bundle();
            String referralMsg = getString(R.string.referral_message);
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, referralMsg);
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            
            // Synchronous referral send
            if (sendButton != null) {
                sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            stopSpamming();
        } else {
            if (editText != null) editText.recycle();
            if (sendButton != null) sendButton.recycle();
            root.recycle();
            stopSpamming(); // Fallback stop
        }
    }

    private final Runnable spamRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSpamming) return;

            // Pause if screen is off
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                backgroundHandler.postDelayed(this, 1000);
                return;
            }

            if (counter >= totalToSend) {
                if (isSpamming && sendReferral) {
                    mainHandler.post(SpamAccessibilityService.this::sendReferralMessage);
                } else {
                    mainHandler.post(SpamAccessibilityService.this::stopSpamming);
                }
                return;
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                backgroundHandler.postDelayed(this, 100);
                return;
            }

            AccessibilityNodeInfo editText = findEditText(root);
            if (editText != null) {
                activeRoot = root;
                activeEditText = editText;

                // 1. Set the text
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message);
                boolean setSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

                if (setSuccess) {
                    // 2. Try Enter as Send (Legacy Trigger)
                    // ACTION_IME_ENTER is API 30+
                    boolean sentViaEnter = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        sentViaEnter = editText.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                    }

                    if (sentViaEnter) {
                        // Fast Path: Successfully triggered IME send
                        finalizeMessageCycle();
                    } else {
                        // Slow Path: Fallback to finding and clicking the send button
                        activeSendButton = findSendButton(root);
                        if (activeSendButton != null) {
                            // Small delay for the button to appear/transition after text set
                            backgroundHandler.postDelayed(() -> {
                                if (isSpamming && activeSendButton != null) {
                                    activeSendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    finalizeMessageCycle();
                                } else {
                                    recycleActiveNodes();
                                    backgroundHandler.post(spamRunnable);
                                }
                            }, delayMs == 0 ? 10 : 30); // Adaptive delay
                        } else {
                            // Could not find send button, try again
                            recycleActiveNodes();
                            backgroundHandler.postDelayed(spamRunnable, 50);
                        }
                    }
                } else {
                    recycleActiveNodes();
                    backgroundHandler.postDelayed(this, 100);
                }
            } else {
                if (root != null) root.recycle();
                backgroundHandler.postDelayed(this, 100);
            }
        }
    };

    /**
     * Completes the current message cycle: increments counter, vibrates, updates UI, and schedules next run.
     * Must be called on background thread.
     */
    private void finalizeMessageCycle() {
        if (!isSpamming) return;

        if (shouldVibrate && vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(5);
            }
        }

        counter++;
        final int c = counter;
        final int t = totalToSend;
        mainHandler.post(() -> {
            if (callback != null) callback.onProgress(c, t);
        });

        if (counter % 20 == 0 || counter >= totalToSend) {
            prefs.edit().putInt(Constants.KEY_COUNTER, counter).apply();
        }

        recycleActiveNodes();

        if (delayMs > 0) {
            backgroundHandler.postDelayed(spamRunnable, delayMs);
        } else {
            // Burst mode - zero delay
            backgroundHandler.post(spamRunnable);
        }
    }


    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo root) {
        if (root == null) return null;
        
        // Priority 1: Fast ID-based lookup
        String[] ids = {"com.whatsapp:id/entry", "com.whatsapp.w4b:id/entry"};
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo target = AccessibilityNodeInfo.obtain(nodes.get(0));
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return target;
            }
        }
        
        // Priority 2: Recursive fallback
        return findEditTextRecursive(root);
    }

    private AccessibilityNodeInfo findEditTextRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        CharSequence className = node.getClassName();
        if (className != null && className.toString().contains("EditText")) {
            return AccessibilityNodeInfo.obtain(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            
            AccessibilityNodeInfo result = findEditTextRecursive(child);
            child.recycle();
            if (result != null) return result;
        }
        return null;
    }

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root) {
        if (root == null) return null;
        
        // Fast View ID search (Optimized)
        String[] possibleIds = {"com.whatsapp:id/send", "com.whatsapp.w4b:id/send"};
        for (String id : possibleIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo target = AccessibilityNodeInfo.obtain(nodes.get(0));
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return target;
            }
        }
        
        // Fallback: Find by description
        return findSendButtonByDescription(root);
    }

    private AccessibilityNodeInfo findSendButtonByDescription(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String s = desc.toString().toLowerCase();
            // Filter: Must be a clickable image or button to avoid matching menus
            String className = String.valueOf(node.getClassName());
            boolean isClickable = node.isClickable();
            boolean isButton = className.contains("Button") || className.contains("ImageView");

            if (isClickable && isButton && (s.contains("send") || s.contains("kirim") || s.contains("enviar") || s.contains("envoy") || s.contains("mandar"))) {
                return node;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findSendButtonByDescription(child);
            if (result != null) {
                if (result != child) child.recycle(); // ONLY recycle if result is not the child itself
                return result;
            }
            if (child != null) child.recycle();
        }
        return null;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Physical Kill Switch - Consume both DOWN and UP for Volume Down
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && isSpamming) {
                mainHandler.post(() -> {
                    stopSpamming();
                    Toast.makeText(this, getString(R.string.emergency_stop_triggered), Toast.LENGTH_SHORT).show();
                });
            }
            return true; // Consume both events
        }
        return super.onKeyEvent(event);
    }
}
