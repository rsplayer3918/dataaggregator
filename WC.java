import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.combat.Combat;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@ScriptManifest(category = Category.WOODCUTTING, name = "Adaptive WC/Mining Pro", author = "You", version = 3.0)
public class WC extends AbstractScript {

    private State state;
    private String selectedActivity = "Woodcutting";
    private String selectedResource = "Tree";
    private String selectedLocation = "Lumbridge";
    private String selectedResourceType = "Tree";

    // Areas
    private Area currentResourceArea;
    private Area currentBankArea;
    private Area[] allBankAreas;

    // State tracking
    private long lastStateChange = 0;
    private int stuckCounter = 0;
    private final int MAX_STUCK_COUNT = 5;

    // Runtime UI - Store all label references
    private JFrame statusFrame;
    private JLabel statusLabel;
    private JLabel skillGainsLabel;
    private JLabel sessionLabel;
    private JLabel resourcesLabel;
    private JLabel nextBreakLabel;
    private JLabel breakDurationLabel;
    private JLabel activityLabel;
    private JLabel locationLabel;
    private JLabel progressLabel;

    // Inner class for break information
    private class BreakInfo {
        String timeUntilBreak;
        String expectedDuration;

        BreakInfo(String timeUntil, String duration) {
            this.timeUntilBreak = timeUntil;
            this.expectedDuration = duration;
        }
    }

    // Advanced Break System
    private AdvancedBreakSystem.SmartBreakManager breakManager;
    private AdvancedBreakSystem.MLEfficiencyManager efficiencyManager;
    private AdvancedBreakSystem.HumanBehaviorSimulator behaviorSim;
    private AdvancedBreakSystem.SessionManager sessionManager;

    // Realistic Human Progression System
    private HumanProgressionSystem.ProgressionTracker progressionTracker;
    private HumanProgressionSystem.ProgressiveBreakManager progressiveBreaks;
    private HumanProgressionSystem.SessionPlan currentSessionPlan;

    private long sessionStartTime;
    private int resourcesGathered = 0;
    private int sessionsCompleted = 0;
    private int currentSessionXP = 0;

    // Area maps - Combined WC and Mining
    private final Map<String, Area> resourceAreaMap = new HashMap<>();
    private final Map<String, Area[]> bankAreaMap = new HashMap<>();

    @Override
    public void onStart() {
        setupAreas();

        // Initialize realistic human progression system
        progressionTracker = new HumanProgressionSystem.ProgressionTracker();
        progressiveBreaks = new HumanProgressionSystem.ProgressiveBreakManager(progressionTracker);
        currentSessionPlan = progressiveBreaks.getCurrentSession();

        // Set activity based on progression system
        selectedActivity = currentSessionPlan.activity;
        selectedResource = currentSessionPlan.resource;
        selectedLocation = currentSessionPlan.location;
        selectedResourceType = selectedResource;

        log("=== REALISTIC HUMAN SESSION STARTING ===");
        log("Stage: " + progressionTracker.getCurrentStage());
        log("Activity: " + selectedActivity + " - " + selectedResource + " at " + selectedLocation);
        log("Planned Duration: " + currentSessionPlan.durationMinutes + " minutes");
        log("Reasoning: " + currentSessionPlan.reasoning);

        // Set current areas
        String areaKey = selectedLocation + "_" + selectedResource;
        currentResourceArea = resourceAreaMap.get(areaKey);
        allBankAreas = bankAreaMap.get(selectedLocation);
        currentBankArea = allBankAreas[0];

        // Initialize systems
        Antiban.initialize(Players.getLocal().getName());
        AdvancedBreakSystem.BreakProfile breakProfile = new AdvancedBreakSystem.BreakProfile("moderate");
        breakManager = new AdvancedBreakSystem.SmartBreakManager(breakProfile);
        efficiencyManager = new AdvancedBreakSystem.MLEfficiencyManager();
        behaviorSim = new AdvancedBreakSystem.HumanBehaviorSimulator();
        sessionManager = new AdvancedBreakSystem.SessionManager();

        sessionStartTime = System.currentTimeMillis();
        lastStateChange = System.currentTimeMillis();
        currentSessionXP = 0;

        // Create runtime status UI
        SwingUtilities.invokeLater(this::createStatusUI);
    }

