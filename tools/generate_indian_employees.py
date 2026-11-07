"""Writes db/seed_indian_employees.sql and db/seed_indian_employees_rollback.sql: 20 sample employees with Indian names (10 in
the Business Applications Department, 10 in the Procurement Department), a General Manager and a CEO, each with a current job
record and a login. For development databases only.

Run from the repo root:  python tools/generate_indian_employees.py
Arabic text is written as UNISTR escapes so it loads correctly whatever the client encoding is. Every login uses PASSWORD below
(stored as a BCrypt hash) and must change it at first sign-in. Needs the departments DEP-044 (Procurement), DEP-049 (Business
Applications) and DEP-061 (General Administration, where the CEO designation lives), the roles GENERAL_MANAGER and CEO (V25),
the office location JUBA_HQ, the company branch JUBA_HO and work shift 5.
"""
import os

import bcrypt

PASSWORD = 'Welcome@123'
CREATED_BY = 'SEEDIN'
BS = chr(92)

# code, English title, Arabic title, grade, department code
NEW_DESIGNATIONS = [
    ('GM', 'General Manager', 'المدير العام', 'E1', 'DEP-061'),
    ('BAPP_MGR', 'Business Applications Manager', 'مدير تطبيقات الأعمال', 'M1', 'DEP-049'),
    ('BAPP_SR_ENG', 'Senior Software Engineer', 'مهندس برمجيات أول', 'P3', 'DEP-049'),
    ('BAPP_ENG', 'Software Engineer', 'مهندس برمجيات', 'P2', 'DEP-049'),
    ('BAPP_BA', 'Business Analyst', 'محلل أعمال', 'P2', 'DEP-049'),
    ('BAPP_DBA', 'Database Administrator', 'مسؤول قواعد البيانات', 'P3', 'DEP-049'),
    ('BAPP_QA', 'QA Engineer', 'مهندس ضمان الجودة', 'P2', 'DEP-049'),
    ('BAPP_SUP', 'Application Support Engineer', 'مهندس دعم التطبيقات', 'P1', 'DEP-049'),
    ('PROC_OFFICER', 'Procurement Officer', 'ضابط مشتريات', 'P1', 'DEP-044'),
]


def person(first, last, first_ar, last_ar, gender, dob, pob, blood, marital, religion, hire, desig, dept, role='EMPLOYEE',
           middle=None, middle_ar=None, probation=False, ctype='FULL_TIME'):
    return dict(first=first, last=last, first_ar=first_ar, last_ar=last_ar, middle=middle, middle_ar=middle_ar, gender=gender,
                dob=dob, pob=pob, blood=blood, marital=marital, religion=religion, hire=hire, desig=desig, dept=dept,
                role=role, probation=probation, ctype=ctype)


# Order matters: the first person of a department is its head; heads report to the GM, the GM to the CEO.
CEO = person('Vikram', 'Rathore', 'فيكرام', 'راثور', 'MALE', '1969-04-12', 'Jaipur', 'O+', 'MARRIED', 'Hindu', '2012-03-01',
             'CEO', 'DEP-061', role='CEO', middle='Singh', middle_ar='سينغ')
GM = person('Anil', 'Mehta', 'أنيل', 'مهتا', 'MALE', '1972-09-23', 'Ahmedabad', 'B+', 'MARRIED', 'Hindu', '2014-06-16',
            'GM', 'DEP-061', role='GENERAL_MANAGER', middle='Kumar', middle_ar='كومار')
