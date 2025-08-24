import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import com.google.gson.*;

/**
 * Data collection system for ML training
 */
public class DataCollector {
    private Queue<TrainingData> dataQueue;
    private List<NeuralBehaviorEngine.GameState> stateHistory;
    private List<BehaviorDecision> decisionHistory;
    private List<Double> reactionTimes;
    private Map<String, Integer> actionCounts;
    private long sessionStartTime;
    private static final int MAX_HISTORY = 10000;

    public DataCollector() {
        dataQueue = new ConcurrentLinkedQueue<>();
        stateHistory = Collections.synchronizedList(new ArrayList<>());
        decisionHistory = Collections.synchronizedList(new ArrayList<>());
        reactionTimes = Collections.synchronizedList(new ArrayList<>());
        actionCounts = new ConcurrentHashMap<>();
        sessionStartTime = System.currentTimeMillis();
    }

    public void recordState(NeuralBehaviorEngine.GameState state) {
        stateHistory.add(state);
        if (stateHistory.size() > MAX_HISTORY) {
            stateHistory.remove(0);
        }
    }

    public void recordDecision(BehaviorDecision decision) {
        decisionHistory.add(decision);
        actionCounts.merge(decision.actionType, 1, Integer::sum);

        if (decisionHistory.size() > MAX_HISTORY) {
            decisionHistory.remove(0);
        }
    }

    public void recordReactionTime(double time) {
        reactionTimes.add(time);
        if (reactionTimes.size() > 1000) {
            reactionTimes.remove(0);
        }
    }

    public List<TrainingData> getRecentData() {
        List<TrainingData> recent = new ArrayList<>();
        int size = Math.min(100, stateHistory.size());

        for (int i = stateHistory.size() - size; i < stateHistory.size(); i++) {
            if (i >= 0 && i < decisionHistory.size()) {
                TrainingData data = new TrainingData();
                data.state = stateHistory.get(i);
                data.decision = decisionHistory.get(i);
                data.timestamp = System.currentTimeMillis();
                recent.add(data);
            }
        }

        return recent;
    }

    public List<TrainingData> getLastNActions(int n) {
        return getRecentData().stream()
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<NeuralBehaviorEngine.Experience> getExperiences() {
        // Convert state history to experiences for Q-learning
        List<NeuralBehaviorEngine.Experience> experiences = new ArrayList<>();

        for (int i = 0; i < stateHistory.size() - 1; i++) {
            if (i < decisionHistory.size()) {
                String currentState = stateHistory.get(i).getStateHash();
                String nextState = stateHistory.get(i + 1).getStateHash();
                NeuralBehaviorEngine.ActionType action =
                        NeuralBehaviorEngine.ActionType.valueOf(decisionHistory.get(i).actionType);

                // Calculate reward based on state transition
                double reward = calculateReward(stateHistory.get(i), stateHistory.get(i + 1));

                experiences.add(new NeuralBehaviorEngine.Experience(
                        currentState, action, reward, nextState
                ));
            }
        }

        return experiences;
    }

    private double calculateReward(NeuralBehaviorEngine.GameState current,
                                   NeuralBehaviorEngine.GameState next) {
        double reward = 0;

        // Positive rewards
        if (next.efficiencyScore > current.efficiencyScore) {
            reward += (next.efficiencyScore - current.efficiencyScore) * 10;
        }
        if (next.inventoryFullness > current.inventoryFullness) {
            reward += 0.5;
        }

        // Negative rewards
        if (next.currentRiskLevel > current.currentRiskLevel) {
            reward -= (next.currentRiskLevel - current.currentRiskLevel) * 5;
        }
        if (next.suspicionLevel > current.suspicionLevel) {
            reward -= 2;
        }

        return reward;
    }

    public NeuralBehaviorEngine.GameState getLastState() {
        return stateHistory.isEmpty() ? new NeuralBehaviorEngine.GameState() :
                stateHistory.get(stateHistory.size() - 1);
    }

    public NeuralBehaviorEngine.GameState getCurrentState() {
        return getLastState();
    }

    public int getActionCount() {
        return actionCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public long getTimeWindow() {
        return System.currentTimeMillis() - sessionStartTime;
    }

    public long getSessionDuration() {
        return getTimeWindow();
    }

    public List<Double> getReactionTimes() {
        return new ArrayList<>(reactionTimes);
    }

    /**
     * Save collected data for offline training
     */
    public void saveData(String filepath) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("states", stateHistory);
        data.put("decisions", decisionHistory);
        data.put("actionCounts", actionCounts);
        data.put("sessionDuration", getSessionDuration());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(data);
        Files.write(Paths.get(filepath), json.getBytes());
    }
}

/**
 * Model training system
 */
public class ModelTrainer {
    private int batchSize = 32;
    private double validationSplit = 0.2;
    private ExecutorService trainingExecutor;