    private void setupAreas() {
        // WOODCUTTING AREAS
        resourceAreaMap.put("Lumbridge_Tree", new Area(3149, 3465, 3171, 3449));
        resourceAreaMap.put("Varrock_Oak", new Area(3185, 3425, 3195, 3415));
        resourceAreaMap.put("Draynor_Willow", new Area(3085, 3231, 3089, 3224));
        resourceAreaMap.put("Seers_Maple", new Area(2722, 3503, 2730, 3496));
        resourceAreaMap.put("Catherby_Yew", new Area(2756, 3429, 2763, 3422));

        // MINING AREAS
        resourceAreaMap.put("Lumbridge_Copper", new Area(3228, 3148, 3242, 3140));
        resourceAreaMap.put("Lumbridge_Tin", new Area(3228, 3148, 3242, 3140));
        resourceAreaMap.put("Varrock_Iron", new Area(3280, 3365, 3290, 3355));
        resourceAreaMap.put("Falador_Coal", new Area(3028, 9739, 3062, 9713));
        resourceAreaMap.put("Mining_Guild_Coal", new Area(3146, 9506, 3190, 9474));
        resourceAreaMap.put("Al_Kharid_Iron", new Area(3298, 3315, 3307, 3302));

        // BANK AREAS
        bankAreaMap.put("Lumbridge", new Area[]{
                new Area(3179, 3448, 3191, 3432), // Lumbridge castle
                new Area(3179, 3448, 3191, 3432)  // Backup same location
        });
        bankAreaMap.put("Varrock", new Area[]{
                new Area(3182, 3436, 3187, 3432), // Varrock east
                new Area(3250, 3424, 3257, 3419)  // Varrock west
        });
        bankAreaMap.put("Draynor", new Area[]{
                new Area(3092, 3240, 3096, 3245)  // Draynor bank
        });
        bankAreaMap.put("Seers", new Area[]{
                new Area(2721, 3493, 2730, 3488)  // Seers bank
        });
        bankAreaMap.put("Catherby", new Area[]{
                new Area(2807, 3441, 2812, 3436)  // Catherby bank
        });
        bankAreaMap.put("Falador", new Area[]{
                new Area(2943, 3371, 2949, 3365), // Falador east
                new Area(2943, 3371, 2949, 3365)  // Same area backup
        });
        bankAreaMap.put("Al_Kharid", new Area[]{
                new Area(3269, 3167, 3272, 3161)  // Al Kharid bank
        });
    }

    private void createStatusUI() {
        statusFrame = new JFrame("Bot Status - " + selectedActivity);
        statusFrame.setSize(500, 400);
        statusFrame.setLayout(new GridLayout(11, 1, 5, 5));
        statusFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        // Initialize all labels with references and CURRENT values
        activityLabel = new JLabel("Activity: " + selectedActivity + " - " + selectedResource);
        locationLabel = new JLabel("Location: " + selectedLocation + " (Banking: " + getBankingLocation() + ")");
        statusLabel = new JLabel("Status: Starting...");

        // Get actual current inventory count
        int currentInventory = getTotalResourcesInInventory();
        resourcesLabel = new JLabel("Resources: " + resourcesGathered + " trips, " + currentInventory + " in inventory");

        skillGainsLabel = new JLabel("XP This Session: 0 (Total: 0)");
        sessionLabel = new JLabel("Session: 0h 0m online");
        nextBreakLabel = new JLabel("Next Break: Session ends in " + (currentSessionPlan != null ? currentSessionPlan.durationMinutes : 20) + " min");
        breakDurationLabel = new JLabel("Break Duration: " + (currentSessionPlan != null ? currentSessionPlan.breakLengthMinutes : 5) + " min");
        progressLabel = new JLabel("Stage: " + (progressionTracker != null ? progressionTracker.getCurrentStage() : "Starting") + " (Session 1)");

        // Add all labels to frame
        statusFrame.add(activityLabel);
        statusFrame.add(locationLabel);
        statusFrame.add(statusLabel);
        statusFrame.add(resourcesLabel);
        statusFrame.add(skillGainsLabel);
        statusFrame.add(sessionLabel);
        statusFrame.add(nextBreakLabel);
        statusFrame.add(breakDurationLabel);
        statusFrame.add(progressLabel);

        // Update button
        JButton updateBtn = new JButton("Refresh Status");
        updateBtn.addActionListener(e -> updateStatusUI());
        statusFrame.add(updateBtn);

        // Close button
        JButton closeBtn = new JButton("Hide Window");
        closeBtn.addActionListener(e -> statusFrame.setVisible(false));
        statusFrame.add(closeBtn);

        statusFrame.setVisible(true);

        // Start update timer - using javax.swing.Timer
        javax.swing.Timer uiTimer = new javax.swing.Timer(3000, e -> updateStatusUI());
        uiTimer.start();
    }

