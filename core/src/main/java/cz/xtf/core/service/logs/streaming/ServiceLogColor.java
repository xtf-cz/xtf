package cz.xtf.core.service.logs.streaming;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This enum contains the colors that are used when logging container's log on the console; each container gets a
 * different color in order to visually differentiate containers; the background is also colored in order to
 * differentiate application log from containers log.
 */
public enum ServiceLogColor {
    ANSI_RESET_FG("\u001B[0m"),
    ANSI_RESET_BG("\u001b[0m"),
    ANSI_POD_LOG_BG("\u001b[40m"),
    ANSI_RED1("\u001B[31m"),
    ANSI_GREEN1("\u001B[32m"),
    ANSI_YELLOW1("\u001B[33m"),
    ANSI_AZURE1("\u001B[34m"),
    ANSI_VIOLET1("\u001B[35m"),
    ANSI_WATER1("\u001B[36m"),
    ANSI_BRIGHT_RED("\u001B[91m"),
    ANSI_BRIGHT_GREEN("\u001B[92m"),
    ANSI_BRIGHT_YELLOW("\u001B[93m"),
    ANSI_BRIGHT_BLUE("\u001B[94m"),
    ANSI_BRIGHT_PURPLE("\u001B[95m"),
    ANSI_BRIGHT_CYAN("\u001B[96m"),
    ANSI_BRIGHT_WHITE("\u001B[97m"),
    ANSI_POD_NAME_BG("\u001b[40;3m");

    public final String value;
    private static final Lock lock = new ReentrantLock();
    private static int idx = 0;

    public static final ServiceLogColor[] COLORS = {
            ANSI_BRIGHT_GREEN, ANSI_BRIGHT_RED, ANSI_BRIGHT_YELLOW,
            ANSI_BRIGHT_BLUE, ANSI_BRIGHT_PURPLE, ANSI_BRIGHT_CYAN, ANSI_BRIGHT_WHITE,
            ANSI_RED1, ANSI_GREEN1, ANSI_YELLOW1, ANSI_AZURE1, ANSI_VIOLET1, ANSI_WATER1
    };

    private ServiceLogColor(String value) {
        this.value = value;
    }

    public static ServiceLogColor getNext() {
        lock.lock();
        try {
            if (++idx >= COLORS.length) {
                idx = 0;
            }
            return COLORS[idx];
        } finally {
            lock.unlock();
        }
    }
}
