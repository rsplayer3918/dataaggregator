import org.dreambot.api.methods.Randoms;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.utilities.Sleep;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.awt.Point;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * ML-Enhanced Behavior Profile for adaptive antiban logic.
 * Simplified version that works without complex dependencies.
 */
public class BehaviorProfile {

    // Basic behavior probabilities (0.0 to 1.0)
    private double mouseMoveProbability;
    private double tabSwitchProbability;
    private double idleProbability;
    private double keystrokeProbability;
    private double backspaceProbability;

    // Timing and movement bounds
    private int minMouseX, maxMouseX;
    private int minMouseY, maxMouseY;
    private int minSleepTime, maxSleepTime;

    // ML-enhanced timing patterns
    private List<Integer> historicalSleepTimes = new ArrayList<>();
    private List<Long> actionTimestamps = new ArrayList<>();
    private Map<String, Double> contextualProbabilities = new HashMap<>();

    // Advanced behavioral patterns
    private Map<Tab, Integer> tabSwitchFrequency = new HashMap<>();
    private List<Point> mouseMovementHistory = new ArrayList<>();
    private Map<String, List<Double>> behaviorClusters = new HashMap<>();

    // Risk assessment and learning
    private double currentRiskLevel = 0.5; // 0 = safe, 1 = high risk
    private int consecutiveSuccessfulSessions = 0;
    private long totalPlayTime = 0;

    // Timing logic
    private long lastUpdateTime;
    private long updateInterval;
    private String accountId;
    private int banCount;

    // ML Learning parameters
    private double learningRate = 0.1;
    private double explorationRate = 0.15; // For epsilon-greedy exploration
    private static final int MAX_HISTORY_SIZE = 1000;

    // Constructor — prefer loading via `loadForAccount`
    private BehaviorProfile() {
        initializeDefaultClusters();
    }

    /**
     * Load a profile for a specific account from file.
     */
    public static BehaviorProfile loadForAccount(String accountId) {
        File profileFile = new File("profiles/" + accountId + ".json");
        if (!profileFile.exists()) {
            BehaviorProfile profile = defaultProfile();
            profile.setAccountId(accountId);
            return profile;
        }

        try (FileReader reader = new FileReader(profileFile)) {
            Gson gson = new GsonBuilder().create();
            BehaviorProfile profile = gson.fromJson(reader, BehaviorProfile.class);
            profile.lastUpdateTime = System.currentTimeMillis();
            profile.accountId = accountId;
            if (profile.historicalSleepTimes == null) profile.historicalSleepTimes = new ArrayList<>();
            if (profile.actionTimestamps == null) profile.actionTimestamps = new ArrayList<>();
            if (profile.contextualProbabilities == null) profile.contextualProbabilities = new HashMap<>();
            if (profile.tabSwitchFrequency == null) profile.tabSwitchFrequency = new HashMap<>();
            if (profile.mouseMovementHistory == null) profile.mouseMovementHistory = new ArrayList<>();
            if (profile.behaviorClusters == null) profile.behaviorClusters = new HashMap<>();
            profile.initializeDefaultClusters();
            return profile;
        } catch (IOException e) {
            System.err.println("Failed to load behavior profile for " + accountId + ": " + e.getMessage());
            BehaviorProfile profile = defaultProfile();
            profile.setAccountId(accountId);
            return profile;
        }
    }

    /**
     * Creates a fallback default behavior profile with ML initialization.
     */
    public static BehaviorProfile defaultProfile() {
        BehaviorProfile p = new BehaviorProfile();

        p.mouseMoveProbability = 0.2;
        p.tabSwitchProbability = 0.1;
        p.idleProbability = 0.4;
        p.keystrokeProbability = 0.2;
        p.backspaceProbability = 0.1;

        p.minMouseX = 0;
        p.maxMouseX = 765;
        p.minMouseY = 0;
        p.maxMouseY = 503;

        p.minSleepTime = 300;
        p.maxSleepTime = 1500;

        p.updateInterval = 60000; // 1 minute
        p.lastUpdateTime = System.currentTimeMillis();
        p.banCount = 0;

        return p;
    }

