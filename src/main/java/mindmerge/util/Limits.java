package mindmerge.util;

/**
 * Input limits, in one place. Most match a column size in sql/schema.sql; the free-text limit keeps
 * well inside MySQL's TEXT type (64 KB). The JSP forms repeat these as maxlength attributes, and
 * js/decision-options.js repeats MAX_OPTIONS.
 */
public final class Limits {

    public static final int FULL_NAME = 120;       // user.full_name
    public static final int EMAIL = 180;           // user.email
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 128;    // also bounds the hashing work per request

    public static final int COMPANY_NAME = 120;    // company.name
    public static final int TEAM_NAME = 150;       // team.name
    public static final int PROJECT_NAME = 180;    // project.name

    public static final int DECISION_TITLE = 220;  // decision.title
    public static final int OPTION_LABEL = 220;    // decision_option.label
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;

    public static final int TEXT = 10000;          // context, rationale and descriptions (TEXT columns)
    public static final int COMMENT = 2000;        // a comment's body (comment.body)

    private Limits() {
    }
}
