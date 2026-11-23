"""Writes db/seed_dept_heads_secretaries.sql and db/seed_dept_heads_secretaries_rollback.sql: a head and a secretary for every
active department that lacks one, plus 3 staff in each department that had nobody (and in ICT Infrastructure and Technical
Services), each with a current job record and a login. South Sudanese names. For development databases only.

Run from the repo root:  python tools/generate_dept_heads_secretaries.py
The department list below is a snapshot of the dev database on 2026-11-23 (which slots were empty, who had staff). At run time
the script still checks each slot: a head or secretary is created only while that department's slot is empty, and a department
that does not exist is skipped. Designations are matched by department + title, so an existing one (e.g. "Security Officer") is
reused instead of duplicated. Every login uses PASSWORD (BCrypt) and is NOT asked to change it (test logins).
Needs: the EMPLOYEE role, office location JUBA_HQ, company branch JUBA_HO, work shift 5. Heads report to the General Manager
(anil.mehta@nilepet.com) when that person exists; secretaries and staff report to their department head.
"""
import os

import bcrypt

PASSWORD = 'Welcome123'   # test logins: same password for all, no forced change (owner's choice 2026-11-23)
CREATED_BY = 'SEEDDH'
BS = chr(92)
GM_EMAIL = 'anil.mehta@nilepet.com'
STAFF_PER_DEPT = 3
ALSO_STAFF = {'DEP-048', 'DEP-006'}   # named by the user although each already had one person

# code, English name, has head, has secretary, current staff (dev snapshot 2026-11-23)
DEPARTMENTS = [
    ('DEP-001', 'Exploration Department', False, False, 0),
    ('DEP-002', 'Petroleum Engineering Department', False, False, 0),
    ('DEP-003', 'Drilling Department', False, False, 0),
    ('DEP-004', 'Field Operations Department', False, False, 0),
    ('DEP-005', 'Field Development Department', False, False, 0),
    ('DEP-006', 'Technical Services Department', True, False, 1),
    ('DEP-007', 'Sales & Marketing Department', False, False, 0),
    ('DEP-008', 'Supply & Distribution Department', False, False, 0),
    ('DEP-009', 'Depot Operations Department', False, False, 0),
    ('DEP-010', 'Retail Operations Department', False, False, 0),
    ('DEP-011', 'LPG Department', False, False, 0),
    ('DEP-012', 'Refinery Operations Department', False, False, 0),
    ('DEP-013', 'Transportation Department', False, False, 0),
    ('DEP-014', 'Training Management Department', False, False, 0),
    ('DEP-015', 'Technical Training Department', False, False, 0),
    ('DEP-016', 'Leadership Development Department', False, False, 0),
    ('DEP-017', 'Scholarship & Professional Development Department', False, False, 0),
    ('DEP-018', 'Occupational Health Department', False, False, 0),
    ('DEP-019', 'Safety Operations Department', False, False, 0),
    ('DEP-020', 'Environmental Management Department', False, False, 0),
    ('DEP-021', 'Risk Management Department', False, False, 0),
    ('DEP-022', 'Joint Venture Operations Department', False, False, 0),
    ('DEP-023', 'Commercial & Partnership Department', False, False, 0),
    ('DEP-024', 'Production Coordination Department', False, False, 0),
    ('DEP-025', 'JV Finance & Administration Department', False, False, 0),
    ('DEP-026', 'Legal Affairs Department', False, False, 0),
    ('DEP-027', 'Contracts Department', False, False, 0),
    ('DEP-028', 'Compliance Department', False, False, 0),
    ('DEP-029', 'Legal Documentation Department', False, False, 0),
    ('DEP-030', 'Engineering Services Department', False, False, 0),
    ('DEP-031', 'Laboratory Services Department', False, False, 0),
    ('DEP-032', 'Research & Innovation Department', False, False, 0),
    ('DEP-033', 'Technical Support Department', False, False, 0),
    ('DEP-034', 'Recruitment & Selection Department', False, False, 0),
    ('DEP-035', 'Compensation & Benefits Department', False, False, 0),
    ('DEP-036', 'Employee Relations Department', False, False, 0),
    ('DEP-037', 'HR Planning & Administration Department', False, False, 0),
    ('DEP-038', 'Performance & Talent Management Department', False, False, 0),
    ('DEP-039', 'Accounting Department', True, True, 4),
    ('DEP-040', 'Budget & Planning Department', False, False, 0),
    ('DEP-041', 'Treasury Department', False, False, 0),
    ('DEP-042', 'Taxation Department', False, False, 0),
    ('DEP-043', 'Financial Control Department', False, False, 0),
    ('DEP-044', 'Procurement Department', True, False, 10),
    ('DEP-045', 'Stores & Inventory Department', False, False, 0),
    ('DEP-046', 'Logistics Department', False, False, 0),
    ('DEP-047', 'Contracts & Vendor Management Department', False, False, 0),
    ('DEP-048', 'ICT Infrastructure Department', False, False, 1),
    ('DEP-049', 'Business Applications Department', True, False, 10),
    ('DEP-050', 'Data & Database Management Department', False, False, 0),
    ('DEP-051', 'Telecommunications Department', False, False, 0),
    ('DEP-052', 'Financial Audit Department', False, False, 0),
    ('DEP-053', 'Operational Audit Department', False, False, 0),
    ('DEP-054', 'Compliance & Risk Audit Department', False, False, 0),
    ('DEP-055', 'Public Relations Department', False, False, 0),
    ('DEP-056', 'Stakeholder Relations Department', False, False, 0),
    ('DEP-057', 'Corporate Social Responsibility Department', False, False, 0),
    ('DEP-058', 'Facilities Management Department', False, False, 0),
    ('DEP-059', 'Transport & Logistics Department', False, False, 0),
    ('DEP-060', 'Security Department', False, False, 0),
    ('DEP-061', 'General Administration Department', False, False, 2),
]

