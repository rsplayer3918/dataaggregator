/**
 * Contains probabilities for different antiban actions
 */
public class ActionProbabilities {
    public double mouseMove;
    public double tabSwitch;
    public double idle;
    public double keystroke;
    public double backspace;

    public ActionProbabilities() {
        this.mouseMove = 0.0;
        this.tabSwitch = 0.0;
        this.idle = 0.0;
        this.keystroke = 0.0;
        this.backspace = 0.0;
    }

    public ActionProbabilities(double mouseMove, double tabSwitch, double idle, double keystroke, double backspace) {
        this.mouseMove = mouseMove;
        this.tabSwitch = tabSwitch;
        this.idle = idle;
        this.keystroke = keystroke;
        this.backspace = backspace;
    }

    /**
     * Get total probability of all actions
     */
    public double getTotalProbability() {
        return mouseMove + tabSwitch + idle + keystroke + backspace;
    }

    /**
     * Normalize probabilities to sum to 1.0
     */
    public void normalize() {
        double total = getTotalProbability();
        if (total > 0) {
            mouseMove /= total;
            tabSwitch /= total;
            idle /= total;
            keystroke /= total;
            backspace /= total;
        }
    }

    /**
     * Scale all probabilities by a factor
     */
    public void scale(double factor) {
        mouseMove *= factor;
        tabSwitch *= factor;
        idle *= factor;
        keystroke *= factor;
        backspace *= factor;
    }

    /**
     * Create a copy of these probabilities
     */
    public ActionProbabilities copy() {
        return new ActionProbabilities(mouseMove, tabSwitch, idle, keystroke, backspace);
    }

    @Override
    public String toString() {
        return String.format("ActionProbs[mouse:%.3f, tab:%.3f, idle:%.3f, key:%.3f, back:%.3f, total:%.3f]",
                mouseMove, tabSwitch, idle, keystroke, backspace, getTotalProbability());
    }
}