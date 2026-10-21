"""Generates the backend of the six employee-information tabs: dependants, education, training, languages,
experience and references (entity, repository, request/response DTOs, service, implementation, controller).

Run from anywhere: python tools/generate_employee_info.py. Output is overwritten, so change the rules HERE, not in the
generated files. The shape follows EmployeeBankAccount and the master checklist: read = HR staff or the employee for
their own record (controller + EmployeeAccessGuard), write = HR_ADMIN only, update needs the current version (400 when
missing, 409 when stale), a record is deactivated, never deleted, a record of another employee is a 404, a chosen master
value must exist and be active (an unchanged one may stay), dates must be in order, audit details carry ids only
(no names, phones or e-mail addresses)."""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "com" / "hrms" / "employee"
P = "com.hrms.employee"

# Java source of the "no control or invisible characters" pattern (two backslashes in the Java text).
NAME_PATTERN = r"^[^\\p{Cc}\\p{Cf}]*$"
PHONE_PATTERN = "^[0-9+() -]*$"


def text(name, col, length, label, req=False, ar=False, kind="text"):
    return dict(name=name, kind=kind, col=col, len=length, label=label, req=req, ar=ar)


def date(name, col, label, future_ok=True):
    return dict(name=name, kind="date", col=col, label=label, future_ok=future_ok)


def ref(name, col, label, cls, rule, req=False):
    return dict(name=name, kind="ref", col=col, label=label, req=req, ref_cls=cls, rule=rule)


