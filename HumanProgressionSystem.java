import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates realistic human progression from new player to addicted grinder
 * Models actual human psychology and gaming addiction patterns
 */
public class HumanProgressionSystem {

    /**
     * Tracks player progression through realistic stages
     */
    public static class ProgressionTracker {
        private PlayerStage currentStage;
        private int totalSessionsPlayed = 0;
        private long totalTimeSpent = 0; // milliseconds
        private int daysSinceStart = 0;
        private Map<String, Integer> skillExperience = new HashMap<>();
        private Random random = ThreadLocalRandom.current();

        // Progression data
        private long firstSessionTime;
        private int sessionsToday = 0;
        private long timePlayedToday = 0;

        public ProgressionTracker() {
            currentStage = PlayerStage.CURIOUS_NEWBIE;
            firstSessionTime = System.currentTimeMillis();
            skillExperience.put("Woodcutting", 0);
            skillExperience.put("Mining", 0);
        }

        public SessionPlan planNextSession() {
            updateStageProgression();
            return currentStage.generateSessionPlan(this);
        }

        private void updateStageProgression() {
            // Calculate days since start
            long daysSinceStartLong = (System.currentTimeMillis() - firstSessionTime) / (24 * 60 * 60 * 1000L);
            daysSinceStart = (int) daysSinceStartLong;

            // Determine stage based on experience and time
            PlayerStage newStage = determineStage();
            if (newStage != currentStage) {
                System.out.println("=== PROGRESSION UPDATE ===");
                System.out.println("Advanced from " + currentStage + " to " + newStage);
                currentStage = newStage;
            }
        }

        private PlayerStage determineStage() {
            long hoursPlayed = totalTimeSpent / 3600000L;

            if (totalSessionsPlayed < 3) {
                return PlayerStage.CURIOUS_NEWBIE;
            } else if (totalSessionsPlayed < 10 && hoursPlayed < 8) {
                return PlayerStage.EXPLORING_CASUAL;
            } else if (totalSessionsPlayed < 25 && hoursPlayed < 25) {
                return PlayerStage.GETTING_HOOKED;
            } else if (hoursPlayed < 60) {
                return PlayerStage.REGULAR_PLAYER;
            } else if (hoursPlayed < 150) {
                return PlayerStage.DEDICATED_GRINDER;
            } else {
                return PlayerStage.ADDICTED_VETERAN;
            }
        }

        public void recordSession(long sessionLength, String activity, int xpGained) {
            totalSessionsPlayed++;
            totalTimeSpent += sessionLength;
            timePlayedToday += sessionLength;
            sessionsToday++;

            skillExperience.merge(activity, xpGained, Integer::sum);

            System.out.println("=== SESSION COMPLETED ===");
            System.out.println("Stage: " + currentStage);
            System.out.println("Total Sessions: " + totalSessionsPlayed);
            System.out.println("Total Time: " + (totalTimeSpent / 3600000L) + " hours");
            System.out.println("Today: " + sessionsToday + " sessions, " + (timePlayedToday / 3600000L) + "h");
        }

        // Getters
        public PlayerStage getCurrentStage() { return currentStage; }
        public int getTotalSessions() { return totalSessionsPlayed; }
        public long getTotalTimeSpent() { return totalTimeSpent; }
        public int getDaysSinceStart() { return daysSinceStart; }
        public int getSkillXP(String skill) { return skillExperience.getOrDefault(skill, 0); }
    }

    /**
     * Different stages of player progression with realistic behaviors
     */
    public enum PlayerStage {
        CURIOUS_NEWBIE("Curious Newbie"),
        EXPLORING_CASUAL("Exploring Casual"),
        GETTING_HOOKED("Getting Hooked"),
        REGULAR_PLAYER("Regular Player"),
        DEDICATED_GRINDER("Dedicated Grinder"),
        ADDICTED_VETERAN("Addicted Veteran");

        private final String description;

        PlayerStage(String desc) {
            this.description = desc;
        }

        public SessionPlan generateSessionPlan(ProgressionTracker tracker) {
            Random random = ThreadLocalRandom.current();

            switch (this) {
                case CURIOUS_NEWBIE:
                    return generateNewbieSession(tracker, random);
                case EXPLORING_CASUAL:
                    return generateCasualSession(tracker, random);
                case GETTING_HOOKED:
                    return generateHookedSession(tracker, random);
                case REGULAR_PLAYER:
                    return generateRegularSession(tracker, random);
                case DEDICATED_GRINDER:
                    return generateGrinderSession(tracker, random);
                case ADDICTED_VETERAN:
                    return generateVeteranSession(tracker, random);
                default:
                    return generateCasualSession(tracker, random);
            }
        }

