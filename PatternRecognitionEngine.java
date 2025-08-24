import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * Advanced Pattern Recognition System
 * Detects and learns from complex behavioral patterns
 */
public class PatternRecognitionEngine {

    private Map<String, PatternSequence> detectedPatterns;
    private Map<String, Double> patternRiskScores;
    private CircularBuffer<ActionEvent> actionHistory;
    private MarkovChain behaviorChain;
    private static final int HISTORY_SIZE = 10000;

    public PatternRecognitionEngine() {
        detectedPatterns = new ConcurrentHashMap<>();
        patternRiskScores = new ConcurrentHashMap<>();
        actionHistory = new CircularBuffer<>(HISTORY_SIZE);
        behaviorChain = new MarkovChain();
    }

    /**
     * Analyze action sequence for patterns
     */
    public PatternAnalysis analyzeSequence(List<ActionEvent> sequence) {
        PatternAnalysis analysis = new PatternAnalysis();

        // Detect repeating patterns
        List<PatternSequence> patterns = detectPatterns(sequence);
        analysis.detectedPatterns = patterns;

        // Calculate pattern entropy
        analysis.entropy = calculateEntropy(sequence);

        // Detect anomalies
        analysis.anomalies = detectAnomalies(sequence);

        // Calculate risk score
        analysis.riskScore = calculatePatternRisk(patterns);

        // Generate recommendations
        analysis.recommendations = generateRecommendations(analysis);

        return analysis;
    }

    /**
     * Detect repeating patterns using suffix arrays
     */
    private List<PatternSequence> detectPatterns(List<ActionEvent> sequence) {
        List<PatternSequence> patterns = new ArrayList<>();

        // Build suffix array
        for (int length = 2; length <= Math.min(10, sequence.size() / 2); length++) {
            Map<String, Integer> patternCounts = new HashMap<>();

            for (int i = 0; i <= sequence.size() - length; i++) {
                String pattern = extractPattern(sequence.subList(i, i + length));
                patternCounts.merge(pattern, 1, Integer::sum);
            }

            // Find significant patterns (appear more than once)
            patternCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .forEach(e -> {
                        PatternSequence ps = new PatternSequence();
                        ps.pattern = e.getKey();
                        ps.frequency = e.getValue();
                        ps.length = length;
                        ps.risk = assessPatternRisk(e.getKey(), e.getValue());
                        patterns.add(ps);
                    });
        }

        return patterns;
    }

