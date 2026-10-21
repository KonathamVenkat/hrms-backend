"""Generates the backend for the V5 masters (entity, repository, DTOs, service, impl, admin controller).

Run from anywhere: python tools/generate_masters.py. Output is overwritten, so change the rules HERE, not in the
generated files. Rules mirror DepartmentServiceImpl / DesignationServiceImpl (see the master checklist)."""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "com" / "hrms" / "employee"
P = "com.hrms.employee"

# cls, label, table, idcol, path, parent=(cls, field, col, label) | None, child=(cls, label, field on the child) | None
# gen=False: already generated and since edited by hand (CompanyBranch has V8 columns), so never overwritten.
# sort=True: the table has a nullable SORT_ORDER and rows are listed by it.
# lookup="all" only where an HR screen reads the dropdown (relations, languages, language levels, education
# levels: the employee-information tabs); the others get none, so no unused endpoint code is generated.
MASTERS = [
    dict(cls="Bank", label="Bank", table="BANKS", idcol="BANK_ID", path="banks", parent=None,
         child=("BankBranch", "bank branches", "bankId"), lookup="all", gen=False),
    dict(cls="BankBranch", label="Bank branch", table="BANK_BRANCHES", idcol="BANK_BRANCH_ID", path="bank-branches",
         parent=("Bank", "bankId", "BANK_ID", "Bank"), child=None, lookup="byParent", gen=False),
    dict(cls="Company", label="Company", table="COMPANIES", idcol="COMPANY_ID", path="companies", parent=None,
         child=("CompanyBranch", "company branches", "companyId"), gen=False),
    dict(cls="CompanyBranch", label="Company branch", table="COMPANY_BRANCHES", idcol="COMPANY_BRANCH_ID",
         path="company-branches", parent=("Company", "companyId", "COMPANY_ID", "Company"), child=None, gen=False),
    # Geography: region > wilaya > province > city.
    dict(cls="Region", label="Region", table="REGIONS", idcol="REGION_ID", path="regions", parent=None,
         child=("Wilaya", "wilayas", "regionId")),
    dict(cls="Wilaya", label="Wilaya", table="WILAYAS", idcol="WILAYA_ID", path="wilayas",
         parent=("Region", "regionId", "REGION_ID", "Region"), child=("Province", "provinces", "wilayaId")),
    dict(cls="Province", label="Province", table="PROVINCES", idcol="PROVINCE_ID", path="provinces",
         parent=("Wilaya", "wilayaId", "WILAYA_ID", "Wilaya"), child=("City", "cities", "provinceId")),
    dict(cls="City", label="City", table="CITIES", idcol="CITY_ID", path="cities",
         parent=("Province", "provinceId", "PROVINCE_ID", "Province"), child=None, gen=False),  # + paged search by hand
    # Employee reference lists.
    dict(cls="Religion", label="Religion", table="RELIGIONS", idcol="RELIGION_ID", path="religions", parent=None, child=None),
    dict(cls="Nationality", label="Nationality", table="NATIONALITIES", idcol="NATIONALITY_ID", path="nationalities",
         parent=None, child=None),
    dict(cls="ContractType", label="Contract type", table="CONTRACT_TYPES", idcol="CONTRACT_TYPE_ID",
         path="contract-types", parent=None, child=None),
    dict(cls="Profession", label="Profession", table="PROFESSIONS", idcol="PROFESSION_ID", path="professions",
         parent=None, child=None),
    dict(cls="EducationLevel", label="Education level", table="EDUCATION_LEVELS", idcol="EDUCATION_LEVEL_ID",
         path="education-levels", parent=None, child=None, lookup="all"),
    dict(cls="Language", label="Language", table="LANGUAGES", idcol="LANGUAGE_ID", path="languages", parent=None, child=None,
         lookup="all"),
    dict(cls="LanguageLevel", label="Language level", table="LANGUAGE_LEVELS", idcol="LANGUAGE_LEVEL_ID",
         path="language-levels", parent=None, child=None, lookup="all", sort=True),
    dict(cls="HouseType", label="House type", table="HOUSE_TYPES", idcol="HOUSE_TYPE_ID", path="house-types",
         parent=None, child=None),
    dict(cls="RelationType", label="Relation type", table="RELATION_TYPES", idcol="RELATION_TYPE_ID",
         path="relation-types", parent=None, child=("Relation", "relations", "relationTypeId")),
    dict(cls="Relation", label="Relation", table="RELATIONS", idcol="RELATION_ID", path="relations",
         parent=("RelationType", "relationTypeId", "RELATION_TYPE_ID", "Relation type"), child=None, lookup="all"),
    dict(cls="Directorate", label="Directorate", table="DIRECTORATES", idcol="DIRECTORATE_ID", path="directorates",
         parent=("CompanyBranch", "companyBranchId", "COMPANY_BRANCH_ID", "Company branch"), child=None),
    # GRADES.COMPANY_ID is optional and unused (the grade scale applies to every company), so it is not mapped.
    dict(cls="Grade", label="Grade", table="GRADES", idcol="GRADE_ID", path="grades", parent=None, child=None, sort=True),
]


