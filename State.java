public enum State {
    // Resource gathering states (applies to both WC and Mining)
    FINDING_RESOURCE("Looking for resource to gather"),
    GATHERING("Currently gathering resource"),

    // Banking states
    WALKING_TO_BANK("Walking to bank area"),
    USEBANK("Opening bank interface"),
    BANKING("Depositing items in bank"),

    // Movement states
    WALKING_TO_RESOURCE("Walking to resource area"),

    // Tool/equipment states
    GETTING_TOOL("Getting required tool from bank"),

    // Legacy states for backwards compatibility
    FINDING_TREE("Looking for tree to chop"),
    CHOPPING_TREE("Currently chopping tree"),
    WALKING_TO_TREES("Walking to tree area"),
    GETTING_AXE("Getting axe from bank"),

    // Break states
    ON_BREAK("Taking a scheduled break"),
    RESUMING("Resuming after break");

    private final String description;

    State(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}