    public ModelTrainer() {
        trainingExecutor = Executors.newFixedThreadPool(2);
    }

    public void trainNeuralNetwork(NeuralBehaviorEngine.BehaviorNeuralNetwork network,
                                   List<TrainingData> data) {
        if (data.isEmpty()) return;

        // Split data into training and validation
        int splitIndex = (int)(data.size() * (1 - validationSplit));
        List<TrainingData> trainingSet = data.subList(0, splitIndex);
        List<TrainingData> validationSet = data.subList(splitIndex, data.size());

        // Train in batches
        for (int i = 0; i < trainingSet.size(); i += batchSize) {
            int end = Math.min(i + batchSize, trainingSet.size());
            List<TrainingData> batch = trainingSet.subList(i, end);

            for (TrainingData sample : batch) {
                // Create action result from historical data
                NeuralBehaviorEngine.ActionResult result = createResultFromData(sample);
                network.train(sample.state, result);
            }
        }

        // Validate performance
        double validationLoss = validate(network, validationSet);
        System.out.println("Validation loss: " + validationLoss);
    }

    public void quickTrain(NeuralBehaviorEngine.BehaviorNeuralNetwork network,
                           List<TrainingData> recentData) {
        for (TrainingData data : recentData) {
            NeuralBehaviorEngine.ActionResult result = createResultFromData(data);
            network.train(data.state, result);
        }
    }

    public void updateQLearning(NeuralBehaviorEngine.QLearningAgent agent,
                                List<NeuralBehaviorEngine.Experience> experiences) {
        for (NeuralBehaviorEngine.Experience exp : experiences) {
            agent.update(exp.state, exp.action, exp.reward, exp.nextState);
        }
        agent.decayEpsilon();
    }

    private NeuralBehaviorEngine.ActionResult createResultFromData(TrainingData data) {
        NeuralBehaviorEngine.ActionResult result = new NeuralBehaviorEngine.ActionResult(
                NeuralBehaviorEngine.ActionType.valueOf(data.decision.actionType)
        );

        // Determine success based on state changes
        result.wasSuccessful = data.outcome != null ? data.outcome.wasSuccessful() : true;
        result.increasedRisk = data.state.currentRiskLevel > 0.5;
        result.resultedInBan = false; // From historical data
        result.efficiencyGain = data.state.efficiencyScore;

        return result;
    }

    private double validate(NeuralBehaviorEngine.BehaviorNeuralNetwork network,
                            List<TrainingData> validationSet) {
        double totalLoss = 0;

        for (TrainingData data : validationSet) {
            NeuralBehaviorEngine.ActionDecision prediction = network.predict(data.state);
            double loss = calculateLoss(prediction, data.decision);
            totalLoss += loss;
        }

        return totalLoss / validationSet.size();
    }

    private double calculateLoss(NeuralBehaviorEngine.ActionDecision predicted,
                                 BehaviorDecision actual) {
        // Simple loss calculation - can be enhanced
        return predicted.type.name().equals(actual.actionType) ? 0.0 : 1.0;
    }
}

/**
 * Performance analyzer for ML optimization
 */
public class PerformanceAnalyzer {
    private Map<String, Double> successRates;
    private Map<String, List<Double>> metricHistory;
    private List<ActionOutcome> outcomeHistory;
    private double neuralNetworkSuccess = 0.5;
    private double qLearningSuccess = 0.5;
    private int reportedPlayers = 0;
    private double modActivity = 0.1;
    private double banWaveIntensity = 0.0;

    public PerformanceAnalyzer() {
        successRates = new ConcurrentHashMap<>();
        metricHistory = new ConcurrentHashMap<>();
        outcomeHistory = Collections.synchronizedList(new ArrayList<>());
    }

