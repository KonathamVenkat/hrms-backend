# Approval workflow on leave (department route) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A leave request travels Branch Manager (picks the target department) → Department Secretary (forwards) → Department Head (approve/reject) → General Manager (approve/reject) → HR (processes), with a full history and a per-request-type switch back to the old one-step approval.

**Architecture:** New module `com.hrms.workflow` owns the flow: structured settings rows → generated BPMN → embedded Flowable (own schema `FLOWABLE`, same datasource). The request table stays the system of record; every action is one `@Transactional` method that updates the request, writes a history row and completes the Flowable task. Other modules only see the `WorkflowService` interface and implement `RequestCompletionHandler`. Leave is the first request type; attendance, overtime and requisitions are later plans. Notifications and the settings screen (spec phase 3) are **not** in this plan: v1 is published by one admin endpoint.

**Tech Stack:** Spring Boot 4.0.8, Java 21, Oracle 23ai (`HRMS`, plus `FLOWABLE` created by the spike script), Flowable 8.0.0 (the version the spike proved), JUnit 5 + Mockito + the existing Oracle DB tests; Angular 21, Material, typed reactive forms, ngx-translate (en/fr/ar).

**Spec:** `docs/superpowers/specs/2026-10-20-approval-workflow-design.md` (sections 4-8, 10-12, 17). Spec phase 1 (organisation) is already built (`273d89c`).

## Global Constraints

- Backend rules from `C:\DEVSTS1\employee\CLAUDE.md`: controller → service (interface + impl) → repository; records for DTOs; `ApiResponse<T>` / `PagedResponse`; `@Transactional` on writes (Hikari `auto-commit=false`); `@Version` conflict → 409; typed exceptions (`BusinessRuleException`, `ResourceNotFoundException`) only; audit via `AuditTrail`; identity from the authenticated user (`EmployeeAccessGuard.currentEmployeeId()`), never a request field.
- Schema: one additive script `V10__approval_workflow.sql` (V9 is taken) + `db/rollback_V10.sql` + `db/flyway_record_v10.sql`, run by the user (Flyway is validate-only). Nullable first; every FK and filter column indexed; CHAR semantics; booleans `NUMBER(1)`.
- Flowable (spec 5.3): table prefix `FLOWABLE.` with `tablePrefixIsSchema=true` through `EngineConfigurationConfigurer`; **never** `flowable.database-schema`; `flowable.idm.enabled=false`, `flowable.eventregistry.enabled=false`, `flowable.database-schema-update=false`, async executor off; no Flowable REST/UI. Only `workflow.service.impl` imports `org.flowable.*`.
- No raw expressions from users: conditions are `field` (allow-list per request type) + operator (`>`,`>=`,`<`,`<=`,`=`,`<>`) + numeric value.
- Roles: `HR_ADMIN`, `HR_MANAGER`, `EMPLOYEE`, `SYSTEM_ADMIN` + new `GENERAL_MANAGER`. Branch manager, department secretary, department head are relationships (spec 6), never login roles.
- Legacy requests (`WORKFLOW_DEFINITION_ID` null) keep the old approve/reject/cancel endpoints unchanged. Per-type switch `WORKFLOW_SETTINGS.WORKFLOW_ENABLED_LEAVE` (default `false`).
- Frontend rules from `C:\angular-workspace\CLAUDE.md` and `projects\ehrms-v2\CLAUDE.md`: standalone, OnPush + signals, typed reactive forms at declaration, no HTTP in components, Material only, shared `confirm-dialog`/`form-field-error`/`empty-state`/`loading-spinner`, no `any`, keys added to `public/i18n/{en,fr,ar}.json`, build `ng build ehrms-v2`, test `ng test ehrms-v2`.
- **No git commits unless the user asks** (standing preference); each task ends at a green checkpoint. Never `git add -A`. Never print the DB password or JWT secret.

## Review Focus