    private void updateStatusUI() {
        if (statusFrame == null || !statusFrame.isVisible()) return;

        try {
            long sessionTime = System.currentTimeMillis() - sessionStartTime;
            long minutes = sessionTime / 60000;
            long hours = minutes / 60;

            // Update all labels using direct references - NO component access by index
            if (statusLabel != null) {
                String currentStatus = (state != null ? state.getDescription() : "Unknown");
                statusLabel.setText("Status: " + currentStatus);
            }

            if (sessionLabel != null) {
                sessionLabel.setText(String.format("Session: %dh %dm online", hours, minutes % 60));
            }

            if (resourcesLabel != null) {
                int totalInInventory = getTotalResourcesInInventory();
                resourcesLabel.setText("Resources: " + resourcesGathered + " trips, " + totalInInventory + " in inventory");
            }

            if (skillGainsLabel != null) {
                skillGainsLabel.setText(String.format("XP This Session: %d (Total: ~%d)",
                        currentSessionXP, calculateEstimatedXP(resourcesGathered, selectedResource)));
            }

            if (currentSessionPlan != null) {
                BreakInfo breakInfo = calculateProgressiveBreakInfo(sessionTime);
                if (nextBreakLabel != null) {
                    nextBreakLabel.setText("Next Break: " + breakInfo.timeUntilBreak);
                }
                if (breakDurationLabel != null) {
                    breakDurationLabel.setText("Break Duration: " + breakInfo.expectedDuration);
                }
            }

            // Update activity and location labels
            if (activityLabel != null) {
                activityLabel.setText("Activity: " + selectedActivity + " - " + selectedResource);
            }

            if (locationLabel != null) {
                locationLabel.setText("Location: " + selectedLocation + " (Banking: " + getBankingLocation() + ")");
            }

            // Update progression label
            if (progressLabel != null && progressionTracker != null) {
                progressLabel.setText("Stage: " + progressionTracker.getCurrentStage() +
                        " (Session " + progressionTracker.getTotalSessions() + ")");
            }

        } catch (Exception e) {
            log("UI update failed: " + e.getMessage());
        }
    }

    private int getTotalResourcesInInventory() {
        try {
            // Count all possible resources, not just current selection
            int count = 0;

            // Woodcutting logs
            count += Inventory.count("Logs");
            count += Inventory.count("Oak logs");
            count += Inventory.count("Willow logs");
            count += Inventory.count("Maple logs");
            count += Inventory.count("Yew logs");

            // Mining ores
            count += Inventory.count("Copper ore");
            count += Inventory.count("Tin ore");
            count += Inventory.count("Iron ore");
            count += Inventory.count("Coal");

            return count;
        } catch (Exception e) {
            log("Failed to count inventory resources: " + e.getMessage());
            return 0;
        }
    }

    private String getBankingLocation() {
        try {
            if (currentBankArea == null) return "Unknown";

            // Determine actual banking location based on current bank area
            for (Map.Entry<String, Area[]> entry : bankAreaMap.entrySet()) {
                String location = entry.getKey();
                Area[] areas = entry.getValue();

                if (areas != null) {
                    for (Area area : areas) {
                        if (area != null && area.equals(currentBankArea)) {
                            return location;
                        }
                    }
                }
            }

            return "Unknown";
        } catch (Exception e) {
            return "Error";
        }
    }

    private BreakInfo calculateProgressiveBreakInfo(long sessionTime) {
        if (currentSessionPlan == null) {
            return new BreakInfo("Unknown", "Unknown");
        }

        long sessionMinutes = sessionTime / 60000;
        long plannedDuration = currentSessionPlan.durationMinutes;

        // Calculate time until session end
        long minutesLeft = plannedDuration - sessionMinutes;

        String timeUntil;
        if (minutesLeft > 0) {
            timeUntil = "Session ends in " + minutesLeft + " minutes";
        } else {
            timeUntil = "Session ending soon";
        }

        String duration = currentSessionPlan.breakLengthMinutes + " minutes";

        return new BreakInfo(timeUntil, duration);
    }