    /**
     * Calculate Shannon entropy of action sequence
     */
    private double calculateEntropy(List<ActionEvent> sequence) {
        Map<String, Integer> frequencies = new HashMap<>();

        for (ActionEvent event : sequence) {
            frequencies.merge(event.actionType, 1, Integer::sum);
        }

        double entropy = 0.0;
        int total = sequence.size();

        for (int freq : frequencies.values()) {
            if (freq > 0) {
                double probability = freq / (double) total;
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }

        return entropy;
    }

    /**
     * Detect anomalous behavior patterns
     */
    private List<Anomaly> detectAnomalies(List<ActionEvent> sequence) {
        List<Anomaly> anomalies = new ArrayList<>();

        // Statistical anomaly detection
        double meanInterval = calculateMeanInterval(sequence);
        double stdDevInterval = calculateStdDevInterval(sequence, meanInterval);

        for (int i = 1; i < sequence.size(); i++) {
            ActionEvent current = sequence.get(i);
            ActionEvent previous = sequence.get(i - 1);

            long interval = current.timestamp - previous.timestamp;

            // Check for timing anomalies
            if (Math.abs(interval - meanInterval) > 3 * stdDevInterval) {
                Anomaly anomaly = new Anomaly();
                anomaly.type = "TIMING_ANOMALY";
                anomaly.severity = calculateAnomalySeverity(interval, meanInterval, stdDevInterval);
                anomaly.event = current;
                anomalies.add(anomaly);
            }

            // Check for impossible sequences
            if (isImpossibleSequence(previous, current)) {
                Anomaly anomaly = new Anomaly();
                anomaly.type = "IMPOSSIBLE_SEQUENCE";
                anomaly.severity = 0.9;
                anomaly.event = current;
                anomalies.add(anomaly);
            }
        }

        return anomalies;
    }

    /**
     * Markov Chain for behavior prediction
     */
    public static class MarkovChain {
        private Map<String, Map<String, Double>> transitionMatrix;
        private Map<String, Integer> stateCounts;

        public MarkovChain() {
            transitionMatrix = new HashMap<>();
            stateCounts = new HashMap<>();
        }

        public void addTransition(String fromState, String toState) {
            transitionMatrix.computeIfAbsent(fromState, k -> new HashMap<>())
                    .merge(toState, 1.0, Double::sum);
            stateCounts.merge(fromState, 1, Integer::sum);
        }

        public String predictNextState(String currentState) {
            Map<String, Double> transitions = transitionMatrix.get(currentState);
            if (transitions == null || transitions.isEmpty()) {
                return null;
            }

            // Normalize probabilities
            double total = transitions.values().stream().mapToDouble(Double::doubleValue).sum();
            double random = Math.random() * total;
            double cumulative = 0;

            for (Map.Entry<String, Double> entry : transitions.entrySet()) {
                cumulative += entry.getValue();
                if (random <= cumulative) {
                    return entry.getKey();
                }
            }

            return transitions.keySet().iterator().next();
        }

        public double getTransitionProbability(String from, String to) {
            Map<String, Double> transitions = transitionMatrix.get(from);
            if (transitions == null) return 0.0;

            double count = transitions.getOrDefault(to, 0.0);
            double total = stateCounts.getOrDefault(from, 1);

            return count / total;
        }
    }

    // Helper classes
    static class PatternSequence {
        String pattern;
        int frequency;
        int length;
        double risk;
    }

    static class PatternAnalysis {
        List<PatternSequence> detectedPatterns;
        double entropy;
        List<Anomaly> anomalies;
        double riskScore;
        List<String> recommendations;
    }

    static class Anomaly {
        String type;
        double severity;
        ActionEvent event;
    }

    static class ActionEvent {
        String actionType;
        long timestamp;
        Map<String, Object> metadata;
    }

    // Helper methods
    private String extractPattern(List<ActionEvent> events) {
        return events.stream()
                .map(e -> e.actionType)
                .collect(Collectors.joining("-"));
    }

    private double assessPatternRisk(String pattern, int frequency) {
        // Patterns that appear too frequently are risky
        double freqRisk = Math.min(1.0, frequency / 50.0);

        // Certain patterns are inherently risky
        double patternRisk = 0.0;
        if (pattern.contains("CLICK-CLICK-CLICK")) patternRisk += 0.3;
        if (pattern.length() > 20) patternRisk += 0.2;

        return Math.min(1.0, freqRisk + patternRisk);
    }

    private double calculatePatternRisk(List<PatternSequence> patterns) {
        if (patterns.isEmpty()) return 0.1;

        return patterns.stream()
                .mapToDouble(p -> p.risk * (p.frequency / 100.0))
                .average()
                .orElse(0.1);
    }

    private List<String> generateRecommendations(PatternAnalysis analysis) {
        List<String> recommendations = new ArrayList<>();

        if (analysis.entropy < 2.0) {
            recommendations.add("Increase action diversity - entropy too low");
        }

        if (analysis.riskScore > 0.7) {
            recommendations.add("Reduce pattern repetition - high risk detected");
        }

        if (!analysis.anomalies.isEmpty()) {
            recommendations.add("Address anomalous behaviors: " + analysis.anomalies.size() + " detected");
        }

        return recommendations;
    }

    private double calculateMeanInterval(List<ActionEvent> sequence) {
        if (sequence.size() < 2) return 1000;

        double sum = 0;
        for (int i = 1; i < sequence.size(); i++) {
            sum += sequence.get(i).timestamp - sequence.get(i-1).timestamp;
        }

        return sum / (sequence.size() - 1);
    }

    private double calculateStdDevInterval(List<ActionEvent> sequence, double mean) {
        if (sequence.size() < 2) return 100;

        double sum = 0;
        for (int i = 1; i < sequence.size(); i++) {
            double interval = sequence.get(i).timestamp - sequence.get(i-1).timestamp;
            sum += Math.pow(interval - mean, 2);
        }

        return Math.sqrt(sum / (sequence.size() - 1));
    }

    private double calculateAnomalySeverity(long interval, double mean, double stdDev) {
        double zScore = Math.abs((interval - mean) / stdDev);
        return Math.min(1.0, zScore / 10.0);
    }

    private boolean isImpossibleSequence(ActionEvent prev, ActionEvent current) {
        // Define impossible sequences
        if (prev.actionType.equals("BANKING") && current.actionType.equals("COMBAT")) {
            long timeDiff = current.timestamp - prev.timestamp;
            if (timeDiff < 1000) return true; // Can't go from banking to combat in < 1 second
        }

        return false;
    }
}

/**
 * Advanced Temporal Learning System
 * Learns time-based patterns and adapts behavior accordingly
 */
public class TemporalLearningSystem {

