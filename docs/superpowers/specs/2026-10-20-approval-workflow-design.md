# Approval workflow (branch → department secretary → department head → General Manager → HR): design

Status: DRAFT awaiting final approval, 2026-10-20; amended 2026-10-25 for the department route (section 17
lists every change). Nothing here is built. A throwaway spike proved the engine
choice (see "Spike results"). Items marked **[my call]** are my decisions, not yours; "(decided)" marks answers
you gave; the four open questions were answered (section 16).

## 1. Intent and why

Today every request (leave, attendance correction, overtime, recruitment requisition) is a one-step approval:
any `HR_ADMIN` or `HR_MANAGER` approves or rejects it. The company works through branches and a Head Office, so
the approval has to follow the organisation: the employee's own branch manager reviews first and picks the
department the request is for; that department's secretary at Head Office checks it is complete and forwards it;
the department head approves or rejects; the General Manager approves or rejects; HR then processes it. The
employee must be told the outcome and keep a history of what happened to each request.

The flow must **not be hard-coded**: HR admins must be able to change it later from a screen (add, remove and
reorder steps, set conditions such as "leave over 10 days goes to the General Manager") without a code release.

## 2. Goals, non-goals, anti-goals

**Goals**
1. One shared approval flow used by leave, attendance corrections, overtime and recruitment requisitions.
2. Routing by branch: an employee's request goes to the manager of the employee's branch, then to Head Office.
3. Flows are data: HR_ADMIN edits steps, approvers and conditions on a settings screen; changes apply to new
   requests only, requests in flight finish on the steps they started with.
4. A complete, per-request history (who, when, what decision, which comment) that the employee can see.
5. In-app notification to the employee on every decision (and to the next approver when a request reaches them).
6. Server-side authorization on every action; the UI only hides things for convenience.

**Non-goals (for this project)**
- A drawing tool for BPMN diagrams. The settings screen is a simple step list (Option B).
- Email/SMS/push notifications (in-app first; the design leaves room for more channels later).
- Parallel approvals, sub-processes, timers/escalations (auto-escalate after N days), delegation calendars.
- Payroll, loans, document requests or any request type beyond the four listed.
- Changing the leave balance rules, attendance rules or recruitment pipeline themselves.

**Anti-goals (would make this a failure even if it "works")**
- An admin can save a flow that gets stuck, skips every approval, or lets a person approve their own request.
- The workflow engine's tables or API become the system of record for business data (requests stay in HRMS).
- A request's status and its workflow position can disagree (half-applied approval).
- Free-text expressions entered on the settings screen are evaluated by the engine (code injection risk).

## 3. Constraints

- Backend: Spring Boot 4.0.8, Java 21, Oracle 23ai, schema `HRMS`, Flyway validate-only (the user runs SQL
  scripts), Hikari `auto-commit=false`, layered `controller → service → repository`, DTO records, `ApiResponse<T>`.
- Roles today: `HR_ADMIN`, `HR_MANAGER`, `EMPLOYEE`, `SYSTEM_ADMIN` (one role per login). Identity comes from the
  JWT `employeeId` claim, never from request bodies.
- Frontend: Angular 21 standalone, Material only, typed reactive forms, no server calls in components,
  English / French / Arabic with RTL.
- Business context: South Sudan, generic (not Omani), weekend Sat/Sun with shift overrides.
- Existing tables are only changed by additive, versioned scripts with a rollback script.

## 4. Decisions you made