    private int calculateEstimatedXP(int trips, String resourceType) {
        // Estimate XP based on resource type and trips (28 logs/ores per trip)
        int itemsPerTrip = 28;
        int totalItems = trips * itemsPerTrip;

        // XP rates per item (approximate)
        Map<String, Integer> xpRates = new HashMap<>();
        xpRates.put("Tree", 5);
        xpRates.put("Oak", 15);
        xpRates.put("Willow", 25);
        xpRates.put("Maple", 50);
        xpRates.put("Yew", 175);
        xpRates.put("Copper", 5);
        xpRates.put("Tin", 5);
        xpRates.put("Iron", 35);
        xpRates.put("Coal", 50);

        int xpPerItem = xpRates.getOrDefault(resourceType, 10);
        return totalItems * xpPerItem;
    }

    @Override
    public int onLoop() {
        try {
            // === Realistic Human Progression Check ===
            if (progressiveBreaks != null && progressiveBreaks.shouldEndSession(System.currentTimeMillis())) {
                handleSessionEnd();
                return 5000; // Longer delay during session transition
            }

            // === Advanced Break System Check ===
            if (breakManager != null && breakManager.shouldTakeBreak(resourcesGathered, System.currentTimeMillis())) {
                breakManager.executeBreak();
                return 2000; // Longer return after break
            }

            // === ML-Enhanced Efficiency Randomization ===
            if (efficiencyManager != null && efficiencyManager.shouldSimulateInefficiency()) {
                int delay = efficiencyManager.getInefficiencyDelay();
                efficiencyManager.recordEfficiencyEvent("inefficiency", System.currentTimeMillis());
                log("Simulating human inefficiency: " + delay + "ms delay");
                return delay;
            }

            // === ML Game Context ===
            GameContext ctx = new GameContext(
                    Players.getLocal().isAnimating(),
                    Players.getLocal().isMoving(),
                    getHealthPercent(),
                    System.currentTimeMillis(),
                    Tabs.getOpen()
            );

            // === Advanced Antiban Logic ===
            BehaviorProfile profile = Antiban.getCurrentProfile();
            if (profile != null) {
                profile.updateContext(ctx);
                double risk = ctx.getRiskFactor();

                // Dynamic antiban frequency based on session length and risk
                double antibanProbability = calculateDynamicAntibanProbability(risk);

                if (Math.random() < antibanProbability && Antiban.shouldPerformAction()) {
                    Antiban.perform();
                    log("Antiban performed (risk: " + String.format("%.2f", risk) + ")");
                }
            }

            // === State Detection ===
            State currentState = getState();
            if (currentState == null) {
                log("Could not determine state. Stopping script.");
                return -1;
            }

            if (state != currentState) {
                state = currentState;
                lastStateChange = System.currentTimeMillis();
                stuckCounter = 0;
                log("State changed to: " + currentState);
            } else if (System.currentTimeMillis() - lastStateChange > 45000) { // Increased to 45s
                stuckCounter++;
                log("WARNING: Stuck in state " + currentState + " (count: " + stuckCounter + ")");
                if (stuckCounter >= MAX_STUCK_COUNT) {
                    log("Max stuck count reached. Ending session.");
                    return -1;
                }
                lastStateChange = System.currentTimeMillis();
            }

            // === State Execution ===
            switch (currentState) {
                case GETTING_TOOL:
                    handleGettingTool();
                    break;
                case WALKING_TO_BANK:
                    handleWalkingToBank();
                    break;
                case USEBANK:
                    handleUseBank();
                    break;
                case BANKING:
                    handleBanking();
                    break;
                case WALKING_TO_RESOURCE:
                    handleWalkingToResource();
                    break;
                case FINDING_RESOURCE:
                    handleFindingResource();
                    break;
                case GATHERING:
                    handleGathering();
                    break;
                default:
                    Sleep.sleep(500, 1000);
                    break;
            }

            // === Dynamic Loop Delay with Human Behavior ===
            return behaviorSim != null ? behaviorSim.getContextualDelay("looping") : 800;

        } catch (Exception e) {
            log("ERROR in loop: " + e.getMessage());
            e.printStackTrace();
            return 2000;
        }
    }