        private SessionPlan generateNewbieSession(ProgressionTracker tracker, Random random) {
            // Very short sessions, lots of switching, frequent breaks
            String activity = random.nextBoolean() ? "Woodcutting" : "Mining";
            String resource = activity.equals("Woodcutting") ? "Tree" : "Copper";
            String location = "Lumbridge";

            return new SessionPlan(
                    activity, resource, location,
                    5 + random.nextInt(15), // 5-20 minutes
                    "Very short session - still learning the game",
                    3 + random.nextInt(5), // 3-8 minute breaks
                    0.8 // 80% chance to switch activities
            );
        }

        private SessionPlan generateCasualSession(ProgressionTracker tracker, Random random) {
            // Moderate sessions, some switching, trying different things
            boolean preferWC = tracker.getSkillXP("Mining") > tracker.getSkillXP("Woodcutting");
            String activity = preferWC ? "Woodcutting" : "Mining";

            String resource, location;
            if (activity.equals("Woodcutting")) {
                resource = random.nextDouble() < 0.7 ? "Tree" : "Oak";
                location = random.nextDouble() < 0.6 ? "Lumbridge" : "Varrock";
            } else {
                resource = random.nextDouble() < 0.8 ? "Copper" : "Tin";
                location = "Lumbridge";
            }

            return new SessionPlan(
                    activity, resource, location,
                    15 + random.nextInt(30), // 15-45 minutes
                    "Casual exploration - trying different activities",
                    5 + random.nextInt(10), // 5-15 minute breaks
                    0.6 // 60% chance to switch
            );
        }

        private SessionPlan generateHookedSession(ProgressionTracker tracker, Random random) {
            // Longer sessions, starting to focus, less switching
            String activity = determinePreferredSkill(tracker, random);

            String resource, location;
            if (activity.equals("Woodcutting")) {
                // Starting to do higher level trees
                double rand = random.nextDouble();
                if (rand < 0.4) {
                    resource = "Oak";
                    location = "Varrock";
                } else if (rand < 0.7) {
                    resource = "Willow";
                    location = "Draynor";
                } else {
                    resource = "Tree";
                    location = "Lumbridge";
                }
            } else {
                resource = random.nextDouble() < 0.6 ? "Iron" : "Tin";
                location = random.nextDouble() < 0.7 ? "Varrock" : "Lumbridge";
            }

            return new SessionPlan(
                    activity, resource, location,
                    30 + random.nextInt(60), // 30-90 minutes
                    "Getting hooked - longer focused sessions",
                    8 + random.nextInt(15), // 8-23 minute breaks
                    0.3 // 30% chance to switch
            );
        }

        private SessionPlan generateRegularSession(ProgressionTracker tracker, Random random) {
            // Focused grinding sessions with specific goals
            String activity = determinePreferredSkill(tracker, random);

            String resource, location;
            if (activity.equals("Woodcutting")) {
                resource = "Willow"; // Efficient training
                location = "Draynor";
            } else {
                resource = "Iron"; // Good XP and profit
                location = "Varrock";
            }

            return new SessionPlan(
                    activity, resource, location,
                    60 + random.nextInt(120), // 1-3 hours
                    "Regular grinding - focused on efficiency",
                    10 + random.nextInt(20), // 10-30 minute breaks
                    0.15 // 15% chance to switch
            );
        }

        private SessionPlan generateGrinderSession(ProgressionTracker tracker, Random random) {
            // Long efficient grinding sessions
            String activity = determineOptimalSkill(tracker, random);

            String resource, location;
            if (activity.equals("Woodcutting")) {
                if (random.nextDouble() < 0.8) {
                    resource = "Willow";
                    location = "Draynor";
                } else {
                    resource = "Maple"; // Occasionally try higher level
                    location = "Seers";
                }
            } else {
                resource = random.nextDouble() < 0.7 ? "Iron" : "Coal";
                location = random.nextDouble() < 0.8 ? "Varrock" : "Falador";
            }

            return new SessionPlan(
                    activity, resource, location,
                    90 + random.nextInt(180), // 1.5-4.5 hours
                    "Dedicated grinding - chasing goals",
                    15 + random.nextInt(25), // 15-40 minute breaks
                    0.05 // 5% chance to switch
            );
        }