    private Map<Integer, HourlyBehaviorProfile> hourlyProfiles;
    private Map<DayOfWeek, DailyBehaviorProfile> dailyProfiles;
    private SeasonalTrend seasonalTrend;
    private CircularTimeBuffer timeBuffer;

    public TemporalLearningSystem() {
        hourlyProfiles = new HashMap<>();
        dailyProfiles = new HashMap<>();
        seasonalTrend = new SeasonalTrend();
        timeBuffer = new CircularTimeBuffer(168); // 1 week of hours

        initializeProfiles();
    }

    /**
     * Get optimal behavior for current time
     */
    public BehaviorRecommendation getTemporalRecommendation() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        DayOfWeek day = now.getDayOfWeek();

        BehaviorRecommendation rec = new BehaviorRecommendation();

        // Get hourly profile
        HourlyBehaviorProfile hourProfile = hourlyProfiles.get(hour);
        rec.activityLevel = hourProfile.avgActivityLevel;
        rec.riskTolerance = hourProfile.riskTolerance;
        rec.preferredActions = hourProfile.commonActions;

        // Adjust for day of week
        DailyBehaviorProfile dayProfile = dailyProfiles.get(day);
        rec.sessionDuration = dayProfile.avgSessionLength;
        rec.breakFrequency = dayProfile.breakFrequency;

        // Apply seasonal adjustments
        rec.adjustForSeason(seasonalTrend.getCurrentTrend());

        // Learn from recent performance
        rec.confidence = calculateConfidence();

        return rec;
    }

    /**
     * Update profiles based on performance
     */
    public void updateTemporalProfile(ActionOutcome outcome) {
        LocalDateTime time = LocalDateTime.now();
        int hour = time.getHour();
        DayOfWeek day = time.getDayOfWeek();

        // Update hourly profile
        HourlyBehaviorProfile hourProfile = hourlyProfiles.get(hour);
        hourProfile.update(outcome);

        // Update daily profile
        DailyBehaviorProfile dayProfile = dailyProfiles.get(day);
        dayProfile.update(outcome);

        // Update seasonal trend
        seasonalTrend.addDataPoint(outcome.efficiency);

        // Add to time buffer
        timeBuffer.add(new TimePoint(time, outcome));
    }

    /**
     * Predict future risk based on temporal patterns
     */
    public RiskPrediction predictTemporalRisk(int hoursAhead) {
        LocalDateTime future = LocalDateTime.now().plusHours(hoursAhead);

        RiskPrediction prediction = new RiskPrediction();
        prediction.timestamp = future;

        // Analyze historical data for this time
        List<TimePoint> historicalPoints = timeBuffer.getPointsForTime(
                future.getHour(), future.getDayOfWeek());

        if (!historicalPoints.isEmpty()) {
            // Calculate risk based on historical performance
            double avgRisk = historicalPoints.stream()
                    .mapToDouble(p -> p.outcome.increasedRisk() ? 1.0 : 0.0)
                    .average()
                    .orElse(0.5);

            prediction.riskLevel = avgRisk;

            // Calculate confidence based on sample size
            prediction.confidence = Math.min(1.0, historicalPoints.size() / 10.0);
        } else {
            // No historical data - use defaults
            prediction.riskLevel = 0.5;
            prediction.confidence = 0.1;
        }

        // Adjust for known high-risk times
        if (isHighRiskTime(future)) {
            prediction.riskLevel = Math.min(1.0, prediction.riskLevel * 1.5);
            prediction.warnings.add("High-risk time period detected");
        }

        return prediction;
    }