    private double calculateDynamicAntibanProbability(double risk) {
        long sessionLength = System.currentTimeMillis() - sessionStartTime;
        double baseProb = 0.08; // 8% base chance

        // Increase probability with longer sessions
        if (sessionLength > 3600000) { // 1 hour
            baseProb *= 1.5;
        }

        // Adjust based on risk
        if (risk > 0.7) {
            baseProb *= 0.6; // Reduce when risky
        } else if (risk < 0.3) {
            baseProb *= 1.3; // Increase when safe
        }

        return Math.min(0.25, baseProb); // Cap at 25%
    }

    private int getHealthPercent() {
        try {
            Player local = Players.getLocal();
            if (local != null && local.getHealthPercent() >= 0) {
                return local.getHealthPercent();
            }
            return 100;
        } catch (Exception e) {
            return 100;
        }
    }

    private void handleGettingTool() {
        String toolName = selectedActivity.equals("Woodcutting") ? "axe" : "pickaxe";

        if (!currentBankArea.contains(Players.getLocal())) {
            log("No " + toolName + " found. Walking to bank...");
            Walking.walk(currentBankArea.getRandomTile());
            Sleep.sleepUntil(() -> !Players.getLocal().isMoving(), 8000, 100);
        } else {
            if (!Bank.isOpen()) {
                // Try bank booth first
                GameObject bankBooth = GameObjects.closest("Bank booth");
                if (bankBooth != null && bankBooth.interact("Bank")) {
                    Sleep.sleepUntil(Bank::isOpen, 5000, 100);
                    return;
                }

                // Try banker NPC if no booth
                org.dreambot.api.wrappers.interactive.NPC banker = NPCs.closest("Banker");
                if (banker != null && banker.interact("Bank")) {
                    Sleep.sleepUntil(Bank::isOpen, 5000, 100);
                    return;
                }

                log("No bank booth or banker found.");
            } else {
                String[] tools = getToolHierarchy();
                for (String tool : tools) {
                    if (Bank.contains(tool)) {
                        log("Withdrawing tool: " + tool);
                        Bank.withdraw(tool, 1);
                        Sleep.sleepUntil(this::hasTool, 3000, 100);
                        break;
                    }
                }

                if (hasTool()) {
                    Bank.close();
                    Sleep.sleepUntil(() -> !Bank.isOpen(), 3000, 100);
                } else {
                    log("No " + toolName + " found. Stopping script.");
                    stop();
                }
            }
        }
    }

    private String[] getToolHierarchy() {
        if (selectedActivity.equals("Woodcutting")) {
            return new String[]{"Dragon axe", "Rune axe", "Adamant axe", "Mithril axe", "Steel axe", "Iron axe", "Bronze axe"};
        } else {
            return new String[]{"Dragon pickaxe", "Rune pickaxe", "Adamant pickaxe", "Mithril pickaxe", "Steel pickaxe", "Iron pickaxe", "Bronze pickaxe"};
        }
    }

    private boolean hasTool() {
        String toolKeyword = selectedActivity.equals("Woodcutting") ? "axe" : "pickaxe";
        return Inventory.contains(i -> i != null && i.getName().toLowerCase().contains(toolKeyword)) ||
                Equipment.contains(i -> i != null && i.getName().toLowerCase().contains(toolKeyword));
    }

    private void handleWalkingToBank() {
        log("Walking to bank...");
        if (!Walking.walk(currentBankArea.getRandomTile())) {
            Area fallback = getFallbackBankArea();
            if (fallback != null) {
                log("Primary bank blocked. Walking to fallback bank.");
                Walking.walk(fallback.getRandomTile());
                currentBankArea = fallback;
            }
        } else {
            Sleep.sleepUntil(() -> currentBankArea.contains(Players.getLocal()) || !Players.getLocal().isMoving(), 10000, 100);
        }
    }

    private void handleUseBank() {
        // Try bank booth first
        GameObject booth = GameObjects.closest("Bank booth");
        if (booth != null && booth.interact("Bank")) {
            Sleep.sleepUntil(Bank::isOpen, 5000, 100);
            return;
        }

        // Try banker NPC if no booth found
        org.dreambot.api.wrappers.interactive.NPC banker = NPCs.closest("Banker");
        if (banker != null && banker.interact("Bank")) {
            Sleep.sleepUntil(Bank::isOpen, 5000, 100);
            return;
        }

        log("No bank booth or banker found.");
    }