    private void initializeDefaultClusters() {
        // Initialize behavior clusters for different contexts
        behaviorClusters.put("idle", Arrays.asList(0.6, 0.05, 0.3, 0.05, 0.0));
        behaviorClusters.put("active", Arrays.asList(0.3, 0.15, 0.2, 0.25, 0.1));
        behaviorClusters.put("risky", Arrays.asList(0.1, 0.05, 0.8, 0.05, 0.0));
        behaviorClusters.put("normal", Arrays.asList(0.2, 0.1, 0.4, 0.2, 0.1));
    }

    /**
     * Updates the profile if it has expired.
     */
    public void updateIfNeeded(long now) {
        if (now - lastUpdateTime > updateInterval) {
            // Update learning parameters
            updateLearningParameters();

            // Decay exploration rate over time
            explorationRate = Math.max(0.05, explorationRate * 0.995);

            // Record action timestamp for temporal pattern learning
            actionTimestamps.add(now);
            trimHistory(actionTimestamps, MAX_HISTORY_SIZE);

            lastUpdateTime = now;
        }
    }

    private void updateLearningParameters() {
        // Adjust learning rate based on success/failure patterns
        if (banCount == 0 && consecutiveSuccessfulSessions > 50) {
            learningRate *= 0.95; // Reduce learning rate when stable
        } else if (banCount > 0) {
            learningRate = Math.min(0.3, learningRate * 1.1); // Increase learning rate after bans
        }
    }

    /**
     * ML-predicted action probabilities based on current context and learning
     */
    public ActionProbabilities getActionProbabilities() {
        ActionProbabilities probs = new ActionProbabilities();

        // Get base probabilities from appropriate cluster
        String clusterKey = selectBehaviorCluster();
        List<Double> clusterProbs = behaviorClusters.get(clusterKey);

        if (clusterProbs != null && clusterProbs.size() >= 5) {
            probs.mouseMove = clusterProbs.get(0);
            probs.tabSwitch = clusterProbs.get(1);
            probs.idle = clusterProbs.get(2);
            probs.keystroke = clusterProbs.get(3);
            probs.backspace = clusterProbs.get(4);
        } else {
            // Fallback to basic probabilities
            probs.mouseMove = mouseMoveProbability;
            probs.tabSwitch = tabSwitchProbability;
            probs.idle = idleProbability;
            probs.keystroke = keystrokeProbability;
            probs.backspace = backspaceProbability;
        }

        // Apply risk-based modifications
        if (currentRiskLevel > 0.7) {
            // High risk - be more idle, less active
            probs.idle *= 1.5;
            probs.mouseMove *= 0.5;
            probs.tabSwitch *= 0.3;
            probs.keystroke *= 0.2;
        }

        // Normalize probabilities
        double total = probs.getTotalProbability();
        if (total > 1.0) {
            probs.mouseMove /= total;
            probs.tabSwitch /= total;
            probs.idle /= total;
            probs.keystroke /= total;
            probs.backspace /= total;
        }

        return probs;
    }

    private String selectBehaviorCluster() {
        if (currentRiskLevel > 0.8) return "risky";
        if (currentRiskLevel < 0.3) return "idle";
        if (Math.random() < 0.7) return "normal";
        return "active";
    }

    /**
     * ML-predicted mouse movement based on historical patterns
     */
    public MouseMovement predictMouseMovement(Point current) {
        // Analyze historical mouse movements to predict realistic next move
        if (mouseMovementHistory.size() > 5) {
            // Use recent movement patterns to predict next movement
            Point avgMovement = calculateAverageMovement();
            int targetX = current.x + (int)(avgMovement.x * (0.5 + Math.random()));
            int targetY = current.y + (int)(avgMovement.y * (0.5 + Math.random()));

            // Add some randomness
            targetX += ThreadLocalRandom.current().nextInt(-50, 51);
            targetY += ThreadLocalRandom.current().nextInt(-50, 51);

            return new MouseMovement(
                    new Point(targetX, targetY),
                    Math.random() < 0.8
            );
        }

        // Fallback to random movement
        return new MouseMovement(
                new Point(randomX(), randomY()),
                Math.random() < mouseMoveProbability
        );
    }

    private Point calculateAverageMovement() {
        if (mouseMovementHistory.size() < 2) return new Point(0, 0);

        int avgX = 0, avgY = 0;
        int count = Math.min(10, mouseMovementHistory.size() - 1);

        for (int i = mouseMovementHistory.size() - count; i < mouseMovementHistory.size() - 1; i++) {
            Point current = mouseMovementHistory.get(i);
            Point next = mouseMovementHistory.get(i + 1);
            avgX += (next.x - current.x);
            avgY += (next.y - current.y);
        }

        return new Point(avgX / count, avgY / count);
    }

