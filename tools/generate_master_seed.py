"""Generates db/seed_masters_reference.sql and its rollback from the lists below.

Run from anywhere: python tools/generate_master_seed.py. Output is overwritten, so change the lists HERE.
Arabic is written as UNISTR escapes so the script loads correctly whatever the client encoding is; the Arabic
text is shown in a comment after each statement block. Rows are skipped when their code already exists, so the
script is safe to run twice. Grades are not here: they have their own script (db/seed_grades_uhrpm.sql)."""
import pathlib

DB = pathlib.Path(__file__).resolve().parent.parent / "db"

# (table, id column, code column prefix, parent column, parent table, parent code column) per master
# rows: (code, English name, Arabic name[, parent code])
REGIONS = [
    ("EQU", "Greater Equatoria", "إكوتوريا الكبرى"),
    ("BEG", "Greater Bahr el Ghazal", "بحر الغزال الكبرى"),
    ("UNL", "Greater Upper Nile", "أعالي النيل الكبرى"),
]
# The ten states plus the three administrative areas (UHRPM era boundaries, 2020).
WILAYAS = [
    ("CES", "Central Equatoria", "وسط الاستوائية", "EQU"),
    ("EES", "Eastern Equatoria", "شرق الاستوائية", "EQU"),
    ("WES", "Western Equatoria", "غرب الاستوائية", "EQU"),
    ("NBG", "Northern Bahr el Ghazal", "شمال بحر الغزال", "BEG"),
    ("WBG", "Western Bahr el Ghazal", "غرب بحر الغزال", "BEG"),
    ("LAK", "Lakes", "البحيرات", "BEG"),
    ("WAR", "Warrap", "واراب", "BEG"),
    ("ABY", "Abyei Administrative Area", "منطقة أبيي الإدارية", "BEG"),
    ("JON", "Jonglei", "جونقلي", "UNL"),
    ("UNI", "Unity", "الوحدة", "UNL"),
    ("UPN", "Upper Nile", "أعالي النيل", "UNL"),
    ("RUW", "Ruweng Administrative Area", "منطقة روينق الإدارية", "UNL"),
    ("GPA", "Greater Pibor Administrative Area", "منطقة بيبور الكبرى الإدارية", "UNL"),
]
# Only the counties that hold a company branch; HR adds the rest in Locations > Provinces.
PROVINCES = [
    ("JUBA_COUNTY", "Juba County", "مقاطعة جوبا", "CES"),
    ("YEI_COUNTY", "Yei County", "مقاطعة ياي", "CES"),
    ("MAGWI_COUNTY", "Magwi County", "مقاطعة ماقوي", "EES"),
    ("WAU_COUNTY", "Wau County", "مقاطعة واو", "WBG"),
    ("RUMBEK_CENTRE", "Rumbek Centre County", "مقاطعة رمبيك الوسطى", "LAK"),
    ("BOR_SOUTH", "Bor South County", "مقاطعة بور الجنوبية", "JON"),
    ("MALAKAL_COUNTY", "Malakal County", "مقاطعة ملكال", "UPN"),
    ("RUBKONA_COUNTY", "Rubkona County", "مقاطعة روبكونا", "UNI"),
]
CITIES = [
    ("JUBA", "Juba", "جوبا", "JUBA_COUNTY"),
    ("YEI", "Yei", "ياي", "YEI_COUNTY"),
    ("NIMULE", "Nimule", "نمولي", "MAGWI_COUNTY"),
    ("WAU", "Wau", "واو", "WAU_COUNTY"),
    ("RUMBEK", "Rumbek", "رمبيك", "RUMBEK_CENTRE"),
    ("BOR", "Bor", "بور", "BOR_SOUTH"),
    ("MALAKAL", "Malakal", "ملكال", "MALAKAL_COUNTY"),
    ("BENTIU", "Bentiu", "بانتيو", "RUBKONA_COUNTY"),
]
RELIGIONS = [
    ("CHRISTIANITY", "Christianity", "المسيحية"),
    ("ISLAM", "Islam", "الإسلام"),
    ("AFRICAN_TRADITIONAL", "African traditional religion", "الديانات الأفريقية التقليدية"),
    ("OTHER", "Other", "أخرى"),
]
NATIONALITIES = [
    ("SSD", "South Sudanese", "جنوب سوداني"), ("SDN", "Sudanese", "سوداني"), ("UGA", "Ugandan", "أوغندي"),
    ("KEN", "Kenyan", "كيني"), ("ETH", "Ethiopian", "إثيوبي"), ("ERI", "Eritrean", "إريتري"),
    ("EGY", "Egyptian", "مصري"), ("COD", "Congolese (DRC)", "كونغولي"), ("TZA", "Tanzanian", "تنزاني"),
    ("RWA", "Rwandan", "رواندي"), ("BDI", "Burundian", "بوروندي"), ("SOM", "Somali", "صومالي"),
    ("NGA", "Nigerian", "نيجيري"), ("GHA", "Ghanaian", "غاني"), ("ZAF", "South African", "جنوب أفريقي"),
    ("ZWE", "Zimbabwean", "زيمبابوي"), ("IND", "Indian", "هندي"), ("PAK", "Pakistani", "باكستاني"),
    ("BGD", "Bangladeshi", "بنغلاديشي"), ("PHL", "Filipino", "فلبيني"), ("LBN", "Lebanese", "لبناني"),
    ("SYR", "Syrian", "سوري"), ("JOR", "Jordanian", "أردني"), ("YEM", "Yemeni", "يمني"),
    ("OMN", "Omani", "عماني"), ("GBR", "British", "بريطاني"), ("USA", "American", "أمريكي"),
    ("CAN", "Canadian", "كندي"), ("FRA", "French", "فرنسي"), ("DEU", "German", "ألماني"),
    ("CHN", "Chinese", "صيني"),
]
# The employment statuses named in the UHRPM (established, non-established, partner and company secondees, direct hire).
CONTRACT_TYPES = [
    ("PERMANENT", "Permanent (established)", "دائم (مثبّت)"),
    ("FIXED_TERM", "Fixed term", "محدد المدة"),
    ("TEMPORARY", "Temporary (non-established)", "مؤقت (غير مثبّت)"),
    ("PART_TIME", "Part time", "دوام جزئي"),
    ("PARTNER_SECONDEE", "Partner secondee", "منتدب من الشريك"),
    ("COMPANY_SECONDEE", "Company secondee", "منتدب من الشركة"),
    ("DIRECT_HIRE", "Direct hire", "تعيين مباشر"),
    ("CONSULTANT", "Consultant", "استشاري"),
    ("TRAINEE", "Trainee", "متدرب"),
]
PROFESSIONS = [
    ("ACCOUNTANT", "Accountant", "محاسب"), ("AUDITOR", "Auditor", "مدقق حسابات"),
    ("ENGINEER", "Engineer", "مهندس"), ("MECH_ENGINEER", "Mechanical engineer", "مهندس ميكانيكي"),
    ("ELECTRICAL_ENGINEER", "Electrical engineer", "مهندس كهربائي"), ("CIVIL_ENGINEER", "Civil engineer", "مهندس مدني"),
    ("GEOLOGIST", "Geologist", "جيولوجي"), ("CHEMIST", "Chemist", "كيميائي"), ("LAWYER", "Lawyer", "محامٍ"),
    ("HR_SPECIALIST", "HR specialist", "أخصائي موارد بشرية"),
    ("IT_SPECIALIST", "IT specialist", "أخصائي تقنية معلومات"),
    ("PROCUREMENT_OFFICER", "Procurement officer", "مسؤول مشتريات"),
    ("LOGISTICS_OFFICER", "Logistics officer", "مسؤول لوجستيات"), ("STORE_KEEPER", "Store keeper", "أمين مخزن"),
    ("DRIVER", "Driver", "سائق"), ("MECHANIC", "Mechanic", "ميكانيكي"), ("ELECTRICIAN", "Electrician", "كهربائي"),
    ("WELDER", "Welder", "لحّام"), ("TECHNICIAN", "Technician", "فني"),
    ("PUMP_ATTENDANT", "Fuel station attendant", "عامل محطة وقود"), ("CASHIER", "Cashier", "أمين صندوق"),
    ("SECURITY_GUARD", "Security guard", "حارس أمن"), ("CLERK", "Clerk", "موظف إداري"),
    ("NURSE", "Nurse", "ممرض"), ("DOCTOR", "Doctor", "طبيب"), ("TEACHER", "Teacher", "معلم"),
    ("SURVEYOR", "Surveyor", "مساح"), ("OTHER", "Other", "أخرى"),
]
EDUCATION_LEVELS = [
    ("PRIMARY", "Primary school", "ابتدائي"), ("SECONDARY", "Secondary school", "ثانوي"),
    ("VOCATIONAL", "Vocational training", "تدريب مهني"), ("DIPLOMA", "Diploma", "دبلوم"),
    ("BACHELOR", "Bachelor's degree", "بكالوريوس"), ("POSTGRAD_DIPLOMA", "Postgraduate diploma", "دبلوم عالٍ"),
    ("MASTER", "Master's degree", "ماجستير"), ("DOCTORATE", "Doctorate", "دكتوراه"),
]
LANGUAGES = [
    ("ENGLISH", "English", "الإنجليزية"), ("ARABIC", "Arabic", "العربية"), ("JUBA_ARABIC", "Juba Arabic", "عربي جوبا"),
    ("DINKA", "Dinka", "الدينكا"), ("NUER", "Nuer", "النوير"), ("BARI", "Bari", "الباري"),
    ("ZANDE", "Zande", "الزاندي"), ("SHILLUK", "Shilluk", "الشلك"), ("MURLE", "Murle", "المورلي"),
    ("MADI", "Madi", "المادي"), ("LUO", "Luo", "اللو"), ("FRENCH", "French", "الفرنسية"),
    ("SWAHILI", "Swahili", "السواحيلية"), ("AMHARIC", "Amharic", "الأمهرية"),
]
LANGUAGE_LEVELS = [
    # The 4th value is SORT_ORDER: the dropdown lists by proficiency, not A-Z (V9 adds the column).
    ("BASIC", "Basic", "أساسي", 10), ("INTERMEDIATE", "Intermediate", "متوسط", 20),
    ("ADVANCED", "Advanced", "متقدم", 30), ("FLUENT", "Fluent", "طليق", 40), ("NATIVE", "Native", "لغة أم", 50),
]
HOUSE_TYPES = [
    ("OWNED", "Owned", "ملك"), ("RENTED", "Rented", "مستأجر"), ("COMPANY_HOUSING", "Company housing", "سكن الشركة"),
    ("FAMILY_HOME", "Family home", "منزل العائلة"), ("SHARED", "Shared housing", "سكن مشترك"),
]
RELATION_TYPES = [
    ("IMMEDIATE", "Immediate family", "الأسرة المباشرة"),
    ("EXTENDED", "Extended family", "الأسرة الممتدة"),
]
RELATIONS = [
    ("SPOUSE", "Spouse", "الزوج / الزوجة", "IMMEDIATE"), ("SON", "Son", "ابن", "IMMEDIATE"),
    ("DAUGHTER", "Daughter", "ابنة", "IMMEDIATE"), ("FATHER", "Father", "أب", "IMMEDIATE"),
    ("MOTHER", "Mother", "أم", "IMMEDIATE"),
    ("BROTHER", "Brother", "أخ", "EXTENDED"), ("SISTER", "Sister", "أخت", "EXTENDED"),
    ("GRANDFATHER", "Grandfather", "جد", "EXTENDED"), ("GRANDMOTHER", "Grandmother", "جدة", "EXTENDED"),
    ("UNCLE", "Uncle", "عم / خال", "EXTENDED"), ("AUNT", "Aunt", "عمة / خالة", "EXTENDED"),
    ("GUARDIAN", "Guardian", "ولي أمر", "EXTENDED"),
]

