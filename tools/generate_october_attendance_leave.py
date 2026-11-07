"""Writes db/seed_october_attendance_leave.sql and its rollback: October 2026 attendance (check-in/check-out rows) and leave for
the 22 sample employees made by db/seed_indian_employees.sql, for development databases only.

Run from the repo root:  python tools/generate_october_attendance_leave.py   (the same random seed always gives the same data)

What it writes
  attendance  one row per working day (Mon-Fri, the shift's working days) the employee was at work: mostly PRESENT, some LATE
              (checked in after the 60-minute grace), a few HALF_DAY, a few days with no row at all (absent), and three Saturdays
              worked. Minutes follow AttendanceCalculator: working = out - in, overtime = minutes beyond the 8 h shift, late =
              minutes after 08:00.
  not written  WEEKEND / HOLIDAY / ON_LEAVE / ABSENT rows and the monthly summary: run "Regenerate day records" (Attendance
              Summary, HR_ADMIN) for October once, which writes them from the leave below.
  leave       APPROVED / REJECTED / CANCELLED requests in October and two PENDING ones in November, plus the 2026 balances
              (ANNUAL 21, SICK 15, CASUAL 6) with used and pending days that match the requests.
"""
import os
import random
from datetime import date, datetime, timedelta

CREATED_BY = 'SEEDATT'
EMAILS = """suresh.iyer priya.nair arjun.reddy kavita.deshmukh rohan.gupta ananya.banerjee imran.sheikh neha.kulkarni
karthik.subramanian simran.kaur ramesh.venkataraman deepa.menon sanjay.patel meera.joshi amit.verma pooja.agarwal faisal.ahmed
lakshmi.narayanan vijay.chauhan sneha.pillai anil.mehta vikram.rathore""".split()
# The 8 employees that existed before the Indian-names seed. start/grace/hours are their October shift (None = no shift on record:
# never late, no overtime). venkat's current shift (12:00) only began on 2026-11-03; in October it was the morning shift.
EXTRA_EMAILS = ['admin@hrms.com', 'konatam.eswarrao@nilepet.com', 'konatam.jaya@nilepet.com', 'amira.khalil@nilepet.com',
                'deng.garang@nilepet.com', 'grace.nyakim@nilepet.com', 'mohamed.salih@nilepet.com', 'rebecca.mabior@nilepet.com']
ALL_EMAILS = EMAILS + EXTRA_EMAILS
SHIFT_5 = (480, 60, 480)      # start minute of day, grace minutes, expected working minutes
SHIFT_1 = (450, 15, 450)
PROFILES = {'admin@hrms.com': None, 'konatam.eswarrao@nilepet.com': SHIFT_1, 'konatam.jaya@nilepet.com': SHIFT_1,
            'rebecca.mabior@nilepet.com': None}
USERNAMES = {'admin@hrms.com': 'admin', 'konatam.eswarrao@nilepet.com': 'venkat', 'konatam.jaya@nilepet.com': 'jaya'}
DEFAULT_DAYS = {'ANNUAL': 21, 'SICK': 15, 'CASUAL': 6}

