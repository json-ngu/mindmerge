package mindmerge.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A person's current membership in a company or a team; role is company_role or team_role. */
public class Member {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private int           userId;
    private String        fullName;
    private String        email;
    private String        role;
    private String        status;   // the account's user.status (company members only)
    private LocalDateTime joinedAt;

    public int getUserId()                  { return userId; }
    public void setUserId(int v)            { this.userId = v; }

    public String getFullName()             { return fullName; }
    public void setFullName(String v)       { this.fullName = v; }

    public String getEmail()                { return email; }
    public void setEmail(String v)          { this.email = v; }

    public String getRole()                 { return role; }
    public void setRole(String v)           { this.role = v; }

    public String getStatus()               { return status; }
    public void setStatus(String v)         { this.status = v; }

    public void setJoinedAt(LocalDateTime v) { this.joinedAt = v; }

    public String getJoinedDate()           { return joinedAt == null ? "" : joinedAt.format(DATE); }
}
