"""Writes db/seed_sample_employees.sql and db/seed_sample_employees_rollback.sql: five sample employees, as the Create Employee
form would store them (personal, contact and employment details, plus a login), for development databases only.

Run from the repo root:  python tools/generate_sample_employees.py
Arabic text is written as UNISTR escapes so it loads correctly whatever the client encoding is. The login password is
PASSWORD below, stored as a BCrypt hash, and every account must change it at first sign-in.
"""
import os

import bcrypt

PASSWORD = 'Welcome@123'
CREATED_BY = 'SAMPLE5'

# first, middle, fourth, last (English, Arabic); place of birth (English, Arabic) is stored in one NVARCHAR column: Arabic.
PEOPLE = [
    dict(first=('Amira', 'أميرة'), middle=('Hassan', 'حسن'), fourth=('Ali', 'علي'), last=('Khalil', 'خليل'),
         dob='1991-03-14', pob='الخرطوم', gender='FEMALE', blood='B+', marital='MARRIED', nationality='Sudanese',
         religion='Muslim', personal='amira.khalil91@gmail.com', work='amira.khalil@nilepet.com',
         pphone='+249912345601', wphone='+249912345611', hire='2022-02-01', probation_end='2022-08-01',
         confirmation='2022-08-01', status='ACTIVE', type='FULL_TIME', notes=None),
    dict(first=('Deng', 'دينق'), middle=('Mayen', 'مايين'), fourth=None, last=('Garang', 'قرنق'),
         dob='1988-11-02', pob='Juba', gender='MALE', blood='O+', marital='MARRIED', nationality='South Sudanese',
         religion='Christian', personal='deng.garang88@gmail.com', work='deng.garang@nilepet.com',
         pphone='+211921000102', wphone='+211921000112', hire='2019-05-15', probation_end='2019-11-15',
         confirmation='2019-11-15', status='ACTIVE', type='FULL_TIME', notes=None),
    dict(first=('Grace', 'جريس'), middle=None, fourth=None, last=('Nyakim', 'نياكيم'),
         dob='1996-07-21', pob='Malakal', gender='FEMALE', blood='A+', marital='SINGLE', nationality='South Sudanese',
         religion='Christian', personal='grace.nyakim96@gmail.com', work='grace.nyakim@nilepet.com',
         pphone='+211921000103', wphone=None, hire='2026-09-01', probation_end='2026-12-01',
         confirmation=None, status='PROBATION', type='FULL_TIME', notes='Joined recently; probation review due in December.'),
    dict(first=('Mohamed', 'محمد'), middle=('Omer', 'عمر'), fourth=('Idris', 'إدريس'), last=('Salih', 'صالح'),
         dob='1985-01-30', pob='أم درمان', gender='MALE', blood='AB+', marital='DIVORCED', nationality='Sudanese',
         religion='Muslim', personal='mohamed.salih85@gmail.com', work='mohamed.salih@nilepet.com',
         pphone='+249912345604', wphone='+249912345614', hire='2023-03-10', probation_end='2023-09-10',
         confirmation='2023-09-10', status='ACTIVE', type='CONTRACT', notes='Fixed-term contract, renewed every year.'),
    dict(first=('Rebecca', 'ريبيكا'), middle=None, fourth=None, last=('Mabior', 'مابيور'),
         dob='2001-12-09', pob='Wau', gender='FEMALE', blood='O-', marital='SINGLE', nationality='South Sudanese',
         religion='Christian', personal='rebecca.mabior01@gmail.com', work='rebecca.mabior@nilepet.com',
         pphone='+211921000105', wphone=None, hire='2026-10-01', probation_end='2027-01-01',
         confirmation=None, status='PROBATION', type='INTERN', notes=None),
]

BS = chr(92)


def ar(text):
    """An NVARCHAR literal that survives any client encoding."""
    return "UNISTR('" + ''.join('%s%04X' % (BS, ord(c)) for c in text) + "')"


def lit(text):
    return 'NULL' if text is None else "'" + text.replace("'", "''") + "'"


def nlit(text):
    """Arabic or Latin free text in an NVARCHAR column."""
    if text is None:
        return 'NULL'
    return ar(text) if any(ord(c) > 127 for c in text) else "'" + text.replace("'", "''") + "'"


def date(text):
    return 'NULL' if text is None else "DATE '%s'" % text


def name_part(person, key):
    part = person.get(key)
    return (lit(part[0]), ar(part[1])) if part else ('NULL', 'NULL')