def write(rel, text):
    f = ROOT / rel
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(text, encoding="utf-8", newline="\n")


def entity(m):
    c, t = m["cls"], m["table"]
    parent = ""
    if m["parent"]:
        _, fld, col, _ = m["parent"]
        parent = f'''
    @Column(name = "{col}", nullable = false)
    private Long {fld};
'''
    sort = ""
    if m.get("sort"):
        sort = '''
    @Column(name = "SORT_ORDER")
    private Integer sortOrder;
'''
    return f'''package {P}.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** {m["label"]} master (table {t}); columns carry the table name as prefix, the Java fields stay plain. */
@Entity
@Table(name = "{t}", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class {c} {{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "{t.lower()}_seq")
    @SequenceGenerator(name = "{t.lower()}_seq", sequenceName = "HRMS.SEQ_{t}", allocationSize = 1)
    @Column(name = "{m["idcol"]}", nullable = false)
    private Long id;
{parent}
    @Column(name = "{t}_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "{t}_NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "{t}_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String nameAr;
{sort}
    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    /** Optimistic lock: two admins saving the same row no longer overwrite each other. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    // Both timestamps default to SYSTIMESTAMP in the table; the service sets UPDATED_AT on change.
    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false)
    private LocalDateTime updatedAt;
}}
'''


def repository(m):
    c = m["cls"]
    order = "findAllByOrderBySortOrderAscNameAsc" if m.get("sort") else "findAllByOrderByNameAsc"
    return f'''package {P}.repository;

import {P}.entity.{c};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface {c}Repository extends JpaRepository<{c}, Long> {{
    List<{c}> {order}();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
{lookup_repo(m)}{child_repo(m)}}}
'''


def lookup_order(m):
    return "findByIsActiveOrderBySortOrderAscNameAsc" if m.get("sort") else "findByIsActiveOrderByNameAsc"


def lookup_repo(m):
    c = m["cls"]
    if m.get("lookup") == "all":
        return f"    List<{c}> {lookup_order(m)}(Boolean isActive);\n"
    if m.get("lookup") == "byParent":
        fld = m["parent"][1]
        return f"    List<{c}> findBy{fld[0].upper() + fld[1:]}AndIsActiveOrderByNameAsc(Long {fld}, Boolean isActive);\n"
    return ""


def lookup_service(m):
    if m.get("lookup") == "all":
        return "    /** Active rows for dropdowns (read-only, for the roles that pick them). */\n    List<MasterLookupResponse> getActiveLookup();\n"
    if m.get("lookup") == "byParent":
        fld = m["parent"][1]
        return (f"    /** Active rows of one parent for dropdowns. */\n"
                f"    List<MasterLookupResponse> getActiveLookupBy{fld[0].upper() + fld[1:]}(Long {fld});\n")
    return ""


def lookup_impl(m):
    c = m["cls"]
    conv = (".stream().map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode())"
            ".name(e.getName()).nameAr(e.getNameAr()).build()).toList()")
    if m.get("lookup") == "all":
        return f"""
    @Override
    public List<MasterLookupResponse> getActiveLookup() {{
        return repository.{lookup_order(m)}(true){conv};
    }}
"""
    if m.get("lookup") == "byParent":
        fld = m["parent"][1]
        cap = fld[0].upper() + fld[1:]
        return f"""
    @Override
    public List<MasterLookupResponse> getActiveLookupBy{cap}(Long {fld}) {{
        return repository.findBy{cap}AndIsActiveOrderByNameAsc({fld}, true){conv};
    }}
"""
    return ""