# table, id column, code prefix (= table), rows, parent=(column, table, parent code prefix) or None. Order = load order.
MASTERS = [
    ("REGIONS", "REGION_ID", REGIONS, None),
    ("WILAYAS", "WILAYA_ID", WILAYAS, ("REGION_ID", "REGIONS")),
    ("PROVINCES", "PROVINCE_ID", PROVINCES, ("WILAYA_ID", "WILAYAS")),
    ("CITIES", "CITY_ID", CITIES, ("PROVINCE_ID", "PROVINCES")),
    ("RELIGIONS", "RELIGION_ID", RELIGIONS, None),
    ("NATIONALITIES", "NATIONALITY_ID", NATIONALITIES, None),
    ("CONTRACT_TYPES", "CONTRACT_TYPE_ID", CONTRACT_TYPES, None),
    ("PROFESSIONS", "PROFESSION_ID", PROFESSIONS, None),
    ("EDUCATION_LEVELS", "EDUCATION_LEVEL_ID", EDUCATION_LEVELS, None),
    ("LANGUAGES", "LANGUAGE_ID", LANGUAGES, None),
    ("LANGUAGE_LEVELS", "LANGUAGE_LEVEL_ID", LANGUAGE_LEVELS, None),
    ("HOUSE_TYPES", "HOUSE_TYPE_ID", HOUSE_TYPES, None),
    ("RELATION_TYPES", "RELATION_TYPE_ID", RELATION_TYPES, None),
    ("RELATIONS", "RELATION_ID", RELATIONS, ("RELATION_TYPE_ID", "RELATION_TYPES")),
]