| Topic | Decision |
|---|---|
| Flow (amended 2026-10-25) | Branch Manager submits (or reviews an employee's request) and **picks the target department per request** → that department's Secretary (Head Office) checks completeness and documents and **only forwards** → the target department's Head approves or rejects → General Manager approves or rejects → HR processes → employee notified → stored in history. |
| Target department | The branch manager chooses it for every request: at submit when the requester is a branch manager, otherwise at the branch step's forward action **[my call on the second case]**. |
| Secretary | Cannot approve or reject, only forward. One secretary per department, stored on the department. |
| Final decision | The General Manager approves or rejects; HR then processes (the leave/overtime/attendance effects run when HR processes). |
| Structure | A location can hold several branches; each branch has exactly one Branch Manager. |
| Head Office | Head Office staff also belong to a branch with a manager. |
| Branch table | Reuse `COMPANY_BRANCHES` as "branch", linked to `OFFICE_LOCATIONS`. |
| Engine | Embedded Flowable (BPMN), not a hand-rolled router. |
| Flexibility | HR_ADMIN edits steps and conditions from a settings screen, changeable any time. |
| Who edits | `HR_ADMIN` only, with a setting that lets `SYSTEM_ADMIN` also manage workflows (off by default). |
| Database | Flowable tables in their own Oracle schema `FLOWABLE`, same database. |
| Earlier guesses you accepted | Branch managers' own requests skip the branch step; HR covers an absent manager (secretary, head or branch manager); in-app notifications first; branch managers can reject; roles Branch Manager and General Manager are new. |

## 5. Architecture

### 5.1 Where things live

- New backend package `com.hrms.workflow` (same shape as other modules: controller, dto.request/response, entity,
  repository, service, service.impl). **Other modules never touch Flowable classes**; they call a
  `WorkflowService` interface. Only `workflow.service.impl` imports `org.flowable.*`.
- Flowable runs embedded, connected through the **existing `HRMS` datasource**. Its tables are in schema
  `FLOWABLE` (created by a script you run, as in the spike). One connection means one transaction.
- The request tables (`LEAVE_REQUESTS`, `OVERTIME_REQUESTS`, `ATTENDANCE_REGULARIZATIONS`, `JOB_REQUISITIONS`)
  stay the system of record. Flowable only holds "where is this request in its flow".
- The 45 Flowable tables are not Flyway-managed in `HRMS`. A separate script (`FLOWABLE` schema) creates them;
  upgrades to a new Flowable version ship the matching Flowable upgrade script.

### 5.2 One transaction per action

Every action (submit, forward, approve, reject, cancel) runs in one `@Transactional` service method that, in the
same transaction: updates the request row and status, writes a history row, completes or starts the Flowable
task, and records a notification. If any part fails everything rolls back (proven in the spike). External
effects (notifications fan-out beyond the database) happen after commit.

### 5.3 Flowable configuration (from the spike, mandatory)

- Table prefix `FLOWABLE.` with `tablePrefixIsSchema=true`, set through `EngineConfigurationConfigurer`.
  **Never** use `flowable.database-schema`: it switches the shared connection's current schema and breaks every
  other query on that connection.
- `flowable.idm.enabled=false` and `flowable.eventregistry.enabled=false` (we use our own users and roles).
- `flowable.database-schema-update=false`; async executor off unless a later feature needs timers.
- The Flowable REST API and any Flowable UI are **not** exposed. All access is through our controllers.

### 5.4 Settings become a workflow (no raw expressions)

Admin settings are stored as structured rows (section 6). When an admin publishes a workflow, the backend
**generates** a BPMN model from those rows and deploys it as a new version of the process definition for that
request type. The admin never types an expression. Conditions are structured: `field` from an allow-list for the
request type, an operator (`>`, `>=`, `<`, `<=`, `=`, `<>`), and a value. The generator builds the expression and
only ever uses allow-listed field names and numeric/enum-checked values. This prevents expression injection.

Validation before publish (blocking errors shown on the screen):
- Every step has at least one outgoing route; every outcome a step offers has a route.
- Every path ends in Approved or Rejected; no unreachable steps; no loops.
- Conditional routes for the same outcome cover all cases (a final "otherwise" route is required) and do not overlap.
- At least one step must be able to approve; the first step is reachable from submit.
- Self-approval cannot be configured: a step whose approver is the requester is skipped at runtime (section 7.3).

## 6. Data model (all new objects are additive; scripts with rollback)

Organisation
- `COMPANY_BRANCHES` (existing): add `LOCATION_ID` (FK `OFFICE_LOCATIONS`, nullable → back-fill → NOT NULL) and
  `BRANCH_MANAGER_EMPLOYEE_ID` (FK `EMPLOYEES`, nullable; one manager per branch, a person may manage more than one
  branch **[my call]**). Index both.
- `EMPLOYEE_JOB_DETAILS`: add `COMPANY_BRANCH_ID` (FK, nullable). A one-time back-fill assigns every existing
  employee a branch (HR does it through a screen; a CSV import is optional). Index.

Department routing (amended)
- `DEPARTMENTS`: add `SECRETARY_EMPLOYEE_ID` (FK `EMPLOYEES`, nullable, index). One secretary per department; a
  person may be secretary of several departments **[my call]**. `HEAD_EMPLOYEE_ID` already exists and is the head.
- Each request table gets `TARGET_DEPARTMENT_ID` (FK `DEPARTMENTS`, nullable, index), set when the branch
  manager picks the department. It must be an active department.

Workflow settings
- `WORKFLOW_DEFINITIONS`: `ID`, `REQUEST_TYPE` (`LEAVE`, `ATTENDANCE_REGULARIZATION`, `OVERTIME`,
  `RECRUITMENT_REQUISITION`), `VERSION_NO`, `STATUS` (`DRAFT`, `ACTIVE`, `RETIRED`), `FLOWABLE_DEPLOYMENT_ID`,
  `FLOWABLE_PROCESS_DEFINITION_ID`, `PUBLISHED_BY`, `PUBLISHED_AT`, audit columns. Exactly one `ACTIVE` per type
  (unique index on the active row).
- `WORKFLOW_STEPS`: `ID`, `DEFINITION_ID`, `STEP_KEY`, `POSITION`, `LABEL_EN`, `LABEL_AR`, `APPROVER_TYPE`
  (`BRANCH_MANAGER`, `DEPARTMENT_SECRETARY`, `DEPARTMENT_HEAD`, `ROLE`), `APPROVER_ROLE` (`HR_ADMIN`,
  `HR_MANAGER`, `GENERAL_MANAGER` when type is `ROLE`), `REQUIRES_DOCUMENT_REVIEW` flag, `PICKS_TARGET_DEPARTMENT`
  flag (true on the branch step). `DEPARTMENT_SECRETARY` and `DEPARTMENT_HEAD` resolve against the request's
  `TARGET_DEPARTMENT_ID`, not the requester's department.
- `WORKFLOW_ROUTES`: `ID`, `STEP_ID`, `OUTCOME` (`FORWARD`, `APPROVE`, `REJECT`), `COND_FIELD`, `COND_OPERATOR`,
  `COND_VALUE` (all null for an unconditional route), `TARGET` (a step key, `APPROVED` or `REJECTED`), `POSITION`.
- `WORKFLOW_SETTINGS`: key/value for global switches; first key `SYSTEM_ADMIN_CAN_MANAGE_WORKFLOWS`
  (default `false`).

Runtime and history
- Each request table gets: `WORKFLOW_DEFINITION_ID` (the version it runs on), `FLOWABLE_PROCESS_INSTANCE_ID`, and a
  workflow `STAGE` column (section 7.1). Existing `STATUS` columns keep their current meaning for downstream code.
- `REQUEST_HISTORY`: `ID`, `REQUEST_TYPE`, `REQUEST_ID`, `STEP_KEY`, `ACTION` (`SUBMITTED`, `FORWARDED`,
  `APPROVED`, `REJECTED`, `CANCELLED`, `OVERRIDDEN`, `AUTO_ROUTED`), `ACTOR_EMPLOYEE_ID`, `ACTOR_ROLE`, `COMMENT`,
  `REASON_CODE`, `ACTED_AT`. Append-only, never updated or deleted. Index `(REQUEST_TYPE, REQUEST_ID)` and
  `(ACTOR_EMPLOYEE_ID)`. This is the employee-visible history; it does not depend on Flowable's own history tables.
- `NOTIFICATIONS`: `ID`, `RECIPIENT_EMPLOYEE_ID`, `REQUEST_TYPE`, `REQUEST_ID`, `TITLE_KEY`, `BODY_KEY`,
  `PARAMS_JSON`, `IS_READ`, `CREATED_AT`, `READ_AT`. Index `(RECIPIENT_EMPLOYEE_ID, IS_READ, CREATED_AT)`.
  Stored as translation keys plus parameters, so each user sees their own language.

Roles
- Add role `GENERAL_MANAGER` (role check constraint, `ROLES` rows, menu rights) **[my call: a real role, like
  `HR_MANAGER`]**.
- **Branch Manager, Department Secretary and Department Head are relationships, not login roles [my call]**: a
  user is a branch manager if their employee is `BRANCH_MANAGER_EMPLOYEE_ID` of an active branch, a secretary if
  they are `SECRETARY_EMPLOYEE_ID` of an active department, a head if they are `HEAD_EMPLOYEE_ID`. They keep role
  `EMPLOYEE`. This avoids the one-role-per-user limit and follows how department heads already work. A person who
  is deactivated stops being secretary of every department (same moment headship is cleared).

## 7. Request lifecycle

### 7.1 Stages

`STAGE` on a request: `DRAFT` (not used unless needed), `PENDING_<stepKey>` (for example `PENDING_BRANCH`,
`PENDING_HR`, `PENDING_FINAL`), then terminal `APPROVED`, `REJECTED`, `CANCELLED`. The stage is derived from the
current Flowable task and written by our service in the same transaction. The old `STATUS` column is set to
`PENDING` while any `PENDING_*` stage is active, and to the terminal value at the end, so existing reports and
balance logic keep working **[my call]**.

### 7.2 Actions and who may take them

| Action | Who | Effect |
|---|---|---|
| Submit | The employee (JWT employee id) | Validates as today, starts the flow, goes to the first step. |
| Forward | Current step's approver | Moves to the next step per the routes (and conditions). The branch step must carry `targetDepartmentId`. The secretary step offers only this action. |
| Approve / Reject | Current step's approver, if the route allows it | Department head and General Manager approve (moves on) or reject (ends the flow, reason required). HR's step is "process": it completes the flow. |
| Cancel | The requester, while not decided | Ends the flow as `CANCELLED`, restores any held balance. |
| Override | `HR_ADMIN` | Acts on a branch, secretary or head step when that person is absent or missing; history shows `OVERRIDDEN` and a mandatory reason. |

An approver may act only when (a) the request is currently at a step they are assigned to, (b) for a
`BRANCH_MANAGER` step: they are the manager of the **requester's** branch, (b2) for a `DEPARTMENT_SECRETARY` /
`DEPARTMENT_HEAD` step: they are that person for the request's **target department**, (c) for a `ROLE` step: they
hold that role, (d) they are not the requester. All of this is enforced on the server in one place
(`WorkflowAuthorizationService`), not in controllers and not by the UI.

### 7.3 Edge cases (decided unless marked)

- Requester is the branch manager → the branch step is skipped (`AUTO_ROUTED` in history).
- Requester has no branch, or the branch has no manager → the request waits at the branch step for an `HR_ADMIN`
  override, which also picks the target department (`AUTO_ROUTED` with the reason) **[my call, replaces the old
  "straight to HR" rule, because HR cannot skip the department]**.
- Requester is a branch manager → the branch step is skipped and the submit must carry `targetDepartmentId`
  (400 `TARGET_DEPARTMENT_REQUIRED` otherwise).
- Target department has no secretary → the secretary step is skipped (`AUTO_ROUTED`) **[my call: a secretary only
  forwards, so skipping loses no decision]**. No head → waits for an `HR_ADMIN` override. Requester is the head
  → the head step is skipped to the General Manager.
- The next approver would be the requester (for example an HR_ADMIN requests leave) → that step is skipped to the
  next eligible one; if there is none, the request goes to `GENERAL_MANAGER` (decided).
- Legacy requests (section 11, `WORKFLOW_DEFINITION_ID` null) never use this flow; the old endpoints stay for them.
- Acting twice (double click, two approvers): the second action gets 409 (task already completed); safe to retry.
- Request edited while pending: not allowed; the employee cancels and resubmits **[my call]**.
- Workflow edited while requests are in flight: those requests finish on the version they started with; new
  requests use the new version.
- Optimistic locking (`@Version`) on request rows; lock failures map to 409.

### 7.4 What happens on the final decision

When the General Manager approves, the stage becomes `PENDING_HR_PROCESS` (the employee is notified; the
legacy `STATUS` stays `PENDING`, any held balance stays held). When HR processes, the flow ends as `APPROVED` and
the existing downstream logic runs unchanged **[my call on timing]**: leave balance deduction, attendance record
adjustment, overtime payable record, recruitment requisition becomes approved. On `REJECTED` and `CANCELLED` any
pending-days hold is released. These existing methods are reused and called from the workflow completion hook,
inside the same transaction; this project does not re-implement them.

### 7.5 Documents

Leave attachments already exist. HR's step is flagged "documents reviewed" when the type requires it; HR sees the
existing attachment download (authorized per request, as today). Attendance corrections and overtime get an
optional supporting-document attachment using the same storage and validation as leave **[my call]**.

## 8. Notifications

- Created in the same transaction as the action, for: the requester (forwarded, approved, rejected, cancelled,
  overridden) and the next approver (a request needs your action).
- Backend: `GET /api/v1/notifications` (paged, own only), unread count, mark read, mark all read. Employee id comes
  from the JWT; a user can never read another user's notifications.
- Frontend: a bell in the top bar with an unread badge and a dropdown list; the app polls the unread count every
  60 seconds and on route change **[my call: no websockets]**. Notification text uses translation keys.
- Retention: notifications older than 180 days are deleted by a scheduled job (idempotent, chunked).

## 9. Screens

Backend APIs under `/api/v1/...`; frontend in the existing feature folders and shared components.

1. **My requests** (employee): one list across the four request types with status/stage, filters, and a request
   detail page with the **timeline** from `REQUEST_HISTORY`.
2. **Approvals inbox**: requests waiting for me, grouped by request type; a branch manager sees only their branch,
   HR sees the HR step, HR Manager and General Manager see forwarded requests. Approve, reject (reason required),
   forward, with `confirm-dialog` stating exactly what changes.
3. **Request detail** (all roles allowed to see it): details, attachments, timeline, available actions.
4. **Notification bell** (top bar) and a notifications page.
5. **Workflow settings** (`HR_ADMIN`, and `SYSTEM_ADMIN` when the switch is on): per request type, an ordered step
   list, approver per step, routes with conditions, validation errors inline, a "Publish" action with a
   confirm-dialog, a version history list (read-only, with who published and when), and a visual preview of the
   steps.
6. **Branches** (admin): branches with location and branch manager; **Job Details** gets a branch dropdown;
   a "branch assignment" back-fill helper for HR.

Existing screens keep working during the transition: current leave approval, attendance approval and
requisition approval screens are replaced by the inbox when each request type moves over.

## 10. Security

- Server-side checks on every action (section 7.2). Identity from the JWT only.
- IDOR: any request id endpoint verifies the caller is the requester, a current or past participant (from
  `REQUEST_HISTORY`), the branch manager of the requester's branch, or HR/GM.
- No Flowable REST/UI exposure; no engine expressions from user input; settings fields come from allow-lists.
- Workflow settings changes and the `SYSTEM_ADMIN` switch are audited (who, when, before/after) in the existing
  audit events.
- History and notification tables are append-only through the service; no update or delete endpoints.
- No PII or salary values in logs, notifications or Flowable variables (variables hold ids and the allow-listed
  condition values only).
- A scan of this feature belongs in the next security audit run (deferred areas list).

## 11. Migration of existing data

1. Run the schema scripts (HRMS additions; FLOWABLE schema and grants, already created for the spike, the spike
   scratch objects removed).
2. HR assigns a branch to each employee (screen), and a manager to each branch.
3. Create the default workflow for each request type as the **ACTIVE v1**: branch manager (picks department) →
   department secretary (forward) → department head → General Manager → HR processes. Whether a small request
   may skip the General Manager is **not fixed in code**: v1 starts as "General Manager always" and the client
   sets a rule (for example leave up to N days goes from the head straight to HR) on the workflow settings screen
   at any time (amended).
4. Cut over one request type at a time (leave first). **Requests already pending at cut-over finish on the old
   one-step approval (decided).** A request is "legacy" when its `WORKFLOW_DEFINITION_ID` is null; legacy requests
   keep using the existing approve/reject/cancel endpoints and screens until they are decided, and never enter
   Flowable. New requests created after cut-over always use the new flow. The inbox and the old approval screen
   therefore run side by side until the last legacy request is closed; the old endpoints are then removed in a
   later cleanup. Approved, rejected and cancelled requests created before cut-over keep their existing
   "approved by / at" data and get a read-only history row built from it, so their timeline is not empty.
5. Rollback per request type: set the type back to the legacy approval path (feature flag per request type
   **[my call]**) until the new path is trusted; data written by the new path remains valid for the old reports.

## 12. Testing and quality bar

Following the project rules: unit tests for services; slice/integration tests for repositories and controllers on
Oracle (existing approach); per role (`EMPLOYEE`, branch manager, `HR_ADMIN`, `HR_MANAGER`, `GENERAL_MANAGER`,
`SYSTEM_ADMIN`); another employee's request; every state transition valid and invalid; double approve/cancel;
requester equals approver; no branch/no manager; condition boundaries (exactly 10 days); concurrent approvals;
publish-time validation failures (stuck flow, overlapping conditions, missing otherwise route); version
isolation (in-flight request unaffected by a new publish); rollback of the whole action on failure; Arabic text;
fixed clock. Regression: leave apply→approve/reject/cancel and balance, attendance regularization and overtime,
recruitment requisition. Frontend: states (loading, empty, error, success), double-submit, role visibility,
unsaved changes on the settings screen, RTL, 360 px.

## 13. Build order (each phase ships and is tested on its own)

1. **Organisation structure**: branch ↔ location, branch manager, employee branch, screens, back-fill.
   (1b, amended: department secretary on the department, set from Job Details like the head.)
2. **Workflow core** on **leave**: engine configuration, settings tables, generator and validation, history,
   authorization service, actions, completion hooks, inbox and detail screens for leave.
3. **Notifications** and the **workflow settings screen**.
4. Move **attendance corrections and overtime**.
5. Move **recruitment requisitions** (department head raises → branch → HR → final; the Finance/Management tick
   boxes stay on the HR step **[my call]**).

Phases 2 to 5 each need a plan of their own after this document is approved.

## 14. Spike results (what is already proven)

Flowable 8.0.0 runs on Spring Boot 4.0.8 + Java 21 + Oracle with Flowable's tables in a separate schema through
the same connection. A step list generated a BPMN flow with conditions (3 days → HR Manager, 15 days → General
Manager); editing the flow left an in-flight request on its old steps; one transaction covered a business row and
the workflow task, with correct rollback. Findings are folded into section 5.3 and the validation rules.
Spike code is throwaway and is not reused as-is.

## 15. Risks

- Flowable upgrades need DDL scripts for the `FLOWABLE` schema; pin the version and read release notes.
- A published workflow with a mistaken condition sends requests to the wrong approver; mitigated by validation,
  preview, confirm-dialog, version history, and re-publish (new requests only).
- Branch data quality: a wrong branch assignment misroutes a request; HR needs the branch back-fill screen and a
  report of employees without a branch or branches without a manager.
- Engine weight: larger jar, longer startup, more tests; accepted.

## 16. Questions answered by you (2026-10-20)

1. **Requests pending at cut-over:** finish on the old one-step approval (section 11, step 4).
2. **General Manager threshold:** configurable from the settings screen, so the client decides at what size a
   request goes to the HR Manager or the General Manager; v1 starts as "HR Manager always" (section 11, step 3).
3. **HR's own requests:** go to the `GENERAL_MANAGER` (section 7.3).
4. **A branch manager may manage several branches:** yes (section 6).

5. **Target department (2026-10-25):** the branch manager picks it per request.
6. **Secretary (2026-10-25):** the department secretary only forwards the request to the department head.

No open questions remain for you. The builder-level choices left open are in the plan phase (table names and
column types follow the existing conventions; the exact REST paths and the screen layouts follow the existing
screens).

## 17. Amendment 2026-10-25 (department route)

Replaces "Branch Manager → Head Office HR → HR Manager/GM" with "Branch Manager → Department Secretary →
Department Head → General Manager → HR processes". Sections changed: 1, 4, 6, 7.2, 7.3, 7.4, 11 (step 3), 13, 16.
New data: `DEPARTMENTS.SECRETARY_EMPLOYEE_ID`, `TARGET_DEPARTMENT_ID` on requests, two new approver types.
Calls I made for you to check: the branch manager picks the department at the forward action when an employee
raised the request; a missing secretary is skipped; a missing head or branch manager waits for HR_ADMIN override;
HR processing (not the General Manager's approval) triggers the balance/attendance effects; the HR step uses
role `HR_ADMIN` in v1 (HR_MANAGER can be chosen on the settings screen).
Confirmed by the user 2026-10-25: HR can also reject at the processing step (HR step outcomes: process or reject).