def child_repo(m):
    # the child master asks its parent's repository nothing; the parent asks the child's, so add the lookups on the child
    return ""


CHILD_METHODS = {
    # child class -> parent field
    "BankBranch": "bankId",
    "CompanyBranch": "companyId",
}


def child_repository_extra(m):
    if not m["parent"]:
        return ""
    fld = m["parent"][1]
    cap = fld[0].upper() + fld[1:]
    return f'''    boolean existsBy{cap}({"Long"} {fld});
    boolean existsBy{cap}AndIsActive(Long {fld}, Boolean isActive);
'''


def request(m):
    l = m["label"]
    sort = ""
    if m.get("sort"):
        sort = '''    @Min(0) @Max(9999)
    private Integer sortOrder;

'''
    parent = ""
    if m["parent"]:
        _, fld, _, plabel = m["parent"]
        parent = f'''
    @NotNull(message = "{plabel} is required")
    private Long {fld};
'''
    return f'''package {P}.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class {m["cls"]}Request {{
{parent}
    @NotBlank(message = "{l} code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String code;

    @NotBlank(message = "{l} name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\\\p{{Cc}}\\\\p{{Cf}}]*$", message = "Name contains control or invisible characters")
    private String name;

    @NotBlank(message = "Arabic {l.lower()} name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\\\p{{Cc}}\\\\p{{Cf}}]*$", message = "Arabic name contains control or invisible characters")
    private String nameAr;

{sort}    private Boolean isActive;

    /** The version the screen was loaded with; omit to skip the stale-edit check. */
    private Long version;
}}
'''


def response(m):
    parent = ""
    if m["parent"]:
        parent = f"    private Long    {m['parent'][1]};\n"
    if m.get("sort"):
        parent += "    private Integer sortOrder;\n"
    return f'''package {P}.dto.response;

import lombok.*;

@Data @Builder
public class {m["cls"]}Response {{
    private Long    id;
{parent}    private String  code;
    private String  name;
    private String  nameAr;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
    private Long    version;
}}
'''


def service(m):
    c = m["cls"]
    return f'''package {P}.service;

import {P}.dto.request.{c}Request;
import {P}.dto.response.{c}Response;
{"import " + P + ".dto.response.MasterLookupResponse;" + chr(10) if m.get("lookup") else ""}import java.util.List;

public interface {c}Service {{
{lookup_service(m)}    List<{c}Response> getAll();
    {c}Response getById(Long id);
    {c}Response create({c}Request request);
    {c}Response update(Long id, {c}Request request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}}
'''