    /**
     * ML-enhanced tab switching prediction
     */
    public Tab predictBestTabSwitch(Tab currentTab, Tab[] possibleTabs) {
        // Use frequency analysis to predict most human-like tab switch
        Map<Tab, Double> tabScores = new HashMap<>();

        for (Tab tab : possibleTabs) {
            if (tab == currentTab) continue;

            int frequency = tabSwitchFrequency.getOrDefault(tab, 0);
            double score = frequency + Math.random() * 5; // Add randomness

            // Prefer tabs that are commonly used but not overused
            if (frequency > 0 && frequency < 20) {
                score += 10;
            }

            tabScores.put(tab, score);
        }

        // Select tab with highest score
        return tabScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(possibleTabs[ThreadLocalRandom.current().nextInt(possibleTabs.length)]);
    }

    /**
     * ML-enhanced idle behavior selection
     */
    public IdleBehavior selectIdleBehavior() {
        // Use temporal patterns to select realistic idle behavior
        long timeSinceLastAction = getTimeSinceLastAction();
        return IdleBehavior.selectForContext(timeSinceLastAction, currentRiskLevel);
    }

    private long getTimeSinceLastAction() {
        if (actionTimestamps.isEmpty()) return 0;
        return System.currentTimeMillis() - actionTimestamps.get(actionTimestamps.size() - 1);
    }

    /**
     * Record action outcome for reinforcement learning
     */
    public void recordActionOutcome(long timestamp) {
        // This would be called after each action to learn from results
        // For now, just record successful completion
        consecutiveSuccessfulSessions++;
        totalPlayTime += (timestamp - lastUpdateTime);
        lastUpdateTime = timestamp;

        // Gradually reduce risk level with successful outcomes
        if (consecutiveSuccessfulSessions > 10) {
            currentRiskLevel *= 0.95;
        }
    }

    /**
     * ML-enhanced realistic keystroke prediction
     */
    public char predictRealisticKeystroke() {
        // Use typing patterns to generate realistic keystrokes
        // For now, favor common keys
        char[] commonKeys = {'a', 'e', 'i', 'o', 'u', 's', 't', 'n', 'r'};
        if (Math.random() < 0.7) {
            return commonKeys[ThreadLocalRandom.current().nextInt(commonKeys.length)];
        }
        return randomChar();
    }

    /**
     * Get ML-predicted timing values
     */
    public int getTabSwitchDelay() {
        return predictTiming("tab_switch", 200, 800);
    }

    public int getShortIdleTime() {
        return predictTiming("short_idle", 500, 2000);
    }

    public int getLongIdleTime() {
        return predictTiming("long_idle", 2000, 8000);
    }

    public long getAFKDuration() {
        return predictTiming("afk", 5000, 30000);
    }

    private int predictTiming(String action, int min, int max) {
        // Use historical data to predict realistic timing
        if (historicalSleepTimes.size() > 10) {
            int avgTime = historicalSleepTimes.stream()
                    .mapToInt(Integer::intValue)
                    .sum() / historicalSleepTimes.size();

            // Apply some bounds and randomness
            avgTime = Math.max(min, Math.min(max, avgTime));
            return avgTime + ThreadLocalRandom.current().nextInt(-avgTime/4, avgTime/4);
        }

        return ThreadLocalRandom.current().nextInt(min, max);
    }

    /**
     * Enhanced auto-update with ML adaptations
     */
    public void autoUpdateOnExit(boolean wasBanned) {
        if (wasBanned) {
            banCount++;
            currentRiskLevel = Math.min(1.0, currentRiskLevel + 0.3);
            consecutiveSuccessfulSessions = 0;

            // ML adaptation: shift toward safer behavior clusters
            adaptBehaviorAfterBan();

        } else {
            consecutiveSuccessfulSessions++;

            // ML adaptation: gradually normalize behavior
            adaptBehaviorAfterSuccess();
        }

        lastUpdateTime = System.currentTimeMillis();
        saveProfile();
    }

