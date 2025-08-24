import org.dreambot.api.methods.tabs.Tab;

/**
 * Represents the current game context for ML decision making
 */
public class GameContext {
    public boolean isAnimating;
    public boolean isMoving;
    public int healthPercent;
    public long timestamp;
    public Tab currentTab;

    public GameContext(boolean animating, boolean moving, int health, long time, Tab tab) {
        this.isAnimating = animating;
        this.isMoving = moving;
        this.healthPercent = health;
        this.timestamp = time;
        this.currentTab = tab;
    }

    /**
     * Generate a context key for learning
     */
    public String generateContextKey() {
        return String.format("%s_%s_%s",
                isAnimating ? "anim" : "idle",
                isMoving ? "move" : "still",
                currentTab != null ? currentTab.name() : "unknown");
    }

    /**
     * Calculate risk factor based on context
     */
    public double getRiskFactor() {
        double riskFactor = 0.0;

        // Higher risk if consistently performing same actions
        if (isAnimating && !isMoving) {
            riskFactor += 0.1;
        }

        // Risk based on health (low health = more attention)
        if (healthPercent < 50) {
            riskFactor += 0.2;
        }

        return Math.max(0.0, Math.min(1.0, riskFactor));
    }
}