BUSINESS_APPS = [
    person('Suresh', 'Iyer', 'سوريش', 'آيير', 'MALE', '1980-02-17', 'Chennai', 'A+', 'MARRIED', 'Hindu', '2016-01-04', 'BAPP_MGR', 'DEP-049'),
    person('Priya', 'Nair', 'بريا', 'نير', 'FEMALE', '1987-06-09', 'Kochi', 'B+', 'MARRIED', 'Hindu', '2018-04-02', 'BAPP_SR_ENG', 'DEP-049'),
    person('Arjun', 'Reddy', 'أرجون', 'ريدي', 'MALE', '1985-11-30', 'Hyderabad', 'O+', 'MARRIED', 'Hindu', '2017-08-21', 'BAPP_SR_ENG', 'DEP-049'),
    person('Kavita', 'Deshmukh', 'كافيتا', 'ديشموخ', 'FEMALE', '1990-03-05', 'Pune', 'AB+', 'SINGLE', 'Hindu', '2019-02-11', 'BAPP_BA', 'DEP-049'),
    person('Rohan', 'Gupta', 'روهان', 'غوبتا', 'MALE', '1993-12-14', 'Delhi', 'A-', 'SINGLE', 'Hindu', '2021-07-05', 'BAPP_ENG', 'DEP-049'),
    person('Ananya', 'Banerjee', 'أنانيا', 'بانرجي', 'FEMALE', '1995-08-27', 'Kolkata', 'O+', 'SINGLE', 'Hindu', '2022-01-17', 'BAPP_ENG', 'DEP-049'),
    person('Imran', 'Sheikh', 'عمران', 'شيخ', 'MALE', '1988-05-19', 'Mumbai', 'B-', 'MARRIED', 'Muslim', '2018-10-08', 'BAPP_DBA', 'DEP-049'),
    person('Neha', 'Kulkarni', 'نيها', 'كولكارني', 'FEMALE', '1992-01-22', 'Nagpur', 'A+', 'MARRIED', 'Hindu', '2020-03-09', 'BAPP_QA', 'DEP-049'),
    person('Karthik', 'Subramanian', 'كارتيك', 'سوبرامانيان', 'MALE', '1997-10-03', 'Bengaluru', 'O+', 'SINGLE', 'Hindu', '2026-09-01', 'BAPP_SUP', 'DEP-049', probation=True),
    person('Simran', 'Kaur', 'سيمران', 'كور', 'FEMALE', '1998-07-16', 'Chandigarh', 'AB-', 'SINGLE', 'Sikh', '2026-10-01', 'BAPP_SUP', 'DEP-049', probation=True),
]
PROCUREMENT = [
    person('Ramesh', 'Venkataraman', 'راميش', 'فينكاتارامان', 'MALE', '1979-09-08', 'Coimbatore', 'B+', 'MARRIED', 'Hindu', '2015-05-18', 'SCM_MGR', 'DEP-044'),
    person('Deepa', 'Menon', 'ديبا', 'مينون', 'FEMALE', '1986-04-25', 'Thiruvananthapuram', 'A+', 'MARRIED', 'Hindu', '2017-09-11', 'PROC_SPEC', 'DEP-044'),
    person('Sanjay', 'Patel', 'سانجاي', 'باتيل', 'MALE', '1984-12-01', 'Ahmedabad', 'O+', 'MARRIED', 'Hindu', '2016-11-14', 'PROC_SPEC', 'DEP-044'),
    person('Meera', 'Joshi', 'ميرا', 'جوشي', 'FEMALE', '1991-02-13', 'Jaipur', 'B+', 'SINGLE', 'Jain', '2019-06-03', 'PROC_SPEC', 'DEP-044'),
    person('Amit', 'Verma', 'أميت', 'فيرما', 'MALE', '1989-07-29', 'Lucknow', 'A+', 'MARRIED', 'Hindu', '2018-01-22', 'INV_PLAN', 'DEP-044'),
    person('Pooja', 'Agarwal', 'بوجا', 'أغاروال', 'FEMALE', '1994-05-06', 'Indore', 'O-', 'SINGLE', 'Hindu', '2021-03-15', 'INV_PLAN', 'DEP-044'),
    person('Faisal', 'Ahmed', 'فيصل', 'أحمد', 'MALE', '1987-10-18', 'Hyderabad', 'AB+', 'MARRIED', 'Muslim', '2019-09-30', 'LOG_PLAN', 'DEP-044', ctype='CONTRACT'),
    person('Lakshmi', 'Narayanan', 'لاكشمي', 'نارايانان', 'FEMALE', '1992-11-11', 'Chennai', 'B+', 'MARRIED', 'Hindu', '2020-08-24', 'LOG_PLAN', 'DEP-044'),
    person('Vijay', 'Chauhan', 'فيجاي', 'تشوهان', 'MALE', '1999-03-21', 'Bhopal', 'A+', 'SINGLE', 'Hindu', '2026-09-15', 'PROC_OFFICER', 'DEP-044', probation=True),
    person('Sneha', 'Pillai', 'سنيها', 'بيلاي', 'FEMALE', '1996-09-02', 'Kochi', 'O+', 'SINGLE', 'Christian', '2022-05-09', 'PROC_OFFICER', 'DEP-044'),
]
PEOPLE = [CEO, GM] + BUSINESS_APPS + PROCUREMENT


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


def work_email(p):
    return '%s.%s@nilepet.com' % (p['first'].lower(), p['last'].lower())


def phone(index, base):
    return '+91%d%09d' % (base, 100000000 + index * 1370)


def manager_email(p):
    if p is CEO:
        return None
    if p is GM:
        return work_email(CEO)
    group = BUSINESS_APPS if p in BUSINESS_APPS else PROCUREMENT
    return work_email(GM) if p is group[0] else work_email(group[0])