def unistr(text):
    return "UNISTR('" + "".join(f"\\{ord(c):04X}" if ord(c) > 127 else c for c in text) + "')"


def q(text):
    return "'" + text.replace("'", "''") + "'"


def statement(table, idcol, row, parent):
    code, name, name_ar = row[0], row[1], row[2]
    sort = row[3] if not parent and len(row) > 3 else None
    cols = (f"{idcol}, " + (f"{parent[0]}, " if parent else "") + f"{table}_CODE, {table}_NAME, {table}_NAME_AR, IS_ACTIVE"
            + (", SORT_ORDER" if sort is not None else ""))
    parent_value = (f"(SELECT {parent[0]} FROM HRMS.{parent[1]} WHERE {parent[1]}_CODE = {q(row[3])}), " if parent else "")
    return (f"INSERT INTO HRMS.{table} ({cols})\n"
            f"SELECT HRMS.SEQ_{table}.NEXTVAL, {parent_value}{q(code)}, {q(name)}, {unistr(name_ar)}, 1{'' if sort is None else ', ' + str(sort)} FROM DUAL\n"
            f" WHERE NOT EXISTS (SELECT 1 FROM HRMS.{table} WHERE {table}_CODE = {q(code)});   -- {name_ar}\n")


def seed():
    out = ["""-- REFERENCE DATA for the master screens (Locations, Employment Masters, personal masters), South Sudan.
-- GENERATED by tools/generate_master_seed.py: change the lists there, not here.
-- Data only, safe to run twice (a code that exists is skipped). Run as the HRMS user, after V5 and V6.
-- Everything is a starting list: SYSTEM_ADMIN adds, renames or switches off rows in the master screens.
-- Geography keeps the original table names: Region = the 3 greater regions, Wilaya = the 10 states plus the 3
-- administrative areas, Province = county, City = town. Only the counties and towns that hold a company branch
-- are loaded; add the rest in the screens. Directorates are not loaded (the real structure is not known yet), and
-- grades have their own script, db/seed_grades_uhrpm.sql.
-- Arabic text is written with UNISTR escapes so it loads correctly whatever the client encoding is; the Arabic is
-- shown in the comment after each statement.
-- Undo: db/seed_masters_reference_rollback.sql
"""]
    for table, idcol, rows, parent in MASTERS:
        out.append(f"-- {table}: {len(rows)} rows")
        out.extend(statement(table, idcol, r, parent) for r in rows)
    out.append("COMMIT;\n")
    return "\n".join(out)


def rollback():
    out = ["""-- UNDO of db/seed_masters_reference.sql: removes the rows it loads, by code. GENERATED by
-- tools/generate_master_seed.py. Run as the HRMS user. Oracle refuses (ORA-02292) a row that something else already
-- uses (a wilaya with provinces you added, an employee record that points at a nationality): remove or move those
-- first. Children are deleted before their parents.
"""]
    for table, _, rows, _ in reversed(MASTERS):
        codes = ", ".join(q(r[0]) for r in rows)
        out.append(f"DELETE FROM HRMS.{table}\n WHERE {table}_CODE IN ({codes});\n")
    out.append("COMMIT;\n")
    return "\n".join(out)


(DB / "seed_masters_reference.sql").write_text(seed(), encoding="utf-8", newline="\n")
(DB / "seed_masters_reference_rollback.sql").write_text(rollback(), encoding="utf-8", newline="\n")
print("ok:", sum(len(m[2]) for m in MASTERS), "rows in", len(MASTERS), "masters")
