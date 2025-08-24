import org.dreambot.api.methods.interactive.Players;

/**
 * Main antiban controller that coordinates between different antiban strategies.
 * Integrates traditional rule-based antiban with ML-enhanced adaptive behavior.
 */
public class Antiban {

    private static BehaviorProfile currentProfile;
    private static long lastAntibanAction = 0;
    private static int antibanCallCount = 0;
    private static boolean initialized = false;

    // Configuration
    private static final long MIN_TIME_BETWEEN_ACTIONS = 5000; // 5 seconds
    private static final double BASE_ACTION_PROBABILITY = 0.15; // 15% chance per call
    private static final int CALLS_BEFORE_FORCED_ACTION = 200; // Force action after 200 calls

    /**
     * Initialize the antiban system with a behavior profile
     */
    public static void initialize(String accountId) {
        currentProfile = BehaviorProfile.loadForAccount(accountId);
        initialized = true;
        log("Antiban system initialized for account: " + accountId);
    }

    /**
     * Main antiban perform method - called from bot main loop
     */
    public static void perform() {
        if (!initialized) {
            log("WARNING: Antiban not initialized! Call Antiban.initialize(accountId) first.");
            return;
        }

        antibanCallCount++;

        try {
            // Don't perform antiban actions too frequently
            if (shouldPerformAction()) {
                AdaptiveAntiban.perform(currentProfile);
                lastAntibanAction = System.currentTimeMillis();
                antibanCallCount = 0; // Reset counter after action
            }

        } catch (Exception e) {
            log("Error in antiban perform: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determines if an antiban action should be performed this cycle
     */
    public static boolean shouldPerformAction() {
        long currentTime = System.currentTimeMillis();

        // Don't perform actions too frequently
        if (currentTime - lastAntibanAction < MIN_TIME_BETWEEN_ACTIONS) {
            return false;
        }

        // Force an action if it's been too long
        if (antibanCallCount >= CALLS_BEFORE_FORCED_ACTION) {
            log("Forcing antiban action after " + CALLS_BEFORE_FORCED_ACTION + " calls");
            return true;
        }

        // Don't interrupt critical animations unless it's been a very long time
        if (Players.getLocal() != null && Players.getLocal().isAnimating()) {
            if (antibanCallCount < CALLS_BEFORE_FORCED_ACTION * 0.8) {
                return false;
            }
        }

        // Use ML-enhanced probability calculation
        double actionProbability = calculateActionProbability();
        boolean shouldAct = Math.random() < actionProbability;

        if (shouldAct) {
            log("Performing antiban action (probability: " + String.format("%.3f", actionProbability) + ")");
        }

        return shouldAct;
    }

    /**
     * Calculate the probability of performing an antiban action based on various factors
     */
    private static double calculateActionProbability() {
        double probability = BASE_ACTION_PROBABILITY;

        // Increase probability based on how long since last action
        long timeSinceLastAction = System.currentTimeMillis() - lastAntibanAction;
        if (timeSinceLastAction > 30000) { // 30+ seconds
            probability *= 1.5;
        }
        if (timeSinceLastAction > 60000) { // 1+ minute
            probability *= 2.0;
        }

        // Increase probability based on call count
        if (antibanCallCount > CALLS_BEFORE_FORCED_ACTION * 0.5) {
            probability *= 1.3;
        }
        if (antibanCallCount > CALLS_BEFORE_FORCED_ACTION * 0.7) {
            probability *= 1.6;
        }

        // Use ML profile to adjust probability
        if (currentProfile != null) {
            double riskLevel = currentProfile.getCurrentRiskLevel();

            // Higher risk = less frequent actions but more idle behavior
            if (riskLevel > 0.7) {
                probability *= 0.7; // Reduce overall activity
            } else if (riskLevel < 0.3) {
                probability *= 1.2; // Can be more active when risk is low
            }
        }

        // Cap the probability
        return Math.min(0.8, probability);
    }

    /**
     * Update the behavior profile with current session data
     */
    public static void updateProfile() {
        if (currentProfile != null) {
            currentProfile.updateIfNeeded(System.currentTimeMillis());
        }
    }

    /**
     * Should be called when the script exits
     */
    public static void onScriptExit(boolean wasBanned) {
        if (currentProfile != null) {
            log("Updating behavior profile on exit. Banned: " + wasBanned);
            currentProfile.autoUpdateOnExit(wasBanned);
            log("Profile updated. Risk level: " + String.format("%.3f", currentProfile.getCurrentRiskLevel()));
            log("Ban count: " + currentProfile.getBanCount());
        }
    }

    /**
     * Get current behavior profile for external access
     */
    public static BehaviorProfile getCurrentProfile() {
        return currentProfile;
    }

    /**
     * Manual trigger for antiban action (for testing or special circumstances)
     */
    public static void forceAction() {
        if (currentProfile != null) {
            log("Manually triggering antiban action");
            AdaptiveAntiban.perform(currentProfile);
            lastAntibanAction = System.currentTimeMillis();
            antibanCallCount = 0;
        }
    }

    /**
     * Get statistics about antiban performance
     */
    public static String getStatistics() {
        if (currentProfile == null) return "Antiban not initialized";

        StringBuilder stats = new StringBuilder();
        stats.append("=== Antiban Statistics ===\n");
        stats.append("Risk Level: ").append(String.format("%.3f", currentProfile.getCurrentRiskLevel())).append("\n");
        stats.append("Ban Count: ").append(currentProfile.getBanCount()).append("\n");
        stats.append("Learning Rate: ").append(String.format("%.3f", currentProfile.getLearningRate())).append("\n");
        stats.append("Calls Since Last Action: ").append(antibanCallCount).append("\n");
        stats.append("Time Since Last Action: ").append(
                (System.currentTimeMillis() - lastAntibanAction) / 1000).append("s\n");
        stats.append("========================");

        return stats.toString();
    }

    /**
     * Reset antiban counters (useful for testing)
     */
    public static void reset() {
        antibanCallCount = 0;
        lastAntibanAction = 0;
        log("Antiban counters reset");
    }

    /**
     * Check if antiban system is healthy and functioning
     */
    public static boolean isHealthy() {
        if (!initialized) {
            log("WARNING: Antiban not initialized");
            return false;
        }

        if (currentProfile == null) {
            log("WARNING: No behavior profile loaded");
            return false;
        }

        // Check if we've been stuck without performing actions for too long
        long timeSinceAction = System.currentTimeMillis() - lastAntibanAction;
        if (timeSinceAction > 300000) { // 5 minutes
            log("WARNING: No antiban actions performed in " + (timeSinceAction/1000) + " seconds");
            return false;
        }

        return true;
    }

    /**
     * Logging utility
     */
    private static void log(String message) {
        System.out.println("[Antiban] " + message);
    }

    /**
     * Advanced: Set custom behavior profile (for testing or special accounts)
     */
    public static void setCustomProfile(BehaviorProfile profile) {
        currentProfile = profile;
        initialized = true;
        log("Custom behavior profile loaded");
    }

    /**
     * Advanced: Get detailed behavior probabilities for current context
     */
    public static ActionProbabilities getCurrentActionProbabilities() {
        if (currentProfile != null) {
            return currentProfile.getActionProbabilities();
        }
        return null;
    }
}