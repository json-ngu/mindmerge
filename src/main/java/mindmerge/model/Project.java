package mindmerge.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Project {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private int           projectId;
    private String        name;
    private String        description;  // may be null
    private String        status;       // active, on_hold, completed, archived
    private String        teamName;
    private String        companyName;
    private int           decisionCount;
    private LocalDateTime lastActivity; // null when the project has no decisions yet
    private boolean       member;       // the viewer is on the project's team, so may record decisions
    private boolean       canManage;    // the viewer leads the team or administers its company

    public int getProjectId()                  { return projectId; }
    public void setProjectId(int v)            { this.projectId = v; }

    public String getName()                    { return name; }
    public void setName(String v)              { this.name = v; }

    public String getDescription()             { return description; }
    public void setDescription(String v)       { this.description = v; }

    public String getStatus()                  { return status; }
    public void setStatus(String v)            { this.status = v; }

    public String getTeamName()                { return teamName; }
    public void setTeamName(String v)          { this.teamName = v; }

    public String getCompanyName()             { return companyName; }
    public void setCompanyName(String v)       { this.companyName = v; }

    public int getDecisionCount()              { return decisionCount; }
    public void setDecisionCount(int v)        { this.decisionCount = v; }

    public void setLastActivity(LocalDateTime v) { this.lastActivity = v; }

    public boolean isMember()                  { return member; }
    public void setMember(boolean v)           { this.member = v; }

    public boolean isCanManage()               { return canManage; }
    public void setCanManage(boolean v)        { this.canManage = v; }

    /** The status as shown to people: "On hold" rather than on_hold. */
    public String getStatusLabel()             { return statusLabel(status); }

    /** Last activity as "Sep 22, 2026", or null when there is none. */
    public String getLastActivityDate() {
        return lastActivity == null ? null : lastActivity.format(SHORT);
    }

    public static String statusLabel(String status) {
        if ("on_hold".equals(status)) return "On hold";
        if (status == null || status.isEmpty()) return "";
        return Character.toUpperCase(status.charAt(0)) + status.substring(1);
    }
}
