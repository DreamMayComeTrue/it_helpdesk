# FixDesk

Role-based IT helpdesk and ticketing web application for a university faculty IT Support Unit.

**Team:** Root Cause
**Course:** SCSE3203 / SECJ3303 Internet Programming, Semester 1 2026/2027, UTM

## Tech stack

Spring Boot 4 (Spring MVC + Thymeleaf), JdbcTemplate, MySQL 8, Bootstrap 5, Ollama (local AI).

## Run it locally

You need Java 17 or newer, Maven, and MySQL 8.

1. Create the database and its user once (in MySQL Workbench or the `mysql` client, as root). Choose your own password:

   ```sql
   CREATE DATABASE fixdesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'fixdesk'@'localhost' IDENTIFIED BY 'your-password-here';
   GRANT ALL PRIVILEGES ON fixdesk.* TO 'fixdesk'@'localhost';
   ```

2. Copy the example configuration. `application.properties` is git-ignored, so it stays on your machine:

   ```powershell
   Copy-Item src\main\resources\application.properties.example src\main\resources\application.properties
   ```

3. Give the application the database password through an environment variable, then start it:

   ```powershell
   $env:DB_PASSWORD = "your-password-here"
   mvn spring-boot:run
   ```

4. Open <http://localhost:8080>.

On the first start FixDesk creates its tables (`schema.sql`) and loads the demo data (`data.sql`).

Run the tests with `mvn test`. They do not need the database.

## Demo accounts

These exist only in the seed data, for development and the demo. All of them use the password `FixDesk@2026`.

| Email | Role | Notes |
| --- | --- | --- |
| `admin@fixdesk.test` | Admin | on duty |
| `tech.farid@fixdesk.test` | Technician | on duty |
| `tech.meiling@fixdesk.test` | Technician | busy |
| `tech.kumar@fixdesk.test` | Technician | on leave |
| `aina@fixdesk.test` | User | student |
| `daniel@fixdesk.test` | User | student |
| `priya@fixdesk.test` | User | student |
| `siti@fixdesk.test` | User | lecturer |

New accounts made on the Register page are always Users. Staff accounts are created by an admin.

## Project layout

One package per module under `src/main/java/com/rootcause/fixdesk/`, so members rarely edit the same file:

| Package | Module | Owner |
| --- | --- | --- |
| `user`, `security` | 1 User management | Alan |
| `ticket` | 2 Ticket submission | Alan |
| `triage` | 3 AI triage and assignment | Kai |
| `workflow` | 4 Status tracking and resolution | Kai |
| `comment` | 5 Comments and notifications | Jiae |
| `kb` | 6 Knowledge base | Jiae |
| `report` | 7 Reports and dashboard | Yihan |
| `audit` | 8 Audit log and history | Yihan |
| `common`, `config` | shared code | coordinator |

Shared files (`pom.xml`, `schema.sql`, `data.sql`, the layout in `templates/layout/`, `application.properties.example`) are changed only through their own small pull request.

For anyone adding a page:

- Use the shared layout (see the comment at the top of `templates/layout/base.html`).
- Every `method="post"` form includes the CSRF field: `<input th:replace="~{layout/fragments :: csrf}">`.
- Show user and AI text with `th:text`, never `th:utext`.
- Role rules for a new URL prefix go in `security/RoleGuard.java`; ownership checks go in the service.
- Every state-changing service method calls `AuditService.log(...)`.

## Branches

| Branch | Owner |
| --- | --- |
| `main` | Protected; merged by the coordinator |
| `alan` | Alan |
| `jiae` | Jiae |
| `yihan` | Yihan |
| `kai` | Kai |
| `voice_agent` | Voice call agent (bonus) |

Merge the latest `main` into your branch before opening a pull request.