    private void adaptBehaviorAfterBan() {
        // Shift probabilities toward safer behavior
        List<Double> safeBehavior = Arrays.asList(0.1, 0.05, 0.8, 0.05, 0.0);
        behaviorClusters.put("normal", interpolateBehaviors(
                behaviorClusters.get("normal"), safeBehavior, 0.3));

        // Increase minimum sleep times
        minSleepTime = Math.min(2000, minSleepTime + 200);
        maxSleepTime = Math.min(5000, maxSleepTime + 500);
    }

    private void adaptBehaviorAfterSuccess() {
        // Gradually move back toward default behavior
        List<Double> defaultBehavior = Arrays.asList(0.2, 0.1, 0.4, 0.2, 0.1);
        behaviorClusters.put("normal", interpolateBehaviors(
                behaviorClusters.get("normal"), defaultBehavior, 0.05));
    }

    private List<Double> interpolateBehaviors(List<Double> current, List<Double> target, double rate) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < Math.min(current.size(), target.size()); i++) {
            double interpolated = current.get(i) * (1 - rate) + target.get(i) * rate;
            result.add(interpolated);
        }
        return result;
    }

    private void trimHistory(List<?> history, int maxSize) {
        while (history.size() > maxSize) {
            history.remove(0);
        }
    }

    /**
     * Save the enhanced profile data back to the file system.
     */
    public void saveProfile() {
        if (accountId == null) return;

        File profileFile = new File("profiles/" + accountId + ".json");
        profileFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(profileFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("Failed to save behavior profile for " + accountId + ": " + e.getMessage());
        }
    }

    // ----------- Existing Utility Methods (Enhanced) -----------

    public int randomX() {
        int x = Randoms.random(minMouseX, maxMouseX);
        mouseMovementHistory.add(new Point(x, randomY()));
        trimHistory(mouseMovementHistory, MAX_HISTORY_SIZE);
        return x;
    }

    public int randomY() {
        return Randoms.random(minMouseY, maxMouseY);
    }

    public void sleepRandom() {
        int sleepTime = Randoms.random(minSleepTime, maxSleepTime);
        historicalSleepTimes.add(sleepTime);
        trimHistory(historicalSleepTimes, MAX_HISTORY_SIZE);

        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {}
    }

    public void shortSleep() {
        int sleepTime = Randoms.random(100, 250);
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {}
    }

    public char randomChar() {
        return (char) Randoms.random(97, 122); // a-z
    }

    public void updateContext(GameContext context) {
        if (context == null) return;

        String contextKey = context.generateContextKey();
        double riskFactor = context.getRiskFactor();

        // Blend contextual probabilities
        contextualProbabilities.put(contextKey,
                contextualProbabilities.getOrDefault(contextKey, 0.5) * (1 - learningRate) + riskFactor * learningRate
        );

        // Optionally adjust risk level
        currentRiskLevel = (currentRiskLevel + riskFactor) / 2;
    }
    public void simulateTabSwitch() {
        try {
            Tab currentTab = Tabs.getOpen();
            Tab[] possibleTabs = {Tab.INVENTORY, Tab.SKILLS, Tab.COMBAT};
            Tab targetTab = possibleTabs[ThreadLocalRandom.current().nextInt(possibleTabs.length)];

            if (targetTab != currentTab && Tabs.open(targetTab)) {
                tabSwitchFrequency.merge(targetTab, 1, Integer::sum);
                Sleep.sleep(getTabSwitchDelay());

                // Sometimes switch back
                if (Math.random() < 0.3) {
                    Sleep.sleep(200, 500);
                    Tabs.open(currentTab);
                }
            }
        } catch (Exception e) {
            sleepRandom(); // Fallback
        }
    }

    // ----------- Getters and Setters -----------

    public double getMouseMoveProbability() { return mouseMoveProbability; }
    public double getTabSwitchProbability() { return tabSwitchProbability; }
    public double getIdleProbability() { return idleProbability; }
    public double getKeystrokeProbability() { return keystrokeProbability; }
    public double getBackspaceProbability() { return backspaceProbability; }
    public double getCurrentRiskLevel() { return currentRiskLevel; }
    public int getBanCount() { return banCount; }
    public double getLearningRate() { return learningRate; }

    public void setAccountId(String id) { this.accountId = id; }
    public String getAccountId() { return accountId; }
}