def employee_sql(index, p):
    probation_end = add_months(p['hire'], 3) if p['probation'] else None
    confirmation = None if p['probation'] else add_months(p['hire'], 6)
    status = 'PROBATION' if p['probation'] else 'ACTIVE'
    personal = '%s.%s%02d@example.com' % (p['first'].lower(), p['last'].lower(), int(p['dob'][2:4]))
    mid = (lit(p['middle']), ar(p['middle_ar'])) if p['middle'] else ('NULL', 'NULL')
    return [
        '-- %s %s -- %s' % (p['first'], p['last'], p['role'] if p['role'] != 'EMPLOYEE' else p['dept']),
        'INSERT INTO HRMS.EMPLOYEES (EMPLOYEE_ID, EMPLOYEE_CODE, FIRST_NAME, FIRST_NAME_AR, MIDDLE_NAME, MIDDLE_NAME_AR, LAST_NAME,',
        '    LAST_NAME_AR, DATE_OF_BIRTH, PLACE_OF_BIRTH, GENDER, BLOOD_GROUP, MARITAL_STATUS, NATIONALITY, RELIGION, PERSONAL_EMAIL,',
        '    WORK_EMAIL, PERSONAL_PHONE, WORK_PHONE, HIRE_DATE, PROBATION_END_DATE, CONFIRMATION_DATE, EMPLOYMENT_STATUS,',
        '    EMPLOYMENT_TYPE, CREATED_BY, DEPARTMENT_ID, DESIGNATION_ID, ROLE_ID)',
        "SELECT HRMS.SEQ_EMPLOYEES.NEXTVAL, 'EMP-2026-' || LPAD(HRMS.SEQ_EMPLOYEES.NEXTVAL, 4, '0'), %s, %s, %s, %s, %s, %s,"
        % (lit(p['first']), ar(p['first_ar']), mid[0], mid[1], lit(p['last']), ar(p['last_ar'])),
        "    %s, %s, %s, %s, %s, 'Indian', %s, %s," % (date(p['dob']), lit(p['pob']), lit(p['gender']), lit(p['blood']),
                                                       lit(p['marital']), lit(p['religion']), lit(personal)),
        '    %s, %s, %s, %s, %s, %s, %s,' % (lit(work_email(p)), lit(phone(index, 9)), lit(phone(index, 8)), date(p['hire']),
                                            date(probation_end), date(confirmation), lit(status)),
        "    %s, %s, (SELECT DEPT_ID FROM HRMS.DEPARTMENTS WHERE CODE = %s)," % (lit(p['ctype']), lit(CREATED_BY), lit(p['dept'])),
        '    (SELECT DESIG_ID FROM HRMS.DESIGNATIONS WHERE CODE = %s), (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = %s)'
        % (lit(p['desig']), lit(p['role'])),
        '  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM HRMS.EMPLOYEES WHERE WORK_EMAIL = %s);' % lit(work_email(p)),
    ]


def job_sql(p):
    mgr = manager_email(p)
    manager = "(SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE WORK_EMAIL = %s)" % lit(mgr) if mgr else 'NULL'
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


def login_sql(p, pw_hash):
    username = work_email(p).split('@')[0]
    full = '%s %s' % (p['first'], p['last'])
    return [
        'INSERT INTO HRMS.AUTH_USERS (USER_ID, USERNAME, EMAIL, FULL_NAME_EN, PASSWORD_HASH, ROLE, EMPLOYEE_ID, EMPLOYEE_CODE,',
        '    DEPARTMENT_ID, FAILED_ATTEMPTS, IS_ACTIVE, IS_LOCKED, MUST_CHANGE_PASSWORD, CREATED_BY)',
        "SELECT HRMS.AUTH_USER_SEQ.NEXTVAL, %s, %s, %s, '%s', %s, e.EMPLOYEE_ID, e.EMPLOYEE_CODE, TO_CHAR(e.DEPARTMENT_ID), 0, TRUE, FALSE, 1, %s"
        % (lit(username), lit(work_email(p)), lit(full), pw_hash, lit(p['role']), lit(CREATED_BY)),
        '  FROM HRMS.EMPLOYEES e',
        ' WHERE e.WORK_EMAIL = %s' % lit(work_email(p)),
        '   AND NOT EXISTS (SELECT 1 FROM HRMS.AUTH_USERS u WHERE u.EMPLOYEE_ID = e.EMPLOYEE_ID OR u.USERNAME = %s);' % lit(username),
    ]