# cls, label, table, idcol, seq, path, target tag, fields, optional unique (field, rule code, message)
SPECS = [
    dict(cls="EmployeeDependent", label="Dependant", table="EMPLOYEE_DEPENDENTS", idcol="DEPENDENT_ID",
         path="dependants", tag="EMPLOYEE_DEPENDENT", unique=None, sort_date=None, fields=[
             ref("relationId", "RELATION_ID", "Relation", "Relation", "RELATION", req=True),
             text("firstName", "FIRST_NAME", 100, "First name", req=True),
             text("middleName", "MIDDLE_NAME", 100, "Middle name"),
             text("lastName", "LAST_NAME", 100, "Last name"),
             text("firstNameAr", "FIRST_NAME_AR", 200, "Arabic first name", ar=True),
             text("middleNameAr", "MIDDLE_NAME_AR", 200, "Arabic middle name", ar=True),
             text("lastNameAr", "LAST_NAME_AR", 200, "Arabic last name", ar=True),
             dict(name="gender", kind="gender", col="GENDER", label="Gender"),
             date("dateOfBirth", "DATE_OF_BIRTH", "Date of birth", future_ok=False),
             text("birthPlace", "BIRTH_PLACE", 80, "Birth place"),
             dict(name="isAlive", kind="bool", col="IS_ALIVE", label="Alive"),
             text("phone", "PHONE", 30, "Phone", kind="phone"),
             text("address", "ADDRESS", 200, "Address"),
         ]),
    dict(cls="EmployeeEducation", label="Education record", table="EMPLOYEE_EDUCATION", idcol="EDUCATION_ID",
         path="education", tag="EMPLOYEE_EDUCATION", unique=None, sort_date="startDate", fields=[
             ref("educationLevelId", "EDUCATION_LEVEL_ID", "Education level", "EducationLevel", "EDUCATION_LEVEL"),
             text("qualification", "QUALIFICATION", 200, "Qualification", req=True),
             text("qualificationAr", "QUALIFICATION_AR", 200, "Arabic qualification", ar=True),
             text("institution", "INSTITUTION", 200, "Institution"),
             date("startDate", "START_DATE", "Start date"),
             date("endDate", "END_DATE", "End date"),
         ]),
    dict(cls="EmployeeTraining", label="Training record", table="EMPLOYEE_TRAINING", idcol="TRAINING_ID",
         path="training", tag="EMPLOYEE_TRAINING", unique=None, sort_date="startDate", fields=[
             text("courseName", "COURSE_NAME", 200, "Course name", req=True),
             text("courseNameAr", "COURSE_NAME_AR", 200, "Arabic course name", ar=True),
             text("provider", "PROVIDER", 200, "Provider"),
             date("startDate", "START_DATE", "Start date"),
             date("endDate", "END_DATE", "End date"),
         ]),
    dict(cls="EmployeeLanguage", label="Language record", table="EMPLOYEE_LANGUAGES", idcol="EMP_LANGUAGE_ID",
         path="languages", tag="EMPLOYEE_LANGUAGE", sort_date=None,
         unique=("languageId", "DUPLICATE_LANGUAGE",
                 "This language is already recorded for the employee. If it was deactivated, activate it instead."),
         fields=[
             ref("languageId", "LANGUAGE_ID", "Language", "Language", "LANGUAGE", req=True),
             ref("readLevelId", "READ_LEVEL_ID", "Reading level", "LanguageLevel", "LANGUAGE_LEVEL"),
             ref("writeLevelId", "WRITE_LEVEL_ID", "Writing level", "LanguageLevel", "LANGUAGE_LEVEL"),
             ref("speakLevelId", "SPEAK_LEVEL_ID", "Speaking level", "LanguageLevel", "LANGUAGE_LEVEL"),
         ]),
    dict(cls="EmployeeExperience", label="Experience record", table="EMPLOYEE_EXPERIENCE", idcol="EXPERIENCE_ID",
         path="experience", tag="EMPLOYEE_EXPERIENCE", unique=None, sort_date="startDate", fields=[
             text("employerName", "EMPLOYER_NAME", 200, "Employer", req=True),
             text("employerNameAr", "EMPLOYER_NAME_AR", 200, "Arabic employer name", ar=True),
             text("jobTitle", "JOB_TITLE", 100, "Job title"),
             text("place", "PLACE", 100, "Place"),
             date("startDate", "START_DATE", "Start date"),
             date("endDate", "END_DATE", "End date"),
         ]),
    dict(cls="EmployeeReference", label="Reference", table="EMPLOYEE_REFERENCES", idcol="REFERENCE_ID",
         path="references", tag="EMPLOYEE_REFERENCE", unique=None, sort_date=None, fields=[
             text("firstName", "FIRST_NAME", 100, "First name", req=True),
             text("middleName", "MIDDLE_NAME", 100, "Middle name"),
             text("lastName", "LAST_NAME", 100, "Last name"),
             text("firstNameAr", "FIRST_NAME_AR", 200, "Arabic first name", ar=True),
             text("middleNameAr", "MIDDLE_NAME_AR", 200, "Arabic middle name", ar=True),
             text("lastNameAr", "LAST_NAME_AR", 200, "Arabic last name", ar=True),
             text("jobTitle", "JOB_TITLE", 80, "Job title"),
             text("address", "ADDRESS", 200, "Address"),
             text("phone", "PHONE", 30, "Phone", kind="phone"),
             text("email", "EMAIL", 200, "E-mail", kind="email"),
         ]),
]


def cap(s):
    return s[0].upper() + s[1:]


def write(rel, content):
    f = ROOT / rel
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(content, encoding="utf-8", newline="\n")


def java_type(f):
    return {"text": "String", "phone": "String", "email": "String", "date": "LocalDate", "gender": "Gender",
            "bool": "Boolean", "ref": "Long"}[f["kind"]]


def is_text(f):
    return f["kind"] in ("text", "phone", "email")


def entity(s):
    c, t = s["cls"], s["table"]
    fields = []
    for f in s["fields"]:
        n = f["name"]
        if is_text(f):
            col = f'@Column(name = "{f["col"]}"' + (", nullable = false" if f.get("req") else "")
            col += ', columnDefinition = "NVARCHAR2(%d)")' % f["len"] if f.get("ar") else f', length = {f["len"]})'
            fields.append(f"    {col}\n    private String {n};\n")
        elif f["kind"] == "date":
            fields.append(f'    @Column(name = "{f["col"]}")\n    private LocalDate {n};\n')
        elif f["kind"] == "gender":
            fields.append(f'    @Enumerated(EnumType.STRING)\n    @Column(name = "{f["col"]}", length = 20)\n'
                          f"    private Gender {n};\n")
        elif f["kind"] == "bool":
            fields.append(f'    @Convert(converter = BooleanToNumberConverter.class)\n'
                          f'    @Column(name = "{f["col"]}", nullable = false)\n    private Boolean {n};\n')
        else:  # ref
            fields.append(f'    @Column(name = "{f["col"]}"' + (", nullable = false" if f.get("req") else "")
                          + f")\n    private Long {n};\n")
    body = "\n".join(fields)
    return f'''package {P}.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.common.enums.Gender;
import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * {s["label"]} of an employee (table {t}, created by V5). Never deleted, only deactivated.
 * Generated by tools/generate_employee_info.py.
 */
@Entity
@Table(name = "{t}", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class {c} extends Auditable {{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "{t.lower()}_seq")
    @SequenceGenerator(name = "{t.lower()}_seq", sequenceName = "HRMS.SEQ_{t}", allocationSize = 1)
    @Column(name = "{s["idcol"]}", nullable = false)
    private Long id;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

{body}
    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    /** Optimistic lock: a stale writer gets HTTP 409. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}}
'''