    /**
     * Behavioral profile for each hour of the day
     */
    static class HourlyBehaviorProfile {
        double avgActivityLevel = 0.5;
        double riskTolerance = 0.5;
        List<String> commonActions = new ArrayList<>();
        int sampleCount = 0;

        void update(ActionOutcome outcome) {
            // Exponential moving average update
            double alpha = 0.1;
            avgActivityLevel = avgActivityLevel * (1 - alpha) + outcome.efficiency * alpha;

            if (outcome.increasedRisk()) {
                riskTolerance *= 0.95; // Reduce risk tolerance on bad outcomes
            } else {
                riskTolerance = Math.min(0.8, riskTolerance * 1.01);
            }

            sampleCount++;
        }
    }

    /**
     * Behavioral profile for each day of the week
     */
    static class DailyBehaviorProfile {
        double avgSessionLength = 7200000; // 2 hours default
        double breakFrequency = 0.1;
        double peakEfficiencyTime = 14; // 2 PM default

        void update(ActionOutcome outcome) {
            // Update based on outcomes
            double alpha = 0.05;

            if (outcome.wasSuccessful()) {
                avgSessionLength *= 1.01; // Slightly longer sessions on success
            } else {
                avgSessionLength *= 0.99;
            }

            // Keep within reasonable bounds
            avgSessionLength = Math.max(1800000, Math.min(14400000, avgSessionLength));
        }
    }

    /**
     * Seasonal trend analysis
     */
    static class SeasonalTrend {
        private List<Double> dataPoints = new ArrayList<>();
        private static final int SEASON_LENGTH = 90; // days

        void addDataPoint(double value) {
            dataPoints.add(value);
            if (dataPoints.size() > SEASON_LENGTH * 24) { // Keep 90 days of hourly data
                dataPoints.remove(0);
            }
        }

        double getCurrentTrend() {
            if (dataPoints.size() < 24) return 0.0;

            // Calculate trend using linear regression
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            int n = Math.min(dataPoints.size(), 24 * 7); // Last week

            for (int i = dataPoints.size() - n; i < dataPoints.size(); i++) {
                double x = i - (dataPoints.size() - n);
                double y = dataPoints.get(i);

                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumX2 += x * x;
            }

            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            return slope; // Positive = improving, negative = declining
        }
    }

    /**
     * Circular buffer for time-based data
     */
    static class CircularTimeBuffer {
        private TimePoint[] buffer;
        private int head = 0;
        private int size = 0;

        CircularTimeBuffer(int capacity) {
            buffer = new TimePoint[capacity];
        }

        void add(TimePoint point) {
            buffer[head] = point;
            head = (head + 1) % buffer.length;
            size = Math.min(size + 1, buffer.length);
        }

        List<TimePoint> getPointsForTime(int hour, DayOfWeek day) {
            List<TimePoint> matches = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                TimePoint point = buffer[i];
                if (point != null &&
                        point.time.getHour() == hour &&
                        point.time.getDayOfWeek() == day) {
                    matches.add(point);
                }
            }

