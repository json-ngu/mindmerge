package mindmerge.model;

/**
 * One of the options a decision weighs; is_chosen marks the option the author selected. The create
 * decision form also uses it, unsaved, to refill the options after an error.
 */
public class DecisionOption {

    private String  label;
    private String  description;  // may be null
    private boolean chosen;

    public DecisionOption() {
    }

    public DecisionOption(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel()                { return label; }
    public void setLabel(String v)          { this.label = v; }

    public String getDescription()          { return description; }
    public void setDescription(String v)    { this.description = v; }

    public boolean isChosen()               { return chosen; }
    public void setChosen(boolean v)        { this.chosen = v; }
}