# email, type, start, end, status, reason, decided by (username)
LEAVES = [
    ('suresh.iyer', 'ANNUAL', '2026-10-12', '2026-10-14', 'APPROVED', 'Family function in Chennai', 'admin'),
    ('priya.nair', 'SICK', '2026-10-06', '2026-10-07', 'APPROVED', 'Viral fever', 'admin'),
    ('arjun.reddy', 'CASUAL', '2026-10-20', '2026-10-20', 'APPROVED', 'Personal work', 'admin'),
    ('kavita.deshmukh', 'ANNUAL', '2026-10-26', '2026-10-30', 'APPROVED', 'Travel to Pune', 'admin'),
    ('rohan.gupta', 'SICK', '2026-10-15', '2026-10-15', 'APPROVED', 'Dental treatment', 'admin'),
    ('ananya.banerjee', 'ANNUAL', '2026-10-19', '2026-10-21', 'REJECTED', 'Short trip', 'admin'),
    ('imran.sheikh', 'CASUAL', '2026-10-09', '2026-10-09', 'APPROVED', 'Bank and passport work', 'admin'),
    ('neha.kulkarni', 'ANNUAL', '2026-10-05', '2026-10-09', 'APPROVED', 'Annual vacation', 'admin'),
    ('ramesh.venkataraman', 'ANNUAL', '2026-10-22', '2026-10-23', 'APPROVED', 'Home visit', 'admin'),
    ('deepa.menon', 'SICK', '2026-10-01', '2026-10-02', 'APPROVED', 'Migraine', 'admin'),
    ('sanjay.patel', 'CASUAL', '2026-10-13', '2026-10-13', 'CANCELLED', 'Plans changed', None),
    ('meera.joshi', 'ANNUAL', '2026-10-14', '2026-10-16', 'APPROVED', 'Festival holidays', 'admin'),
    ('amit.verma', 'ANNUAL', '2026-11-16', '2026-11-18', 'PENDING', 'Wedding in the family', None),
    ('pooja.agarwal', 'SICK', '2026-10-27', '2026-10-27', 'APPROVED', 'Stomach infection', 'admin'),
    ('faisal.ahmed', 'ANNUAL', '2026-11-23', '2026-11-24', 'PENDING', 'Personal travel', None),
    ('lakshmi.narayanan', 'CASUAL', '2026-10-30', '2026-10-30', 'APPROVED', 'Family errand', 'admin'),
    ('anil.mehta', 'ANNUAL', '2026-10-19', '2026-10-23', 'APPROVED', 'Annual vacation', 'vikram.rathore'),
    ('vikram.rathore', 'CASUAL', '2026-10-16', '2026-10-16', 'APPROVED', 'Personal work', 'admin'),
    ('sneha.pillai', 'ANNUAL', '2026-10-26', '2026-10-28', 'APPROVED', 'Visit to Kochi', 'admin'),
    ('konatam.eswarrao@nilepet.com', 'ANNUAL', '2026-10-12', '2026-10-14', 'APPROVED', 'Family function', 'admin'),
    ('konatam.jaya@nilepet.com', 'SICK', '2026-10-08', '2026-10-08', 'APPROVED', 'Cold and fever', 'admin'),
    ('amira.khalil@nilepet.com', 'ANNUAL', '2026-10-19', '2026-10-23', 'APPROVED', 'Annual vacation', 'admin'),
    ('deng.garang@nilepet.com', 'CASUAL', '2026-10-28', '2026-10-28', 'APPROVED', 'Personal work', 'admin'),
    ('grace.nyakim@nilepet.com', 'SICK', '2026-10-15', '2026-10-16', 'APPROVED', 'Throat infection', 'admin'),
    ('mohamed.salih@nilepet.com', 'ANNUAL', '2026-10-26', '2026-10-27', 'APPROVED', 'Home visit', 'admin'),
    ('admin@hrms.com', 'CASUAL', '2026-10-21', '2026-10-21', 'APPROVED', 'Personal work', 'anil.mehta'),
]
SATURDAYS_WORKED = [('rohan.gupta', '2026-10-10'), ('imran.sheikh', '2026-10-17'), ('amit.verma', '2026-10-24')]
ABSENT_DAYS = [('arjun.reddy', '2026-10-02'), ('simran.kaur', '2026-10-14'), ('vijay.chauhan', '2026-10-08'),
               ('faisal.ahmed', '2026-10-21'), ('karthik.subramanian', '2026-10-29'),
               ('mohamed.salih@nilepet.com', '2026-10-07'), ('amira.khalil@nilepet.com', '2026-10-02')]


def addr(email):
    return email if '@' in email else email + '@nilepet.com'


def d(text):
    return datetime.strptime(text, '%Y-%m-%d').date()


def working_days(start, end):
    n, day = 0, start
    while day <= end:
        n += day.weekday() < 5
        day += timedelta(days=1)
    return n


def ts(value):
    return "TIMESTAMP '%s'" % value.strftime('%Y-%m-%d %H:%M:%S')


def emp(email):
    return "(SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE WORK_EMAIL = '%s')" % addr(email)


def leave_days_set():
    days = {}
    for email, _t, s, e, status, _r, _b in LEAVES:
        if status == 'APPROVED':
            day = d(s)
            while day <= d(e):
                days.setdefault(email, set()).add(day)
                day += timedelta(days=1)
    return days


