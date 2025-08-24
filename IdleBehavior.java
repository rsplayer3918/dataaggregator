/**
 * Different types of idle behaviors for ML-based selection
 */
public enum IdleBehavior {
    SHORT_PAUSE("Short pause", 500, 2000),
    LONG_PAUSE("Long pause", 2000, 8000),
    MOUSE_WIGGLE("Mouse wiggle", 1000, 3000),
    AFK_SIM("AFK simulation", 5000, 30000);

    private final String description;
    private final int minDuration;
    private final int maxDuration;

    IdleBehavior(String description, int minDuration, int maxDuration) {
        this.description = description;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    /**
     * Get human-readable description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get minimum duration in milliseconds
     */
    public int getMinDuration() {
        return minDuration;
    }

    /**
     * Get maximum duration in milliseconds  
     */
    public int getMaxDuration() {
        return maxDuration;
    }

    /**
     * Get a random duration within the range
     */
    public int getRandomDuration() {
        return minDuration + (int)(Math.random() * (maxDuration - minDuration));
    }

    /**
     * Select an appropriate idle behavior based on context
     */
    public static IdleBehavior selectForContext(long timeSinceLastAction, double riskLevel) {
        // High risk = longer, less frequent actions
        if (riskLevel > 0.7) {
            return Math.random() < 0.8 ? LONG_PAUSE : AFK_SIM;
        }

        // Recent action = short pause
        if (timeSinceLastAction < 30000) { // Less than 30 seconds
            return SHORT_PAUSE;
        }
        // Medium time = varied behavior
        else if (timeSinceLastAction < 120000) { // Less than 2 minutes
            return Math.random() < 0.7 ? MOUSE_WIGGLE : LONG_PAUSE;
        }
        // Long time = AFK simulation
        else {
            return AFK_SIM;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%d-%dms)", description, minDuration, maxDuration);
    }
}