package com.antinuke.bot.utils;

/**
 * Execution Timer - Tracks command execution time
 */
public class ExecutionTimer {
    private final long startTime;
    
    public ExecutionTimer() {
        this.startTime = System.currentTimeMillis();
    }
    
    public long getElapsedMillis() {
        return System.currentTimeMillis() - startTime;
    }
    
    public String getElapsedString() {
        long millis = getElapsedMillis();
        if (millis < 1000) {
            return String.format("%.0f ms", (double) millis);
        } else {
            return String.format("%.2f s", millis / 1000.0);
        }
    }
    
    public String getFooterText() {
        return "Action executed in " + getElapsedString();
    }
}