def log_sql(email, day, check_in, check_out, status, late, working, overtime, early, source):
    return '\n'.join([
        'INSERT INTO HRMS.ATTENDANCE_LOGS (LOG_ID, EMPLOYEE_ID, EMPLOYEE_CODE, ATTENDANCE_DATE, CHECK_IN_TIME, CHECK_OUT_TIME, WORKING_MINUTES,',
        '    OVERTIME_MINUTES, LATE_MINUTES, EARLY_LEAVE_MINUTES, STATUS, PUNCH_SOURCE, LOCATION_ID, IS_REGULARIZED, IS_ACTIVE, CREATED_BY,',
        '    CREATED_AT, UPDATED_BY, UPDATED_AT)',
        'SELECT HRMS.SEQ_ATTENDANCE_LOGS.NEXTVAL, e.EMPLOYEE_ID, e.EMPLOYEE_CODE, DATE \'%s\', %s, %s, %d, %d, %d, %d, \'%s\', \'%s\',' % (
            day, ts(check_in), ts(check_out), working, overtime, late, early, status, source),
        "    (SELECT LOCATION_ID FROM HRMS.OFFICE_LOCATIONS WHERE LOCATION_CODE = 'JUBA_HQ'), 0, 1, '%s', SYSTIMESTAMP, '%s', SYSTIMESTAMP" % (
            CREATED_BY, CREATED_BY),
        "  FROM HRMS.EMPLOYEES e WHERE e.WORK_EMAIL = '%s'" % addr(email),
        "   AND NOT EXISTS (SELECT 1 FROM HRMS.ATTENDANCE_LOGS x WHERE x.EMPLOYEE_ID = e.EMPLOYEE_ID AND x.ATTENDANCE_DATE = DATE '%s' AND x.IS_ACTIVE = 1);" % day,
    ])


