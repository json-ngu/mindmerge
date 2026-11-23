package mindmerge.model;

/**
 * A tag. Each company has its own tag list; lists and filters that only show names use the
 * name-only constructor.
 */
public class Tag {

    private static final int COLORS = 6;

    private int tagId;
    private int companyId;
    private final String name;
    private int uses;  // decisions with this tag, on the Tags page

    public Tag(String name) { this.name = name; }

    public Tag(int tagId, int companyId, String name) {
        this.tagId = tagId;
        this.companyId = companyId;
        this.name = name;
    }

    public int getTagId()        { return tagId; }
    public int getCompanyId()    { return companyId; }
    public String getName()      { return name; }

    public int getUses()         { return uses; }
    public void setUses(int v)   { this.uses = v; }

    /** Stable 0-5 palette slot, so a tag always gets the same color everywhere. */
    public int getColor()        { return Math.floorMod(name.hashCode(), COLORS); }
}