    public void recordOutcome(BehaviorDecision decision, ActionOutcome outcome) {
        outcomeHistory.add(outcome);

        // Update success rates
        String actionType = decision.actionType;
        double currentRate = successRates.getOrDefault(actionType, 0.5);
        double newRate = outcome.wasSuccessful() ?
                currentRate * 0.9 + 0.1 : currentRate * 0.9;
        successRates.put(actionType, newRate);

        // Update ML system success rates
        if (decision.source != null) {
            if (decision.source.equals("neural")) {
                neuralNetworkSuccess = neuralNetworkSuccess * 0.95 +
                        (outcome.wasSuccessful() ? 0.05 : 0);
            } else if (decision.source.equals("qlearning")) {
                qLearningSuccess = qLearningSuccess * 0.95 +
                        (outcome.wasSuccessful() ? 0.05 : 0);
            }
        }

        // Keep history manageable
        if (outcomeHistory.size() > 10000) {
            outcomeHistory.remove(0);
        }
    }

    public double calculateMouseEntropy() {
        // Calculate entropy of mouse movement patterns
        List<ActionOutcome> mouseActions = outcomeHistory.stream()
                .filter(o -> o.actionType.contains("MOUSE"))
                .collect(java.util.stream.Collectors.toList());

        if (mouseActions.isEmpty()) return 0.5;

        // Calculate Shannon entropy
        Map<String, Integer> patterns = new HashMap<>();
        for (ActionOutcome action : mouseActions) {
            String pattern = action.getPattern();
            patterns.merge(pattern, 1, Integer::sum);
        }

        double entropy = 0;
        int total = mouseActions.size();
        for (int count : patterns.values()) {
            double probability = count / (double) total;
            if (probability > 0) {
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }

        return entropy / Math.log(patterns.size()) / Math.log(2); // Normalize
    }

    public double getClickAccuracy() {
        List<ActionOutcome> clicks = outcomeHistory.stream()
                .filter(o -> o.actionType.contains("CLICK"))
                .collect(java.util.stream.Collectors.toList());

        if (clicks.isEmpty()) return 0.95;

        long accurate = clicks.stream()
                .filter(ActionOutcome::wasAccurate)
                .count();

        return accurate / (double) clicks.size();
    }

    public double getReactionTimeVariance() {
        List<Double> reactionTimes = outcomeHistory.stream()
                .map(ActionOutcome::getReactionTime)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        if (reactionTimes.size() < 2) return 100.0;

        double mean = reactionTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(500.0);

        double variance = reactionTimes.stream()
                .mapToDouble(time -> Math.pow(time - mean, 2))
                .average()
                .orElse(100.0);

        return Math.sqrt(variance);
    }

    public double getAFKFrequency() {
        long afkActions = outcomeHistory.stream()
                .filter(o -> o.actionType.contains("IDLE") || o.actionType.contains("AFK"))
                .count();

        return afkActions / (double) Math.max(1, outcomeHistory.size());
    }

    public double getMistakeRate() {
        long mistakes = outcomeHistory.stream()
                .filter(ActionOutcome::wasMistake)
                .count();

        return mistakes / (double) Math.max(1, outcomeHistory.size());
    }

    public double getEfficiencyScore() {
        return outcomeHistory.stream()
                .mapToDouble(ActionOutcome::getEfficiencyGain)
                .average()
                .orElse(0.5);
    }

    public double getPatternRepetition() {
        if (outcomeHistory.size() < 10) return 0.0;

        // Look for repeated sequences
        List<String> sequence = outcomeHistory.stream()
                .limit(100)
                .map(o -> o.actionType)
                .collect(java.util.stream.Collectors.toList());

        Set<String> uniquePatterns = new HashSet<>();
        for (int len = 2; len <= 5; len++) {
            for (int i = 0; i <= sequence.size() - len; i++) {
                String pattern = String.join(",", sequence.subList(i, i + len));
                uniquePatterns.add(pattern);
            }
        }

        // Lower ratio = more repetition
        return uniquePatterns.size() / (double) Math.max(1, sequence.size());
    }

    public double getHumanlikeness() {
        double score = 0.0;

        // Factors that contribute to human-likeness
        score += getClickAccuracy() * 0.2; // Not perfect accuracy
        score += (1.0 - Math.abs(0.5 - calculateMouseEntropy())) * 0.2; // Moderate entropy
        score += Math.min(1.0, getReactionTimeVariance() / 200) * 0.2; // Variable reaction
        score += getAFKFrequency() * 0.2; // Some AFK behavior
        score += getMistakeRate() * 0.2; // Some mistakes

        return Math.min(1.0, score);
    }

    public double getConsistencyScore() {
        // Measure consistency of behavior patterns
        return 1.0 - getPatternRepetition();
    }

    public double getAdaptabilityScore() {
        // Measure how well the bot adapts to different situations
        Map<String, Integer> contextActions = new HashMap<>();

        for (ActionOutcome outcome : outcomeHistory) {
            String context = outcome.getContext();
            contextActions.merge(context, 1, Integer::sum);
        }

        // More diverse context-action pairs = higher adaptability
        return Math.min(1.0, contextActions.size() / 50.0);
    }

    public double calculateBanProbability() {
        double probability = 0.0;

        // Factors that increase ban probability
        probability += (1.0 - getHumanlikeness()) * 0.3;
        probability += getPatternRepetition() * 0.2;
        probability += (reportedPlayers / 10.0) * 0.2;
        probability += banWaveIntensity * 0.3;

        return Math.min(1.0, probability);
    }

    public double getSuspicionLevel() {
        return calculateBanProbability() * 0.8;
    }

    public double getUnusualBehaviorScore() {
        // Detect behaviors that deviate from normal patterns
        double deviation = 0.0;

        if (getEfficiencyScore() > 0.95) deviation += 0.3; // Too efficient
        if (getMistakeRate() < 0.001) deviation += 0.2; // Too perfect
        if (getAFKFrequency() < 0.01) deviation += 0.2; // Never AFK
        if (getClickAccuracy() > 0.99) deviation += 0.3; // Perfect clicking

        return Math.min(1.0, deviation);
    }

    public double getBotDetectionScore() {
        return (getUnusualBehaviorScore() + (1.0 - getHumanlikeness())) / 2.0;
    }

    public int getFlaggedActionsCount() {
        return (int) outcomeHistory.stream()
                .filter(ActionOutcome::wasFlagged)
                .count();
    }

    public double getTrustScore() {
        // Higher trust = less likely to be banned
        double trust = 0.5;

        trust += getHumanlikeness() * 0.3;
        trust -= getBotDetectionScore() * 0.3;
        trust += Math.min(0.2, outcomeHistory.size() / 10000.0); // Time factor

        return Math.max(0.0, Math.min(1.0, trust));
    }

    public double getActionConsistency() {
        // Measure how consistent actions are within short time windows
        if (outcomeHistory.size() < 10) return 0.5;

        List<ActionOutcome> recent = outcomeHistory.subList(
                Math.max(0, outcomeHistory.size() - 20),
                outcomeHistory.size()
        );

        Map<String, Integer> actionCounts = new HashMap<>();
        for (ActionOutcome outcome : recent) {
            actionCounts.merge(outcome.actionType, 1, Integer::sum);
        }

        // Calculate standard deviation of action distribution
        double mean = recent.size() / (double) actionCounts.size();
        double variance = actionCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average()
                .orElse(0.0);

        return 1.0 / (1.0 + Math.sqrt(variance));
    }

    // Getters for external systems
    public int getReportedPlayersCount() { return reportedPlayers; }
    public double getModActivityLevel() { return modActivity; }
    public double getBanWaveIntensity() { return banWaveIntensity; }
    public double getNeuralNetworkSuccess() { return neuralNetworkSuccess; }
    public double getQLearningSuccess() { return qLearningSuccess; }

    public void setReportedPlayers(int count) { this.reportedPlayers = count; }
    public void setModActivity(double level) { this.modActivity = level; }
    public void setBanWaveIntensity(double intensity) { this.banWaveIntensity = intensity; }

    public void saveMetrics(String filepath) throws IOException {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("successRates", successRates);
        metrics.put("neuralSuccess", neuralNetworkSuccess);
        metrics.put("qLearningSuccess", qLearningSuccess);
        metrics.put("trustScore", getTrustScore());
        metrics.put("humanlikeness", getHumanlikeness());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(Paths.get(filepath), gson.toJson(metrics).getBytes());
    }

    public void loadMetrics(String filepath) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(filepath)));
        Gson gson = new Gson();
        Map<String, Object> metrics = gson.fromJson(json, Map.class);