1. Double click / two approvers / stale tab acting on an old step → second call gets 409, exactly one history row and one Flowable completion. Tested in Task 7.
2. The requester is also the approver for a step (branch manager raising own leave, head raising own leave, HR_ADMIN raising leave) → step skipped (`AUTO_ROUTED`), never self-approved; if nothing is left the General Manager decides. Tested in Tasks 6 and 7.
3. Target department missing (branch manager as requester), inactive, or unknown → 400 `TARGET_DEPARTMENT_REQUIRED` / `DEPARTMENT_INACTIVE`; department without secretary → secretary step skipped; without head → waits for `HR_ADMIN` override. Tested in Tasks 6 and 7.
4. A head/secretary/branch manager is replaced while requests are in flight → authorization uses the **live** holder (old holder gets 403, new holder can act); the inbox column is refreshed on the next transition. Tested in Task 6.
5. The completion handler fails (balance row missing) → the whole HR "process" action rolls back and the request stays at the HR step. Tested in Tasks 7 and 8.
6. Leave cancelled after the General Manager approved but before HR processed → held balance released, flow ends `CANCELLED`. Tested in Task 8.

## File Structure

Backend (`src\main\java\com\hrms\…`):
- Create `workflow/enums/{RequestType,ApproverType,StepOutcome,HistoryAction}.java`; `workflow/entity/{WorkflowDefinition,WorkflowStep,WorkflowRoute,WorkflowSetting,RequestHistory}.java`; matching `workflow/repository/*Repository.java`; `workflow/dto/request/{WorkflowActionRequest,WorkflowOverrideRequest}.java`; `workflow/dto/response/{RequestHistoryResponse,InboxItemResponse,WorkflowStatusResponse}.java`; `workflow/service/{WorkflowService,WorkflowDefinitionService,RequestCompletionHandler,InboxService}.java`; `workflow/service/impl/{WorkflowServiceImpl,WorkflowDefinitionServiceImpl,WorkflowValidator,BpmnGenerator,ApproverResolver,WorkflowAuthorization,InboxServiceImpl}.java`; `workflow/config/FlowableConfig.java`; `workflow/controller/{WorkflowController,InboxController}.java`.
- Create `leave/service/impl/LeaveCompletionHandler.java`; `employee/service/DepartmentSecretaryService.java` + `impl`, `employee/controller/DepartmentSecretaryController.java`, `employee/dto/response/DepartmentSecretaryResponse.java`.
- Modify `pom.xml`; `application.properties` (Flowable keys); `employee/entity/Department.java`; `employee/repository/DepartmentRepository.java`; the employee-deactivation path next to `DepartmentHeadService.clearHeadOf`; `leave/entity/LeaveRequest.java`; `leave/service/impl/LeaveServiceImpl.java` (apply, cancel, `processLeave` guard); `leave/dto/request/CreateLeaveRequest.java` (`targetDepartmentId`); `leave/dto/response/LeaveRequestResponse.java` (`stage`, `targetDepartmentId`).
- DB: `src/main/resources/db/migration/V10__approval_workflow.sql`, `db/rollback_V10.sql`, `db/flyway_record_v10.sql`, `db/workflow_menu.sql`.
- Tests under `src\test\java\com\hrms\{workflow,leave,employee}\…`.

Frontend (`C:\angular-workspace\projects\ehrms-v2\src\app\…`):
- Create `features/workflow/{workflow-routing.ts, models/workflow.model.ts, services/workflow.service.ts(+spec), pages/approvals-inbox/*, pages/request-detail/*, components/forward-dialog/*, components/history-timeline/*}`.
- Modify `app.routes.ts`, `features/leave/models/leave-request.model.ts`, `features/leave/pages/leave-list/*` (stage + link), `features/employee/pages/job-details/*` (secretary toggle), `public/i18n/{en,fr,ar}.json`.

## Interfaces used across tasks (exact)

