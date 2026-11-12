package mindmerge.model;

import java.util.ArrayList;
import java.util.List;

public class Team {

    private int          teamId;
    private String       name;
    private String       companyName;  // set where teams from several companies are listed together
    private List<Member> members = new ArrayList<>(); // current members; role is team_role

    public int getTeamId()                 { return teamId; }
    public void setTeamId(int v)           { this.teamId = v; }

    public String getName()                { return name; }
    public void setName(String v)          { this.name = v; }

    public String getCompanyName()         { return companyName; }
    public void setCompanyName(String v)   { this.companyName = v; }

    public List<Member> getMembers()       { return members; }

    public boolean hasMember(int userId) {
        for (Member m : members) {
            if (m.getUserId() == userId) return true;
        }
        return false;
    }
}