        // Restore metrics
        if (metrics.containsKey("neuralSuccess")) {
            neuralNetworkSuccess = (Double) metrics.get("neuralSuccess");
        }
        if (metrics.containsKey("qLearningSuccess")) {
            qLearningSuccess = (Double) metrics.get("qLearningSuccess");
        }
    }
}

/**
 * Cloud synchronization for collective learning
 */
public class CloudLearningSync {
    private String apiEndpoint;
    private String apiKey;
    private ExecutorService syncExecutor;

    public CloudLearningSync() {
        this.apiEndpoint = "https://your-ml-server.com/api/";
        this.apiKey = System.getenv("ML_API_KEY");
        this.syncExecutor = Executors.newSingleThreadExecutor();
    }

    public void syncModels(NeuralBehaviorEngine.BehaviorNeuralNetwork network,
                           NeuralBehaviorEngine.QLearningAgent qAgent,
                           NeuralBehaviorEngine.GeneticOptimizer genetic) {
        syncExecutor.submit(() -> {
            try {
                // Upload local models
                uploadModel(network, "neural_network");
                uploadQTable(qAgent, "q_learning");
                uploadGeneticData(genetic, "genetic");

                // Download collective knowledge
                downloadCollectiveKnowledge();

            } catch (Exception e) {
                System.err.println("Cloud sync failed: " + e.getMessage());
            }
        });
    }