MALE = [('Deng', 'دينق'), ('John', 'جون'), ('Peter', 'بيتر'), ('James', 'جيمس'), ('Michael', 'مايكل'), ('David', 'ديفيد'),
        ('Samuel', 'صموئيل'), ('Daniel', 'دانيال'), ('Joseph', 'جوزيف'), ('Emmanuel', 'إيمانويل'), ('Simon', 'سايمون'),
        ('Paul', 'بول'), ('Stephen', 'ستيفن'), ('Lual', 'لوال'), ('Majok', 'ماجوك'), ('Akol', 'أكول'), ('Kuol', 'كوال'),
        ('Chol', 'شول'), ('Malual', 'مالوال'), ('Ajak', 'أجاك'), ('Wani', 'واني'), ('Taban', 'تابان'), ('Lomoro', 'لومورو'),
        ('Kenyi', 'كيني'), ('Gatluak', 'قاتلواك'), ('Ruai', 'رواي'), ('Kuach', 'كواج'), ('Thon', 'ثون'), ('Arop', 'أروب'),
        ('Yien', 'يين'), ('Ahmed', 'أحمد'), ('Ibrahim', 'إبراهيم'), ('Mohamed', 'محمد'), ('Hassan', 'حسن'), ('Martin', 'مارتن'),
        ('Andrew', 'أندرو'), ('Isaac', 'إسحاق'), ('Mayen', 'ماين')]
