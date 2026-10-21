# Organisation structure (phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every employee belongs to a branch; every branch sits in an office location and has one manager (a person may manage several branches); HR can set this up, back-fill existing employees, and see what is still missing.

**Architecture:** Additive Oracle script (V8) adds `LOCATION_ID` and `BRANCH_MANAGER_EMPLOYEE_ID` to `COMPANY_BRANCHES` and `COMPANY_BRANCH_ID` to `EMPLOYEE_JOB_DETAILS`. The backend stays inside the existing `com.hrms.employee` module (a branch is already an employee-module master): one new `OrganisationController` for HR (placement, lookup, summary, gap list, bulk assignment), job-details gains the branch. Angular gets a lazy `features/organisation/` area (two pages) and a branch dropdown on Job Details. Phases 2 to 5 (workflow engine) build on this and are not touched here.

**Tech Stack:** Spring Boot 4.0.8, Java 21, Oracle 23ai (schema `HRMS`), JPA/Hibernate, Mockito/JUnit 5 and the existing Oracle DB tests; Angular 21 standalone, Angular Material, typed reactive forms, ngx-translate (en/fr/ar).

**Spec:** `docs/superpowers/specs/2026-10-20-approval-workflow-design.md` (sections 3, 6, 9, 11, 13 phase 1).

## Global Constraints

