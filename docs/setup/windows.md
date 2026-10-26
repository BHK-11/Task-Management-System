# Windows development setup

1. Install a supported JDK 21 or newer and PostgreSQL. Verify Java with `java -version`.
2. Create the PostgreSQL database and an application role that can create the `task_os` schema and tables.
3. In PowerShell, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the current session. `.env.example` documents names but is not loaded by Spring Boot.
4. From the project directory, run `.\mvnw.cmd spring-boot:run`.
5. Open `http://localhost:8080`, create an account at `/signup`, and sign in at `/login`.
6. Run `.\mvnw.cmd test` for the H2-backed security, migration, and MVC tests. Configure `TASKOS_TEST_DB_URL` to a dedicated PostgreSQL test database to run the opt-in PostgreSQL integration check.

If PostgreSQL is unavailable, startup stops at the datasource/Flyway boundary instead of silently substituting a different database. Check PostgreSQL service status, the database name, role grants, and the three environment variables. Use `SERVER_PORT` to select another listening port. No demo credentials or seeded administrator account are provided.
