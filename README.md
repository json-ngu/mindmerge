# MindMerge
### Written by Jason Nguyen (Updated as of October 20, 2026)

**What is MindMerge?**

A web platform that records the reasoning behind a team's decisions: the context,
the options considered, and why the final choice was made. So that "why did we do
it this way?" can be answered months later, even after the people involved have left.

**CS157A Team 4** - Jason Nguyen, Andrew Khoi Luu, Hailey Liu

This document covers how to set the project up and run it on macOS or Windows. The application runs locally. Public hosting may be revisited later, since a Tomcat deployment requires a Java-capable host rather than a static or serverless platform.

## Features

**Working now**

- **Accounts**: sign up with full name, email and password; log in and out. Passwords are
  stored as salted PBKDF2-SHA256 hashes, and sessions expire after 30 minutes idle.
- **Dashboard**: decisions from your teams' projects with status, chosen option, author and
  color-coded tags; a "Needs your review" list for leads and approvers; your projects; a
  Team / Mine toggle; and search that updates as you type.
- **Companies**: create a company (you become its owner) and switch invites on or off in Settings.
- **People** (owners and admins): invite existing users by email, change roles, and remove
  members. Invites appear on the invited person's dashboard to join or decline.
- **Teams** (owners and admins): create teams and add members as lead, approver or member.
- **History is kept**: removing someone only ends their membership. Their decisions, comments
  and past memberships stay in the database.
- **Projects**: team leads and company owners/admins create projects and set their status and
  description; each project page lists its decisions.
- **Create decision and approvals**: record a decision with its options, the option you
  recommend and your rationale; a team lead or approver reviews and approves it.
- **Decision details and comments**: select a decision on the dashboard or a project page to
  see its context, options, rationale and newest-first discussion. Current team members, and
  the company's owners and administrators, can add permanent comments up to 2,000 characters.
  See [the comments guide](docs/comments.md) for code explanations, focused checks and manual
  testing steps.

**Coming next** (shown as "Soon" in the sidebar): the decisions list, tags and the audit log.

## Demo accounts

After loading `sql/schema.sql` (Setup step 2), these accounts can log in:

| Name | Email | Password |
|---|---|---|
| Jason Nguyen | jason.nguyen06@sjsu.edu | `jasonnguyen` |
| Andrew Khoi Luu | andrewkhoi.luu@sjsu.edu | `andrewluu` |
| Hailey Liu | hailey.liu01@sjsu.edu | `haileyliu` |

All three belong to the MindMerge company. Hailey is its owner and Jason is an admin, so both
see the Admin section (People, Teams); Andrew is a member. These are demo passwords for local
use only. The other seeded users have placeholder passwords and can't log in.

## Architecture

| Tier | Technology |
|---|---|
| Client | Browser; HTML, CSS, JSP pages |
| Application | Apache Tomcat 9; Java Servlets, JDBC |
| Data | MySQL |

Every page is rendered on the server. A request for the dashboard travels:

```
browser → /dashboard → DashboardServlet → DashboardDao → JDBC → MySQL
                              ↓
                    WEB-INF/dashboard.jsp → HTML → browser
```

The landing page (`/home`) needs no database. Signed-in pages (`/dashboard`, `/people`,
`/teams`, `/settings`) redirect to `/login` when there is no session.

## Requirements

| Tool | Version | Check with |
|---|---|---|
| JDK | 21 or newer | `java -version` |
| Apache Tomcat | **9.x** | folder name, e.g. `apache-tomcat-9.0.122` |
| MySQL Server | 8.0 or newer | `mysql --version` |
| Maven | 3.9 or newer | `mvn -v` |

> **Tomcat 9 is required.** Tomcat 10 and later renamed the `javax.servlet` package
> to `jakarta.servlet`. This project imports `javax.servlet`, so it will not run on
> Tomcat 10 or 11. Download Tomcat 9 at https://tomcat.apache.org/download-90.cgi

**Installing Maven**