            return matches;
        }
    }

    static class TimePoint {
        LocalDateTime time;
        ActionOutcome outcome;

        TimePoint(LocalDateTime time, ActionOutcome outcome) {
            this.time = time;
            this.outcome = outcome;
        }
    }

    static class BehaviorRecommendation {
        double activityLevel;
        double riskTolerance;
        List<String> preferredActions;
        double sessionDuration;
        double breakFrequency;
        double confidence;

        void adjustForSeason(double trend) {
            if (trend > 0) {
                // Positive trend - can be slightly more aggressive
                activityLevel *= 1.1;
                riskTolerance *= 1.05;
            } else if (trend < 0) {
                // Negative trend - be more conservative
                activityLevel *= 0.9;
                riskTolerance *= 0.95;
            }
        }
    }

    static class RiskPrediction {
        LocalDateTime timestamp;
        double riskLevel;
        double confidence;
        List<String> warnings = new ArrayList<>();
    }

    private void initializeProfiles() {
        // Initialize hourly profiles
        for (int hour = 0; hour < 24; hour++) {
            HourlyBehaviorProfile profile = new HourlyBehaviorProfile();

            // Set initial values based on typical patterns
            if (hour >= 2 && hour <= 6) {
                profile.avgActivityLevel = 0.3; // Low activity at night
                profile.riskTolerance = 0.7; // Higher risk tolerance when fewer players
            } else if (hour >= 18 && hour <= 23) {
                profile.avgActivityLevel = 0.8; // High activity in evening
                profile.riskTolerance = 0.4; // Lower risk during peak hours
            }

            hourlyProfiles.put(hour, profile);
        }

        // Initialize daily profiles
        for (DayOfWeek day : DayOfWeek.values()) {
            DailyBehaviorProfile profile = new DailyBehaviorProfile();

            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                profile.avgSessionLength = 10800000; // 3 hours on weekends
                profile.breakFrequency = 0.08; // Less frequent breaks
            }

            dailyProfiles.put(day, profile);
        }
    }

    private double calculateConfidence() {
        // Calculate confidence based on data availability
        int dataPoints = timeBuffer.size;
        return Math.min(1.0, dataPoints / 168.0); // Full confidence after 1 week
    }

    private boolean isHighRiskTime(LocalDateTime time) {
        int hour = time.getHour();
        DayOfWeek day = time.getDayOfWeek();

        // Peak playing hours = higher risk
        if (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY) {
            if (hour >= 19 && hour <= 23) return true;
        }

        // Update/maintenance times
        if (day == DayOfWeek.WEDNESDAY && hour >= 10 && hour <= 12) {
            return true; // Common update time
        }

        return false;
    }
}

/**
 * Behavioral DNA System - Unique identity for each bot
 */
public class BehavioralDNA {

    private final String dnaSequence;
    private Map<String, Double> traits;
    private Map<String, Double> preferences;
    private PersonalityMatrix personality;

    public BehavioralDNA(String accountId) {
        this.dnaSequence = generateDNA(accountId);
        this.traits = generateTraits();
        this.preferences = generatePreferences();
        this.personality = new PersonalityMatrix(dnaSequence);
    }

