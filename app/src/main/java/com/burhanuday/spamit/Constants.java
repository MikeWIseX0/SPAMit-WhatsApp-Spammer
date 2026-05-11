package com.burhanuday.spamit;

/**
 * Application-wide constants for SharedPreferences keys, custom key codes,
 * and configuration limits.
 */
public final class Constants {

    private Constants() {} // Prevent instantiation

    // SharedPreferences file name
    public static final String PREFS_NAME = "com.burhanuday.spamit";

    // SharedPreferences keys
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_COUNT = "count";
    public static final String KEY_TO_SEND = "toSend";
    public static final String KEY_COUNTER = "counter";
    public static final String KEY_VIBRATE = "vibrate";
    public static final String KEY_LAST_MESSAGE = "last_message";
    public static final String KEY_FIRST_RUN = "firstrun";
    public static final String KEY_DELAY_MS = "delay_ms";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_FOLLOW_SYSTEM = "follow_system";

    // Default values
    public static final String DEFAULT_MESSAGE = "SPAMit!";
    public static final int DEFAULT_COUNT = 30;
    public static final int MAX_COUNT = 500;
    public static final int MIN_COUNT = 1;
    public static final int DEFAULT_DELAY_MS = 50;
    public static final int MIN_DELAY_MS = 0;
    public static final int MAX_DELAY_MS = 2000;


    // Message history
    public static final String KEY_HISTORY_PREFIX = "history_";
    public static final String KEY_HISTORY_COUNT = "history_count";
    public static final int MAX_HISTORY = 5;
}