- macOS: `brew install maven`
- Windows: download the binary zip from https://maven.apache.org/download.cgi,
  extract it, and add its `bin` folder to your PATH.

**Windows only: two things to set up first**

1. **`JAVA_HOME` must be set,** or Tomcat's startup script exits immediately.
   Settings → System → About → Advanced system settings → Environment Variables →
   New system variable, name `JAVA_HOME`, value your JDK folder
   (e.g. `C:\Program Files\Java\jdk-21`).

2. **Add MySQL to your PATH** if `mysql --version` isn't recognized. The folder is
   usually `C:\Program Files\MySQL\MySQL Server 8.0\bin`. Alternatively, use MySQL
   Workbench for the database steps below.

Throughout this document, `TOMCAT_HOME` means wherever you installed Tomcat 9,
for example `~/tools/apache-tomcat-9.0.122` or `C:\apache-tomcat-9.0.122`.

## Setup

### 1. Get the code

```bash
git clone https://github.com/json-ngu/CS157A-4.git mindmerge
cd mindmerge
```

### 2. Create the database

This creates the `mindmerge_db` database with all of its tables and the team's seed data
(companies, users, teams, projects, decisions, options, tags, comments and audit log).

> **It drops and recreates `mindmerge_db` every time.** Re-run it after pulling changes to
> `sql/schema.sql`. Anything you created locally (accounts, companies) is reset.

**macOS / Linux**

```bash
mysql -u root -p < sql/schema.sql
```

**Windows: Command Prompt**

```
mysql -u root -p < sql\schema.sql
```