```java
public enum RequestType { LEAVE, ATTENDANCE_REGULARIZATION, OVERTIME, RECRUITMENT_REQUISITION }
public enum ApproverType { BRANCH_MANAGER, DEPARTMENT_SECRETARY, DEPARTMENT_HEAD, ROLE }
public enum StepOutcome { FORWARD, APPROVE, REJECT }
public enum HistoryAction { SUBMITTED, FORWARDED, APPROVED, REJECTED, CANCELLED, OVERRIDDEN, AUTO_ROUTED }

// ApproverResolver (pure lookups, no writes)
Optional<Long> resolve(WorkflowStep step, long requesterEmployeeId, Long targetDepartmentId); // empty = nobody
boolean holds(WorkflowStep step, long callerEmployeeId, Set<String> callerRoles, long requesterEmployeeId, Long targetDepartmentId);

// WorkflowValidator
List<String> validate(List<WorkflowStep> steps, List<WorkflowRoute> routes, RequestType type); // error codes; empty = publishable

// WorkflowDefinitionService
WorkflowDefinition publishDefault(RequestType type);          // builds v1 in memory, validates, deploys, retires the previous ACTIVE
Optional<WorkflowDefinition> active(RequestType type);

// WorkflowService (the only API other modules call)
WorkflowStatusResponse submit(RequestType type, long requestId, long requesterEmployeeId,
                              Long targetDepartmentId, Map<String, Number> facts);   // facts e.g. {"totalDays": 12}
WorkflowStatusResponse act(RequestType type, long requestId, WorkflowActionRequest action);
WorkflowStatusResponse cancel(RequestType type, long requestId);
WorkflowStatusResponse override(RequestType type, long requestId, WorkflowOverrideRequest action);
List<RequestHistoryResponse> history(RequestType type, long requestId);

// records
WorkflowActionRequest(@NotBlank String stepKey, @NotNull StepOutcome outcome, Long targetDepartmentId, @Size(max=500) String comment)
WorkflowOverrideRequest(@NotBlank String stepKey, @NotNull StepOutcome outcome, Long targetDepartmentId, @NotBlank @Size(max=500) String reason)
WorkflowStatusResponse(RequestType type, long requestId, String stage, String stepKey, String stepLabelEn, String stepLabelAr, List<StepOutcome> allowedOutcomes, boolean pickTargetDepartment)
RequestHistoryResponse(String stepKey, HistoryAction action, Long actorEmployeeId, String actorName, String actorRole, String comment, LocalDateTime actedAt)
InboxItemResponse(RequestType type, long requestId, String stepKey, String stepLabelEn, String stepLabelAr, long requesterEmployeeId, String requesterName, String summary, LocalDateTime submittedAt)

public interface RequestCompletionHandler {
    RequestType type();
    void onApproved(long requestId);    // HR processed: apply downstream effects, same transaction
    void onRejected(long requestId, String reason);
    void onCancelled(long requestId);
}
```

```java
// Implemented once per request module (Task 8 for leave); the workflow module never imports request repositories
public interface WorkflowRequestAccess {
    RequestType type();
    long requesterEmployeeId(long requestId);                       // 404 via ResourceNotFoundException
    Long targetDepartmentId(long requestId);
    void markEntered(long requestId, Long definitionId, String processInstanceId, Long targetDepartmentId,
                     String stage, String stepKey, Long approverEmployeeId, String approverRole);
    void markStage(long requestId, String stage, String stepKey, Long approverEmployeeId, String approverRole);
}
```

Default v1 for LEAVE (steps → outcomes): `BRANCH`(BRANCH_MANAGER, picks target dept: FORWARD, REJECT) → `SECRETARY`(DEPARTMENT_SECRETARY: FORWARD only) → `HEAD`(DEPARTMENT_HEAD: APPROVE, REJECT) → `GM`(ROLE GENERAL_MANAGER: APPROVE, REJECT) → `HR`(ROLE HR_ADMIN: APPROVE = process, or REJECT: confirmed by the user 2026-10-25). Stage text is `PENDING_<STEP_KEY>`; terminal `APPROVED`, `REJECTED`, `CANCELLED`. The request row also stores `CURRENT_STEP_KEY`, `CURRENT_APPROVER_EMPLOYEE_ID` (person steps, resolved when the step is entered) and `CURRENT_APPROVER_ROLE` (role steps) so the inbox is one indexed query independent of workflow versions.

---

### Task 1: Schema V10, roles and menu

**Files:** Create `V10__approval_workflow.sql`, `rollback_V10.sql`, `flyway_record_v10.sql`, `workflow_menu.sql`; Test `src/test/java/com/hrms/workflow/WorkflowSchemaDbTest.java` (pattern: `OrganisationSchemaDbTest`).

**Interfaces:** Produces the tables and columns every later task maps.