def repository(s):
    c = s["cls"]
    extra = ""
    if s["unique"]:
        f = cap(s["unique"][0])
        extra = (f"    boolean existsByEmployeeIdAnd{f}(Long employeeId, Long {s['unique'][0]});\n"
                 f"    boolean existsByEmployeeIdAnd{f}AndIdNot(Long employeeId, Long {s['unique'][0]}, Long id);\n")
    return f'''package {P}.repository;

import {P}.entity.{c};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface {c}Repository extends JpaRepository<{c}, Long> {{
    List<{c}> findByEmployeeIdOrderByIdAsc(Long employeeId);
{extra}}}
'''


def request(s):
    lines = []
    for f in s["fields"]:
        n, label = f["name"], f["label"]
        k = f["kind"]
        if is_text(f):
            ann = []
            if f.get("req"):
                ann.append(f'@NotBlank(message = "{label} is required")')
            ann.append(f'@Size(max = {f["len"]})')
            if k == "phone":
                ann.append(f'@Pattern(regexp = "{PHONE_PATTERN}", message = "Phone may contain only digits, + ( ) - and spaces")')
            elif k == "email":
                ann.append('@Email(message = "E-mail address is not valid")')
            else:
                ann.append(f'@Pattern(regexp = "{NAME_PATTERN}", message = "{label} contains control or invisible characters")')
            lines.append("    " + "\n    ".join(ann) + f"\n    private String {n};\n")
        elif k == "date":
            ann = "    @PastOrPresent(message = \"%s cannot be in the future\")\n" % label if not f["future_ok"] else ""
            lines.append(f"{ann}    private LocalDate {n};\n")
        elif k == "gender":
            lines.append(f"    private Gender {n};\n")
        elif k == "bool":
            lines.append(f"    /** Left out on add means true. */\n    private Boolean {n};\n")
        else:
            ann = f'    @NotNull(message = "{label} is required")\n' if f.get("req") else ""
            lines.append(f"{ann}    private Long {n};\n")
    body = "\n".join(lines)
    return f'''package {P}.dto.request;

import com.hrms.common.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class {s["cls"]}Request {{

{body}
    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}}
'''


def response(s):
    lines = []
    for f in s["fields"]:
        n = f["name"]
        lines.append(f"    private {java_type(f)} {n};")
        if f["kind"] == "ref":
            base = n[:-2]  # relationId -> relation
            lines.append(f"    private String {base}Name;")
            lines.append(f"    private String {base}NameAr;")
    body = "\n".join(lines)
    return f'''package {P}.dto.response;

import com.hrms.common.enums.Gender;
import lombok.*;

import java.time.LocalDate;

@Data @Builder
public class {s["cls"]}Response {{
    private Long id;
    private Long employeeId;
{body}
    private Boolean isActive;
    private Long version;
}}
'''


def service(s):
    c = s["cls"]
    return f'''package {P}.service;

import {P}.dto.request.{c}Request;
import {P}.dto.response.{c}Response;

import java.util.List;

/** {s["label"]}s of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface {c}Service {{
    List<{c}Response> getAll(Long employeeId);
    {c}Response add(Long employeeId, {c}Request request);
    {c}Response update(Long employeeId, Long id, {c}Request request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}}
'''