    private void handleBanking() {
        if (Bank.isOpen()) {
            try {
                // Get all possible resource products to deposit
                String[] possibleProducts = {
                        "Logs", "Oak logs", "Willow logs", "Maple logs", "Yew logs",
                        "Copper ore", "Tin ore", "Iron ore", "Coal"
                };

                boolean depositedSomething = false;

                // Try to deposit each type of resource
                for (String product : possibleProducts) {
                    if (Inventory.contains(product)) {
                        log("Depositing: " + product);
                        Bank.depositAll(product);
                        Sleep.sleepUntil(() -> !Inventory.contains(product), 3000, 100);
                        depositedSomething = true;
                    }
                }

                // If we deposited something, record the trip
                if (depositedSomething) {
                    resourcesGathered++;

                    // Estimate XP gained this trip
                    int xpThisTrip = estimateXPForTrip(selectedResource);
                    currentSessionXP += xpThisTrip;

                    log("Deposited resources. Trip #" + resourcesGathered + " (+"+xpThisTrip+"xp)");
                }

                // Close bank
                Bank.close();
                Sleep.sleepUntil(() -> !Bank.isOpen(), 3000, 100);

                if (sessionManager != null) {
                    sessionManager.recordAction("banking");
                }

            } catch (Exception e) {
                log("Banking error: " + e.getMessage());
            }
        } else {
            log("Banking called but bank is not open!");
        }
    }

    private int estimateXPForTrip(String resourceType) {
        // Estimate XP for a full inventory (28 items)
        Map<String, Integer> xpRates = new HashMap<>();
        xpRates.put("Tree", 5 * 28);      // 140 XP
        xpRates.put("Oak", 15 * 28);      // 420 XP
        xpRates.put("Willow", 25 * 28);   // 700 XP
        xpRates.put("Maple", 50 * 28);    // 1400 XP
        xpRates.put("Yew", 175 * 28);     // 4900 XP
        xpRates.put("Copper", 5 * 28);    // 140 XP
        xpRates.put("Tin", 5 * 28);       // 140 XP
        xpRates.put("Iron", 35 * 28);     // 980 XP
        xpRates.put("Coal", 50 * 28);     // 1400 XP

        return xpRates.getOrDefault(resourceType, 280); // Default 10 XP per item
    }

    private String getResourceProduct() {
        if (selectedActivity.equals("Woodcutting")) {
            // Different log types for different trees
            switch (selectedResource) {
                case "Oak": return "Oak logs";
                case "Willow": return "Willow logs";
                case "Maple": return "Maple logs";
                case "Yew": return "Yew logs";
                default: return "Logs"; // Regular tree
            }
        } else {
            // Mining products
            switch (selectedResource) {
                case "Copper": return "Copper ore";
                case "Tin": return "Tin ore";
                case "Iron": return "Iron ore";
                case "Coal": return "Coal";
                default: return "ore";
            }
        }
    }

    private void handleWalkingToResource() {
        log("Walking to " + selectedResource + " area...");
        if (!Walking.walk(currentResourceArea.getRandomTile())) {
            log("Walking failed.");
        } else {
            Sleep.sleepUntil(() -> currentResourceArea.contains(Players.getLocal()) || !Players.getLocal().isMoving(), 10000, 100);
        }
    }