- [ ] **Step 1: Write the failing DB test** `WorkflowSchemaDbTest` asserting: `DEPARTMENTS.SECRETARY_EMPLOYEE_ID` exists and is nullable; `LEAVE_REQUESTS` has nullable `WORKFLOW_DEFINITION_ID`, `FLOWABLE_PROCESS_INSTANCE_ID`, `STAGE`, `TARGET_DEPARTMENT_ID`, `CURRENT_STEP_KEY`, `CURRENT_APPROVER_EMPLOYEE_ID`, `CURRENT_APPROVER_ROLE`; tables `WORKFLOW_DEFINITIONS`, `WORKFLOW_STEPS`, `WORKFLOW_ROUTES`, `WORKFLOW_SETTINGS`, `REQUEST_HISTORY` exist; a second `ACTIVE` row for the same `REQUEST_TYPE` is rejected by the unique index; the `ROLES` row `GENERAL_MANAGER` exists.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest=WorkflowSchemaDbTest test`. Expected: FAIL (columns missing).
- [ ] **Step 3: Write `V10__approval_workflow.sql`** following `V8__organisation_branches.sql` style: columns above (nullable, FK + index on every FK and on `STAGE`, `CURRENT_APPROVER_EMPLOYEE_ID`, `CURRENT_APPROVER_ROLE`); tables per spec section 6 with `PK`, `CHECK` on enums, unique index on `(REQUEST_TYPE)` where `STATUS='ACTIVE'` (function index), audit columns, `REQUEST_HISTORY` indexes `(REQUEST_TYPE, REQUEST_ID)` and `(ACTOR_EMPLOYEE_ID)`; `WORKFLOW_STEPS` has `PICKS_TARGET_DEPARTMENT NUMBER(1)`; insert `WORKFLOW_SETTINGS` keys `SYSTEM_ADMIN_CAN_MANAGE_WORKFLOWS=false`, `WORKFLOW_ENABLED_LEAVE=false`; extend the `ROLES` check constraint/rows with `GENERAL_MANAGER` (copy how `SYSTEM_ADMIN` was added; end with `COMMIT`). Write `rollback_V10.sql` (drop in reverse order) and the Flyway record script. `workflow_menu.sql`: menu entries "Approvals" (route `/workflow/inbox`) for all roles, mirroring `organisation_menu.sql`.
- [ ] **Step 4: Ask the user to run V10 and `flyway_record_v10.sql`**, then re-run the test. Expected: PASS.

### Task 2: Department secretary

**Files:** Modify `Department.java` (`@Column(name = "SECRETARY_EMPLOYEE_ID") Long secretaryEmployeeId`), `DepartmentRepository.java` (`List<Department> findBySecretaryEmployeeId(Long)`), the deactivation path; Create `DepartmentSecretaryService(+Impl)`, `DepartmentSecretaryController`, `DepartmentSecretaryResponse`; Test `DepartmentSecretaryServiceImplTest`, `DepartmentSecretaryControllerTest`.

**Interfaces:** Produces `DepartmentSecretaryResponse get(Long employeeId)`, `DepartmentSecretaryResponse set(Long employeeId, boolean secretary)`, `void clearSecretaryOf(Long employeeId)`. Endpoints `GET|PUT /api/v1/employees/{employeeId}/department-secretary` (`HR_ADMIN`, `HR_MANAGER`), same shape as `DepartmentHeadController`.

- [ ] **Step 1: Write failing tests** mirroring `DepartmentHeadServiceImpl` behaviour: setting a secretary on an inactive employee or inactive department → `BusinessRuleException` (`EMP_INACTIVE`, `DEPARTMENT_INACTIVE`); setting replaces the previous secretary and audits `DEPARTMENT_SECRETARY_SET`; clearing audits `DEPARTMENT_SECRETARY_CLEARED`; `clearSecretaryOf` clears every department of a deactivated employee; `EMPLOYEE` role gets 403; a stale department `version` → 409.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest="DepartmentSecretary*Test" test`. Expected: FAIL.
- [ ] **Step 3: Implement** the service as a copy of `DepartmentHeadServiceImpl` using `secretaryEmployeeId`; call `clearSecretaryOf` where `clearHeadOf` is called on deactivation.
- [ ] **Step 4: Run** the same command. Expected: PASS.