def impl(s):
    c, label = s["cls"], s["label"]
    refs = [f for f in s["fields"] if f["kind"] == "ref"]
    ref_classes = sorted({f["ref_cls"] for f in refs})
    names = {f["name"] for f in s["fields"]}
    has_range = "startDate" in names and "endDate" in names

    imports = "".join(f"import {P}.entity.{r};\nimport {P}.repository.{r}Repository;\n" for r in ref_classes)
    deps = "".join(f"    private final {r}Repository {r[0].lower() + r[1:]}Repository;\n" for r in ref_classes)

    apply_lines = []
    for f in s["fields"]:
        n, k = f["name"], f["kind"]
        setter = "set" + cap(n)
        getter = "get" + cap(n)
        if is_text(f):
            apply_lines.append(f"        e.{setter}({'r.%s().trim()' % getter if f.get('req') else 'Strings.trimToNull(r.%s())' % getter});")
        elif k == "bool":
            apply_lines.append(f"        if (r.{getter}() != null) e.{setter}(r.{getter}());")
        else:
            apply_lines.append(f"        e.{setter}(r.{getter}());")
    apply_body = "\n".join(apply_lines)

    validate = []
    if has_range:
        validate.append('''        if (r.getStartDate() != null && r.getEndDate() != null && r.getEndDate().isBefore(r.getStartDate())) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "The end date cannot be before the start date.");
        }''')
    for f in refs:
        rc = f["ref_cls"]
        repo = rc[0].lower() + rc[1:] + "Repository"
        cur = f"current == null ? null : current.get{cap(f['name'])}()"
        g = f"r.get{cap(f['name'])}()"
        validate.append(f'        requireActive({g} == null ? null : {repo}.findById({g}).orElse(null), {g}, {cur}, '
                        f'"{f["rule"]}", "{f["label"]}");')
    validate_body = "\n".join(validate) or "        // nothing beyond bean validation"

    unique_add = unique_upd = ""
    if s["unique"]:
        uf, code, msg = s["unique"]
        U = cap(uf)
        unique_add = (f'        if (repository.existsByEmployeeIdAnd{U}(employeeId, request.get{U}())) {{\n'
                      f'            throw new BusinessRuleException("{code}",\n                "{msg}");\n        }}\n')
        unique_upd = (f'        if (!request.get{U}().equals(existing.get{U}())\n'
                      f'                && repository.existsByEmployeeIdAnd{U}AndIdNot(employeeId, request.get{U}(), id)) {{\n'
                      f'            throw new BusinessRuleException("{code}",\n                "{msg}");\n        }}\n')

    builder_defaults = ".isAlive(true)" if "isAlive" in names else ""
    lookup_imports = ("import java.util.Map;\nimport java.util.Objects;\nimport java.util.function.Function;\n"
                      "import java.util.stream.Collectors;\nimport java.util.stream.Stream;\n") if ref_classes else ""

    refs_record_fields = ", ".join(f"Map<Long, {r}> {r[0].lower() + r[1:]}" for r in ref_classes)
    refs_load = ""
    for r in ref_classes:
        v = r[0].lower() + r[1:]
        ids = " + ".join([]) or ""
    refs_for_parts = []
    for r in ref_classes:
        v = r[0].lower() + r[1:]
        getters = [f for f in refs if f["ref_cls"] == r]
        id_stream = "Stream.of(" + ", ".join(f"e.get{cap(g['name'])}()" for g in getters) + ")"
        refs_for_parts.append(
            f"            byId({v}Repository.findAllById(rows.stream().flatMap(e -> {id_stream}).filter(Objects::nonNull)"
            f".collect(Collectors.toSet())), {r}::getId)")
    refs_for_body = ",\n".join(refs_for_parts)
    refs_record = f"    private record Refs({refs_record_fields}) {{}}\n" if ref_classes else ""
    refs_method = (f"    private Refs refsFor(List<{c}> rows) {{\n        return new Refs(\n{refs_for_body});\n    }}\n"
                   if ref_classes else "")
    by_id = ("\n    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {\n"
             "        return rows.stream().collect(Collectors.toMap(id, Function.identity()));\n    }\n\n") if ref_classes else ""
    refs_arg_decl = ", Refs refs" if ref_classes else ""
    refs_arg_use = ", refs" if ref_classes else ""
    refs_arg_empty = "refsFor(List.of(saved))" if ref_classes else ""

    resp = []
    for f in s["fields"]:
        n = f["name"]
        resp.append(f"            .{n}(e.get{cap(n)}())")
        if f["kind"] == "ref":
            base = n[:-2]
            rc = f["ref_cls"]
            v = rc[0].lower() + rc[1:]
            resp.append(f"            .{base}Name(nameOf(refs.{v}().get(e.get{cap(n)}())))")
            resp.append(f"            .{base}NameAr(nameArOf(refs.{v}().get(e.get{cap(n)}())))")
    resp_body = "\n".join(resp)

    name_helpers = ""
    for r in ref_classes:
        name_helpers += (f"    private static String nameOf({r} m) {{\n        return m == null ? null : m.getName();\n    }}\n\n"
                         f"    private static String nameArOf({r} m) {{\n        return m == null ? null : m.getNameAr();\n    }}\n\n")

    sort_expr = ".thenComparing(e -> e.getId())"
    if s["sort_date"]:
        sort_expr = (f".thenComparing(Comparator.comparing({c}::get{cap(s['sort_date'])}, "
                     f"Comparator.nullsLast(Comparator.reverseOrder())))\n                .thenComparing({c}::getId)")

    requires_active = ""
    if refs:
        requires_active = '''
    /** A chosen master value must exist and be active; the value the record already holds may stay as it is. */
    private static void requireActive(Object master, Long id, Long currentId, String rule, String label) {
        if (id == null) return;
        if (master == null) throw new ResourceNotFoundException(label, "id", id);
        if (!id.equals(currentId) && !Boolean.TRUE.equals(activeOf(master))) {
            throw new BusinessRuleException(rule + "_INACTIVE", label + " is not active.");
        }
    }

    private static Boolean activeOf(Object master) {
''' + "".join(f'        if (master instanceof {r} m) return m.getIsActive();\n' for r in ref_classes) + '''        return Boolean.FALSE;
    }
'''

    return f'''package {P}.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.util.Strings;
import {P}.dto.request.{c}Request;
import {P}.dto.response.{c}Response;
import {P}.entity.{c};
{imports}import {P}.repository.{c}Repository;
import {P}.repository.EmployeeRepository;
import {P}.service.{c}Service;
import {P}.service.EmployeeChecks;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
{lookup_imports}
/**
 * {label}s of an employee. Generated by tools/generate_employee_info.py. Writers are HR_ADMIN only (the controller);
 * the audit trail carries ids and never the personal values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class {c}ServiceImpl implements {c}Service {{

    private static final String TARGET = "{s["tag"]}";

    private final {c}Repository repository;
    private final EmployeeRepository employeeRepository;
{deps}    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<{c}Response> getAll(Long employeeId) {{
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        List<{c}> rows = repository.findByEmployeeIdOrderByIdAsc(employeeId);
        {"Refs refs = refsFor(rows);" if ref_classes else ""}
        return rows.stream()
            .sorted(Comparator.comparing(({c} e) -> !e.getIsActive())
                {sort_expr})
            .map(e -> toResponse(e{refs_arg_use}))
            .toList();
    }}

    @Override
    @Transactional
    public {c}Response add(Long employeeId, {c}Request request) {{
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        validate(request, null);
{unique_add}        {c} e = {c}.builder().employeeId(employeeId).isActive(true){builder_defaults}.build();
        apply(e, request);
        {c} saved = repository.save(e);
        entityManager.flush();
        log.info("{label} {{}} added for employee {{}}", saved.getId(), employeeId);
        audit.record("{s["tag"]}_ADDED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved{", " + refs_arg_empty if ref_classes else ""});
    }}

    @Override
    @Transactional
    public {c}Response update(Long employeeId, Long id, {c}Request request) {{
        {c} existing = find(employeeId, id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a record");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        if (!existing.getIsActive()) {{
            throw new BusinessRuleException("RECORD_INACTIVE", "A deactivated record cannot be edited. Activate it first.");
        }}
        validate(request, existing);
{unique_upd}        apply(existing, request);
        {c} saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("{s["tag"]}_UPDATED", TARGET, id, summary(saved));
        return toResponse(saved{", " + refs_arg_empty if ref_classes else ""});
    }}

    @Override
    @Transactional
    public void deactivate(Long employeeId, Long id) {{
        {c} e = find(employeeId, id);
        if (!e.getIsActive()) return;
        e.setIsActive(false);
        repository.save(e);
        audit.record("{s["tag"]}_DEACTIVATED", TARGET, id, summary(e));
    }}

    @Override
    @Transactional
    public void activate(Long employeeId, Long id) {{
        {c} e = find(employeeId, id);
        if (e.getIsActive()) return;
        e.setIsActive(true);
        repository.save(e);
        audit.record("{s["tag"]}_ACTIVATED", TARGET, id, summary(e));
    }}

    // ── helpers ────────────────────────────────────────────────

    /** The record of THIS employee only: another employee's record is reported as not found. */
    private {c} find(Long employeeId, Long id) {{
        {c} e = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("{label}", "id", id));
        if (!e.getEmployeeId().equals(employeeId)) throw new ResourceNotFoundException("{label}", "id", id);
        return e;
    }}

    private void validate({c}Request r, {c} current) {{
{validate_body}
    }}

    private static void apply({c} e, {c}Request r) {{
{apply_body}
    }}

    private static String summary({c} e) {{
        return "employeeId=" + e.getEmployeeId() + ", active=" + e.getIsActive();
    }}
{requires_active}
{refs_record}
{refs_method}{by_id}{name_helpers}    private {c}Response toResponse({c} e{refs_arg_decl}) {{
        return {c}Response.builder()
            .id(e.getId())
            .employeeId(e.getEmployeeId())
{resp_body}
            .isActive(e.getIsActive())
            .version(e.getVersion())
            .build();
    }}
}}
'''


