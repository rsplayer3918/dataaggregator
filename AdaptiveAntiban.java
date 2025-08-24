import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.Randoms;
import org.dreambot.api.input.event.impl.keyboard.type.TypeKey;
import org.dreambot.api.input.event.impl.keyboard.type.PressKey;
import org.dreambot.api.input.event.impl.keyboard.type.ReleaseKey;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.interactive.Players;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

public class AdaptiveAntiban {

    private static long lastActionTime = 0;
    private static int consecutiveActions = 0;
    private static final int MAX_CONSECUTIVE_ACTIONS = 3;

    public static void perform(BehaviorProfile profile) {
        try {
            long now = System.currentTimeMillis();

            // Update the profile with current time
            profile.updateIfNeeded(now);

            // Prevent too many consecutive antiban actions
            if (consecutiveActions >= MAX_CONSECUTIVE_ACTIONS) {
                if (now - lastActionTime < 5000) { // 5 second cooldown
                    return;
                }
                consecutiveActions = 0;
            }

            // Get ML-predicted action probabilities
            double roll = Math.random();
            ActionProbabilities probs = profile.getActionProbabilities();

            // Execute action based on ML predictions
            if (roll < probs.mouseMove && shouldPerformMouseAction()) {
                moveMouseIntelligently(profile);
                recordAction();
            } else if (roll < probs.mouseMove + probs.tabSwitch && shouldPerformTabAction()) {
                performTabSwitch(profile);
                recordAction();
            } else if (roll < probs.mouseMove + probs.tabSwitch + probs.idle) {
                performIdleBehavior(profile);
                recordAction();
            } else if (roll < probs.mouseMove + probs.tabSwitch + probs.idle + probs.keystroke) {
                performKeystroke(profile);
                recordAction();
            } else if (roll < probs.getTotalProbability() && probs.backspace > 0) {
                performBackspace(profile);
                recordAction();
            }

            // Feed back the action result to the ML model for learning
            profile.recordActionOutcome(now);

        } catch (Exception e) {
            System.err.println("Error in AdaptiveAntiban: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void recordAction() {
        lastActionTime = System.currentTimeMillis();
        consecutiveActions++;
    }

    private static boolean shouldPerformMouseAction() {
        // Don't move mouse if player is in critical animation
        return !Players.getLocal().isAnimating() || Math.random() < 0.1;
    }

    private static boolean shouldPerformTabAction() {
        // Less likely to switch tabs during important actions
        return !Players.getLocal().isMoving() && !Players.getLocal().isAnimating();
    }

    private static void moveMouseIntelligently(BehaviorProfile profile) {
        try {
            // Get current mouse position
            Point current = Mouse.getPosition();

            // Use ML to determine realistic mouse movement
            MouseMovement movement = profile.predictMouseMovement(current);

            if (movement.shouldMove()) {
                // Simulate human-like mouse movement with curves and delays
                Point target = movement.getTargetPoint();

                // Add some randomness to make it more human-like
                target.x += ThreadLocalRandom.current().nextInt(-10, 11);
                target.y += ThreadLocalRandom.current().nextInt(-10, 11);

                // Ensure target is within screen bounds
                target = constrainToScreen(target);

                // Move with human-like speed variation
                Mouse.move(target);

                // Random micro-pause after mouse movement
                if (Math.random() < 0.3) {
                    Sleep.sleep(50, 200);
                }
            }

        } catch (Exception e) {
            // Fallback to simple random movement
            moveMouseRandomly(profile);
        }
    }

    private static void moveMouseRandomly(BehaviorProfile profile) {
        int x = profile.randomX();
        int y = profile.randomY();
        Mouse.move(constrainToScreen(new Point(x, y)));
        profile.sleepRandom();
    }

    private static Point constrainToScreen(Point point) {
        // Assuming 1920x1080 screen, adjust as needed
        point.x = Math.max(0, Math.min(1920, point.x));
        point.y = Math.max(0, Math.min(1080, point.y));
        return point;
    }

    private static void performTabSwitch(BehaviorProfile profile) {
        try {
            Tab currentTab = Tabs.getOpen();
            Tab[] possibleTabs = {Tab.INVENTORY, Tab.SKILLS, Tab.COMBAT, Tab.QUEST};

            // Use ML to select most human-like tab to switch to
            Tab targetTab = profile.predictBestTabSwitch(currentTab, possibleTabs);

            if (targetTab != null && targetTab != currentTab) {
                if (Tabs.open(targetTab)) {
                    // Random delay after tab switch
                    Sleep.sleep(profile.getTabSwitchDelay(), profile.getTabSwitchDelay() + 500);

                    // Sometimes switch back quickly (human behavior)
                    if (Math.random() < 0.2) {
                        Sleep.sleep(200, 800);
                        Tabs.open(currentTab);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to random tab switch
            profile.simulateTabSwitch();
        }
    }

    private static void performIdleBehavior(BehaviorProfile profile) {
        // ML-determined idle patterns
        IdleBehavior idleType = profile.selectIdleBehavior();

        switch (idleType) {
            case SHORT_PAUSE:
                Sleep.sleep(idleType.getRandomDuration());
                break;
            case LONG_PAUSE:
                Sleep.sleep(idleType.getRandomDuration());
                break;
            case MOUSE_WIGGLE:
                wiggleMouse(profile);
                break;
            case AFK_SIM:
                simulateAFKBehavior(profile);
                break;
        }
    }

    private static void wiggleMouse(BehaviorProfile profile) {
        Point start = Mouse.getPosition();
        for (int i = 0; i < ThreadLocalRandom.current().nextInt(2, 5); i++) {
            int offsetX = ThreadLocalRandom.current().nextInt(-20, 21);
            int offsetY = ThreadLocalRandom.current().nextInt(-20, 21);
            Mouse.move(new Point(start.x + offsetX, start.y + offsetY));
            Sleep.sleep(100, 300);
        }
        Mouse.move(start); // Return to original position
    }

    private static void simulateAFKBehavior(BehaviorProfile profile) {
        // Simulate looking away from screen
        long afkDuration = profile.getAFKDuration();
        Sleep.sleep((int)afkDuration, (int)(afkDuration + 1000));
    }

    private static void performKeystroke(BehaviorProfile profile) {
        try {
            char c = profile.predictRealisticKeystroke();
            TypeKey tk = new TypeKey(c);
            tk.run();

            // Variable delay after keystroke
            Sleep.sleep(50, 200);

        } catch (Exception e) {
            // Fallback to random character
            typeKeystroke(profile);
        }
    }

    private static void typeKeystroke(BehaviorProfile profile) {
        char c = profile.randomChar();
        TypeKey tk = new TypeKey(c);
        tk.run();
        profile.sleepRandom();
    }

    private static void performBackspace(BehaviorProfile profile) {
        try {
            PressKey press = new PressKey('\b');
            ReleaseKey release = new ReleaseKey('\b');

            press.run();
            Sleep.sleep(20, 100); // Realistic key press duration
            release.run();

            Sleep.sleep(100, 300); // Pause after backspace

        } catch (Exception e) {
            // Fallback
            backspace(profile);
        }
    }

    private static void backspace(BehaviorProfile profile) {
        PressKey press = new PressKey('\b');
        ReleaseKey release = new ReleaseKey('\b');
        press.run();
        profile.shortSleep();
        release.run();
        profile.sleepRandom();
    }
}