### Task 3: Flowable engine configuration

**Files:** Modify `pom.xml` (`flowable.version` property = 8.0.0, dependency `flowable-spring-boot-starter-process`, excluding IDM/REST/event-registry starters), `application.properties`; Create `FlowableConfig`; Test `FlowableConfigDbTest`.

**Interfaces:** Produces an autowirable `RepositoryService`/`RuntimeService`/`TaskService` bound to schema `FLOWABLE` (used only inside `workflow.service.impl`).

- [ ] **Step 1: Write failing test** `FlowableConfigDbTest`: context starts; a trivial BPMN (start → user task → end) deploys, a process instance starts and its task completes; afterwards the other tables on the same connection still resolve unqualified `HRMS` names (query `SELECT COUNT(*) FROM EMPLOYEES`); no Flowable REST bean exists.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest=FlowableConfigDbTest test`. Expected: FAIL (no engine).
- [ ] **Step 3: Implement** `FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` setting `databaseTablePrefix("FLOWABLE.")` and `tablePrefixIsSchema(true)` (spec 5.3); add the properties from Global Constraints.
- [ ] **Step 4: Run** the test, then `.\mvnw.cmd clean verify`. Expected: PASS and BUILD SUCCESS (startup time noted in the report).

### Task 4: Settings entities and validator

**Files:** Create the five entities and repositories, `WorkflowValidator`; Test `WorkflowValidatorTest` (plain unit test, no Spring).

**Interfaces:** Produces `WorkflowValidator.validate(...)` (above) returning error codes `NO_ROUTE_FOR_OUTCOME`, `UNREACHABLE_STEP`, `LOOP`, `MISSING_OTHERWISE_ROUTE`, `OVERLAPPING_CONDITIONS`, `NO_APPROVING_STEP`, `UNKNOWN_CONDITION_FIELD`, `BAD_CONDITION_VALUE`, `SECRETARY_CANNOT_DECIDE`. Condition fields allowed for `LEAVE`: `totalDays`.

- [ ] **Step 1: Write failing tests**: the default v1 steps/routes (build them in the test with a small helper) → empty list; each error code above has one test that builds the smallest bad flow (a step with an unrouted outcome, a cycle, `totalDays > 10` with no otherwise route, overlapping `> 5` and `> 3`, a secretary step offering APPROVE or REJECT, field `hackerField`, value `abc`); boundary: `totalDays > 10` and `<= 10` together is valid.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest=WorkflowValidatorTest test`. Expected: FAIL.
- [ ] **Step 3: Implement** entities (JPA, `@Version` on definitions, sequences named like existing ones — read how `DEPARTMENTS` ids are generated and copy; IDs from sequences, no triggers) and `WorkflowValidator` (graph walk from the first step; allow-lists as constants, not strings in code paths).
- [ ] **Step 4: Run** the test. Expected: PASS.

### Task 5: BPMN generation and publishing

**Files:** Create `BpmnGenerator`, `WorkflowDefinitionServiceImpl`; Test `BpmnGeneratorTest`, `WorkflowPublishDbTest`.

**Interfaces:** Consumes Task 3 services and Task 4 validator. Produces `publishDefault` / `active` (above). BPMN process key `wf_<TYPE>`, one user task per step with id = step key, an exclusive gateway per step reading process variable `outcome` plus variables for allow-listed condition fields (e.g. `totalDays`).

