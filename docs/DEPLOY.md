# Deploying the employee service

Server setup checklist for the Spring Boot backend (`com.hrms`, port 8082). It covers secrets,
configuration, build and the checks to run after the first start. It contains no secret values.

## 1. Production database user (once, by the DBA)

- [ ] Create a dedicated user for the application, separate from any development user.
- [ ] Grant SELECT, INSERT, UPDATE and DELETE on the `HRMS` tables and SELECT on the `HRMS`
      sequences. No DDL rights: Hibernate only validates the schema (`ddl-auto=validate`).
- [ ] Give it a new strong password.
- [ ] Confirm the unique index `UQ_EJD_ONE_CURRENT` (one current job row per employee) exists in the
      production schema:

  ```sql
  CREATE UNIQUE INDEX HRMS.UQ_EJD_ONE_CURRENT
    ON HRMS.EMPLOYEE_JOB_DETAILS (CASE WHEN IS_CURRENT = 1 THEN EMPLOYEE_ID END);
  ```
- [ ] Apply every script in `db/` that the production schema does not have yet, **before** deploying
      the backend build (Hibernate only validates, so a missing table or column stops the
      application from starting):
  - `leave_request_attachment.sql`, then `store_documents_in_db.sql` (run in this order)
  - `auth_must_change_password.sql`
  - `employee_photo.sql` (table `HRMS.EMPLOYEE_PHOTO`, employee profile photos)
  - `employee_menu_profile.sql` (data only: Employee > Profile in the sidebar opens `/app/profile`)
  - `work_shifts_sat_sun_weekend.sql` (data only: work shifts get Monday-Friday working days, so Saturday and Sunday are the weekend)
  - `hide_unbuilt_menu_items.sql` (data only: hides sidebar entries for screens that are not built yet; keeps Employee, Attendance, Leave, Payroll and Admin Config). Users keep their cached menu until they sign out and in.
- [ ] Do **not** run the one-off scripts that fix development test data: `fix_pending_leave_venkat_oct_2026.sql`,
      `cleanup_test_data_oct_2026.sql`, `cleanup_test_checkin_venkat_oct_4.sql`.

## 2. JWT secret

- [ ] Generate a new secret for production: `openssl rand -base64 48`.
- [ ] It must be Base64 and decode to at least 32 bytes, otherwise the application refuses to start.
- [ ] Store it in the secret store or password manager. Do not write it into a file in the repository.
- [ ] Never reuse a development secret in production.

## 3. Environment variables

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:oracle:thin:@<host>:1521/<service>` |
| `SPRING_DATASOURCE_USERNAME` | the production database user |
| `SPRING_DATASOURCE_PASSWORD` | its password |
| `HRMS_JWT_SECRET` | the secret from step 2 |
| `HRMS_CORS_ALLOWED_ORIGINS` | exact frontend origin (scheme, host, port); comma-separated, no spaces, e.g. `https://hrms.example.com` |
| `HRMS_WORK_EMAIL_DOMAIN` | company domain for generated work emails; no default, startup fails without it |
| `HRMS_BUSINESS_ZONE` | time zone for dates and the nightly attendance jobs, e.g. `Africa/Juba` |
| `APP_BASE_URL` | public URL of this service, if it is not `http://localhost:8082` |
| `HRMS_REFRESH_COOKIE_SECURE` | optional, default `true`. Leave it `true` in production: the refresh cookie is then only sent over HTTPS. Set `false` only for local development over plain http |
| `HRMS_REFRESH_COOKIE_SAME_SITE` | optional, default `Strict` |
| `HRMS_UPLOAD_MAX_FILE_SIZE_MB` | optional, default 25 |

- [ ] Set them in the service manager, container or secret store, not in a file in the repository.

## 4. Configuration file

- [ ] Either copy `src/main/resources/application.properties.example` to `application.properties`
      next to the jar (it reads the variables above and holds no secrets), or use environment
      variables only.
- [ ] `application.properties` is gitignored. Keep it that way.
- [ ] Leave Swagger/OpenAPI and Hibernate bind-value logging off (the defaults in the example file).

## 5. Build and deploy

- [ ] Install the shared library first: `mvnw -f common-lib/pom.xml install`.
- [ ] Build and test the service: `mvnw clean verify`; run the jar from `target/`.
- [ ] Frontend: set `serviceUrl` in `environment.prod.ts` to the backend's public address, then
      `ng build ehrms`.
- [ ] Serve both over HTTPS behind a reverse proxy. Do not expose port 8082 publicly.
- [ ] Host the frontend and the API on the **same site** (the same registrable domain, for example
      `hrms.example.com` and `api.example.com`). The refresh token is a SameSite cookie, so on two
      unrelated domains the browser will not send it and users would be signed out on every page reload.

## 6. Checks after the first start

- [ ] The application starts. A missing variable fails at startup with a clear message.
- [ ] `GET /actuator/health` returns UP, with no database details for an anonymous caller.
- [ ] `/swagger-ui.html` and `/v3/api-docs` are not reachable without signing in as HR_ADMIN.
- [ ] Sign in from the real frontend origin. A CORS error means `HRMS_CORS_ALLOWED_ORIGINS` does not
      match the origin exactly.
- [ ] After signing in, reload the page: you stay signed in. In the browser tools the `hrms_refresh`
      cookie is HttpOnly, Secure and scoped to `/api/v1/auth`, and local/session storage hold no token.
- [ ] Sign out: the cookie is removed and a reload shows the sign-in page.
- [ ] Create a test employee: the work email should end with the configured domain.
- [ ] Sign in as an EMPLOYEE: Employee > Profile in the sidebar opens their own profile.
- [ ] The start-up log says `Business time zone set to Africa/Juba` (or your zone). The example config defaults to `Africa/Juba`;
      if the property is blank the server's own zone is used (with a warning), and "today" and the nightly jobs follow that.
- [ ] Attendance: the 00:05 nightly job writes the absent / weekend / holiday / leave rows for yesterday and re-checks
      the last 7 days (no database change). For days that are already over when you go live, back-fill them once as
      HR_ADMIN: `POST /api/v1/attendance/admin/day-records?from=YYYY-MM-DD&to=YYYY-MM-DD` (at most 62 days per call,
      up to yesterday). Then open one month in the Attendance Summary: absent days are no longer always 0. HR_ADMIN can also do this from the
      "Regenerate day records" card at the bottom of that page (selected month, up to yesterday).
- [ ] Open an employee, upload a profile photo (JPG, PNG or WebP, up to 2 MB), reload the page: the photo
      is still shown. Remove it again.
- [ ] Read the first minutes of the log: no bound values (national IDs, password hashes) and no SQL.
- [ ] A token issued by a development instance is rejected with 401, which shows the production
      secret is in use.

## 7. Afterwards

- [ ] Change the development database password if it was ever reused elsewhere.
- [ ] Record the date the JWT secret was set and plan to rotate it on a schedule. Rotating it signs
      every user out.