    /**
     * Generate unique DNA sequence for account
     */
    private String generateDNA(String accountId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(accountId.getBytes());

            StringBuilder dna = new StringBuilder();
            for (byte b : hash) {
                dna.append(String.format("%02x", b));
            }

            return dna.toString();
        } catch (Exception e) {
            return accountId.hashCode() + "";
        }
    }

    /**
     * Generate personality traits from DNA
     */
    private Map<String, Double> generateTraits() {
        Map<String, Double> traits = new HashMap<>();

        // Use DNA to seed random generator for consistency
        Random rand = new Random(dnaSequence.hashCode());

        // Big Five personality traits adapted for bots
        traits.put("aggression", rand.nextGaussian() * 0.2 + 0.5); // How aggressive in gathering
        traits.put("patience", rand.nextGaussian() * 0.2 + 0.5); // Willingness to wait
        traits.put("precision", rand.nextGaussian() * 0.2 + 0.5); // Click accuracy tendency
        traits.put("curiosity", rand.nextGaussian() * 0.2 + 0.5); // Exploration tendency
        traits.put("consistency", rand.nextGaussian() * 0.2 + 0.5); // Pattern consistency

        // Normalize traits to 0-1 range
        traits.replaceAll((k, v) -> Math.max(0, Math.min(1, v)));

        return traits;
    }

    /**
     * Generate behavioral preferences from DNA
     */
    private Map<String, Double> generatePreferences() {
        Map<String, Double> prefs = new HashMap<>();
        Random rand = new Random(dnaSequence.hashCode() + 1);

        // Activity preferences
        prefs.put("preferredSessionLength", 3600000 + rand.nextGaussian() * 1800000); // 1-2 hours
        prefs.put("preferredBreakLength", 300000 + rand.nextGaussian() * 180000); // 3-8 minutes
        prefs.put("mouseSpeed", 0.5 + rand.nextGaussian() * 0.2);
        prefs.put("reactionTime", 500 + rand.nextGaussian() * 200);
        prefs.put("mistakeRate", 0.02 + rand.nextGaussian() * 0.01);

        // Normalize to reasonable ranges
        prefs.compute("preferredSessionLength", (k, v) -> Math.max(1800000, Math.min(14400000, v)));
        prefs.compute("preferredBreakLength", (k, v) -> Math.max(60000, Math.min(1800000, v)));
        prefs.compute("mouseSpeed", (k, v) -> Math.max(0.2, Math.min(1.0, v)));
        prefs.compute("reactionTime", (k, v) -> Math.max(200, Math.min(2000, v)));
        prefs.compute("mistakeRate", (k, v) -> Math.max(0.001, Math.min(0.1, v)));

        return prefs;
    }

    /**
     * Get behavior modifier based on DNA
     */
    public BehaviorModifier getBehaviorModifier(String context) {
        BehaviorModifier modifier = new BehaviorModifier();

        // Apply personality traits
        modifier.aggressionMultiplier = traits.get("aggression");
        modifier.patienceMultiplier = traits.get("patience");
        modifier.precisionMultiplier = traits.get("precision");

        // Apply context-specific modifications
        modifier.applyContext(context, personality);

        return modifier;
    }

    /**
     * Evolve DNA based on experiences
     */
    public void evolve(List<ActionOutcome> experiences) {
        // Slightly adjust traits based on success/failure
        for (ActionOutcome outcome : experiences) {
            if (outcome.wasSuccessful()) {
                // Reinforce successful traits
                String dominantTrait = getDominantTraitForAction(outcome.actionType);
                traits.compute(dominantTrait, (k, v) -> Math.min(1.0, v * 1.01));
            } else {
                // Reduce unsuccessful traits
                String dominantTrait = getDominantTraitForAction(outcome.actionType);
                traits.compute(dominantTrait, (k, v) -> Math.max(0.0, v * 0.99));
            }
        }
    }

    static class PersonalityMatrix {
        private double[][] matrix;

        PersonalityMatrix(String dna) {
            Random rand = new Random(dna.hashCode());
            matrix = new double[5][5];

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    matrix[i][j] = rand.nextDouble();
                }
            }
        }

        double getInteraction(int trait1, int trait2) {
            return matrix[trait1][trait2];
        }
    }

    static class BehaviorModifier {
        double aggressionMultiplier = 1.0;
        double patienceMultiplier = 1.0;
        double precisionMultiplier = 1.0;
        double speedMultiplier = 1.0;
        double riskMultiplier = 1.0;

        void applyContext(String context, PersonalityMatrix personality) {
            switch (context) {
                case "high_risk":
                    aggressionMultiplier *= 0.7;
                    patienceMultiplier *= 1.3;
                    break;
                case "competition":
                    aggressionMultiplier *= 1.2;
                    speedMultiplier *= 1.1;
                    break;
                case "fatigue":
                    precisionMultiplier *= 0.8;
                    speedMultiplier *= 0.9;
                    break;
            }
        }
    }

    private String getDominantTraitForAction(String actionType) {
        switch (actionType) {
            case "AGGRESSIVE_FARMING":
                return "aggression";
            case "PATIENT_WAITING":
                return "patience";
            case "PRECISE_CLICKING":
                return "precision";
            default:
                return "consistency";
        }
    }
}

/**
 * Circular buffer implementation
 */
class CircularBuffer<T> {
    private Object[] buffer;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public CircularBuffer(int capacity) {
        buffer = new Object[capacity];
    }

    public void add(T item) {
        buffer[head] = item;
        head = (head + 1) % buffer.length;

        if (size < buffer.length) {
            size++;
        } else {
            tail = (tail + 1) % buffer.length;
        }
    }

    @SuppressWarnings("unchecked")
    public List<T> getAll() {
        List<T> result = new ArrayList<>();
        int current = tail;

        for (int i = 0; i < size; i++) {
            result.add((T) buffer[current]);
            current = (current + 1) % buffer.length;
        }

        return result;
    }

    public int size() {
        return size;
    }
}