    private void uploadModel(Object model, String modelType) {
        // Implementation would send model to cloud
        // This is a placeholder for the actual HTTP request
    }

    private void uploadQTable(NeuralBehaviorEngine.QLearningAgent agent, String type) {
        // Upload Q-learning table to cloud
    }

    private void uploadGeneticData(NeuralBehaviorEngine.GeneticOptimizer genetic, String type) {
        // Upload best chromosomes to cloud
    }

    private void downloadCollectiveKnowledge() {
        // Download aggregated learning from all bots
        // Merge with local knowledge using federated learning principles
    }
}

// Supporting classes
class TrainingData {
    NeuralBehaviorEngine.GameState state;
    BehaviorDecision decision;
    ActionOutcome outcome;
    long timestamp;
}

class BehaviorDecision {
    String actionType;
    double confidence;
    Map<String, Object> parameters;
    String source; // "neural", "qlearning", etc.

    public BehaviorDecision() {
        parameters = new HashMap<>();
    }
}

class ActionOutcome {
    String actionType;
    boolean successful;
    boolean riskIncreased;
    boolean banned;
    double efficiency;
    double humanlikeness;
    Double reactionTime;
    String pattern;
    String context;
    boolean accurate;
    boolean mistake;
    boolean flagged;
    long duration;

    public boolean wasSuccessful() { return successful; }
    public boolean increasedRisk() { return riskIncreased; }
    public boolean resultedInBan() { return banned; }
    public double getEfficiencyGain() { return efficiency; }
    public double getHumanlikenessScore() { return humanlikeness; }
    public Double getReactionTime() { return reactionTime; }
    public String getPattern() { return pattern != null ? pattern : "default"; }
    public String getContext() { return context != null ? context : "general"; }
    public boolean wasAccurate() { return accurate; }
    public boolean wasMistake() { return mistake; }
    public boolean wasFlagged() { return flagged; }
    public long getDuration() { return duration; }
}

class CurrentGameContext {
    // Bridge between your existing game state and ML system
    // Add getters for all necessary game state information

    public long getTimeSinceLastAction() { return 0; }
    public long getSessionDuration() { return 0; }
    public int getConsecutiveActions() { return 0; }
    public long getTimeSinceBreak() { return 0; }
    public int getSessionNumber() { return 0; }
    public long getTotalPlaytime() { return 0; }
    public int getHealthPercent() { return 100; }
    public int getEnergyPercent() { return 100; }
    public int getInventoryCount() { return 0; }
    public boolean isAnimating() { return false; }
    public boolean isMoving() { return false; }
    public boolean isInCombat() { return false; }
    public double getDistanceFromBank() { return 0; }
    public double getDistanceFromResource() { return 0; }
    public int getPlayerLevel() { return 1; }
    public int getCombatLevel() { return 3; }
    public int getPlayersNearby() { return 0; }
    public int getWorldPopulation() { return 0; }
    public double getServerLag() { return 0; }
    public double getConnectionStability() { return 1.0; }
    public double getCurrentRiskLevel() { return 0.5; }
    public int getReportCount() { return 0; }
    public double getAccountAge() { return 0; }
    public int getBanCount() { return 0; }
    public int getSkillLevel() { return 1; }
    public int getVisibleResources() { return 0; }
    public int getVisibleArea() { return 100; }
}