def controller(s):
    c, label = s["cls"], s["label"]
    return f'''package {P}.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.ApiResponse;
import {P}.dto.request.{c}Request;
import {P}.dto.response.{c}Response;
import {P}.service.{c}Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {label}s of one employee. Read: HR staff, or the employee for their own record. Write: HR_ADMIN only.
 * A record is deactivated, never deleted. Generated by tools/generate_employee_info.py.
 */
@RestController
@RequestMapping("/api/v1/employees/{{employeeId}}/{s["path"]}")
@RequiredArgsConstructor
public class {c}Controller {{

    private final {c}Service service;
    private final EmployeeAccessGuard accessGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<{c}Response>>> getAll(@PathVariable Long employeeId) {{
        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(ApiResponse.<List<{c}Response>>builder()
            .success(true).message("{label}s fetched successfully")
            .data(service.getAll(employeeId)).statusCode(200).build());
    }}

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<{c}Response>> add(
            @PathVariable Long employeeId, @Valid @RequestBody {c}Request request) {{
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<{c}Response>builder()
            .success(true).message("{label} added successfully")
            .data(service.add(employeeId, request)).statusCode(201).build());
    }}

    @PutMapping("/{{id}}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<{c}Response>> update(
            @PathVariable Long employeeId, @PathVariable Long id, @Valid @RequestBody {c}Request request) {{
        return ResponseEntity.ok(ApiResponse.<{c}Response>builder()
            .success(true).message("{label} updated successfully")
            .data(service.update(employeeId, id, request)).statusCode(200).build());
    }}

    @PatchMapping("/{{id}}/deactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long employeeId, @PathVariable Long id) {{
        service.deactivate(employeeId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("{label} deactivated").statusCode(200).build());
    }}

    @PatchMapping("/{{id}}/activate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long employeeId, @PathVariable Long id) {{
        service.activate(employeeId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("{label} activated").statusCode(200).build());
    }}
}}
'''


for s in SPECS:
    c = s["cls"]
    write(f"entity/{c}.java", entity(s))
    write(f"repository/{c}Repository.java", repository(s))
    write(f"dto/request/{c}Request.java", request(s))
    write(f"dto/response/{c}Response.java", response(s))
    write(f"service/{c}Service.java", service(s))
    write(f"service/impl/{c}ServiceImpl.java", impl(s))
    write(f"controller/{c}Controller.java", controller(s))
print("ok:", len(SPECS), "employee-information sections")
