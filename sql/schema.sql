DROP DATABASE IF EXISTS mindmerge_db;
CREATE DATABASE mindmerge_db;
USE mindmerge_db;

CREATE TABLE company (
  company_id    INT           AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(120)  NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user` (
  user_id       INT           AUTO_INCREMENT PRIMARY KEY,
  full_name     VARCHAR(120)  NOT NULL,
  email         VARCHAR(180)  NOT NULL UNIQUE,
  password_hash VARCHAR(255)  NOT NULL,
  salt          VARCHAR(80)   NOT NULL,
  status        ENUM('active', 'deactivated', 'invited') NOT NULL DEFAULT 'active',
  allow_invites BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE employee (
  user_id       INT           NOT NULL,
  company_id    INT           NOT NULL,
  company_role  ENUM('owner', 'admin', 'member') NOT NULL DEFAULT 'member',
  joined_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at       DATETIME,
  PRIMARY KEY (user_id, company_id),
  FOREIGN KEY (user_id)    REFERENCES `user` (user_id),
  FOREIGN KEY (company_id) REFERENCES company (company_id)
);

CREATE TABLE company_invite (
  invite_id     INT           AUTO_INCREMENT PRIMARY KEY,
  company_id    INT           NOT NULL,
  user_id       INT           NOT NULL,
  company_role  ENUM('admin', 'member') NOT NULL DEFAULT 'member',
  invited_by    INT           NOT NULL,
  status        ENUM('pending', 'accepted', 'declined', 'cancelled') NOT NULL DEFAULT 'pending',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at  DATETIME,
  FOREIGN KEY (company_id) REFERENCES company (company_id),
  FOREIGN KEY (user_id)    REFERENCES `user` (user_id),
  FOREIGN KEY (invited_by) REFERENCES `user` (user_id)
);

CREATE TABLE team (
  team_id       INT           AUTO_INCREMENT PRIMARY KEY,
  company_id    INT           NOT NULL,
  name          VARCHAR(150)  NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (company_id) REFERENCES company (company_id)
);

CREATE TABLE team_member (
  user_id       INT           NOT NULL,
  team_id       INT           NOT NULL,
  team_role     ENUM('lead', 'approver', 'member') NOT NULL DEFAULT 'member',
  joined_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at       DATETIME,
  PRIMARY KEY (user_id, team_id),
  FOREIGN KEY (user_id) REFERENCES `user` (user_id),
  FOREIGN KEY (team_id) REFERENCES team (team_id)
);

CREATE TABLE project (
  project_id    INT           AUTO_INCREMENT PRIMARY KEY,
  team_id       INT           NOT NULL,
  name          VARCHAR(180)  NOT NULL,
  description   TEXT,
  status        ENUM('active', 'archived', 'on_hold', 'completed') NOT NULL DEFAULT 'active',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (team_id) REFERENCES team (team_id)
);

CREATE TABLE decision (
  decision_id   INT           AUTO_INCREMENT PRIMARY KEY,
  project_id    INT           NOT NULL,
  created_by    INT           NOT NULL,
  title         VARCHAR(220)  NOT NULL,
  context       TEXT          NOT NULL,
  rationale     TEXT,
  status        ENUM('proposed', 'decided', 'superseded') NOT NULL DEFAULT 'proposed',
  decided_at    DATETIME,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES project (project_id),
  FOREIGN KEY (created_by) REFERENCES `user` (user_id),
  FULLTEXT INDEX ft_decision_text (title, context, rationale)
);

CREATE TABLE decision_option (
  decision_id   INT           NOT NULL,
  option_num    INT           NOT NULL,
  label         VARCHAR(220)  NOT NULL,
  description   TEXT,
  is_chosen     BOOLEAN       NOT NULL DEFAULT FALSE,
  PRIMARY KEY (decision_id, option_num),
  FOREIGN KEY (decision_id) REFERENCES decision (decision_id)
);

CREATE TABLE decision_link (
  decision_id         INT       NOT NULL,
  related_decision_id INT       NOT NULL,
  link_type           ENUM('supersedes', 'depends_on', 'relates_to') NOT NULL,
  created_at          DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (decision_id, related_decision_id),
  FOREIGN KEY (decision_id)         REFERENCES decision (decision_id),
  FOREIGN KEY (related_decision_id) REFERENCES decision (decision_id),
  CHECK (decision_id <> related_decision_id)
);

CREATE TABLE tag (
  tag_id        INT           AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100)  NOT NULL UNIQUE
);

CREATE TABLE decision_tag (
  decision_id   INT           NOT NULL,
  tag_id        INT           NOT NULL,
  PRIMARY KEY (decision_id, tag_id),
  FOREIGN KEY (decision_id) REFERENCES decision (decision_id),
  FOREIGN KEY (tag_id)      REFERENCES tag (tag_id)
);

CREATE TABLE comment (
  comment_id    INT           AUTO_INCREMENT PRIMARY KEY,
  decision_id   INT           NOT NULL,
  user_id       INT           NOT NULL,
  body          TEXT          NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (decision_id) REFERENCES decision (decision_id),
  FOREIGN KEY (user_id)     REFERENCES `user` (user_id)
);

CREATE TABLE audit_log (
  log_id        INT           AUTO_INCREMENT PRIMARY KEY,
  user_id       INT           NOT NULL,
  company_id    INT           NOT NULL,
  operation     VARCHAR(40)   NOT NULL,
  entity_type   VARCHAR(80)   NOT NULL,
  entity_id     INT           NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id)    REFERENCES `user` (user_id),
  FOREIGN KEY (company_id) REFERENCES company (company_id)
);

INSERT INTO company (company_id, name, created_at) VALUES
  (1,  'MindMerge',     '2026-01-12 09:00:00'),
  (2,  'NVIDIA',        '2026-01-19 10:30:00'),
  (3,  'Intel',         '2026-02-02 14:15:00'),
  (4,  'Apple',         '2026-02-10 08:45:00'),
  (5,  'AMD',           '2026-02-23 11:00:00'),
  (6,  'Qualcomm',      '2026-03-04 09:20:00'),
  (7,  'Cisco Systems', '2026-03-16 13:40:00'),
  (8,  'Oracle',        '2026-03-30 15:05:00'),
  (9,  'Adobe',         '2026-04-07 10:10:00'),
  (10, 'Salesforce',    '2026-04-21 16:25:00');

INSERT INTO `user` (user_id, full_name, email, password_hash, salt, status, created_at) VALUES
  (1,  'Jason Nguyen',    'jason.nguyen06@sjsu.edu',     'pbkdf2$210000$tuFcYDhfA1uG/M1lKxQ6ko3bR08UOwNn+vH1vFTSOm4=', 'wV4kL9TFQh8/OhE9m39LDg==', 'active',      '2026-01-12 09:05:00'),
  (2,  'Andrew Khoi Luu', 'andrewkhoi.luu@sjsu.edu',     'pbkdf2$210000$44z4rNwTlBPm75OSMWMOqQmSvXmDxE4xRWa94J8WRCI=', 'eflMOvaucZXW0vvUnoErxg==', 'active',      '2026-01-12 09:40:00'),
  (3,  'Hailey Liu',      'hailey.liu01@sjsu.edu',       'pbkdf2$210000$PgTK3pg/06dRoRRMcNzW59p5CCBc9jVnXWmWZt87oos=', 'PjxQPk6D1QHQUQqraxGivw==', 'active',      '2026-01-12 09:02:00'),
  (4,  'Priya Raman',     'priya.raman@nvidia.com',      'pbkdf2$Qa2wSx4eDc6rFv8tGb0yHn1uJm3iKl5o', 'b58d4e1f9c27a063', 'active',      '2026-01-19 10:35:00'),
  (5,  'Marcus Webb',     'marcus.webb@nvidia.com',      'pbkdf2$Pl9okM7nJi5buH3vyG1cfT0xdR2zsE4w', '2e07c6b3d184f9a5', 'active',      '2026-01-20 14:50:00'),
  (6,  'Dana Okafor',     'dana@okaforadvisory.com',     'pbkdf2$Mn6bVc4xZl2kSj0hGf8dAs5pOi3uYt1r', '9a3f5107e62bc8d4', 'active',      '2026-01-26 08:20:00'),
  (7,  'Tomas Ibarra',    'tomas.ibarra@intel.com',      'pbkdf2$Ws3edR5tfY7uhI9ojP1lkM2njB4vgC6x', 'd607b2a8f4916c35', 'active',      '2026-02-02 14:20:00'),
  (8,  'Chloe Bennett',   'chloe.bennett@apple.com',     'pbkdf2$Lk8jHg6fDs4aPo2iUy0tRe1wQz3xCv5b', '5c2a90f7b4e1d836', 'active',      '2026-02-10 08:50:00'),
  (9,  'Rohan Mehta',     'rohan@mehtaconsulting.io',    'pbkdf2$Ty5uIo7pAs9dFg1hJk3lZx4cVb6nMq8w', 'e81d347a0c69f2b5', 'active',      '2026-02-16 13:05:00'),
  (10, 'Elena Sokolov',   'elena.sokolov@amd.com',       'pbkdf2$Bn4mQw6eRt8yUi0oPa1sDf3gHj5kLz7x', '3f9e71c5a208d64b', 'active',      '2026-02-23 11:05:00'),
  (11, 'Gabriel Santos',  'gabriel.santos@qualcomm.com', 'pbkdf2$Vc7xZa5sQw3eDr1tFy9uGi0oHp2jKl4b', 'a705d2f8e39c1b46', 'active',      '2026-03-04 09:25:00'),
  (12, 'Nadia Farouk',    'nadia@faroukpartners.com',    'pbkdf2$Gh2jKl4zXc6vBn8mQa0wSe1dRf3tGy5u', '6b4c80a1f57e2d93', 'active',      '2026-03-09 15:45:00'),
  (13, 'Liam Kerr',       'liam.kerr@cisco.com',         'pbkdf2$Rf6tGy8hUj0kIl2oPz3xAc5vBn7mQw9e', 'c239f6b4d81a075e', 'deactivated', '2026-03-16 13:45:00'),
  (14, 'Mei Tanaka',      'mei.tanaka@oracle.com',       'pbkdf2$Kj1lPo3iUy5tRe7wQa9sDf0gHz2xCv4b', '8d5107e3a9c62fb4', 'active',      '2026-03-30 15:10:00'),
  (15, 'Owen Price',      'owen.price@adobe.com',        'pbkdf2$Xs9dCf7vGb5hNj3mKl1qWp2oEi4rTy6u', '41e9b07c5d2a836f', 'active',      '2026-04-07 10:15:00'),
  (16, 'Sofia Duarte',    'sofia.duarte@salesforce.com', 'pbkdf2$Zq4wXe6rCt8vYb0nUm1iIo3pAl5sKd7f', 'f6a2c91403e8b57d', 'active',      '2026-04-21 16:30:00'),
  (17, 'Victor Alonso',   'victor.alonso@sjsu.edu',      'pbkdf2$Nm5bHg3fTr1eWq9oKi7uYt0pLz2xSc4d', '07b3e5d91f4ca268', 'invited',     '2026-09-18 09:35:00');

INSERT INTO employee (user_id, company_id, company_role, joined_at) VALUES
  (1,  1,  'admin',  '2026-01-12 09:05:00'),
  (2,  1,  'member', '2026-01-12 09:40:00'),
  (3,  1,  'owner',  '2026-01-12 09:02:00'),
  (6,  1,  'member', '2026-01-26 08:30:00'),
  (17, 1,  'member', '2026-09-18 09:35:00'),
  (4,  2,  'owner',  '2026-01-19 10:35:00'),
  (5,  2,  'member', '2026-01-20 14:50:00'),
  (6,  2,  'admin',  '2026-02-04 09:10:00'),
  (6,  3,  'member', '2026-02-18 10:45:00'),
  (7,  3,  'owner',  '2026-02-02 14:20:00'),
  (8,  4,  'owner',  '2026-02-10 08:50:00'),
  (9,  4,  'admin',  '2026-02-16 13:05:00'),
  (9,  5,  'member', '2026-03-02 09:55:00'),
  (10, 5,  'owner',  '2026-02-23 11:05:00'),
  (11, 6,  'owner',  '2026-03-04 09:25:00'),
  (12, 6,  'admin',  '2026-03-09 15:45:00'),
  (12, 7,  'member', '2026-03-23 11:30:00'),
  (13, 7,  'owner',  '2026-03-16 13:45:00'),
  (12, 8,  'member', '2026-04-02 14:00:00'),
  (14, 8,  'owner',  '2026-03-30 15:10:00'),
  (15, 9,  'owner',  '2026-04-07 10:15:00'),
  (16, 10, 'owner',  '2026-04-21 16:30:00');

INSERT INTO team (team_id, company_id, name, created_at) VALUES
  (1,  1,  'Platform Engineering', '2026-01-14 09:00:00'),
  (2,  1,  'Product Design',       '2026-01-15 10:20:00'),
  (3,  2,  'Inference Runtime',    '2026-01-21 09:30:00'),
  (4,  2,  'Driver Validation',    '2026-01-22 13:10:00'),
  (5,  3,  'Compiler Toolchain',   '2026-02-03 11:40:00'),
  (6,  4,  'Silicon Bring-Up',     '2026-02-11 09:15:00'),
  (7,  5,  'Memory Subsystem',     '2026-02-24 10:05:00'),
  (8,  6,  'Modem Firmware',       '2026-03-05 14:30:00'),
  (9,  7,  'Network Telemetry',    '2026-03-17 08:55:00'),
  (10, 8,  'Query Optimizer',      '2026-03-31 11:25:00'),
  (11, 9,  'Rendering Pipeline',   '2026-04-08 09:45:00'),
  (12, 10, 'Data Platform',        '2026-04-22 10:35:00');

INSERT INTO team_member (user_id, team_id, team_role, joined_at) VALUES
  (1,  1,  'approver', '2026-01-14 09:10:00'),
  (2,  1,  'member',   '2026-01-14 09:15:00'),
  (3,  1,  'lead',     '2026-01-14 09:05:00'),
  (6,  1,  'member',   '2026-01-27 08:40:00'),
  (1,  2,  'lead',     '2026-01-15 10:25:00'),
  (2,  2,  'member',   '2026-01-15 10:35:00'),
  (3,  2,  'approver', '2026-01-15 10:30:00'),
  (4,  3,  'lead',     '2026-01-21 09:35:00'),
  (5,  3,  'member',   '2026-01-21 09:40:00'),
  (6,  3,  'approver', '2026-02-04 09:20:00'),
  (5,  4,  'lead',     '2026-01-22 13:15:00'),
  (6,  4,  'member',   '2026-02-05 10:00:00'),
  (6,  5,  'approver', '2026-02-18 10:50:00'),
  (7,  5,  'lead',     '2026-02-03 11:45:00'),
  (8,  6,  'lead',     '2026-02-11 09:20:00'),
  (9,  6,  'approver', '2026-02-17 13:30:00'),
  (9,  7,  'member',   '2026-03-02 10:05:00'),
  (10, 7,  'lead',     '2026-02-24 10:10:00'),
  (11, 8,  'lead',     '2026-03-05 14:35:00'),
  (12, 8,  'approver', '2026-03-10 15:50:00'),
  (12, 9,  'member',   '2026-03-23 11:35:00'),
  (13, 9,  'lead',     '2026-03-17 09:00:00'),
  (12, 10, 'member',   '2026-04-02 14:10:00'),
  (14, 10, 'lead',     '2026-03-31 11:30:00'),
  (15, 11, 'lead',     '2026-04-08 09:50:00'),
  (16, 12, 'lead',     '2026-04-22 10:40:00');

INSERT INTO project (project_id, team_id, name, description, status, created_at) VALUES
  (1,  1,  'Decision Log Service',            'Servlet and DAO layer that records decisions, options and rationale.', 'active',    '2026-01-16 09:30:00'),
  (2,  1,  'Three-Tier Deployment Pipeline',  'Automate WAR builds and deployment to the Tomcat instance.',           'archived',  '2026-01-28 11:00:00'),
  (3,  2,  'Decision Timeline UI',            'Chronological view of a project decision history with filtering.',     'active',    '2026-02-06 08:50:00'),
  (4,  3,  'Kernel Autotuning',               'Select tile sizes and launch parameters for inference kernels.',       'active',    '2026-02-09 10:15:00'),
  (5,  4,  'Driver Regression Suite',         'Automated regression coverage for display and compute drivers.',       'on_hold',   '2026-02-20 14:40:00'),
  (6,  5,  'Vectorizer Rewrite',              'Rebuild the loop vectorization pass on the new compiler IR.',          'active',    '2026-02-27 09:05:00'),
  (7,  6,  'Power Management Bring-Up',       'Bring up clock and power domains on the next silicon revision.',       'active',    '2026-03-06 08:30:00'),
  (8,  7,  'Cache Coherence Protocol',        'Revise the coherence protocol for higher core counts.',                'active',    '2026-03-11 10:50:00'),
  (9,  8,  'Modem Slot Scheduler',            'Scheduling policy for uplink slot allocation in the modem firmware.',  'active',    '2026-03-19 13:20:00'),
  (10, 9,  'Streaming Telemetry Pipeline',    'Replace device polling with a streaming telemetry collector.',        'completed', '2026-03-25 09:40:00'),
  (11, 10, 'Join Reordering',                 'Cost-based join order selection in the query optimizer.',              'active',    '2026-04-03 11:10:00'),
  (12, 11, 'GPU Rasterizer',                  'Move the vector rasterizer from CPU to GPU compute.',                  'active',    '2026-04-13 10:25:00'),
  (13, 12, 'Change Data Capture',             'Stream row-level changes out of the primary store.',                   'on_hold',   '2026-04-27 15:00:00');

INSERT INTO decision (decision_id, project_id, created_by, title, context, rationale, status, decided_at, created_at) VALUES
  (1, 1, 3,
   'Store decisions in MySQL rather than a document database',
   'The decision records have a fixed shape and are queried by project, author and date far more often than they are read whole, and the team already has to run a relational database for the class requirement.',
   'A relational schema gives us joins across projects, tags and options for free. A document store would have pushed those joins into application code for no gain.',
   'superseded', '2026-01-23 15:00:00', '2026-01-19 09:45:00'),
  (2, 1, 1,
   'Keep the servlet layer on javax.servlet and Tomcat 9',
   'Tomcat 10 renamed every servlet package from javax.servlet to jakarta.servlet, and half the team already has Tomcat 9 configured and working locally.',
   'The namespace change buys us nothing this semester and breaks every machine already set up. We pinned javax.servlet 4.0.1 as provided scope so nobody silently upgrades.',
   'decided', '2026-02-04 11:20:00', '2026-01-30 10:10:00'),
  (3, 1, 3,
   'Move the decision store to MySQL 8 with InnoDB full-text search',
   'Search was running LIKE queries against the context and rationale columns, which scanned the whole table and got noticeably slower past a few thousand rows.',
   'InnoDB full-text indexes handle the search without adding a second system to run. Staying relational means the January decision still holds, only the version and index strategy change.',
   'decided', '2026-05-14 14:30:00', '2026-05-06 09:15:00'),
  (4, 2, 1,
   'Build the WAR with Maven instead of Ant',
   'The project has to produce a deployable WAR that the professor and both teammates can build on Windows and macOS without hand-copying JAR files.',
   'Maven resolves the JDBC driver and JSTL automatically and marks the servlet API as provided, so the WAR stays clean. Ant would have meant checking JARs into the repository.',
   'decided', '2026-02-11 10:00:00', '2026-02-02 13:25:00'),
  (5, 3, 1,
   'Render the timeline server-side with JSP rather than a single-page app',
   'The timeline is read far more than it is interacted with, and the course requires a visible three-tier separation between the browser, the servlet layer and the database.',
   'Server-rendered JSP keeps the presentation tier thin and the tier boundaries obvious. A single-page app would have added a build step and a JSON layer we do not need for a read-mostly view.',
   'decided', '2026-02-20 09:30:00', '2026-02-13 08:40:00'),
  (6, 4, 4,
   'Autotune inference kernels at build time instead of at runtime',
   'Runtime autotuning added roughly 400ms to the first inference call on each new shape, which showed up directly in the latency numbers customers measure.',
   'Build-time tuning moves that cost into CI where nobody is waiting on it. We accept a larger binary because the shapes we ship are known ahead of time.',
   'decided', '2026-03-02 13:45:00', '2026-02-24 11:05:00'),
  (7, 4, 5,
   'Cache autotuning results per GPU architecture',
   'Tuning results are currently recomputed per device, and the build matrix now covers fourteen devices across four architectures with largely identical results inside each architecture.',
   'Results within an architecture are nearly identical, so caching per architecture collapses fourteen tuning runs into four with no measurable loss. A per-device cache would barely be reused across the build matrix.',
   'proposed', NULL, '2026-09-08 10:20:00'),
  (8, 5, 5,
   'Block driver releases on any regression suite failure',
   'The suite has been advisory since January, and two releases shipped with known display regressions because the failures were treated as noise.',
   'Making the gate hard is the only version of this that changes behavior. We cut the suite to the tests we trust first so that a failure actually means something.',
   'decided', '2026-03-05 15:10:00', '2026-02-26 14:00:00'),
  (9, 6, 7,
   'Rewrite the vectorizer pass on the new IR rather than porting it',
   'The existing pass depends on loop metadata that the new IR does not carry, and the port kept growing compatibility shims to work around that.',
   'A rewrite costs about six weeks more up front but removes the shim layer entirely. Keeping both paths behind a flag would have doubled the test matrix indefinitely.',
   'decided', '2026-03-13 10:40:00', '2026-03-04 09:30:00'),
  (10, 7, 8,
   'Bring up power management before the display pipeline',
   'Both teams need the same silicon slot, and the display pipeline cannot be characterized until the clock and power domains are stable anyway.',
   'Power first unblocks display later, but display first does not unblock power. The ordering follows the dependency, not the team sizes.',
   'decided', '2026-03-24 11:15:00', '2026-03-16 09:50:00'),
  (11, 8, 10,
   'Move to a directory-based coherence protocol',
   'Snoop traffic grows with the square of the core count, and the next part doubles core count again on the same interconnect budget.',
   'A directory scales with core count, while snooping would quadruple traffic on an interconnect that is not growing. The snoop filter only delays the same problem by one generation.',
   'proposed', NULL, '2026-09-14 13:35:00'),
  (12, 9, 11,
   'Schedule uplink slots with a deadline-first policy',
   'Round-robin allocation meets average throughput targets but misses the latency bound on voice frames when the cell is congested.',
   'Deadline-first protects the traffic that has a hard bound and degrades the traffic that does not. Weighted fair queueing was close but needed per-flow tuning we cannot ship.',
   'decided', '2026-04-01 14:05:00', '2026-03-23 13:50:00'),
  (13, 10, 13,
   'Push telemetry with gNMI streaming instead of SNMP polling',
   'The collector polls about 4,000 devices on a 60-second interval, and operators see state that is up to a minute stale during an incident.',
   'Streaming pushes state as it changes, which removes both the staleness and the polling load. SNMP stays available for the older devices that cannot stream.',
   'decided', '2026-04-06 09:25:00', '2026-03-28 10:05:00'),
  (14, 11, 14,
   'Reorder joins with a cost model rather than heuristics',
   'The heuristic ordering picks a left-deep plan that is correct but occasionally orders of magnitude slower on queries with selective predicates on the inner relation.',
   'A cost model with real cardinality estimates fixes the pathological cases. Hint-driven ordering would have pushed the problem onto whoever wrote the query.',
   'decided', '2026-04-14 16:00:00', '2026-04-06 11:30:00'),
  (15, 12, 15,
   'Rasterize vector content on the GPU with compute shaders',
   'The CPU rasterizer cannot keep a large canvas at 60 frames per second once more than a few thousand paths are visible.',
   'Compute shaders give us the parallelism without the correctness problems the fixed-function pipeline has on self-intersecting paths. The CPU path stays as a fallback.',
   'decided', '2026-04-21 10:50:00', '2026-04-15 09:20:00'),
  (16, 13, 16,
   'Capture row changes from the redo log instead of table triggers',
   'Triggers add write latency to every transaction on the primary store, and the capture logic has to be maintained per table.',
   'Reading the redo log removes the per-transaction trigger cost and the per-table capture code. The log format must be handled per database version, which is a smaller and more predictable maintenance cost.',
   'proposed', NULL, '2026-09-22 15:40:00');

INSERT INTO decision_option (decision_id, option_num, label, description, is_chosen) VALUES
  (1,  1, 'Relational schema in MySQL',                    'Joins across projects, options and tags handled by the database.',            TRUE),
  (1,  2, 'MongoDB document store',                        'Flexible shape, but every join moves into application code.',                 FALSE),
  (1,  3, 'JSON files in the repository',                  'No database to run, no querying worth the name either.',                      FALSE),
  (2,  1, 'Stay on javax.servlet with Tomcat 9',           'Everything already configured keeps working. Pin the API at 4.0.1.',          TRUE),
  (2,  2, 'Migrate to jakarta.servlet and Tomcat 10',      'Current namespace, but breaks every local setup already running.',            FALSE),
  (3,  1, 'Stay on MySQL 5.7 with LIKE queries',           'No migration, but search keeps scanning the whole table.',                    FALSE),
  (3,  2, 'MySQL 8 with InnoDB full-text indexes',         'Indexed search with no second system to operate.',                            TRUE),
  (3,  3, 'Add a dedicated search engine',                 'Best search quality, another service to run and keep in sync.',               FALSE),
  (4,  1, 'Maven',                                         'Resolves dependencies, marks the servlet API provided, builds a clean WAR.',  TRUE),
  (4,  2, 'Ant with a hand-written build file',            'Full control, but JARs get checked into the repository.',                     FALSE),
  (4,  3, 'Gradle',                                        'Capable, but nobody on the team has used it before.',                         FALSE),
  (5,  1, 'Server-rendered JSP with JSTL',                 'Thin presentation tier and visible tier boundaries.',                         TRUE),
  (5,  2, 'React single-page app over a JSON API',         'Richer interaction, adds a build step and a JSON layer.',                     FALSE),
  (5,  3, 'JSP shell with fetch-based partial updates',    'Middle ground, but two rendering models to maintain.',                        FALSE),
  (6,  1, 'Autotune at build time',                        'Cost moves into CI. Larger binary, no first-call penalty.',                   TRUE),
  (6,  2, 'Autotune at runtime on first launch',           'Adapts to any device, costs ~400ms on the first call per shape.',             FALSE),
  (6,  3, 'Fixed hand-written launch parameters',          'Predictable, leaves roughly 20 percent of throughput unclaimed.',             FALSE),
  (7,  1, 'Cache per GPU architecture',                    'Fourteen devices collapse to four cache entries.',                            TRUE),
  (7,  2, 'Cache per exact device ID',                     'Most precise, almost no reuse across the build matrix.',                      FALSE),
  (7,  3, 'No cache',                                      'Simplest, keeps the full tuning cost on every build.',                        FALSE),
  (8,  1, 'Block the release on any failure',              'A failure stops the release. Requires trimming flaky tests first.',           TRUE),
  (8,  2, 'Warn and release anyway',                       'Current behavior, which is how two regressions shipped.',                     FALSE),
  (9,  1, 'Rewrite the pass on the new IR',                'Six weeks longer, removes the compatibility shims entirely.',                 TRUE),
  (9,  2, 'Port the existing pass unchanged',              'Fastest path, keeps growing the shim layer.',                                 FALSE),
  (9,  3, 'Keep both paths behind a flag',                 'No forced migration, doubles the test matrix indefinitely.',                  FALSE),
  (10, 1, 'Power management first',                        'Unblocks display characterization afterward.',                                TRUE),
  (10, 2, 'Display pipeline first',                        'Larger team starts sooner, but stalls on unstable clocks.',                   FALSE),
  (11, 1, 'Keep the snooping protocol',                    'No redesign, snoop traffic grows with the square of core count.',             FALSE),
  (11, 2, 'Directory-based coherence',                     'Scales with core count, costs directory storage and added latency.',          TRUE),
  (11, 3, 'Hybrid snoop filter in front of snooping',      'Smaller change, delays the problem by one generation.',                       FALSE),
  (12, 1, 'Round-robin allocation',                        'Fair on average, misses the voice latency bound under congestion.',           FALSE),
  (12, 2, 'Deadline-first allocation',                     'Protects bounded traffic, degrades best-effort traffic first.',               TRUE),
  (12, 3, 'Weighted fair queueing',                        'Close results, needs per-flow tuning we cannot ship.',                        FALSE),
  (13, 1, 'SNMP polling on a 60-second interval',          'Current behavior. State can be a full minute stale.',                         FALSE),
  (13, 2, 'gNMI streaming telemetry',                      'State pushed on change. Older devices stay on SNMP.',                         TRUE),
  (14, 1, 'Heuristic left-deep ordering',                  'Cheap to plan, occasionally orders of magnitude slower to run.',              FALSE),
  (14, 2, 'Cost-based ordering with cardinality estimates', 'Fixes the pathological plans, costs planning time.',                         TRUE),
  (14, 3, 'Hint-driven ordering',                          'Predictable, pushes the problem onto whoever writes the query.',              FALSE),
  (15, 1, 'CPU rasterizer',                                'Current behavior. Drops below 60fps past a few thousand paths.',              FALSE),
  (15, 2, 'GPU compute shaders',                           'Parallel and correct on self-intersecting paths. CPU path kept as fallback.', TRUE),
  (15, 3, 'Fixed-function graphics pipeline',              'Fast, wrong on self-intersecting and overlapping fills.',                     FALSE),
  (16, 1, 'Table triggers',                                'Works today, adds write latency and per-table maintenance.',                  FALSE),
  (16, 2, 'Redo log reader',                               'No write-path cost, needs log format handling per version.',                  TRUE),
  (16, 3, 'Periodic full snapshots',                       'Simple, cannot express row-level change events.',                             FALSE);

INSERT INTO decision_link (decision_id, related_decision_id, link_type, created_at) VALUES
  (3, 1, 'supersedes', '2026-05-14 14:35:00'),
  (2, 1, 'depends_on', '2026-01-30 10:20:00'),
  (3, 2, 'depends_on', '2026-05-06 09:30:00'),
  (4, 2, 'relates_to', '2026-02-02 13:40:00'),
  (4, 3, 'depends_on', '2026-05-18 11:05:00'),
  (5, 1, 'relates_to', '2026-02-13 08:55:00'),
  (5, 2, 'relates_to', '2026-02-14 09:10:00'),
  (7, 6, 'depends_on', '2026-09-08 10:30:00'),
  (8, 6, 'relates_to', '2026-03-01 14:15:00'),
  (8, 7, 'relates_to', '2026-09-09 11:45:00');

INSERT INTO tag (tag_id, name) VALUES
  (1,  'infrastructure'),
  (2,  'database'),
  (3,  'performance'),
  (4,  'security'),
  (5,  'cost'),
  (6,  'tooling'),
  (7,  'hardware'),
  (8,  'user-experience'),
  (9,  'data-model'),
  (10, 'reversible'),
  (11, 'one-way-door'),
  (12, 'compatibility');

INSERT INTO decision_tag (decision_id, tag_id) VALUES
  (1, 2), (1, 9), (1, 11),
  (2, 12), (2, 6),
  (3, 2), (3, 3), (3, 11),
  (4, 6), (4, 1),
  (5, 8), (5, 10),
  (6, 3), (6, 6),
  (7, 3), (7, 5),
  (8, 6), (8, 10),
  (9, 3), (9, 12),
  (10, 7),
  (11, 7), (11, 3),
  (12, 3),
  (13, 1), (13, 4),
  (14, 2), (14, 3),
  (15, 7), (15, 3),
  (16, 2), (16, 9);

INSERT INTO comment (comment_id, decision_id, user_id, body, created_at) VALUES
  (1,  1,  1,  'Agreed on relational. Worth writing down that this also makes the ER diagram assignment match what we actually built.', '2026-01-20 10:30:00'),
  (2,  1,  2,  'One concern: full-text search over the rationale column is going to be slow with plain LIKE. Not a blocker now but it will come up.', '2026-01-21 14:05:00'),
  (3,  2,  2,  'I had already started migrating my local copy to jakarta before this was decided. Reverting it, noting here so the history shows why.', '2026-02-02 09:50:00'),
  (4,  2,  1,  'Thanks. Lets keep the servlet API version pinned in the pom so an upgrade cannot happen by accident.', '2026-02-02 15:20:00'),
  (5,  3,  6,  'Check the full-text minimum token length before you rely on it. The InnoDB default drops anything under three characters.', '2026-05-07 11:20:00'),
  (6,  3,  3,  'Good catch. Set it to two and reindexed. Short tags like v2 are searchable now.', '2026-05-11 15:35:00'),
  (7,  5,  2,  'Server-rendered is the right call for the timeline, but the filter controls will still need a little JavaScript. That is fine, it just is not a SPA.', '2026-02-16 10:15:00'),
  (8,  6,  5,  'Build-time only works while the shipped shapes stay fixed. If dynamic shapes land next quarter we revisit this.', '2026-02-26 13:40:00'),
  (9,  6,  6,  'Confirmed the binary size increase is about 60MB, which is inside the limit the release team set.', '2026-02-27 09:05:00'),
  (10, 7,  4,  'Before deciding, can we confirm the tuning results are actually identical within an architecture? I expect they are, but we should measure it.', '2026-09-10 11:30:00'),
  (11, 10, 9,  'Suggest starting bring-up on the lowest clock bin. Fewer thermal variables while the power domains are still unstable.', '2026-03-18 10:40:00'),
  (12, 12, 12, 'Deadline-first needs a documented starvation bound for best-effort traffic or the carriers will ask about it in certification.', '2026-03-25 14:50:00'),
  (13, 13, 12, 'The streaming collector will need its own back-pressure story. Four thousand devices pushing on change is a different load shape than polling.', '2026-03-30 09:15:00'),
  (14, 14, 12, 'Make sure the cost model degrades gracefully when statistics are stale. That is where cost-based optimizers usually lose to heuristics.', '2026-04-08 16:25:00'),
  (15, 15, 15, 'Keeping the CPU path as a fallback is the part that makes this reversible. Lets not let it rot.', '2026-04-17 10:05:00');

INSERT INTO audit_log (log_id, user_id, company_id, operation, entity_type, entity_id, created_at) VALUES
  (1,  3,  1, 'create',        'decision', 1,  '2026-01-19 09:45:00'),
  (2,  1,  1, 'create',        'comment',  1,  '2026-01-20 10:30:00'),
  (3,  2,  1, 'create',        'comment',  2,  '2026-01-21 14:05:00'),
  (4,  1,  1, 'create',        'decision', 2,  '2026-01-30 10:10:00'),
  (5,  6,  1, 'update',        'project',  1,  '2026-02-09 09:35:00'),
  (6,  3,  1, 'create',        'decision', 3,  '2026-05-06 09:15:00'),
  (7,  3,  1, 'status_change', 'decision', 1,  '2026-05-14 14:35:00'),
  (8,  1,  1, 'archive',       'project',  2,  '2026-06-02 11:15:00'),
  (9,  4,  2, 'create',        'decision', 6,  '2026-02-24 11:05:00'),
  (10, 6,  2, 'update',        'decision', 6,  '2026-02-27 09:10:00'),
  (11, 5,  2, 'create',        'decision', 7,  '2026-09-08 10:20:00'),
  (12, 6,  3, 'update',        'project',  6,  '2026-03-10 10:45:00'),
  (13, 8,  4, 'create',        'decision', 10, '2026-03-16 09:50:00'),
  (14, 9,  5, 'update',        'project',  8,  '2026-03-13 14:20:00'),
  (15, 11, 6, 'create',        'decision', 12, '2026-03-23 13:50:00'),
  (16, 12, 6, 'update',        'decision', 12, '2026-03-26 15:10:00'),
  (17, 13, 7, 'create',        'decision', 13, '2026-03-28 10:05:00'),
  (18, 12, 7, 'update',        'project',  10, '2026-04-02 09:55:00'),
  (19, 14, 8, 'create',        'decision', 14, '2026-04-06 11:30:00'),
  (20, 12, 8, 'update',        'decision', 14, '2026-04-09 13:45:00'),
  (21, 15, 9, 'create',        'decision', 15, '2026-04-15 09:20:00');