def build():
    pw_hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode()
    out = [
        '-- SAMPLE DATA: five employees as the Create Employee form stores them (personal, contact and employment details) and a login',
        '-- for each. DEVELOPMENT DATABASES ONLY: every login has the same temporary password and must change it at first sign-in.',
        '-- Run AFTER V17 (place of birth, notes) as the HRMS user. Safe to run twice: a person whose work e-mail exists is skipped.',
        '-- No job record is created, so the Job Details tab starts empty: fill it in through the guided tab flow.',
        '-- Written by tools/generate_sample_employees.py. Undo: db/seed_sample_employees_rollback.sql',
        '--   Logins: username = the part of the work e-mail before @, password = %s (change required at first sign-in).' % PASSWORD,
        '',
    ]
    for p in PEOPLE:
        mid_en, mid_ar = name_part(p, 'middle')
        fou_en, fou_ar = name_part(p, 'fourth')
        out += [
            '-- %s %s' % (p['first'][0], p['last'][0]),
            'INSERT INTO HRMS.EMPLOYEES (EMPLOYEE_ID, EMPLOYEE_CODE, FIRST_NAME, FIRST_NAME_AR, MIDDLE_NAME, MIDDLE_NAME_AR, FOURTH_NAME,',
            '    FOURTH_NAME_AR, LAST_NAME, LAST_NAME_AR, DATE_OF_BIRTH, PLACE_OF_BIRTH, GENDER, BLOOD_GROUP, MARITAL_STATUS, NATIONALITY,',
            '    RELIGION, PERSONAL_EMAIL, WORK_EMAIL, PERSONAL_PHONE, WORK_PHONE, HIRE_DATE, PROBATION_END_DATE, CONFIRMATION_DATE,',
            '    EMPLOYMENT_STATUS, EMPLOYMENT_TYPE, NOTES, CREATED_BY)',
            "SELECT HRMS.SEQ_EMPLOYEES.NEXTVAL, 'EMP-2026-' || LPAD(HRMS.SEQ_EMPLOYEES.NEXTVAL, 4, '0'), %s, %s, %s, %s, %s, %s, %s, %s,"
            % (lit(p['first'][0]), ar(p['first'][1]), mid_en, mid_ar, fou_en, fou_ar, lit(p['last'][0]), ar(p['last'][1])),
            '    %s, %s, %s, %s, %s, %s, %s,' % (date(p['dob']), nlit(p['pob']), lit(p['gender']), lit(p['blood']), lit(p['marital']),
                                                  lit(p['nationality']), lit(p['religion'])),
            '    %s, %s, %s, %s, %s, %s, %s,' % (lit(p['personal']), lit(p['work']), lit(p['pphone']), lit(p['wphone']),
                                                  date(p['hire']), date(p['probation_end']), date(p['confirmation'])),
            '    %s, %s, %s, %s' % (lit(p['status']), lit(p['type']), nlit(p['notes']), lit(CREATED_BY)),
            '  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM HRMS.EMPLOYEES WHERE WORK_EMAIL = %s);' % lit(p['work']),
        ]
    out += ['-- Expect: 5 rows inserted (0 on a second run).', '']
    for p in PEOPLE:
        username = p['work'].split('@')[0]
        full = '%s %s' % (p['first'][0], p['last'][0])
        out += [
            'INSERT INTO HRMS.AUTH_USERS (USER_ID, USERNAME, EMAIL, FULL_NAME_EN, PASSWORD_HASH, ROLE, EMPLOYEE_ID, EMPLOYEE_CODE,',
            '    FAILED_ATTEMPTS, IS_ACTIVE, IS_LOCKED, MUST_CHANGE_PASSWORD, CREATED_BY)',
            "SELECT HRMS.AUTH_USER_SEQ.NEXTVAL, %s, %s, %s, '%s', 'EMPLOYEE', e.EMPLOYEE_ID, e.EMPLOYEE_CODE, 0, TRUE, FALSE, 1, %s"
            % (lit(username), lit(p['work']), lit(full), pw_hash, lit(CREATED_BY)),
            '  FROM HRMS.EMPLOYEES e',
            ' WHERE e.WORK_EMAIL = %s' % lit(p['work']),
            '   AND NOT EXISTS (SELECT 1 FROM HRMS.AUTH_USERS u WHERE u.EMPLOYEE_ID = e.EMPLOYEE_ID OR u.USERNAME = %s);' % lit(username),
        ]
    out += ['-- Expect: 5 rows inserted (0 on a second run).', '', 'COMMIT;', '']
    return '\n'.join(out)


ROLLBACK = '''-- UNDO of db/seed_sample_employees.sql: removes the five sample employees and their logins (created by SAMPLE5).
-- Run as the HRMS user. If an employee already has other data (job record, addresses, documents, leave...), Oracle refuses the
-- delete (ORA-02292): remove or move that data first. Order: logins before employees.
DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = 'SAMPLE5';
DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SAMPLE5';
COMMIT;
'''

if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, 'db', 'seed_sample_employees.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(build())
    with open(os.path.join(root, 'db', 'seed_sample_employees_rollback.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(ROLLBACK)
    print('written: db/seed_sample_employees.sql, db/seed_sample_employees_rollback.sql')
