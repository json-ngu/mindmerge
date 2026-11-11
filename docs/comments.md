# Decision comments

## How the code fits together

- `GET /comments?decision=<id>` and `POST` to that same URL use `CommentServlet`.
  Dashboard/project entries have a separate Comments link. The page contains only a
  decision title and discussion; decision detail/list features belong to the teammate.
  No `/decisions` route, decision detail JSP, or `DecisionDao.findById` is added.
- `Auth.requireUser` reads the authenticated session and supplies `userZone`.
  `CommentDao.findDiscussionTitle` checks current team membership and loads only a title.
  Nonmembers and missing decisions both receive 404, including decisions with no comments.
- `CommentDao` repeats `team_member.left_at IS NULL` in both its read and insert SQL.
  The insert uses `INSERT ... SELECT ... WHERE EXISTS` to check membership as part
  of the write, rather than trusting an earlier form display. Its user ID comes
  only from the authenticated session. All submitted values are bound parameters.
- `validationError` is shared between the servlet and DAO. Blank values (including
  Unicode whitespace) and values over `CommentDao.MAX_BODY = 2000` are rejected.
  Length counts UTF-16 units, matching HTML `maxlength`. Submitted text is not trimmed.
- `jspf/comments.jspf` escapes text with `c:out`, including the preserved form value.
  The form is above the thread. Comments use `Zones.dateTime`, preserve line breaks,
  and appear in `created_at DESC, comment_id DESC` order. The ID breaks timestamp ties.
  `js/comments.js` shows characters remaining as you type, paste, or remove text, using
  the textarea's `maxlength`; it also initializes drafts preserved after validation errors.
- Success redirects to `/comments?decision=<id>#comments` (post/redirect/get).
  The existing `comment` table supplies IDs and timestamps. No schema migration,
  database reset, update, or delete operation is needed. Historical authors' comments
  stay visible even when those authors leave; only the viewer's membership is checked.

## Build and focused checks

Run `mvn package`. The dependency-free checks are explicit main programs rather than
JUnit tests, so Maven compiles them but does not run them automatically:

```powershell
# Windows; run after mvn package. Semicolon is the Windows classpath separator.
$checkClasspath = 'target/test-classes;target/classes;' + ((Get-ChildItem target/mindmerge/WEB-INF/lib/*.jar).FullName -join ';')
java -cp $checkClasspath mindmerge.CommentChecks
java -cp $checkClasspath mindmerge.CommentChecks --database
```

The second command uses your existing `db.properties`. It checks existing current and
former memberships, sorted comments, and denied inserts. It never inserts an authorized
comment or changes memberships. A missing former-member fixture is reported explicitly.

## Manual checks on Tomcat 9

1. Deploy the built WAR using the normal project setup, without re-running `schema.sql`.
   Sign in and click Comments on the dashboard or project decision list.
   Verify the decision title and the form above the comments.
   Existing seeded comments should show their authors and the normal viewer-zone dates.
   A decision without comments should invite you to start the discussion.
2. Post two distinct comments. Each submission should redirect to `#comments`; the newest
   should be first. Refresh should not submit again. For a timestamp tie, submit twice
   within one database second and confirm the higher `comment_id` appears first.
   Compare with `SELECT comment_id, body, created_at FROM comment WHERE decision_id = <id>
   ORDER BY created_at DESC, comment_id DESC;` in your SQL client.
3. Submit spaces, tabs, and newlines: expect a helpful error and the original text retained.
   Try exactly 2,000 characters (accepted). To test server enforcement, remove `maxlength`
   and `required` with browser developer tools and send 2,001 characters or an empty body.
   Expect rejection, preserved text, and no new database row.
4. Submit literal `<script>alert(1)</script>` and quotes: they should display as text.
   Add a forged `user_id` field in developer tools; a valid comment must still be authored
   by the signed-in user. Comments are permanent, so use sensible test text.
5. Open the GET URL and submit a POST while logged out: both should redirect to login.
   Repeat as a signed-in nonmember, including a company admin who is not on this team:
   both should return 404 and insert nothing. Repeat with an existing former member
   whose `left_at` is non-null. A form left open before membership removal must also fail
   on submission. Use the normal membership UI or a disposable test environment for
   that scenario; do not reset the database.
6. Check `/decisions/create` and `/decisions/new` still open the existing creation flow.

The main-program checks do not verify browser layout, JSP rendering, authenticated HTTP
requests, or successful authorized posting. Those require the manual Tomcat checks above.

## Including comments in the teammate's decision detail page

The discussion already runs independently. Once the detail page exists, its GET servlet
can use the same DAO and supply these request attributes after checking decision access:

```java
CommentDao comments = new CommentDao();
request.setAttribute("discussionDecisionId", decisionId);
request.setAttribute("comments", comments.findByDecision(decisionId, user.getUserId()));
request.setAttribute("commentLimit", CommentDao.MAX_BODY);
// Auth.requireUser already supplies userZone.
```

In a JSP with the JSTL core taglib, include:

```jsp
<%@ include file="/WEB-INF/jspf/comments.jspf" %>
```

The fragment includes its own CSS and JavaScript. Its form posts to `CommentServlet`,
which checks membership again, preserves invalid drafts, and redirects successful posts
to the standalone discussion. Returning to the detail page instead is a small integration
change once its final route is agreed. No changes to DashboardDao, DecisionDao, Limits,
or the shared stylesheet are required by comments.
