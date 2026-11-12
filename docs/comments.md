# Decision comments

## How the code fits together

- The thread is shown on the decision page (`/decisions/details?id=<id>`, `DecisionDetailServlet`),
  which includes `jspf/comments.jspf`. Its form posts back to the same address (`doPost`).
  Each dashboard/project entry's Comments link opens the decision page at `#comments`.
- `Auth.requireUser` reads the authenticated session and supplies `userZone`.
  Who may read and add comments matches the decision page: current members of the project's team
  and current owners and administrators of its company (`ProjectDao.CAN_SEE`, used by
  `CommentDao.CAN_VIEW` and `DashboardDao.findVisible`). Anyone else, and missing decisions,
  receive 404, including decisions with no comments.
- `CommentDao` repeats the `left_at IS NULL` checks in both its read and insert SQL.
  The insert uses `INSERT ... SELECT ... WHERE EXISTS` to check membership as part
  of the write, rather than trusting an earlier form display. Its user ID comes
  only from the authenticated session. All submitted values are bound parameters.
- `validationError` is shared between the servlet and DAO. Blank values (including
  Unicode whitespace) and values over `CommentDao.MAX_BODY` (`Limits.COMMENT` = 2000) are rejected.
  Length counts UTF-16 units, matching HTML `maxlength`. Submitted text is not trimmed.
- `jspf/comments.jspf` escapes text with `c:out`, including the preserved form value.
  The form is above the thread. Comments use `Zones.dateTime`, preserve line breaks,
  and appear in `created_at DESC, comment_id DESC` order. The ID breaks timestamp ties.
  `js/comments.js` shows characters remaining as you type, paste, or remove text, using
  the textarea's `maxlength`; it also initializes drafts preserved after validation errors.
- Success redirects to `/decisions/details?id=<id>#comments` (post/redirect/get). A rejected
  comment is shown again on the decision page, with the error and its text kept.
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
   Verify the decision page opens at the Comments card, with the form above the comments.
   Existing seeded comments should show their authors and the normal viewer-zone dates.
   A decision without comments should say that no comments have been added.
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
5. Open the decision page and submit a POST while logged out: both should redirect to login.
   Repeat as a signed-in nonmember: both should return 404 and insert nothing. A company
   owner or administrator who is not on this team can read and add comments. Repeat with
   an existing former member whose `left_at` is non-null. A form left open before membership removal must also fail
   on submission. Use the normal membership UI or a disposable test environment for
   that scenario; do not reset the database.
6. Check `/decisions/create` still opens the existing creation flow.

The main-program checks do not verify browser layout, JSP rendering, authenticated HTTP
requests, or successful authorized posting. Those require the manual Tomcat checks above.

## How the decision page includes comments

`DecisionDetailServlet` checks decision access, sets `decision`, then supplies the fragment's attributes:

```java
request.setAttribute("comments", commentDao.findByDecision(decisionId, user.getUserId()));
request.setAttribute("commentLimit", CommentDao.MAX_BODY);
// Auth.requireUser already supplies userZone.
```

and `decision-details.jsp` includes `<%@ include file="/WEB-INF/jspf/comments.jspf" %>`.
Its styles are in the "Comments" section of `css/style.css`; the fragment loads `js/comments.js` itself.
The including page must declare the JSTL `c` and `fn` taglibs.