        private SessionPlan generateVeteranSession(ProgressionTracker tracker, Random random) {
            // Very long sessions, minimal breaks, hardcore grinding
            String activity = determineOptimalSkill(tracker, random);

            String resource, location;
            if (activity.equals("Woodcutting")) {
                if (random.nextDouble() < 0.6) {
                    resource = "Yew"; // High level content
                    location = "Catherby";
                } else {
                    resource = "Maple";
                    location = "Seers";
                }
            } else {
                resource = "Coal"; // Profitable grinding
                location = "Falador";
            }

            return new SessionPlan(
                    activity, resource, location,
                    180 + random.nextInt(300), // 3-8 hours
                    "Veteran grinding - hardcore sessions",
                    20 + random.nextInt(40), // 20-60 minute breaks
                    0.02 // 2% chance to switch
            );
        }

        private String determinePreferredSkill(ProgressionTracker tracker, Random random) {
            int wcXP = tracker.getSkillXP("Woodcutting");
            int miningXP = tracker.getSkillXP("Mining");

            // Slight preference for the lower skill (balanced training)
            if (Math.abs(wcXP - miningXP) > 5000) {
                return wcXP < miningXP ? "Woodcutting" : "Mining";
            }

            // Otherwise random with slight preference
            return random.nextDouble() < 0.6 ? "Woodcutting" : "Mining";
        }

        private String determineOptimalSkill(ProgressionTracker tracker, Random random) {
            // Advanced players often stick to one skill for longer periods
            int wcXP = tracker.getSkillXP("Woodcutting");
            int miningXP = tracker.getSkillXP("Mining");

            // If one skill is significantly higher, continue with it
            if (wcXP - miningXP > 10000) return "Woodcutting";
            if (miningXP - wcXP > 10000) return "Mining";

            // Otherwise prefer woodcutting (generally more AFK)
            return random.nextDouble() < 0.75 ? "Woodcutting" : "Mining";
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Complete plan for a gaming session
     */
    public static class SessionPlan {
        public String activity;
        public String resource;
        public String location;
        public int durationMinutes;
        public String reasoning;
        public int breakLengthMinutes;
        public double switchProbability;

        public SessionPlan(String activity, String resource, String location,
                           int duration, String reasoning, int breakLength, double switchProb) {
            this.activity = activity;
            this.resource = resource;
            this.location = location;
            this.durationMinutes = duration;
            this.reasoning = reasoning;
            this.breakLengthMinutes = breakLength;
            this.switchProbability = switchProb;
        }

        public long getDurationMs() {
            return durationMinutes * 60000L;
        }

        public long getBreakLengthMs() {
            return breakLengthMinutes * 60000L;
        }

        @Override
        public String toString() {
            return String.format("%s %s at %s (%dm) - %s",
                    activity, resource, location, durationMinutes, reasoning);
        }
    }

    /**
     * Integration with break system for realistic progression
     */
    public static class ProgressiveBreakManager {
        private ProgressionTracker progression;
        private SessionPlan currentSession;
        private long sessionStartTime;

        public ProgressiveBreakManager(ProgressionTracker tracker) {
            this.progression = tracker;
            startNewSession();
        }

        public void startNewSession() {
            currentSession = progression.planNextSession();
            sessionStartTime = System.currentTimeMillis();

            System.out.println("=== NEW SESSION PLAN ===");
            System.out.println("Stage: " + progression.getCurrentStage());
            System.out.println("Plan: " + currentSession);
            System.out.println("Break Length: " + currentSession.breakLengthMinutes + " minutes");
            System.out.println("Switch Probability: " + (currentSession.switchProbability * 100) + "%");
        }

        public boolean shouldEndSession(long currentTime) {
            long sessionLength = currentTime - sessionStartTime;
            return sessionLength >= currentSession.getDurationMs();
        }

        public void completeSession(int xpGained) {
            long sessionLength = System.currentTimeMillis() - sessionStartTime;
            progression.recordSession(sessionLength, currentSession.activity, xpGained);
        }

        public SessionPlan getCurrentSession() {
            return currentSession;
        }

        public boolean shouldSwitchActivity() {
            return ThreadLocalRandom.current().nextDouble() < currentSession.switchProbability;
        }
    }
}