- [ ] **Step 1: Write failing tests**: `BpmnGeneratorTest` generates v1 and asserts one user task per step key and no expression contains text that did not come from the allow-lists (feed a step label `${x}` and assert it appears only inside `<name>` escaped, never in a condition); `WorkflowPublishDbTest`: `publishDefault(LEAVE)` makes v1 ACTIVE; publishing again creates v2, retires v1, leaves exactly one ACTIVE; a request started on v1 completes its steps on v1 after v2 is published (version isolation); an invalid definition throws `BusinessRuleException("WORKFLOW_INVALID", …)` and deploys nothing.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest="BpmnGeneratorTest,WorkflowPublishDbTest" test`. Expected: FAIL.
- [ ] **Step 3: Implement** the generator with the Flowable `BpmnModel` API (no string-built XML) and `publishDefault` in one `@Transactional` method (deploy, save definition rows, retire previous, audit `WORKFLOW_PUBLISHED`).
- [ ] **Step 4: Run** the tests. Expected: PASS.

### Task 6: Approver resolution and authorization

**Files:** Create `ApproverResolver`, `WorkflowAuthorization`; Test `ApproverResolverTest`, `WorkflowAuthorizationTest`.

**Interfaces:** Consumes `CompanyBranchRepository`, `DepartmentRepository`, `EmployeeJobDetailsRepository`, `EmployeeAccessGuard`. Produces `ApproverResolver` (above) and `WorkflowAuthorization.assertMayAct(WorkflowStep step, long requesterId, Long targetDepartmentId)` (throws `AccessDeniedException`; 409-style `BusinessRuleException("SELF_APPROVAL")` for the requester) and `assertMayOverride(...)` (`HR_ADMIN` only, step must be a person step with nobody resolved).

- [ ] **Step 1: Write failing tests**: branch step resolves to the manager of the requester's branch; requester without branch or branch without manager → empty; secretary/head resolve from the **target** department, not the requester's; head replaced → `holds` true for the new head and false for the old; caller who is the requester → never holds (self-approval); `HR_ADMIN` override allowed only when `resolve` is empty, otherwise 403; `EMPLOYEE` who is nobody's approver → 403; a manager of branch A cannot act on a request from branch B (IDOR).
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest="ApproverResolverTest,WorkflowAuthorizationTest" test`. Expected: FAIL.
- [ ] **Step 3: Implement** both classes; all checks server-side in `WorkflowAuthorization` only (spec 7.2).
- [ ] **Step 4: Run** the tests. Expected: PASS.

### Task 7: Workflow actions, history and completion hook

**Files:** Create `WorkflowServiceImpl`, `RequestHistory` writes, DTO records; Test `WorkflowServiceImplTest` (mocks) and `WorkflowActionsDbTest` (real Oracle + Flowable, a fake `RequestCompletionHandler` bean).

**Interfaces:** Consumes Tasks 3-6. Produces `WorkflowService` and `RequestCompletionHandler` (above). The service writes `STAGE`, `CURRENT_STEP_KEY`, `CURRENT_APPROVER_*` on the request through a small `WorkflowRequestAccess` port implemented by each request module (Task 8 for leave) so `workflow` never imports `leave` repositories.