FEMALE = [('Mary', 'ماري'), ('Sarah', 'سارة'), ('Elizabeth', 'إليزابيث'), ('Martha', 'مارثا'), ('Rose', 'روز'),
          ('Agnes', 'أغنيس'), ('Esther', 'إستير'), ('Ruth', 'روث'), ('Anna', 'آنا'), ('Josephine', 'جوزفين'),
          ('Christine', 'كريستين'), ('Margaret', 'مارغريت'), ('Helen', 'هيلين'), ('Susan', 'سوزان'), ('Victoria', 'فيكتوريا'),
          ('Joyce', 'جويس'), ('Achol', 'أشول'), ('Nyibol', 'نيبول'), ('Ayen', 'أين'), ('Abuk', 'أبوك'), ('Adut', 'أدوت'),
          ('Akuol', 'أكوال'), ('Nyandeng', 'نياندينق'), ('Atong', 'أتونغ'), ('Awut', 'أووت'), ('Yar', 'يار'), ('Amel', 'أمل'),
          ('Fatima', 'فاطمة'), ('Mariam', 'مريم'), ('Halima', 'حليمة'), ('Zainab', 'زينب'), ('Nadia', 'نادية'),
          ('Salwa', 'سلوى'), ('Lucy', 'لوسي')]
SURNAMES = [('Lual', 'لوال'), ('Majok', 'ماجوك'), ('Kuol', 'كوال'), ('Chol', 'شول'), ('Malual', 'مالوال'), ('Ajak', 'أجاك'),
            ('Wani', 'واني'), ('Taban', 'تابان'), ('Lomoro', 'لومورو'), ('Kenyi', 'كيني'), ('Gatkuoth', 'قاتكوث'),
            ('Thon', 'ثون'), ('Arop', 'أروب'), ('Machar', 'مشار'), ('Yien', 'يين'), ('Lado', 'لادو'), ('Modi', 'مودي'),
            ('Jada', 'جادا'), ('Lako', 'لاكو'), ('Tombe', 'تومبي'), ('Loku', 'لوكو'), ('Swaka', 'سواكا'), ('Kiir', 'كير'),
            ('Atem', 'أتيم'), ('Akech', 'أكيج'), ('Manyang', 'مانيانق'), ('Makuei', 'ماكوي'), ('Dut', 'دوت'),
            ('Ayuen', 'أيوين'), ('Kuany', 'كوان'), ('Nhial', 'نيال'), ('Wol', 'وول'), ('Mading', 'مادينق'), ('Pal', 'بال'),
            ('Gatwech', 'قاتويج'), ('Ochan', 'أوشان'), ('Okello', 'أوكيلو'), ('Juma', 'جمعة'), ('Abdalla', 'عبدالله'),
            ('Hamid', 'حامد'), ('Amum', 'أموم'), ('Bior', 'بيور'), ('Riak', 'رياك'), ('Lam', 'لام'), ('Mawien', 'ماوين')]
TOWNS = ['Juba', 'Wau', 'Malakal', 'Bor', 'Yei', 'Torit', 'Rumbek', 'Aweil', 'Bentiu', 'Kuajok', 'Yambio', 'Nimule']
BLOOD = ['O+', 'A+', 'B+', 'O+', 'AB+', 'A-', 'O-', 'B-']

HEAD_TITLE, HEAD_TITLE_AR = 'Head of Department', 'رئيس القسم'
SEC_TITLE, SEC_TITLE_AR = 'Department Secretary', 'سكرتير القسم'
STAFF_TITLE_AR_PREFIX = 'موظف '


def staff_title(dept_name):
    core = dept_name[:-len(' Department')] if dept_name.endswith(' Department') else dept_name
    return core + ' Officer'


def ar(text):
    """An NVARCHAR literal that survives any client encoding."""
    return "UNISTR('" + ''.join('%s%04X' % (BS, ord(c)) for c in text) + "')"


def lit(text):
    return 'NULL' if text is None else "'" + text.replace("'", "''") + "'"


def date(text):
    return 'NULL' if text is None else "DATE '%s'" % text