def attendance(rng, emails, saturdays=()):
    out, on_leave, absent = [], leave_days_set(), {(e, d(x)) for e, x in ABSENT_DAYS}
    day = date(2026, 10, 1)
    while day <= date(2026, 10, 31):
        if day.weekday() < 5:
            midnight = datetime.combine(day, datetime.min.time())
            for email in emails:
                if day in on_leave.get(email, set()) or (email, day) in absent:
                    continue
                profile = PROFILES.get(email, SHIFT_5)
                roll = rng.random()
                source = rng.choices(['WEB', 'MOBILE', 'BIOMETRIC'], [8, 1, 1])[0]
                if profile is None:                   # no shift on record: never late, no overtime
                    check_in = midnight + timedelta(hours=8, minutes=rng.randint(0, 30))
                    working = 480 + rng.choice([0, 0, 10, 20])
                    out.append(log_sql(email, day, check_in, check_in + timedelta(minutes=working), 'PRESENT', 0, working, 0, 0, source))
                    continue
                start, grace, expected = profile
                if roll < 0.10:                       # late: after the grace period
                    late = rng.randint(grace + 5, grace + 75)
                    check_in = midnight + timedelta(minutes=start + late)
                    working = expected + rng.randint(0, 40)
                    status = 'LATE'
                elif roll < 0.13:                     # half day
                    late = 0
                    check_in = midnight + timedelta(minutes=start + rng.randint(0, 30))
                    working = rng.randint(190 * expected // 480, 230 * expected // 480)
                    status = 'HALF_DAY'
                else:
                    late = rng.randint(0, grace - 5) if rng.random() < 0.3 else 0
                    check_in = midnight + timedelta(minutes=start + late)
                    working = expected + rng.choice([0, 0, 5, 10, 20, 35, 45, 60])
                    status = 'PRESENT'
                check_out = check_in + timedelta(minutes=working)
                out.append(log_sql(email, day, check_in, check_out, status, late if status == 'LATE' else 0, working,
                                   max(working - expected, 0), max(expected - working, 0), source))
        day += timedelta(days=1)
    for email, text in saturdays:
        check_in = datetime.combine(d(text), datetime.min.time()) + timedelta(hours=9, minutes=5)
        working = 300 + rng.randint(0, 60)
        out.append(log_sql(email, d(text), check_in, check_in + timedelta(minutes=working), 'PRESENT', 0, working, working, 0, 'WEB'))
    return out


def leaves():
    out, balance = [], {}
    for email, ltype, s, e, status, reason, decider in LEAVES:
        days = working_days(d(s), d(e))
        submitted = datetime.combine(d(s), datetime.min.time()) - timedelta(days=9 if status != 'PENDING' else 12) + timedelta(hours=10)
        decided = submitted + timedelta(days=2, hours=3)
        rejection = "'Team is short-staffed that week; please choose another date.'" if status == 'REJECTED' else 'NULL'
        by = "'%s'" % decider if decider else 'NULL'
        at = ts(decided) if decider else 'NULL'
        out.append('\n'.join([
            'INSERT INTO HRMS.LEAVE_REQUESTS (LEAVE_REQ_ID, CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY, APPROVED_AT, APPROVED_BY, EMPLOYEE_CODE,',
            '    EMPLOYEE_ID, END_DATE, IS_ACTIVE, LEAVE_TYPE, REASON, REJECTION_REASON, START_DATE, STATUS, TOTAL_DAYS, VERSION)',
            "SELECT HRMS.LEAVE_REQ_SEQ.NEXTVAL, %s, '%s', %s, %s, %s, %s, e.EMPLOYEE_CODE, e.EMPLOYEE_ID, DATE '%s', TRUE, '%s', '%s', %s," % (
                ts(submitted), USERNAMES.get(email, email.split('@')[0]), ts(decided if decider else submitted), by, at, by, e, ltype, reason, rejection),
            "    DATE '%s', '%s', %d, 0" % (s, status, days),
            "  FROM HRMS.EMPLOYEES e WHERE e.WORK_EMAIL = '%s'" % addr(email),
            "   AND NOT EXISTS (SELECT 1 FROM HRMS.LEAVE_REQUESTS x WHERE x.EMPLOYEE_ID = e.EMPLOYEE_ID AND x.START_DATE = DATE '%s' AND x.END_DATE = DATE '%s' AND x.LEAVE_TYPE = '%s');" % (s, e, ltype),
        ]))
        key = (email, ltype)
        used, pending = balance.get(key, (0, 0))
        if status == 'APPROVED':
            used += days
        elif status == 'PENDING':
            pending += days
        balance[key] = (used, pending)
    for email in ALL_EMAILS:
        for ltype, total in DEFAULT_DAYS.items():
            used, pending = balance.get((email, ltype), (0, 0))
            out.append('\n'.join([
                'INSERT INTO HRMS.LEAVE_BALANCES (BALANCE_ID, CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY, EMPLOYEE_ID, LEAVE_TYPE, PENDING_DAYS, TOTAL_DAYS, USED_DAYS, YEAR, VERSION)',
                "SELECT HRMS.LEAVE_BAL_SEQ.NEXTVAL, SYSTIMESTAMP, '%s', SYSTIMESTAMP, '%s', e.EMPLOYEE_ID, '%s', %d, %d, %d, 2026, 0" % (
                    CREATED_BY, CREATED_BY, ltype, pending, total, used),
                "  FROM HRMS.EMPLOYEES e WHERE e.WORK_EMAIL = '%s'" % addr(email),
                "   AND NOT EXISTS (SELECT 1 FROM HRMS.LEAVE_BALANCES x WHERE x.EMPLOYEE_ID = e.EMPLOYEE_ID AND x.LEAVE_TYPE = '%s' AND x.YEAR = 2026);" % ltype,
            ]))
    return out


def build():
    rng = random.Random(2026)
    head = [
        '-- SAMPLE DATA: October 2026 attendance and leave for the 22 employees made by db/seed_indian_employees.sql and the 8 that existed before.',
        '-- DEVELOPMENT DATABASES ONLY. Safe to run twice: a row that exists is skipped. Written by tools/generate_october_attendance_leave.py.',
        "-- After it: open Attendance Summary as HR_ADMIN and run 'Regenerate day records' for October. That writes the weekend, leave and",
        '-- absent days and the monthly summary. Undo: db/seed_october_attendance_leave_rollback.sql',
        '',
    ]
    return '\n'.join(head + attendance(rng, EMAILS, SATURDAYS_WORKED) + attendance(random.Random(2027), EXTRA_EMAILS) + [''] + leaves() + ['', 'COMMIT;', ''])


def rollback():
    people = ', '.join("'%s'" % addr(e) for e in ALL_EMAILS)
    ids = '(SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE WORK_EMAIL IN (%s))' % people
    return '\n'.join([
        '-- UNDO of db/seed_october_attendance_leave.sql: the October 2026 attendance rows (seeded and generated by Regenerate day',
        '-- records), the monthly summary, the leave requests and the 2026 balances of the 22 sample employees.',
        "DELETE FROM HRMS.ATTENDANCE_LOGS WHERE EMPLOYEE_ID IN %s AND ATTENDANCE_DATE BETWEEN DATE '2026-10-01' AND DATE '2026-10-31';" % ids,
        'DELETE FROM HRMS.ATTENDANCE_SUMMARY WHERE EMPLOYEE_ID IN %s AND SUMMARY_YEAR = 2026 AND SUMMARY_MONTH = 10;' % ids,
        'DELETE FROM HRMS.LEAVE_REQUESTS WHERE EMPLOYEE_ID IN %s;' % ids,
        'DELETE FROM HRMS.LEAVE_BALANCES WHERE EMPLOYEE_ID IN %s AND YEAR = 2026;' % ids,
        'COMMIT;',
        '',
    ])


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, 'db', 'seed_october_attendance_leave.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(build())
    with open(os.path.join(root, 'db', 'seed_october_attendance_leave_rollback.sql'), 'w', encoding='utf8', newline='\n') as f:
        f.write(rollback())
    print('written')