- [ ] **Step 1: Write failing tests** (one per row): submit with flag off → `BusinessRuleException("WORKFLOW_DISABLED")`; submit by an employee → stage `PENDING_BRANCH`, history `SUBMITTED`; branch manager forwards without `targetDepartmentId` → 400 `TARGET_DEPARTMENT_REQUIRED`; with an inactive department → `DEPARTMENT_INACTIVE`; forward → `PENDING_SECRETARY`; secretary tries APPROVE or REJECT → 400; secretary forwards → `PENDING_HEAD`; no secretary → skipped with `AUTO_ROUTED`; head rejects without comment → 400, with reason → `REJECTED` and `onRejected` called once; head approves → `PENDING_GM`; GM approves → `PENDING_HR_PROCESS` (handler **not** called yet); HR processes → `APPROVED`, `onApproved` once; requester is the branch manager → first stage `PENDING_SECRETARY` and submit without `targetDepartmentId` → `TARGET_DEPARTMENT_REQUIRED`; requester is the head → head step skipped to GM; `HR_ADMIN` override on a step with no head → `OVERRIDDEN` row with reason; second identical act (stale `stepKey`) → 409 with no extra history row; handler throws on `onApproved` → everything rolled back, stage stays at HR; cancel by the requester at any pending stage → `CANCELLED`, `onCancelled` once; cancel by someone else → 403; condition rule `totalDays <= 3` routes head → HR when configured (use a test definition).
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest="WorkflowServiceImplTest,WorkflowActionsDbTest" test`. Expected: FAIL.
- [ ] **Step 3: Implement** `WorkflowServiceImpl` in the shape: load request via the port → `WorkflowAuthorization` → assert `stepKey` equals the current Flowable task key (else 409) → complete the task with `outcome` and condition facts → read the next task, apply auto-skip rules from spec 7.3 in a loop bounded by the step count → write stage/current-approver and one history row → call the handler on terminal outcomes → `audit.record(...)`. Methods stay under ~40 lines by splitting skip logic into `nextActionableStep(...)`.
- [ ] **Step 4: Run** the tests. Expected: PASS.

### Task 8: Leave integration

**Files:** Modify `LeaveRequest.java` (new columns from Task 1, `Long targetDepartmentId`, `String stage`, `Long workflowDefinitionId`, `String currentStepKey`, `Long currentApproverEmployeeId`, `String currentApproverRole`), `CreateLeaveRequest` (`Long targetDepartmentId`), `LeaveRequestResponse` (`stage`, `targetDepartmentId`), `LeaveServiceImpl` (apply, cancel, `processLeave`); Create `LeaveCompletionHandler`; Test `LeaveWorkflowDbTest`, update `LeaveServiceImplTest`.

**Interfaces:** Consumes `WorkflowService`, `RequestCompletionHandler`, `WorkflowRequestAccess`. Produces `LeaveCompletionHandler.onApproved` (moves pending days to used, exactly the arithmetic now inside `processLeave`), `onRejected` (releases pending days, stores rejection reason), `onCancelled` (releases pending days). Extract these three balance operations into private methods of `LeaveServiceImpl` or a small package-private helper used by both the legacy `processLeave` and the handler (no duplicated arithmetic).

- [ ] **Step 1: Write failing tests**: with the flag on, `applyLeave` starts the workflow (stage `PENDING_BRANCH`, `workflowDefinitionId` set) and still holds pending days; with the flag off behaviour is byte-for-byte as today (existing tests unchanged); `processLeave` on a workflow request → `BusinessRuleException("USE_WORKFLOW")` (409); end to end employee → branch manager → secretary → head → GM → HR: balance moves pending → used only at HR; head rejects → pending released, reason stored; cancel after GM approval but before HR → pending released (Review Focus 6); handler failure on HR process → request still `PENDING`, balance unchanged (Review Focus 5); legacy request (null definition) can still be approved with the old endpoint.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest="LeaveWorkflowDbTest,LeaveServiceImplTest" test`. Expected: FAIL.
- [ ] **Step 3: Implement** the leave changes; `cancelLeave` delegates to `WorkflowService.cancel` for workflow requests and keeps today's code for legacy ones; `applyLeave` passes `facts = {"totalDays": leaveDays}`.
- [ ] **Step 4: Run** the tests, then the whole leave test package. Expected: PASS.

### Task 9: REST endpoints and inbox

**Files:** Create `WorkflowController`, `InboxController`, `InboxServiceImpl`; Test `WorkflowControllerTest` (slice, per role), `InboxServiceDbTest`.

**Interfaces:** Produces
`GET /api/v1/requests/{type}/{requestId}/workflow` → `WorkflowStatusResponse`; `POST .../actions` body `WorkflowActionRequest`; `POST .../cancel`; `POST .../override` (`HR_ADMIN`); `GET .../history` → `List<RequestHistoryResponse>`; `POST /api/v1/admin/workflows/{type}/publish-default` (`HR_ADMIN`, and `SYSTEM_ADMIN` only when the setting is true); `GET /api/v1/inbox?page=&size=` → `PagedResponse<InboxItemResponse>` (size capped with `PageLimits`). All `@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','GENERAL_MANAGER','EMPLOYEE')")`; the real check is `WorkflowAuthorization`.

- [ ] **Step 1: Write failing tests**: each endpoint × role (`EMPLOYEE` non-participant, branch manager, secretary, head, `GENERAL_MANAGER`, `HR_ADMIN`, `HR_MANAGER`, `SYSTEM_ADMIN`) with allowed/denied expectations; history readable only by requester, current/past participant (from `REQUEST_HISTORY`), the branch manager of the requester's branch, or HR/GM (IDOR: another employee's request → 404/403); inbox returns only items whose `CURRENT_APPROVER_EMPLOYEE_ID` is the caller or whose `CURRENT_APPROVER_ROLE` is one of the caller's roles; 0 items → empty page; size 10 000 → capped; sorted by submitted date, allow-listed sort only.
- [ ] **Step 2: Run** `.\mvnw.cmd -Dtest="WorkflowControllerTest,InboxServiceDbTest" test`. Expected: FAIL.
- [ ] **Step 3: Implement** controllers (thin) and the inbox as one paged query on `LEAVE_REQUESTS` using the three `CURRENT_*` columns and the indexes from Task 1 (state this in the class comment).
- [ ] **Step 4: Run** the tests, then `.\mvnw.cmd clean verify`. Expected: PASS and BUILD SUCCESS.

