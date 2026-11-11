package mindmerge.model;

import mindmerge.util.Zones;
import java.time.LocalDateTime;

/** A permanent entry in a decision's discussion, including the author's display name. */
public class Comment {
    private int commentId;
    private String authorName;
    private String body;
    private LocalDateTime createdAt;

    public int getCommentId() { 
    	return commentId; 
    }
    public void setCommentId(int v) { 
    	commentId = v; 
    }
    public String getAuthorName() {
    	return authorName; 
    }
    public void setAuthorName(String v) { 
    	authorName = v; 
    }
    public String getBody() {
    	return body; 
    }
    public void setBody(String v){ 
    	body = v; 
    }
    public LocalDateTime getCreatedAt() { 
    	return createdAt;
    }
    public void setCreatedAt(LocalDateTime v) { 
    	createdAt = v; 
    }

    /** Uses the same viewer time zone and display format as decisions in the dashboard. */
    public String dateTime(String zoneId) {
        return Zones.dateTime(createdAt, zoneId);
    }
}