def add_months(day, months):
    y, m, d = map(int, day.split('-'))
    m0 = m - 1 + months
    return '%04d-%02d-%02d' % (y + m0 // 12, m0 % 12 + 1, d)


def people():
    """Deterministic list of everyone to create, in department order: head, secretary, staff."""
    used, out = set(), []
    counters = {'MALE': 0, 'FEMALE': 0}

    def name(gender):
        pool = MALE if gender == 'MALE' else FEMALE
        while True:
            k = counters[gender]
            counters[gender] += 1
            first = pool[k % len(pool)]
            last = SURNAMES[(k * 7 + k // len(pool)) % len(SURNAMES)]
            key = (first[0] + '.' + last[0]).lower()
            if first[0] != last[0] and key not in used:
                used.add(key)
                return first, last

    n = 0
    for code, dname, has_head, has_sec, staff in DEPARTMENTS:
        slots = []
        if not has_head:
            slots.append('HEAD')
        if not has_sec:
            slots.append('SECRETARY')
        if staff == 0 or code in ALSO_STAFF:
            slots += ['STAFF'] * STAFF_PER_DEPT
        for slot in slots:
            gender = {'HEAD': 'FEMALE' if n % 4 == 3 else 'MALE',
                      'SECRETARY': 'MALE' if n % 4 == 1 else 'FEMALE'}.get(slot, 'MALE' if n % 2 else 'FEMALE')
            first, last = name(gender)
            birth_year = {'HEAD': 1968 + n % 12, 'SECRETARY': 1986 + n % 12}.get(slot, 1984 + n % 16)
            hire_year = {'HEAD': 2010 + n % 8, 'SECRETARY': 2015 + n % 8}.get(slot, 2016 + n % 9)
            out.append(dict(n=n, dept=code, dept_name=dname, slot=slot, gender=gender, first=first[0], first_ar=first[1],
                            last=last[0], last_ar=last[1], dob='%d-%02d-%02d' % (birth_year, 1 + n % 12, 1 + n % 28),
                            hire='%d-%02d-%02d' % (hire_year, 1 + (n * 5) % 12, 1 + (n * 3) % 28), pob=TOWNS[n % len(TOWNS)],
                            blood=BLOOD[n % len(BLOOD)], marital='MARRIED' if slot == 'HEAD' or n % 3 else 'SINGLE',
                            religion='Muslim' if first[0] in ('Ahmed', 'Ibrahim', 'Mohamed', 'Hassan', 'Fatima', 'Mariam',
                                                             'Halima', 'Zainab', 'Nadia', 'Salwa', 'Amel') else 'Christian'))
            n += 1
    return out


def work_email(p):
    return '%s.%s@nilepet.com' % (p['first'].lower(), p['last'].lower())


def title_of(p):
    return {'HEAD': HEAD_TITLE, 'SECRETARY': SEC_TITLE}.get(p['slot']) or staff_title(p['dept_name'])


def desig_lookup(title, dept_expr):
    return '(SELECT g.DESIG_ID FROM HRMS.DESIGNATIONS g WHERE g.DEPARTMENT_ID = %s AND UPPER(g.TITLE) = UPPER(%s))' % (
        dept_expr, lit(title))


def slot_guard(p):
    return {'HEAD': '   AND d.HEAD_EMPLOYEE_ID IS NULL', 'SECRETARY': '   AND d.SECRETARY_EMPLOYEE_ID IS NULL'}.get(p['slot'])


def designations_sql():
    out, count = [], 0
    for code, dname, has_head, has_sec, staff in DEPARTMENTS:
        wanted = []
        if not has_head:
            wanted.append(('HEAD', HEAD_TITLE, ar(HEAD_TITLE_AR)))
        if not has_sec:
            wanted.append(('SEC', SEC_TITLE, ar(SEC_TITLE_AR)))
        if staff == 0 or code in ALSO_STAFF:
            wanted.append(('OFF', staff_title(dname), ar(STAFF_TITLE_AR_PREFIX) + ' || d.NAME_AR'))
        for suffix, title, title_ar in wanted:
            dcode = 'SDH-%s-%s' % (code, suffix)
            out += [
                'INSERT INTO HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID, IS_ACTIVE, TITLE_AR)',
                'SELECT HRMS.SEQ_DESIGNATIONS.NEXTVAL, %s, %s, d.DEPT_ID, 1, %s' % (lit(title), lit(dcode), title_ar),
                '  FROM HRMS.DEPARTMENTS d WHERE d.CODE = %s' % lit(code),
                '   AND NOT EXISTS (SELECT 1 FROM HRMS.DESIGNATIONS x WHERE x.CODE = %s' % lit(dcode),
                '                    OR (x.DEPARTMENT_ID = d.DEPT_ID AND UPPER(x.TITLE) = UPPER(%s)));' % lit(title),
            ]
            count += 1
    return out, count


def employee_sql(p):
    personal = '%s.%s%s@example.com' % (p['first'].lower(), p['last'].lower(), p['dob'][2:4])
    phone = '+2119%08d' % (12000000 + p['n'] * 3571)
    lines = [
        '-- %s %s -- %s, %s' % (p['first'], p['last'], p['slot'].lower(), p['dept_name']),
        'INSERT INTO HRMS.EMPLOYEES (EMPLOYEE_ID, EMPLOYEE_CODE, FIRST_NAME, FIRST_NAME_AR, LAST_NAME, LAST_NAME_AR, DATE_OF_BIRTH,',
        '    PLACE_OF_BIRTH, GENDER, BLOOD_GROUP, MARITAL_STATUS, NATIONALITY, RELIGION, PERSONAL_EMAIL, WORK_EMAIL, PERSONAL_PHONE,',
        '    HIRE_DATE, CONFIRMATION_DATE, EMPLOYMENT_STATUS, EMPLOYMENT_TYPE, CREATED_BY, DEPARTMENT_ID, DESIGNATION_ID, ROLE_ID)',
        "SELECT HRMS.SEQ_EMPLOYEES.NEXTVAL, 'EMP-2026-' || LPAD(HRMS.SEQ_EMPLOYEES.NEXTVAL, 4, '0'), %s, %s, %s, %s, %s,"
        % (lit(p['first']), ar(p['first_ar']), lit(p['last']), ar(p['last_ar']), date(p['dob'])),
        "    %s, %s, %s, %s, 'South Sudanese', %s, %s, %s, %s," % (lit(p['pob']), lit(p['gender']), lit(p['blood']),
                                                               lit(p['marital']), lit(p['religion']), lit(personal),
                                                               lit(work_email(p)), lit(phone)),
        "    %s, %s, 'ACTIVE', 'FULL_TIME', %s, d.DEPT_ID, %s," % (date(p['hire']), date(add_months(p['hire'], 6)),
                                                                 lit(CREATED_BY), desig_lookup(title_of(p), 'd.DEPT_ID')),
        "    (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'EMPLOYEE')",
        '  FROM HRMS.DEPARTMENTS d WHERE d.CODE = %s' % lit(p['dept']),
    ]
    if slot_guard(p):
        lines.append(slot_guard(p))
    lines.append('   AND NOT EXISTS (SELECT 1 FROM HRMS.EMPLOYEES x WHERE x.WORK_EMAIL = %s);' % lit(work_email(p)))
    return lines


def job_sql(p, manager):
    return [
        'INSERT INTO HRMS.EMPLOYEE_JOB_DETAILS (JOB_DETAILS_ID, EMPLOYEE_ID, DEPARTMENT_ID, DESIGNATION_ID, REPORTING_MANAGER_ID,',
        '    LOCATION_ID, SHIFT_ID, COMPANY_BRANCH_ID, WORK_MODE, EFFECTIVE_FROM, IS_CURRENT, CREATED_BY)',
        'SELECT HRMS.SEQ_EMPLOYEE_JOB_DETAILS.NEXTVAL, e.EMPLOYEE_ID, e.DEPARTMENT_ID, e.DESIGNATION_ID, %s,' % manager,
        "    (SELECT LOCATION_ID FROM HRMS.OFFICE_LOCATIONS WHERE LOCATION_CODE = 'JUBA_HQ'), 5,",
        "    (SELECT COMPANY_BRANCH_ID FROM HRMS.COMPANY_BRANCHES WHERE COMPANY_BRANCHES_CODE = 'JUBA_HO'),",
        "    'ON_SITE', %s, 1, %s" % (date(p['hire']), lit(CREATED_BY)),
        '  FROM HRMS.EMPLOYEES e',
        ' WHERE e.WORK_EMAIL = %s' % lit(work_email(p)),
        '   AND NOT EXISTS (SELECT 1 FROM HRMS.EMPLOYEE_JOB_DETAILS j WHERE j.EMPLOYEE_ID = e.EMPLOYEE_ID);',
    ]


def slot_update_sql(p, column):
    return [
        'UPDATE HRMS.DEPARTMENTS d SET d.%s = (SELECT e.EMPLOYEE_ID FROM HRMS.EMPLOYEES e WHERE e.WORK_EMAIL = %s)'
        % (column, lit(work_email(p))),
        ' WHERE d.CODE = %s AND d.%s IS NULL' % (lit(p['dept']), column),
        '   AND EXISTS (SELECT 1 FROM HRMS.EMPLOYEES e WHERE e.WORK_EMAIL = %s);' % lit(work_email(p)),
    ]


def login_sql(p, pw_hash):
    username = work_email(p).split('@')[0]
    return [
        'INSERT INTO HRMS.AUTH_USERS (USER_ID, USERNAME, EMAIL, FULL_NAME_EN, PASSWORD_HASH, ROLE, EMPLOYEE_ID, EMPLOYEE_CODE,',
        '    DEPARTMENT_ID, FAILED_ATTEMPTS, IS_ACTIVE, IS_LOCKED, MUST_CHANGE_PASSWORD, CREATED_BY)',
        "SELECT HRMS.AUTH_USER_SEQ.NEXTVAL, %s, %s, %s, '%s', 'EMPLOYEE', e.EMPLOYEE_ID, e.EMPLOYEE_CODE, TO_CHAR(e.DEPARTMENT_ID),"
        % (lit(username), lit(work_email(p)), lit('%s %s' % (p['first'], p['last'])), pw_hash),
        '    0, TRUE, FALSE, 0, %s' % lit(CREATED_BY),
        '  FROM HRMS.EMPLOYEES e',
        ' WHERE e.WORK_EMAIL = %s' % lit(work_email(p)),
        '   AND NOT EXISTS (SELECT 1 FROM HRMS.AUTH_USERS u WHERE u.EMPLOYEE_ID = e.EMPLOYEE_ID OR u.USERNAME = %s);' % lit(username),
    ]


def build():
    everyone = people()
    heads = [p for p in everyone if p['slot'] == 'HEAD']
    secretaries = [p for p in everyone if p['slot'] == 'SECRETARY']
    others = [p for p in everyone if p['slot'] != 'HEAD']
    pw_hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode()
    desig, n_desig = designations_sql()
    gm = "(SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE WORK_EMAIL = %s)" % lit(GM_EMAIL)
    own_head = '(SELECT h.HEAD_EMPLOYEE_ID FROM HRMS.DEPARTMENTS h WHERE h.DEPT_ID = e.DEPARTMENT_ID)'
    out = [
        '-- SAMPLE DATA: department heads and secretaries for every active department that lacks one, plus %d staff in each'
        % STAFF_PER_DEPT,
        '-- department that had nobody (and in ICT Infrastructure DEP-048 and Technical Services DEP-006): %d people' % len(everyone),
        '-- (%d heads, %d secretaries, %d staff), each with a current job record and an EMPLOYEE login.'
        % (len(heads), len(secretaries), len(everyone) - len(heads) - len(secretaries)),
        '-- DEVELOPMENT DATABASES ONLY: every login shares one password and is not asked to change it (test logins).',
        '-- A head/secretary is created only while that slot is empty; existing heads and secretaries are never replaced.',
        '-- Missing departments are skipped. Reporting: heads -> General Manager (%s, if present); secretaries and' % GM_EMAIL,
        '-- staff -> their department head. Safe to run twice: a row that exists is skipped.',
        '-- Needs: EMPLOYEE role, office location JUBA_HQ, branch JUBA_HO, work shift 5. Run as the HRMS user.',
        '-- Written by tools/generate_dept_heads_secretaries.py. Undo: db/seed_dept_heads_secretaries_rollback.sql',
        '--   Logins: username = the part of the work e-mail before @, password = %s (no change required).'
        % PASSWORD,
        '',
        '-- ===== 1. Designations (Head of Department, Department Secretary, <Department> Officer; existing titles reused) =====',
    ] + desig + ['-- Expect: up to %d rows inserted (fewer where the title already exists; 0 on a second run).' % n_desig, '',
                 '-- ===== 2. Employees =====']
    for p in everyone:
        out += employee_sql(p)
    out += ['-- Expect: up to %d rows inserted (0 on a second run).' % len(everyone), '',
            '-- ===== 3. Job records of the new heads (report to the General Manager) =====']
    for p in heads:
        out += job_sql(p, gm)
    out += ['', '-- ===== 4. Set the new heads (only where the slot is still empty) =====']
    for p in heads:
        out += slot_update_sql(p, 'HEAD_EMPLOYEE_ID')
    out += ['', '-- ===== 5. Job records of secretaries and staff (report to their department head) =====']
    for p in others:
        out += job_sql(p, own_head)
    out += ['', '-- ===== 6. Set the new secretaries (only where the slot is still empty) =====']
    for p in secretaries:
        out += slot_update_sql(p, 'SECRETARY_EMPLOYEE_ID')
    out += ['', '-- ===== 7. Logins (role EMPLOYEE; heads and secretaries get their extra menus from the post, not the role) =====']
    for p in everyone:
        out += login_sql(p, pw_hash)
    out += ['-- Expect: up to %d rows inserted (0 on a second run).' % len(everyone), '', 'COMMIT;', '']
    return '\n'.join(out)


def rollback():
    return '\n'.join([
        '-- UNDO of db/seed_dept_heads_secretaries.sql: clears the head/secretary slots it filled, removes its employees',
        "-- (CREATED_BY = '%s'), their logins and job records, and the designations it added (codes SDH-...)." % CREATED_BY,
        '-- Run as the HRMS user. Rows other data refers to (leave requests, attendance, documents...) make Oracle refuse the delete',
        '-- (ORA-02292): remove or move that data first.',
        'UPDATE HRMS.DEPARTMENTS SET HEAD_EMPLOYEE_ID = NULL',
        ' WHERE HEAD_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = %s);' % lit(CREATED_BY),
        'UPDATE HRMS.DEPARTMENTS SET SECRETARY_EMPLOYEE_ID = NULL',
        ' WHERE SECRETARY_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = %s);' % lit(CREATED_BY),
        'DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = %s;' % lit(CREATED_BY),
        'DELETE FROM HRMS.EMPLOYEE_JOB_DETAILS WHERE CREATED_BY = %s;' % lit(CREATED_BY),
        'DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = %s;' % lit(CREATED_BY),
        "DELETE FROM HRMS.DESIGNATIONS WHERE CODE LIKE 'SDH-%';",
        'COMMIT;',
        '',
    ])


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, 'db', 'seed_dept_heads_secretaries.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(build())
    with open(os.path.join(root, 'db', 'seed_dept_heads_secretaries_rollback.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(rollback())
    print('written: db/seed_dept_heads_secretaries.sql, db/seed_dept_heads_secretaries_rollback.sql')
