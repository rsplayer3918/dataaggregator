
/**
 * Test harness to verify the ML antiban system is working correctly.
 * This simulates bot behavior and tests the learning capabilities.
 */
public class MLBotTester {

    public static void main(String[] args) {
        System.out.println("=== ML Bot Antiban System Test ===\n");

        // Test 1: Basic Initialization
        testInitialization();

        // Test 2: Profile Loading and Saving
        testProfilePersistence();

        // Test 3: ML Learning Simulation
        testMLLearning();

        // Test 4: Risk Assessment
        testRiskAssessment();

        // Test 5: Adaptive Behavior
        testAdaptiveBehavior();

        System.out.println("\n=== All Tests Completed ===");
    }

    private static void testInitialization() {
        System.out.println("Test 1: Basic Initialization");
        System.out.println("-----------------------------");

        try {
            // Initialize antiban system
            Antiban.initialize("test_account_001");

            // Check if system is healthy
            boolean healthy = Antiban.isHealthy();
            System.out.println("System Health: " + (healthy ? "PASS" : "FAIL"));

            // Get initial statistics
            System.out.println("Initial Statistics:");
            System.out.println(Antiban.getStatistics());

            System.out.println("✓ Initialization test passed\n");

        } catch (Exception e) {
            System.err.println("✗ Initialization test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testProfilePersistence() {
        System.out.println("Test 2: Profile Loading and Saving");
        System.out.println("-----------------------------------");

        try {
            // Create a test profile
            BehaviorProfile profile1 = BehaviorProfile.loadForAccount("test_persistence");
            profile1.autoUpdateOnExit(false); // Simulate successful session

            // Load the same profile again
            BehaviorProfile profile2 = BehaviorProfile.loadForAccount("test_persistence");

            System.out.println("Profile 1 risk level: " + profile1.getCurrentRiskLevel());
            System.out.println("Profile 2 risk level: " + profile2.getCurrentRiskLevel());

            System.out.println("✓ Profile persistence test passed\n");

        } catch (Exception e) {
            System.err.println("✗ Profile persistence test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testMLLearning() {
        System.out.println("Test 3: ML Learning Simulation");
        System.out.println("-------------------------------");

        try {
            BehaviorProfile profile = BehaviorProfile.loadForAccount("test_learning");

            System.out.println("Initial probabilities:");
            ActionProbabilities initialProbs = profile.getActionProbabilities();
            printProbabilities(initialProbs);

            // Simulate multiple sessions with different outcomes
            System.out.println("\nSimulating 5 successful sessions...");
            for (int i = 0; i < 5; i++) {
                profile.autoUpdateOnExit(false); // Successful session
            }

            System.out.println("After successful sessions:");
            ActionProbabilities afterSuccess = profile.getActionProbabilities();
            printProbabilities(afterSuccess);

            // Simulate a ban
            System.out.println("\nSimulating 1 ban...");
            profile.autoUpdateOnExit(true);

            System.out.println("After ban:");
            ActionProbabilities afterBan = profile.getActionProbabilities();
            printProbabilities(afterBan);

            System.out.println("✓ ML Learning test passed\n");

        } catch (Exception e) {
            System.err.println("✗ ML Learning test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testRiskAssessment() {
        System.out.println("Test 4: Risk Assessment");
        System.out.println("------------------------");

        try {
            BehaviorProfile profile = BehaviorProfile.loadForAccount("test_risk");

            System.out.println("Initial risk level: " + String.format("%.3f", profile.getCurrentRiskLevel()));

            // Simulate high-risk scenario (multiple bans)
            for (int i = 0; i < 3; i++) {
                profile.autoUpdateOnExit(true);
                System.out.println("After ban " + (i+1) + ": " + String.format("%.3f", profile.getCurrentRiskLevel()));
            }

            // Simulate recovery (successful sessions)
            System.out.println("\nSimulating recovery...");
            for (int i = 0; i < 10; i++) {
                profile.autoUpdateOnExit(false);
                if (i % 2 == 0) {
                    System.out.println("After " + (i+1) + " successful sessions: " +
                            String.format("%.3f", profile.getCurrentRiskLevel()));
                }
            }

            System.out.println("✓ Risk Assessment test passed\n");

        } catch (Exception e) {
            System.err.println("✗ Risk Assessment test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testAdaptiveBehavior() {
        System.out.println("Test 5: Adaptive Behavior Simulation");
        System.out.println("-------------------------------------");

        try {
            Antiban.initialize("test_adaptive");
            BehaviorProfile profile = Antiban.getCurrentProfile();

            // Simulate different game contexts
            GameContext idleContext = new GameContext(
                    false, false, 100, System.currentTimeMillis(), null);

            GameContext activeContext = new GameContext(
                    true, false, 80, System.currentTimeMillis(), null);

            System.out.println("Testing context adaptation...");

            // Update with different contexts
            profile.updateContext(idleContext);
            ActionProbabilities idleProbs = profile.getActionProbabilities();
            System.out.println("Idle context probabilities:");
            printProbabilities(idleProbs);

            profile.updateContext(activeContext);
            ActionProbabilities activeProbs = profile.getActionProbabilities();
            System.out.println("Active context probabilities:");
            printProbabilities(activeProbs);

            // Test antiban action execution
            System.out.println("\nTesting antiban actions...");
            for (int i = 0; i < 5; i++) {
                if (Antiban.shouldPerformAction()) {
                    System.out.println("Action " + (i+1) + ": Performing antiban action");
                } else {
                    System.out.println("Action " + (i+1) + ": Skipping antiban action");
                }

                // Simulate some time passing
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }

            System.out.println("✓ Adaptive Behavior test passed\n");

        } catch (Exception e) {
            System.err.println("✗ Adaptive Behavior test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printProbabilities(ActionProbabilities probs) {
        if (probs == null) {
            System.out.println("  ERROR: Null probabilities");
            return;
        }

        System.out.println("  Mouse Move: " + String.format("%.3f", probs.mouseMove));
        System.out.println("  Tab Switch: " + String.format("%.3f", probs.tabSwitch));
        System.out.println("  Idle: " + String.format("%.3f", probs.idle));
        System.out.println("  Keystroke: " + String.format("%.3f", probs.keystroke));
        System.out.println("  Backspace: " + String.format("%.3f", probs.backspace));
        System.out.println("  Total: " + String.format("%.3f", probs.getTotalProbability()));
    }
}