    private void handleFindingResource() {
        if (!Players.getLocal().isAnimating() && !Players.getLocal().isMoving()) {

            // Apply efficiency randomization - sometimes "miss" the resource
            if (efficiencyManager != null && efficiencyManager.shouldMissClick()) {
                log("Simulating miss-click...");
                if (behaviorSim != null) {
                    behaviorSim.simulateMissClick();
                }
                efficiencyManager.recordEfficiencyEvent("miss_click", System.currentTimeMillis());
                return;
            }

            // Human hesitation simulation
            if (behaviorSim != null) {
                behaviorSim.simulateHesitation();
            }

            String interactionName = selectedActivity.equals("Woodcutting") ? "Chop down" : "Mine";

            // Get the correct object name for detection
            String objectName = getCorrectResourceName(selectedResourceType);
            log("Looking for: " + objectName + " (selected: " + selectedResourceType + ")");

            GameObject resource = GameObjects.closest(r ->
                    r != null && r.exists() &&
                            r.getName().equalsIgnoreCase(objectName) &&
                            currentResourceArea.contains(r));

            if (resource != null) {
                log("Found " + resource.getName() + " at " + resource.getTile());
                if (resource.interact(interactionName)) {
                    log("Starting to gather: " + objectName);
                    if (sessionManager != null) {
                        sessionManager.recordAction("gather_start");
                    }
                    Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 5000, 100);
                }
            } else {
                // Debug: show what objects are actually in the area
                debugNearbyObjects();
                log("No " + objectName + " found. Moving inside area...");

                // Area rotation for human-like behavior
                if (behaviorSim != null && behaviorSim.shouldRotateArea()) {
                    log("Rotating to different spot in area...");
                }

                Walking.walk(currentResourceArea.getRandomTile());
                int searchDelay = behaviorSim != null ? behaviorSim.getContextualDelay("searching") : 1500;
                Sleep.sleep(searchDelay);
            }
        }
    }

    private String getCorrectResourceName(String selectedResource) {
        // Map GUI selections to actual in-game object names
        Map<String, String> nameMapping = new HashMap<>();

        // Woodcutting mappings
        nameMapping.put("Tree", "Tree");
        nameMapping.put("Oak", "Oak tree");
        nameMapping.put("Willow", "Willow tree");
        nameMapping.put("Maple", "Maple tree");
        nameMapping.put("Yew", "Yew tree");

        // Mining mappings - using correct rock names
        nameMapping.put("Copper", "Copper rocks");
        nameMapping.put("Tin", "Tin rocks");
        nameMapping.put("Iron", "Iron rocks");
        nameMapping.put("Coal", "Coal rocks");

        return nameMapping.getOrDefault(selectedResource, selectedResource);
    }

    private void debugNearbyObjects() {
        try {
            // Log nearby objects for debugging
            java.util.List<GameObject> nearbyObjectsList = GameObjects.all(obj ->
                    obj != null &&
                            obj.exists() &&
                            currentResourceArea.contains(obj) &&
                            obj.distance() < 10
            );

            if (!nearbyObjectsList.isEmpty()) {
                log("Nearby objects in area:");
                for (int i = 0; i < Math.min(5, nearbyObjectsList.size()); i++) {
                    GameObject obj = nearbyObjectsList.get(i);
                    log("  - " + obj.getName() + " at " + obj.getTile());
                }
            } else {
                log("No objects found in current area!");
                log("Current area: " + currentResourceArea);
                log("Player location: " + Players.getLocal().getTile());
            }
        } catch (Exception e) {
            log("Debug failed: " + e.getMessage());
        }
    }

    private void handleGathering() {
        if (Players.getLocal().isAnimating() && !Inventory.isFull()) {
            String product = getResourceProduct();
            int count = Inventory.count(product);
            log("Gathering... " + count + "/28 " + product);

            // Variable delays during gathering with fatigue simulation
            int delay = behaviorSim != null ? behaviorSim.getContextualDelay("gathering") : 1500;
            Sleep.sleep(delay, delay + 500);

            if (sessionManager != null) {
                sessionManager.recordAction("gathering");
            }
        } else if (!Players.getLocal().isAnimating() && !Inventory.isFull()) {
            log("Finished resource. Searching for next...");
        }
    }

    private void handleSessionEnd() {
        log("=== SESSION ENDING ===");
        if (currentSessionPlan != null) {
            log("Completed session: " + currentSessionPlan);
        }

        // Record session completion
        if (progressiveBreaks != null) {
            progressiveBreaks.completeSession(currentSessionXP);
        }

        // Take break based on progression stage
        long breakDuration = currentSessionPlan != null ? currentSessionPlan.getBreakLengthMs() : 300000;
        log("Taking " + (breakDuration / 60000) + " minute break...");

        // Simulate the break
        try {
            Thread.sleep(Math.min(breakDuration, 300000)); // Cap at 5 minutes for testing
        } catch (InterruptedException e) {
            log("Break interrupted");
        }

        // Plan next session
        if (progressiveBreaks != null) {
            progressiveBreaks.startNewSession();
            currentSessionPlan = progressiveBreaks.getCurrentSession();
        }

        // Check if we should switch activities
        if (currentSessionPlan != null &&
                (!currentSessionPlan.activity.equals(selectedActivity) ||
                        !currentSessionPlan.resource.equals(selectedResource) ||
                        !currentSessionPlan.location.equals(selectedLocation))) {

            log("=== SWITCHING ACTIVITIES ===");
            log("From: " + selectedActivity + " " + selectedResource + " at " + selectedLocation);
            log("To: " + currentSessionPlan.activity + " " + currentSessionPlan.resource + " at " + currentSessionPlan.location);

            // Update current activity
            selectedActivity = currentSessionPlan.activity;
            selectedResource = currentSessionPlan.resource;
            selectedLocation = currentSessionPlan.location;
            selectedResourceType = selectedResource;

            // Update areas
            String areaKey = selectedLocation + "_" + selectedResource;
            currentResourceArea = resourceAreaMap.get(areaKey);
            allBankAreas = bankAreaMap.get(selectedLocation);
            if (allBankAreas != null && allBankAreas.length > 0) {
                currentBankArea = allBankAreas[0];
            }

            // Update UI
            SwingUtilities.invokeLater(this::updateActivityInUI);
        }

        // Reset counters for new session
        resourcesGathered = 0;
        sessionsCompleted++;
        currentSessionXP = 0;
        sessionStartTime = System.currentTimeMillis();

        log("=== NEW SESSION STARTED ===");
        if (progressionTracker != null) {
            log("Stage: " + progressionTracker.getCurrentStage());
        }
        log("Activity: " + selectedActivity + " - " + selectedResource + " at " + selectedLocation);
        if (currentSessionPlan != null) {
            log("Planned Duration: " + currentSessionPlan.durationMinutes + " minutes");
        }
    }

    private void updateActivityInUI() {
        try {
            if (activityLabel != null) {
                activityLabel.setText("Activity: " + selectedActivity + " - " + selectedResource);
            }
            if (locationLabel != null) {
                locationLabel.setText("Location: " + selectedLocation + " (Banking: " + getBankingLocation() + ")");
            }
        } catch (Exception e) {
            log("Activity UI update failed: " + e.getMessage());
        }
    }

    private Area getFallbackBankArea() {
        if (allBankAreas == null) return null;

        for (Area area : allBankAreas) {
            if (!area.equals(currentBankArea)) {
                return area;
            }
        }
        return null;
    }

    private State getState() {
        try {
            if (!hasTool()) {
                return State.GETTING_TOOL;
            }

            if (Inventory.isFull()) {
                if (!currentBankArea.contains(Players.getLocal().getTile())) {
                    return State.WALKING_TO_BANK;
                } else if (!Bank.isOpen()) {
                    return State.USEBANK;
                } else {
                    return State.BANKING;
                }
            }

            // Check if we have any resources to bank (even if not full inventory)
            if (hasResourcesToBank()) {
                if (!currentBankArea.contains(Players.getLocal().getTile())) {
                    return State.WALKING_TO_BANK;
                } else if (!Bank.isOpen()) {
                    return State.USEBANK;
                } else {
                    return State.BANKING;
                }
            }

            if (!currentResourceArea.contains(Players.getLocal().getTile())) {
                return State.WALKING_TO_RESOURCE;
            }

            if (Players.getLocal().isAnimating()) {
                return State.GATHERING;
            }

            return State.FINDING_RESOURCE;

        } catch (Exception e) {
            log("ERROR in getState(): " + e.getMessage());
            return null;
        }
    }

    private boolean hasResourcesToBank() {
        try {
            // Check if we have any resources worth banking
            String[] resources = {
                    "Logs", "Oak logs", "Willow logs", "Maple logs", "Yew logs",
                    "Copper ore", "Tin ore", "Iron ore", "Coal"
            };

            for (String resource : resources) {
                if (Inventory.contains(resource)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onExit() {
        long totalTime = System.currentTimeMillis() - sessionStartTime;
        log("=== SESSION ENDED ===");
        log("Total runtime: " + (totalTime / 60000) + " minutes");
        log("Resources gathered: " + resourcesGathered);
        log("Sessions completed: " + sessionsCompleted);
        if (sessionManager != null) {
            log(sessionManager.getSessionSummary());
        }

        // Close status UI
        if (statusFrame != null) {
            statusFrame.dispose();
        }

        if (Antiban.getCurrentProfile() != null) {
            Antiban.onScriptExit(false);
        }
    }
}