**Windows: PowerShell** (PowerShell doesn't support `<` redirection)

```powershell
Get-Content sql\schema.sql | mysql -u root -p
```

**Or in MySQL Workbench, on any platform:** File → Open SQL Script, choose
`sql/schema.sql`, then click the lightning bolt to run it.

Confirm it worked:

```bash
mysql -u root -p -e "SELECT COUNT(*) FROM mindmerge_db.decision;"
```

You should see `16`. In Workbench, refresh the Schemas panel (right-click → Refresh All)
and expand `mindmerge_db` → Tables.

### 3. Add your database credentials

The repository contains `db.properties.example` but not `db.properties`, the real
file holds a password and is excluded from version control, so each
person needs to create their own.

**macOS / Linux**

```bash
cp db.properties.example src/main/resources/db.properties
```

**Windows: Command Prompt**

```
copy db.properties.example src\main\resources\db.properties
```

**Windows: PowerShell**

```powershell
Copy-Item db.properties.example src\main\resources\db.properties
```

Then open `src/main/resources/db.properties` and replace `your_password_here` with
your MySQL root password. No quotes and no trailing spaces:

```properties
db.url=jdbc:mysql://localhost:3306/mindmerge_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=YOUR_PASSWORD
```

### 4. Build

Same command on both platforms, run from the project root:

```bash
mvn clean package
```

The first run downloads dependencies and takes about a minute. Look for
`BUILD SUCCESS` and a new file at `target/mindmerge.war`.

### 5. Deploy and start Tomcat

**macOS / Linux**

```bash
cp target/mindmerge.war TOMCAT_HOME/webapps/
TOMCAT_HOME/bin/startup.sh
```

If you get "permission denied," make the scripts executable once:

```bash
chmod +x TOMCAT_HOME/bin/*.sh
```

**Windows: Command Prompt**

```
copy target\mindmerge.war TOMCAT_HOME\webapps\
TOMCAT_HOME\bin\startup.bat
```

**Windows: PowerShell**

```powershell
Copy-Item target\mindmerge.war TOMCAT_HOME\webapps\
& TOMCAT_HOME\bin\startup.bat
```

On Windows, `startup.bat` opens a second console window showing the server log.
Leave that window open; closing it stops Tomcat.

### 6. Open the application

**http://localhost:8080/mindmerge/**

You should see the landing page. Click **Log in** and use one of the
[demo accounts](#demo-accounts), or click **Get started** to create your own.

**To stop the server:**

- macOS / Linux: `TOMCAT_HOME/bin/shutdown.sh`
- Windows: `TOMCAT_HOME\bin\shutdown.bat`

## Troubleshooting

**`db.properties not found`**
The file must be at `src/main/resources/db.properties`, not the project root. Run
`mvn clean package` again after creating it. Maven copies it into the build, so
editing the source file alone changes nothing.

**`Access denied for user 'root'@'localhost'`**
The password in `db.properties` doesn't match your MySQL install. Use the same one
that works for `mysql -u root -p`.

**`Unknown database 'mindmerge_db'`**
Step 2 hasn't run successfully yet.

**A demo account says "Invalid email or password", or the log shows `Unknown column` / `Table ... doesn't exist`**
Your database is from an older version of `sql/schema.sql`. Re-run step 2, then rebuild and redeploy.

**`No suitable driver found for jdbc:mysql://...`**
Tomcat is serving an outdated copy of the app. Stop Tomcat, delete both
`webapps/mindmerge/` and `webapps/mindmerge.war`, rebuild, copy the new war in, and
start Tomcat again.

**Compilation errors mentioning `javax.servlet`**
You're running Tomcat 10 or later. Install Tomcat 9.

**Tomcat's startup script exits immediately (Windows)**
`JAVA_HOME` isn't set. See the Windows setup notes above.

**`Address already in use` when starting Tomcat**
Another process holds port 8080.

- macOS / Linux: `lsof -i :8080`
- Windows: `netstat -ano | findstr :8080`, then look up the PID in Task Manager

**`mysql` is not recognized (Windows)**
MySQL isn't on your PATH. Add it, or use MySQL Workbench for the database steps.

**Anything else — check Tomcat's log**, which is where stack traces land:

- macOS / Linux: `tail -50 TOMCAT_HOME/logs/catalina.out`
- Windows: open the newest `TOMCAT_HOME\logs\catalina.YYYY-MM-DD.log`
  (Windows has no `catalina.out`; the startup console window shows the same output)

## Repository layout

```
mindmerge/
├── pom.xml                        Maven build configuration
├── db.properties.example          Template for local database credentials
├── sql/schema.sql                 All tables, seed data and demo logins
├── docs/comments.md               How the comments feature works and how to check it
└── src/
    ├── test/java/mindmerge/       CommentChecks (run by hand; see docs/comments.md)
    └── main/
        ├── java/mindmerge/
        │   ├── util/              DbUtil (JDBC connections), PasswordUtil (hashing),
        │   │                      Auth (session helpers), Limits (input limits), Zones (time zones)
        │   ├── model/             Decision, DecisionOption, Comment, Project, Tag, User, Viewer,
        │   │                      Company, Member, Invite, Team
        │   ├── dao/               SQL: DashboardDao, DecisionDao, ApprovalDao, CommentDao,
        │   │                      ProjectDao, UserDao, CompanyDao, InviteDao, TeamDao
        │   └── servlet/           Request handlers: Home, Legal, Login, Register, Logout,
        │                          Dashboard, DecisionCreate, DecisionDetail, Approval, Project,
        │                          Company, People, Teams, Settings, Invite
        ├── resources/             db.properties goes here (you create it)
        └── webapp/
            ├── index.jsp          Entry point, redirects to /home
            ├── login.jsp, register.jsp
            ├── css/style.css      All styling
            ├── js/                Landing animation, scroll effects, live search,
            │                      password reveal, small form helpers
            ├── img/logo.svg       Logo and favicon
            └── WEB-INF/
                ├── web.xml        Deployment descriptor (session timeout, error pages)
                ├── home.jsp       Landing page; privacy.jsp, terms.jsp, error.jsp
                ├── dashboard.jsp, decision-create.jsp, decision-details.jsp,
                │   approvals.jsp, approval.jsp, projects.jsp, project.jsp,
                │   people.jsp, teams.jsp, settings.jsp, company-new.jsp
                └── jspf/          Shared fragments: head, sidebar, top bar, logo, notices,
                                   decision list item, comments
```
