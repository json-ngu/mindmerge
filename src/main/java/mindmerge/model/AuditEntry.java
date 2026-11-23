package mindmerge.model;

import mindmerge.util.Zones;

import java.time.LocalDateTime;

/** One audit_log row as the Audit log page shows it: who did what to which item, and when. */
public class AuditEntry {

    private LocalDateTime createdAt;
    private String actor;        // full name of the person who made the change
    private String operation;    // create, update, decide, reject, invite, change_role, ...
    private String entityType;   // decision, comment, project, tag, team, employee, company
    private String entityName;   // the item's current name or title, null if it cannot be found
    private String detail;       // specifics, may be null
    private int decisionId;      // for decisions and comments: the decision page to link to
    private int projectId;       // for projects: the project page to link to

    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public String getActor()                  { return actor; }
    public void setActor(String v)            { this.actor = v; }
    public String getOperation()              { return operation; }
    public void setOperation(String v)        { this.operation = v; }
    public String getEntityType()             { return entityType; }
    public void setEntityType(String v)       { this.entityType = v; }
    public String getEntityName()             { return entityName; }
    public void setEntityName(String v)       { this.entityName = v; }
    public String getDetail()                 { return detail; }
    public void setDetail(String v)           { this.detail = v; }
    public int getDecisionId()                { return decisionId; }
    public void setDecisionId(int v)          { this.decisionId = v; }
    public int getProjectId()                 { return projectId; }
    public void setProjectId(int v)           { this.projectId = v; }

    public String dateTime(String zoneId) {
        return Zones.dateTime(createdAt, zoneId);
    }

    /** The words before the item's name: "Approved decision", "Invited", "Removed". */
    public String getBefore() {
        switch (entityType + ":" + operation) {
            case "employee:invite":          return "Invited";
            case "employee:cancel_invite":   return "Cancelled the invitation of";
            case "employee:join":            return "Joined the company";
            case "employee:decline_invite":  return "Declined the invitation to join the company";
            case "employee:change_role":     return "Changed the company role of";
            case "employee:remove":          return "Removed";
            case "employee:leave":           return "Left the company";
            case "team:add_member":          return "Added a member to team";
            case "team:change_role":         return "Changed a role on team";
            case "team:remove_member":       return "Removed a member from team";
            case "comment:create":           return "Commented on decision";
            default:                         return verb() + " " + label();
        }
    }

    /** The words after the item's name, if any: "from the company". */
    public String getAfter() {
        return "employee".equals(entityType) && "remove".equals(operation) ? "from the company" : "";
    }

    /** Whether the sentence names the item (joining, declining or leaving names nothing else). */
    public boolean isNamed() {
        return !("employee".equals(entityType)
                && ("join".equals(operation) || "decline_invite".equals(operation) || "leave".equals(operation)));
    }

    private String verb() {
        switch (operation) {
            case "create":          return "Created";
            case "update":          return "Updated";
            case "rename":          return "Renamed";
            case "decide":          return "Approved";
            case "reject":          return "Rejected";
            case "request_changes": return "Requested changes on";
            case "status_change":   return "Changed the status of";
            case "archive":         return "Archived";
            default:                return operation.replace('_', ' ');
        }
    }

    private String label() {
        return "employee".equals(entityType) ? "member" : entityType;
    }
}