- Backend rules from `C:\DEVSTS1\employee\CLAUDE.md`: controller → service (interface + impl) → repository; DTOs are separate from entities; `ApiResponse<T>` / `PagedResponse`; null-safe patch semantics; `@Transactional` on every write (Hikari `auto-commit=false`); `@Version` conflict → 409; typed exceptions only; audit via `AuditTrail`; identity comes from the JWT, never a request field.
- Schema change = versioned additive script `V8` + `db/rollback_V8.sql` + `db/flyway_record_v8.sql`, applied by the user (Flyway is validate-only). Add nullable column → back-fill → constrain; no drop/rename. Every FK has an index. VARCHAR2 uses CHAR semantics.
- Roles for this phase: `HR_ADMIN` and `HR_MANAGER` do all organisation work (same as department head). `SYSTEM_ADMIN` keeps maintaining branch code/name/company in the existing masters screen. Branch Manager is a relationship (a branch's `BRANCH_MANAGER_EMPLOYEE_ID`), not a login role.
- A person may manage several branches; a branch has exactly one manager. Only an **active** employee can be manager. A deactivated employee stops being manager of every branch (same moment department headship is cleared).
- An employee's branch must be **active** and its `LOCATION_ID` must equal the job's `locationId`.
- Existing rows keep working: new columns are nullable in this phase. Making them NOT NULL is a later cleanup after the back-fill.
- Frontend rules from `C:\angular-workspace\CLAUDE.md` and `projects\ehrms-v2\CLAUDE.md`: standalone, OnPush + signals, typed reactive forms initialised at declaration, no HTTP in components (feature service on `core/services/api` pattern used by siblings), Angular Material only, `form-field-error`/`confirm-dialog`/`loading-spinner`/`empty-state` shared pieces, no `any`, targets ≥ 44 px, logical CSS properties, `<bdi>` around codes, keys in `public/i18n/{en,fr,ar}.json` (add only), build with `ng build ehrms-v2` and `ng test ehrms-v2` (never bare `ng`).
- **No git commits unless the user asks** (standing preference). Each task ends at a green checkpoint instead of a commit. Never `git add -A`.
- Never print the DB password or JWT secret.

## Review Focus

1. Branch in a different location than the job's location → rejected with a clear business-rule error (`BRANCH_LOCATION_MISMATCH`), not stored. Tested in Task 3.
2. Same person set as manager of two branches is allowed; setting a deactivated or unknown employee is rejected; deactivating the employee clears all their branch managerships. Tested in Tasks 2 and 3.
3. Two HR users edit the same branch placement: the second save gets 409 (stale `version`). Tested in Task 2.
4. Bulk assignment with a mix of valid and invalid rows (inactive branch, unknown employee, employee with no current job): valid rows applied, every invalid row listed with a reason, nothing partial inside one row. Tested in Task 4.
5. Employee/branch lists with 0 rows and with 10 000 employees: gap list is paged server-side, capped page size (max 100). Tested in Tasks 4 and 7.
6. Arabic names and long names render without breaking layout; branch dropdown on Job Details only offers branches of the chosen location and clears when the location changes. Tested in Task 8.

## File Structure

Backend (`C:\DEVSTS1\employee\src\main\java\com\hrms\employee\…`):
- Modify `entity/CompanyBranch.java`, `entity/EmployeeJobDetails.java`; `repository/CompanyBranchRepository.java`, `repository/EmployeeJobDetailsRepository.java`, `repository/projection/JobDetailsProjection.java`; `dto/request/JobDetailsRequest.java`, `dto/response/JobDetailsResponse.java`; `service/impl/JobDetailsServiceImpl.java`; the employee-deactivation path next to `DepartmentHeadService.clearHeadOf`.
- Create `dto/request/BranchPlacementRequest.java`, `dto/request/BranchAssignmentRequest.java`, `dto/response/BranchPlacementResponse.java`, `dto/response/BranchLookupResponse.java`, `dto/response/BranchAssignmentResult.java`, `dto/response/OrganisationSummaryResponse.java`, `dto/response/EmployeeBranchGapResponse.java`; `service/OrganisationService.java`, `service/impl/OrganisationServiceImpl.java`; `controller/OrganisationController.java`.
- DB: `src/main/resources/db/migration/V8__organisation_branches.sql`, `db/rollback_V8.sql`, `db/flyway_record_v8.sql`, `db/organisation_menu.sql`.
- Tests under `src/test/java/com/hrms/employee/…`.

Frontend (`C:\angular-workspace\projects\ehrms-v2\src\app\…`):
- Create `features/organisation/{organisation-routing.ts, models/organisation.model.ts, services/organisation.service.ts(+spec), pages/branch-setup/*, pages/branch-assignments/*}`.
- Modify `app.routes.ts`, `features/employee/models/job-details.model.ts`, `features/employee/pages/job-details/job-details.{ts,html}`, `public/i18n/{en,fr,ar}.json`.

## Interfaces used across tasks (exact)

```java
// BranchPlacementRequest (record)    : @NotNull Long locationId, Long managerEmployeeId, @NotNull Long version
// BranchPlacementResponse (record)   : Long branchId, String code, String name, String nameAr, Long companyId,
//   Long locationId, String locationName, String locationNameAr, Long managerEmployeeId, String managerName,
//   String managerCode, Boolean isActive, Long version
// BranchLookupResponse (record)      : Long id, String nameEn, String nameAr, Long locationId
// BranchAssignmentRequest (record)   : @NotEmpty @Size(max = 200) List<@Valid Item> items;
//   record Item(@NotNull Long employeeId, @NotNull Long branchId)
// BranchAssignmentResult (record)    : int assigned, List<Skipped> skipped; record Skipped(Long employeeId, String reason)
// OrganisationSummaryResponse        : long employeesWithoutBranch, long branchesWithoutManager, long branchesWithoutLocation
// EmployeeBranchGapResponse (record) : Long employeeId, String employeeCode, String fullName, String fullNameAr,
//   String departmentName, Long locationId, String locationName
public interface OrganisationService {
  List<BranchPlacementResponse> listBranches();
  List<BranchLookupResponse> lookupBranches(Long locationId);          // active only, optional location filter
  BranchPlacementResponse setPlacement(Long branchId, BranchPlacementRequest r);
  OrganisationSummaryResponse summary();
  Page<EmployeeBranchGapResponse> employeesWithoutBranch(String q, Pageable p); // page size capped at 100
  BranchAssignmentResult assignBranches(BranchAssignmentRequest r);
  void clearManagerOf(Long employeeId);                                // called when an employee is deactivated
}
```

REST (all `@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")`, base `/api/v1/organisation`):
`GET /branches`, `GET /branches/lookup?locationId=`, `PUT /branches/{id}/placement`, `GET /summary`,
`GET /employees-without-branch?q=&page=&size=`, `PUT /branch-assignments`.

---

### Task 1: Database script V8 (and rollback, record, menu)

**Files:** Create `src/main/resources/db/migration/V8__organisation_branches.sql`, `db/rollback_V8.sql`, `db/flyway_record_v8.sql`, `db/organisation_menu.sql`; Test `src/test/java/com/hrms/employee/OrganisationSchemaDbTest.java`.

**Interfaces:** Produces columns `COMPANY_BRANCHES.LOCATION_ID` (FK `OFFICE_LOCATIONS`), `COMPANY_BRANCHES.BRANCH_MANAGER_EMPLOYEE_ID` (FK `EMPLOYEES`), `EMPLOYEE_JOB_DETAILS.COMPANY_BRANCH_ID` (FK `COMPANY_BRANCHES`); indexes `IDX_CB_LOCATION_ID`, `IDX_CB_MANAGER_ID`, `IDX_EJD_BRANCH_ID`. Recruitment's planned V8 is renumbered V9 (note it in the file header).

- [ ] **Step 1:** Write `OrganisationSchemaDbTest` (pattern of `RecruitmentSchemaDbTest`): asserts the three columns exist and are nullable, the three FKs and three indexes exist, and that a branch cannot be given a non-existent location id (FK violation).
- [ ] **Step 2:** Run it (it must fail because V8 is not applied). `.\mvnw.cmd -q test -Dtest=OrganisationSchemaDbTest` → fails.
- [ ] **Step 3:** Write V8 following the V7 header conventions: three `ALTER TABLE … ADD` (nullable), FKs, indexes. Write `rollback_V8.sql` (drop indexes, FKs, columns) and `flyway_record_v8.sql` (rank 7, checksum placeholder explained as in v7). Write `organisation_menu.sql` (pattern of `recruitment_menu.sql`, idempotent): main menu `Organisation` with sub menus `Branch Setup` (`/app/organisation/branches`) and `Branch Assignments` (`/app/organisation/branch-assignments`) for `HR_ADMIN` and `HR_MANAGER`.
- [ ] **Step 4:** Ask the user to run V8 then `flyway_record_v8.sql` (not `organisation_menu.sql` yet). Do not run SQL yourself. After confirmation re-run the test → PASS.
- [ ] **Checkpoint:** schema test green; V8 applied by the user.

### Task 2: Branch placement (location + manager) in the service

**Files:** Modify `entity/CompanyBranch.java` (fields `locationId`, `branchManagerEmployeeId`), `repository/CompanyBranchRepository.java`; Create the five DTOs it needs, `service/OrganisationService.java`, `service/impl/OrganisationServiceImpl.java`; Test `service/OrganisationServiceImplTest.java` (Mockito, pattern of `DepartmentHeadServiceImplTest`).

**Interfaces:** Produces `OrganisationService.listBranches()`, `lookupBranches(Long)`, `setPlacement(Long, BranchPlacementRequest)`, `summary()` (counts only branches here; employee count added in Task 4), `clearManagerOf(Long)`. Repository: `List<CompanyBranch> findByLocationIdAndIsActiveTrueOrderByNameAsc(Long)`, `List<CompanyBranch> findByBranchManagerEmployeeId(Long)`, `long countByBranchManagerEmployeeIdIsNull()`, `long countByLocationIdIsNull()`.

- [ ] **Step 1:** Write failing tests with these names and assertions: `setPlacement_setsLocationAndManager`; `sameManagerOnTwoBranches_isAllowed`; `setPlacement_unknownLocation_throwsResourceNotFound`; `setPlacement_inactiveEmployee_throwsBusinessRule("MANAGER_NOT_ACTIVE")`; `setPlacement_staleVersion_throwsConflict` (uses `Versions.requireCurrent` like `CompanyBranchServiceImpl`); `setPlacement_nullManager_clearsManager`; `setPlacement_inactiveBranch_throwsBusinessRule`; `clearManagerOf_clearsEveryBranchOfThatEmployee`; `lookup_returnsOnlyActiveBranchesOfLocation`; every write calls `audit.record("BRANCH_PLACEMENT_SET", "COMPANY_BRANCH", id, …)` with ids only (no PII).
- [ ] **Step 2:** Run → FAIL. `.\mvnw.cmd -q test -Dtest=OrganisationServiceImplTest`.
- [ ] **Step 3:** Add the two entity fields (plain `Long`, columns per Task 1), the repository methods above, the DTO records, and `OrganisationServiceImpl.setPlacement` (`@Transactional`; load branch; check version; check location exists and is active; if manager given, load employee, require active; save; flush; audit). Response mapping includes `managerName`/`managerCode` from the employee already loaded (no per-row query in `listBranches`: fetch managers with one `findAllById`).
- [ ] **Step 4:** Run → PASS.
- [ ] **Checkpoint:** tests green.

### Task 3: Branch on Job Details (SCD2) and deactivation hook

**Files:** Modify `entity/EmployeeJobDetails.java` (`companyBranchId`, col `COMPANY_BRANCH_ID`), `dto/request/JobDetailsRequest.java` (`Long companyBranchId`, optional), `dto/response/JobDetailsResponse.java` (`companyBranchId`, `branchCode`, `branchName`, `branchNameAr`, `branchManagerName`), `repository/projection/JobDetailsProjection.java`, both native queries in `repository/EmployeeJobDetailsRepository.java` (LEFT JOIN `COMPANY_BRANCHES` and the manager's name), `service/impl/JobDetailsServiceImpl.java` (`assignJob`, `updateCurrentJob` including the "unchanged?" comparison at the `locationId` check, response mapping), the employee-deactivation code that calls `DepartmentHeadService.clearHeadOf`; Test `service/JobDetailsServiceImplTest.java` (extend).

**Interfaces:** Consumes `OrganisationService.clearManagerOf(Long)`. Produces job-details JSON fields `companyBranchId`, `branchCode`, `branchName`, `branchNameAr`, `branchManagerName` (null fields are omitted by the API).

- [ ] **Step 1:** Add failing tests: `assignJob_withBranchOfSameLocation_storesBranch`; `assignJob_branchOfOtherLocation_throwsBusinessRule("BRANCH_LOCATION_MISMATCH")` (Review Focus 1); `assignJob_inactiveBranch_throwsBusinessRule`; `assignJob_withoutBranch_storesNull`; `updateCurrentJob_changingOnlyBranch_createsNewHistoryRow`; `deactivateEmployee_clearsManagerOfAllBranches` (verifies `organisationService.clearManagerOf(id)` is invoked wherever `clearHeadOf` is).
- [ ] **Step 2:** Run → FAIL. `.\mvnw.cmd -q test -Dtest=JobDetailsServiceImplTest`.
- [ ] **Step 3:** Implement: validation helper `validateBranch(Long branchId, Long locationId)` (exists, active, `locationId` equal); copy the field when the SCD2 row is created; include it in the "nothing changed" comparison; map the new response fields; add the call to `clearManagerOf` beside `clearHeadOf`.
- [ ] **Step 4:** Run → PASS; also run `EmployeeServiceImplTest`.
- [ ] **Checkpoint:** both classes green.

### Task 4: Gap report and bulk assignment (back-fill)

**Files:** Modify `service/impl/OrganisationServiceImpl.java`, `repository/EmployeeJobDetailsRepository.java`; Create `BranchAssignmentRequest`, `BranchAssignmentResult`, `EmployeeBranchGapResponse`; Test `service/OrganisationServiceImplTest.java` (extend).

**Interfaces:** Produces `employeesWithoutBranch(String q, Pageable)` and `assignBranches(BranchAssignmentRequest)`. Repository: a paged native query listing **active** employees whose **current** job row has `COMPANY_BRANCH_ID IS NULL`, filtered by `q` (name/code, parameterised, allow-listed sort), returning the gap fields; `int setBranchOnCurrentJob(Long employeeId, Long branchId)` (`@Modifying`, updates only the `IS_CURRENT = 1` row **only while its branch is null**).

- [ ] **Step 1:** Failing tests: `employeesWithoutBranch_capsPageSizeAt100`; `employeesWithoutBranch_filtersByQuery`; `assignBranches_appliesValidRows_andListsSkippedWithReasons` (inactive branch → `BRANCH_INACTIVE`, unknown employee → `EMPLOYEE_NOT_FOUND`, no current job → `NO_CURRENT_JOB`, branch of other location than the employee's job location → `BRANCH_LOCATION_MISMATCH`, already has a branch → `ALREADY_ASSIGNED`); `assignBranches_audits_oneEventPerAssignedRow`; `assignBranches_over200_isRejectedByValidation`; `summary_countsAllThreeGaps`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Back-fill edits the current row in place **only when it has no branch yet** (a correction, not a transfer; transfers go through Job Details and create history). Each row is validated and applied independently; one `@Transactional` method for the whole call.
- [ ] **Step 4:** Run → PASS.
- [ ] **Checkpoint:** tests green.

### Task 5: Controller and security

**Files:** Create `controller/OrganisationController.java`; Test `controller/OrganisationControllerSecurityTest.java` (pattern of `DepartmentHeadControllerSecurityTest`).

**Interfaces:** Consumes `OrganisationService`. Produces the REST endpoints listed above, each wrapped in `ApiResponse`, list endpoint wrapped via `PagedResponse.from(...)`.

- [ ] **Step 1:** Failing tests: `hrAdmin_and_hrManager_areAllowed_onEveryEndpoint`; `employee_isForbidden`; `systemAdmin_isForbidden` (they use the masters screen, not this one); `anonymous_isUnauthorized`; `assignBranches_rejectsEmptyAndOver200Items_with400`; `placement_requiresVersionAndLocation_with400`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement controller (class-level `@PreAuthorize`, `@Valid`, no entity returned). **Step 4:** Run → PASS.
- [ ] **Checkpoint:** green. Then run `.\mvnw.cmd clean verify` once for the whole backend; all tests must pass.

### Task 6: Angular model, service, routing

**Files:** Create `features/organisation/models/organisation.model.ts` (interfaces mirroring the records above field-for-field), `features/organisation/services/organisation.service.ts`, `…spec.ts`, `features/organisation/organisation-routing.ts`; Modify `app.routes.ts` (lazy `organisation` area guarded like the `recruitment` route, roles `HR_ADMIN`, `HR_MANAGER`).

**Interfaces:** Produces `OrganisationService` with `listBranches(): Observable<BranchPlacement[]>`, `lookupBranches(locationId?: number): Observable<BranchLookup[]>`, `setPlacement(id: number, body: BranchPlacementRequest): Observable<BranchPlacement>`, `summary(): Observable<OrganisationSummary>`, `employeesWithoutBranch(q: string, page: number, size: number): Observable<Page<EmployeeBranchGap>>`, `assignBranches(items: {employeeId: number; branchId: number}[]): Observable<BranchAssignmentResult>`.

- [ ] **Step 1:** Spec `organisation.service.spec.ts` (HTTP mocked): each method hits the exact path and verb above, unwraps `ApiResponse.data`, sends `q/page/size` as query params. **Step 2:** Run `ng test ehrms-v2` → FAIL. **Step 3:** Implement per sibling services (`department-head.service.ts`). **Step 4:** PASS.
- [ ] **Checkpoint:** spec green.

### Task 7: Branch setup and Branch assignments pages

**Files:** Create `features/organisation/pages/branch-setup/branch-setup.{ts,html,css,spec.ts}` and `…/branch-assignments/branch-assignments.{ts,html,css,spec.ts}`; Modify `public/i18n/{en,fr,ar}.json` (new `organisation.*` keys; fr/ar are drafts for a native reader).

**Interfaces:** Consumes `OrganisationService`, `OfficeLocationService` (for the location list), existing `employee-search-select` (inputs `placeholder`, `inputId`; check its output name before use). Produces two routed pages: `/app/organisation/branches`, `/app/organisation/branch-assignments`.

- [ ] **Step 1:** Specs, each with these named cases: **branch-setup** — `showsLoadingThenRows`, `showsEmptyState`, `showsErrorAndRetry`, `saveDisabledWhileInFlight_andIgnoresSecondClick`, `staleVersion409_showsRefreshMessage_andKeepsInput`, `rowWithoutLocationOrManager_isFlagged` (text, not colour only). **branch-assignments** — summary tiles render the three counts, list is server-paged, search debounces 300 ms (fake timers), selecting a branch per row then "Assign selected" calls `assignBranches` once, skipped rows are listed with their reason, success refreshes the list, 0 rows shows the "nothing to assign" empty state, branch options are limited to the employee's location.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement with typed forms, Material table, `confirm-dialog` before bulk assign naming the count, `aria-live` for results, 44 px targets, logical CSS, `<bdi>` for codes. **Step 4:** PASS.
- [ ] **Checkpoint:** specs green.

### Task 8: Branch dropdown on Job Details

**Files:** Modify `features/employee/models/job-details.model.ts`, `features/employee/pages/job-details/job-details.ts` and `.html`; add `job-details.spec.ts` if absent.

**Interfaces:** Consumes `OrganisationService.lookupBranches(locationId)`. The form gets control `companyBranchId: number | null`, optional; sent as `null` when empty.

- [ ] **Step 1:** Spec cases: `branchOptions_loadForSelectedLocation`; `changingLocation_clearsBranchAndReloadsOptions`; `submitSendsNullWhenNoBranch`; `showsCurrentBranchNameInReadOnlyView`; `branchLookupFailure_showsInlineErrorAndKeepsForm`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement beside the existing location select; respect the existing unsaved-changes guard. **Step 4:** PASS.
- [ ] **Checkpoint:** green.

### Task 9: Whole-project verification and hand-over

- [ ] Backend: `.\mvnw.cmd clean verify` → all tests pass. Frontend: `ng build ehrms-v2` and `ng test ehrms-v2` from `C:\angular-workspace` → pass.
- [ ] Browser check (recipe in memory `project_ehrms_v2_ui_redesign_2026_10_14`): stubbed backend, log in as HR_ADMIN, open Branch Setup, Branch Assignments and an employee's Job Details; English, Arabic RTL, 360 px. Report what was not seen.
- [ ] Give the user: V8 order of running (`V8` → `flyway_record_v8` → deploy backend and frontend → `organisation_menu.sql`), the back-fill procedure (set location and manager per branch first, then assign employees), and the list of files changed per project. Do not commit.

---

## Self-review

- **Spec coverage:** section 6 organisation columns → Task 1; one manager per branch, several branches per person, active-only, clear on deactivation → Tasks 2–3; employee branch on Job Details → Tasks 3 and 8; back-fill and the missing-data report → Tasks 4 and 7; screens in section 9 item 6 → Tasks 7–8; migration step 2 (HR assigns branches and managers) → Tasks 4 and 7. Roles `GENERAL_MANAGER`, workflow tables, Flowable, notifications and request routing belong to phases 2–5 and are intentionally absent.
- **Types:** record names and `OrganisationService` signatures are defined once above and reused unchanged; the Angular interfaces mirror the records field for field.
- **Proportion:** tasks give signatures, test names and rules, not method bodies.
- **Decisions I took that you did not state (confirm or change):** HR roles (not SYSTEM_ADMIN) set a branch's location and manager through a new screen, while SYSTEM_ADMIN keeps code/name/company; back-fill edits the current job row in place only when no branch is set; bulk assignment applies valid rows and reports the rest; recruitment's planned `V8` becomes `V9`.