def impl(m):
    c, l = m["cls"], m["label"]
    ll = l.lower()
    order = "findAllByOrderBySortOrderAscNameAsc" if m.get("sort") else "findAllByOrderByNameAsc"
    sort_apply = "        e.setSortOrder(r.getSortOrder());\n" if m.get("sort") else ""
    sort_resp = "            .sortOrder(e.getSortOrder())\n" if m.get("sort") else ""
    sort_summary = ' + ", sortOrder=" + e.getSortOrder()' if m.get("sort") else ""
    tag = re.sub(r"(?<!^)(?=[A-Z])", "_", c).upper()  # BANK_BRANCH
    imports_extra, fields_extra, parent_check, parent_apply, parent_resp, parent_summary = "", "", "", "", "", ""
    child_check_deact, child_check_delete = "", ""
    if m["parent"]:
        pc, fld, _, plabel = m["parent"]
        cap = fld[0].upper() + fld[1:]
        imports_extra += f"import {P}.repository.{pc}Repository;\n"
        imports_extra += f"import {P}.entity.{pc};\n"
        fields_extra += f"    private final {pc}Repository {pc[0].lower()+pc[1:]}Repository;\n"
        pr = pc[0].lower() + pc[1:] + "Repository"
        parent_check = f'''
    /** The {plabel.lower()} must exist, and an active {ll} needs an active {plabel.lower()}. */
    private void checkParent(Long {fld}, boolean childActive) {{
        {pc} parent = {pr}.findById({fld})
            .orElseThrow(() -> new ResourceNotFoundException("{plabel}", "id", {fld}));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {{
            throw new BusinessRuleException("PARENT_INACTIVE",
                "{plabel} " + parent.getCode() + " is inactive, so it cannot hold an active {ll}.");
        }}
    }}
'''
        parent_apply = f"        e.set{cap}(r.get{cap}());\n"
        parent_resp = f"            .{fld}(e.get{cap}())\n"
        parent_summary = f' + ", {fld}=" + e.get{cap}()'
    if m["child"]:
        cc, clabel, cfld = m["child"]
        cr = cc[0].lower() + cc[1:] + "Repository"
        ccap = cfld[0].upper() + cfld[1:]
        imports_extra += f"import {P}.repository.{cc}Repository;\n"
        fields_extra += f"    private final {cc}Repository {cr};\n"
        child_check_deact = f'''
    private void checkNoActiveChildren({c} e) {{
        if ({cr}.existsBy{ccap}AndIsActive(e.getId(), true)) {{
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "{l} " + e.getCode() + " still has active {clabel}. Deactivate them first.");
        }}
    }}
'''
        child_check_delete = f"{cr}.existsBy{ccap}(id)"
    deact_call = "        if (!active) checkNoActiveChildren(e);\n" if m["child"] else ""
    upd_deact = "        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);\n" if m["child"] else ""
    upd_parent, create_parent, activate_parent, move_guard = "", "", "", ""
    if m["parent"]:
        fld = m["parent"][1]
        cap = fld[0].upper() + fld[1:]
        upd_parent = (f"        boolean active = request.getIsActive() != null ? request.getIsActive()"
                      f" : Boolean.TRUE.equals(existing.getIsActive());\n"
                      f"        checkParent(request.get{cap}(), active);\n")
        create_parent = f"        checkParent(request.get{cap}(), active);\n"
        if m["child"]:
            move_guard = (
                f"        if (!existing.get{cap}().equals(request.get{cap}()) && {child_check_delete}) {{\n"
                f"            throw new BusinessRuleException(\"MASTER_HAS_CHILDREN\",\n"
                f"                \"{l} \" + existing.getCode() + \" has {m['child'][1]} below it, so it cannot move to "
                f"another {m['parent'][3].lower()}.\");\n"
                f"        }}\n")
        activate_parent = f"        if (active) checkParent(e.get{cap}(), true);\n"
    delete_guard = ""
    if m["child"]:
        delete_guard = f"        if ({child_check_delete}) throw inUse(e);\n"
    return f'''package {P}.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import {P}.dto.request.{c}Request;
import {P}.dto.response.{c}Response;
{"import " + P + ".dto.response.MasterLookupResponse;" + chr(10) if m.get("lookup") else ""}import {P}.entity.{c};
import {P}.repository.{c}Repository;
{imports_extra}import {P}.service.{c}Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class {c}ServiceImpl implements {c}Service {{

    private static final String TARGET = "{tag}";

    private final {c}Repository repository;
{fields_extra}    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<{c}Response> getAll() {{
        return repository.{order}().stream().map(this::toResponse).toList();
    }}

{lookup_impl(m)}
    @Override
    public {c}Response getById(Long id) {{
        return toResponse(findById(id));
    }}

    @Override
    @Transactional
    public {c}Response create({c}Request request) {{
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {{
            throw new DuplicateResourceException("{l}", "code", code);
        }}
        boolean active = request.getIsActive() == null || request.getIsActive();
{create_parent}
        {c} entity = {c}.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        {c} saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("{l} created. ID: {{}}, code: {{}}", saved.getId(), saved.getCode());
        audit.record("{tag}_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }}

    @Override
    @Transactional
    public {c}Response update(Long id, {c}Request request) {{
        {c} existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a {ll}");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {{
            throw new DuplicateResourceException("{l}", "code", code);
        }}
{upd_parent}{move_guard}{upd_deact}
        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        {c} saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("{tag}_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }}

    @Override
    @Transactional
    public void deactivate(Long id) {{
        setActive(id, false);
    }}

    @Override
    @Transactional
    public void activate(Long id) {{
        setActive(id, true);
    }}

    @Override
    @Transactional
    public void delete(Long id) {{
        {c} e = findById(id);
{delete_guard}        repository.delete(e);
        try {{
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        }} catch (DataIntegrityViolationException ex) {{
            throw inUse(e);
        }}
        log.info("{l} deleted. ID: {{}}, code: {{}}", id, e.getCode());
        audit.record("{tag}_DELETED", TARGET, id, summary(e));
    }}

    private BusinessRuleException inUse({c} e) {{
        return new BusinessRuleException("MASTER_IN_USE",
            "{l} " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }}

    private void setActive(Long id, boolean active) {{
        {c} e = findById(id);
{deact_call}{activate_parent}        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "{tag}_ACTIVATED" : "{tag}_DEACTIVATED", TARGET, id, e.getCode());
    }}
{child_check_deact}{parent_check}
    private static String summary({c} e) {{
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr(){parent_summary}{sort_summary}
            + ", active=" + e.getIsActive();
    }}

    private {c} findById(Long id) {{
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("{l}", "id", id));
    }}

    private void apply({c} e, {c}Request r, String code) {{
{parent_apply}        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
{sort_apply}    }}

    private {c}Response toResponse({c} e) {{
        return {c}Response.builder()
            .id(e.getId())
{parent_resp}            .code(e.getCode())
            .name(e.getName())
            .nameAr(e.getNameAr())
{sort_resp}            .isActive(Boolean.TRUE.equals(e.getIsActive()))
            .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
            .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null)
            .version(e.getVersion())
            .build();
    }}
}}
'''