def build():
    pw_hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode()
    out = [
        '-- SAMPLE DATA: 20 employees with Indian names (10 Business Applications Department, 10 Procurement Department), a General',
        '-- Manager and a CEO, each with a current job record and a login. DEVELOPMENT DATABASES ONLY: all 22 logins share one temporary',
        '-- password and must change it at first sign-in. Do not run this on a production database.',
        '-- Run AFTER V25 (CEO role) and db/seed_nilepet_south_sudan_branches.sql, as the HRMS user. Safe to run twice: a row that exists',
        '-- is skipped. Needs departments DEP-044, DEP-049, DEP-061, office location JUBA_HQ, branch JUBA_HO and work shift 5.',
        '-- Reporting lines: employees -> their department head -> General Manager (anil.mehta) -> CEO (vikram.rathore).',
        '-- Department heads (Business Applications: suresh.iyer, Procurement: ramesh.venkataraman) are set only where the head is empty.',
        '-- Written by tools/generate_indian_employees.py. Undo: db/seed_indian_employees_rollback.sql',
        '--   Logins: username = the part of the work e-mail before @, password = %s (change required at first sign-in).' % PASSWORD,
        '',
        '-- ===== 1. Designations (the General Manager, 7 Business Applications titles, 1 Procurement title) =====',
    ]
    for code, title, title_ar, grade, dept in NEW_DESIGNATIONS:
        out += [
            'INSERT INTO HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID, IS_ACTIVE, TITLE_AR, GRADE_LEVEL)',
            "SELECT HRMS.SEQ_DESIGNATIONS.NEXTVAL, %s, %s, (SELECT DEPT_ID FROM HRMS.DEPARTMENTS WHERE CODE = %s), 1, %s, %s"
            % (lit(title), lit(code), lit(dept), ar(title_ar), lit(grade)),
            '  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM HRMS.DESIGNATIONS WHERE CODE = %s);' % lit(code),
        ]
    out += ['-- Expect: %d rows inserted (0 on a second run).' % len(NEW_DESIGNATIONS), '',
            '-- ===== 2. Employees (the CEO and GM first, then each head before the people who report to them) ====='
            ]
    for i, p in enumerate(PEOPLE):
        out += employee_sql(i, p)
    out += ['-- Expect: %d rows inserted (0 on a second run).' % len(PEOPLE), '', '-- ===== 3. Job records (current) =====']
    for p in PEOPLE:
        out += job_sql(p)
    out += ['-- Expect: %d rows inserted (0 on a second run).' % len(PEOPLE), '', '-- ===== 4. Logins =====']
    for p in PEOPLE:
        out += login_sql(p, pw_hash)
    out += ['-- Expect: %d rows inserted (0 on a second run).' % len(PEOPLE), '', '-- ===== 5. Department heads (only where empty) =====']
    for group in (BUSINESS_APPS, PROCUREMENT):
        out += [
            'UPDATE HRMS.DEPARTMENTS SET HEAD_EMPLOYEE_ID = (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE WORK_EMAIL = %s)'
            % lit(work_email(group[0])),
            ' WHERE CODE = %s AND HEAD_EMPLOYEE_ID IS NULL;' % lit(group[0]['dept']),
        ]
    out += ['', 'COMMIT;', '']
    return '\n'.join(out)


def rollback():
    codes = ', '.join(lit(d[0]) for d in NEW_DESIGNATIONS)
    return '\n'.join([
        '-- UNDO of db/seed_indian_employees.sql: removes the 22 sample employees (created by SEEDIN), their logins and job records,',
        '-- the department heads it set, and the 9 designations it added.',
        '-- Run as the HRMS user. Rows other data refers to (leave requests, attendance, documents...) make Oracle refuse the delete',
        '-- (ORA-02292): remove or move that data first. Order: dependants before parents.',
        'UPDATE HRMS.DEPARTMENTS SET HEAD_EMPLOYEE_ID = NULL',
        " WHERE HEAD_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = %s);" % lit(CREATED_BY),
        'DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = %s;' % lit(CREATED_BY),
        'DELETE FROM HRMS.EMPLOYEE_JOB_DETAILS WHERE CREATED_BY = %s;' % lit(CREATED_BY),
        'DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = %s;' % lit(CREATED_BY),
        'DELETE FROM HRMS.DESIGNATIONS WHERE CODE IN (%s);' % codes,
        'COMMIT;',
        '',
    ])


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, 'db', 'seed_indian_employees.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(build())
    with open(os.path.join(root, 'db', 'seed_indian_employees_rollback.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(rollback())
    print('written: db/seed_indian_employees.sql, db/seed_indian_employees_rollback.sql')