### Task 10: Angular — inbox, detail, forward dialog

**Files:** see "File Structure"; Test `workflow.service.spec.ts`, `approvals-inbox.spec.ts`, `request-detail.spec.ts`, `forward-dialog.spec.ts`, `history-timeline.spec.ts`.

**Interfaces:** Consumes the Task 9 endpoints. Produces `WorkflowService` (Angular) with `inbox(page, size)`, `status(type, id)`, `history(type, id)`, `act(type, id, body)`, `cancel(type, id)`, `override(type, id, body)`; models mirror the Java records field for field (`WorkflowStatusResponse`, `RequestHistoryResponse`, `InboxItemResponse`, `WorkflowActionRequest`); the department picker reuses the existing department lookup service (`{id, nameEn, nameAr}`, `mat-select [value]="item.id"`).

- [ ] **Step 1: Write failing tests**: service builds each URL/body and maps errors; inbox shows loading, empty, error and 0/1/many rows, pages on the server, ignores an out-of-order response; detail page shows only the actions in `allowedOutcomes`, requires a reason for reject (`form-field-error`), shows the department picker only when `pickTargetDepartment` is true, disables the buttons while a call is in flight and ignores a second click, shows a 403 state; every high-risk action opens `confirm-dialog` naming the employee, leave dates and days; timeline renders `AUTO_ROUTED` and `OVERRIDDEN` rows with text (not colour only); Arabic names and RTL via logical CSS; a 409 shows "already handled, refreshing" and reloads the status.
- [ ] **Step 2: Run** `ng test ehrms-v2`. Expected: FAIL (files missing).
- [ ] **Step 3: Implement** the lazy `workflow-routing.ts` (route `/workflow/inbox`, `/workflow/requests/:type/:id`, role guard as siblings do), pages and components; link the leave list rows to the detail page and show `stage`; add the secretary toggle beside the head toggle on Job Details; add keys to `en/fr/ar`; trim strings, send `null` not `''`.
- [ ] **Step 4: Run** `ng test ehrms-v2` and `ng build ehrms-v2`. Expected: PASS and a clean build (check component style budgets).

### Task 11: End-to-end check and regression

**Files:** Test `src/test/java/com/hrms/workflow/LeaveFlowEndToEndDbTest.java`.

- [ ] **Step 1: Write the end-to-end test** using the seeded Nilepet data pattern (create or look up a branch manager, a department with secretary and head, a General Manager user, an HR_ADMIN): the full chain from the amended spec with real tokens per role through MockMvc; assert the history rows (6), the final balance, and the notification-free state (no notification code exists yet).
- [ ] **Step 2: Run** `.\mvnw.cmd clean verify`. Expected: BUILD SUCCESS, all existing leave, attendance, employee, recruitment and auth regression tests green.
- [ ] **Step 3: Browser check** (real backend on :8082, `ng serve ehrms-v2` on :4201): publish v1 (`POST …/publish-default`), flip `WORKFLOW_ENABLED_LEAVE` to `true`, apply leave as an employee, act through each role, in light and dark mode, English and Arabic, at 360 px. Report anything not seen.
- [ ] **Step 4: Report** files changed in each project and the user's to-dos: run V10 and the two follow-up scripts, assign branch managers/secretaries/heads, create the General Manager login, publish v1, flip the flag per environment.

## Not in this plan (deliberate)

Notifications and the bell; the settings screen (v1 is published by the admin endpoint; custom flows come with spec phase 3); attendance, overtime and recruitment moves; Flowable history cleanup; re-resolving the stored inbox approver when a head/secretary/branch manager changes mid-flight (authorization is live, the inbox column refreshes on the next transition, `HR_ADMIN` can override); the General Manager threshold rule screen.