def controller(m):
    c, l = m["cls"], m["label"]
    return f'''package {P}.controller;

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

/** Master-data maintenance for {l.lower()}s. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/{m["path"]}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class {c}AdminController {{

    private final {c}Service service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<{c}Response>>> getAll() {{
        return ResponseEntity.ok(ApiResponse.<List<{c}Response>>builder()
            .success(true).message("{l} list fetched")
            .data(service.getAll()).statusCode(200).build());
    }}

    @GetMapping("/{{id}}")
    public ResponseEntity<ApiResponse<{c}Response>> getById(@PathVariable Long id) {{
        return ResponseEntity.ok(ApiResponse.<{c}Response>builder()
            .success(true).message("{l} fetched")
            .data(service.getById(id)).statusCode(200).build());
    }}

    @PostMapping
    public ResponseEntity<ApiResponse<{c}Response>> create(@Valid @RequestBody {c}Request request) {{
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<{c}Response>builder()
                .success(true).message("{l} created successfully")
                .data(service.create(request)).statusCode(201).build());
    }}

    @PutMapping("/{{id}}")
    public ResponseEntity<ApiResponse<{c}Response>> update(
            @PathVariable Long id, @Valid @RequestBody {c}Request request) {{
        return ResponseEntity.ok(ApiResponse.<{c}Response>builder()
            .success(true).message("{l} updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }}

    @PatchMapping("/{{id}}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {{
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("{l} deactivated").statusCode(200).build());
    }}

    @PatchMapping("/{{id}}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {{
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("{l} activated").statusCode(200).build());
    }}

    @DeleteMapping("/{{id}}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {{
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("{l} deleted").statusCode(200).build());
    }}
}}
'''


for m in MASTERS:
    if not m.get("gen", True):
        continue
    c = m["cls"]
    repo = repository(m)
    extra = child_repository_extra(m)
    if extra:
        repo = repo.rstrip().rstrip("}") + extra + "}\n"
    write(f"entity/{c}.java", entity(m))
    write(f"repository/{c}Repository.java", repo)
    write(f"dto/request/{c}Request.java", request(m))
    write(f"dto/response/{c}Response.java", response(m))
    write(f"service/{c}Service.java", service(m))
    write(f"service/impl/{c}ServiceImpl.java", impl(m))
    write(f"controller/{c}AdminController.java", controller(m))
print("ok")
