"""Adds the texts of the six employee-information sections and the three new detail tabs to the Angular translation
files (en, fr, ar): employee.detail.tabs.{family,qualifications,experience} and the whole employee.info block.

Run from anywhere: python tools/generate_employee_info_texts.py (then Prettier on the three files). It only adds or
overwrites those keys, so it can be run again after an edit. French and Arabic are written by hand here and need a
native read before go-live."""
import json
import pathlib

I18N = pathlib.Path(r"C:\angular-workspace\projects\ehrms-v2\public\i18n")

T = {
    "en": {
        "tabs": {"family": "Family", "qualifications": "Qualifications", "experience": "Experience"},
        "common": {
            "activeCount": "{{count}} active", "inactiveCount": "{{count}} deactivated",
            "loading": "Loading…", "retry": "Retry", "chooseOne": "Select…", "saving": "Saving…",
            "update": "Update", "deactivate": "Deactivate", "activate": "Activate", "present": "present",
            "born": "Born {{date}}", "deceased": "Deceased",
            "read": "Reading", "write": "Writing", "speak": "Speaking",
            "gender": {"MALE": "Male", "FEMALE": "Female", "OTHER": "Other", "PREFER_NOT_TO_SAY": "Prefer not to say"},
            "success": {"added": "The record was added.", "updated": "The record was updated.",
                        "deactivated": "The record was deactivated.", "activated": "The record was activated."},
            "confirm": {"deactivateTitle": "Deactivate this record?",
                        "deactivateHint": "It stays on file and can be activated again later.",
                        "activateTitle": "Activate this record?",
                        "activateHint": "It will show in the active list again."},
            "errors": {
                "stale": "Someone else changed this record while you were editing. Close this window, reload and try again.",
                "loadFailed": "Could not load the records. Check your connection and try again.",
                "future": "The date cannot be in the future.",
                "phone": "Phone may contain only digits, + ( ) - and spaces.",
                "dateRange": "The end date cannot be before the start date.",
                "rules": {
                    "RELATION_INACTIVE": "This relation is no longer active. Choose another one.",
                    "EDUCATION_LEVEL_INACTIVE": "This education level is no longer active. Choose another one.",
                    "LANGUAGE_INACTIVE": "This language is no longer active. Choose another one.",
                    "LANGUAGE_LEVEL_INACTIVE": "This language level is no longer active. Choose another one.",
                    "INVALID_DATE_RANGE": "The end date cannot be before the start date.",
                    "DUPLICATE_LANGUAGE": "This language is already recorded for the employee. If it was deactivated, activate it instead.",
                    "RECORD_INACTIVE": "A deactivated record cannot be edited. Activate it first.",
                },
            },
        },
        "fields": {
            "relationId": "Relation", "firstName": "First name", "middleName": "Middle name", "lastName": "Last name",
            "firstNameAr": "First name (Arabic)", "middleNameAr": "Middle name (Arabic)", "lastNameAr": "Last name (Arabic)",
            "gender": "Gender", "dateOfBirth": "Date of birth", "birthPlace": "Place of birth", "isAlive": "Is alive",
            "phone": "Phone", "address": "Address", "educationLevelId": "Education level", "qualification": "Qualification",
            "qualificationAr": "Qualification (Arabic)", "institution": "Institution", "startDate": "Start date",
            "endDate": "End date", "courseName": "Course", "courseNameAr": "Course (Arabic)", "provider": "Provider",
            "languageId": "Language", "readLevelId": "Reading level", "writeLevelId": "Writing level",
            "speakLevelId": "Speaking level", "employerName": "Employer", "employerNameAr": "Employer (Arabic)",
            "jobTitle": "Job title", "place": "Place", "email": "E-mail",
        },
        "sections": {
            "dependants": ("Dependants", "Add dependant", "Edit dependant", "No dependants recorded",
                           "Add the spouse, the children or other dependants."),
            "education": ("Education", "Add education", "Edit education", "No education recorded",
                          "Add a degree, diploma or other qualification."),
            "training": ("Training", "Add training", "Edit training", "No training recorded",
                         "Add a course or training programme."),
            "languages": ("Languages", "Add language", "Edit language", "No languages recorded",
                          "Add the languages the employee knows and how well."),
            "experience": ("Work experience", "Add experience", "Edit experience", "No work experience recorded",
                           "Add earlier employers."),
            "references": ("References", "Add reference", "Edit reference", "No references recorded",
                           "Add people who can vouch for the employee."),
        },
    },
    "fr": {
        "tabs": {"family": "Famille", "qualifications": "Qualifications", "experience": "Expérience"},
        "common": {
            "activeCount": "{{count}} actifs", "inactiveCount": "{{count}} désactivés",
            "loading": "Chargement…", "retry": "Réessayer", "chooseOne": "Sélectionner…", "saving": "Enregistrement…",
            "update": "Mettre à jour", "deactivate": "Désactiver", "activate": "Activer", "present": "aujourd’hui",
            "born": "Né(e) le {{date}}", "deceased": "Décédé(e)",
            "read": "Lecture", "write": "Écriture", "speak": "Oral",
            "gender": {"MALE": "Homme", "FEMALE": "Femme", "OTHER": "Autre", "PREFER_NOT_TO_SAY": "Préfère ne pas répondre"},
            "success": {"added": "L’enregistrement a été ajouté.", "updated": "L’enregistrement a été mis à jour.",
                        "deactivated": "L’enregistrement a été désactivé.", "activated": "L’enregistrement a été activé."},
            "confirm": {"deactivateTitle": "Désactiver cet enregistrement ?",
                        "deactivateHint": "Il reste au dossier et pourra être réactivé plus tard.",
                        "activateTitle": "Activer cet enregistrement ?",
                        "activateHint": "Il réapparaîtra dans la liste des enregistrements actifs."},
            "errors": {
                "stale": "Quelqu’un d’autre a modifié cet enregistrement pendant votre édition. Fermez cette fenêtre, rechargez et réessayez.",
                "loadFailed": "Impossible de charger les enregistrements. Vérifiez votre connexion et réessayez.",
                "future": "La date ne peut pas être dans le futur.",
                "phone": "Le téléphone ne peut contenir que des chiffres, + ( ) - et des espaces.",
                "dateRange": "La date de fin ne peut pas précéder la date de début.",
                "rules": {
                    "RELATION_INACTIVE": "Ce lien de parenté n’est plus actif. Choisissez-en un autre.",
                    "EDUCATION_LEVEL_INACTIVE": "Ce niveau d’études n’est plus actif. Choisissez-en un autre.",
                    "LANGUAGE_INACTIVE": "Cette langue n’est plus active. Choisissez-en une autre.",
                    "LANGUAGE_LEVEL_INACTIVE": "Ce niveau de langue n’est plus actif. Choisissez-en un autre.",
                    "INVALID_DATE_RANGE": "La date de fin ne peut pas précéder la date de début.",
                    "DUPLICATE_LANGUAGE": "Cette langue est déjà enregistrée pour l’employé. Si elle a été désactivée, activez-la plutôt.",
                    "RECORD_INACTIVE": "Un enregistrement désactivé ne peut pas être modifié. Activez-le d’abord.",
                },
            },
        },
        "fields": {
            "relationId": "Lien de parenté", "firstName": "Prénom", "middleName": "Deuxième prénom", "lastName": "Nom de famille",
            "firstNameAr": "Prénom (arabe)", "middleNameAr": "Deuxième prénom (arabe)", "lastNameAr": "Nom de famille (arabe)",
            "gender": "Sexe", "dateOfBirth": "Date de naissance", "birthPlace": "Lieu de naissance", "isAlive": "En vie",
            "phone": "Téléphone", "address": "Adresse", "educationLevelId": "Niveau d’études", "qualification": "Diplôme ou qualification",
            "qualificationAr": "Diplôme ou qualification (arabe)", "institution": "Établissement", "startDate": "Date de début",
            "endDate": "Date de fin", "courseName": "Formation", "courseNameAr": "Formation (arabe)", "provider": "Organisme",
            "languageId": "Langue", "readLevelId": "Niveau de lecture", "writeLevelId": "Niveau d’écriture",
            "speakLevelId": "Niveau d’expression orale", "employerName": "Employeur", "employerNameAr": "Employeur (arabe)",
            "jobTitle": "Intitulé du poste", "place": "Lieu", "email": "E-mail",
        },
        "sections": {
            "dependants": ("Personnes à charge", "Ajouter une personne à charge", "Modifier la personne à charge",
                           "Aucune personne à charge enregistrée", "Ajoutez le conjoint, les enfants ou d’autres personnes à charge."),
            "education": ("Études et diplômes", "Ajouter une formation initiale", "Modifier la formation initiale",
                          "Aucune formation initiale enregistrée", "Ajoutez un diplôme ou une autre qualification."),
            "training": ("Formations professionnelles", "Ajouter une formation professionnelle",
                         "Modifier la formation professionnelle", "Aucune formation professionnelle enregistrée",
                         "Ajoutez un cours ou un programme de formation."),
            "languages": ("Langues", "Ajouter une langue", "Modifier la langue", "Aucune langue enregistrée",
                          "Ajoutez les langues que l’employé connaît et leur niveau."),
            "experience": ("Expérience professionnelle", "Ajouter une expérience", "Modifier l’expérience",
                           "Aucune expérience professionnelle enregistrée", "Ajoutez les employeurs précédents."),
            "references": ("Références", "Ajouter une référence", "Modifier la référence", "Aucune référence enregistrée",
                           "Ajoutez des personnes qui peuvent se porter garantes de l’employé."),
        },
    },
    "ar": {
        "tabs": {"family": "العائلة", "qualifications": "المؤهلات", "experience": "الخبرات"},
        "common": {
            "activeCount": "{{count}} نشط", "inactiveCount": "{{count}} معطّل",
            "loading": "جارٍ التحميل…", "retry": "إعادة المحاولة", "chooseOne": "اختر…", "saving": "جارٍ الحفظ…",
            "update": "تحديث", "deactivate": "تعطيل", "activate": "تفعيل", "present": "حتى الآن",
            "born": "وُلد في {{date}}", "deceased": "متوفى",
            "read": "القراءة", "write": "الكتابة", "speak": "المحادثة",
            "gender": {"MALE": "ذكر", "FEMALE": "أنثى", "OTHER": "آخر", "PREFER_NOT_TO_SAY": "أفضّل عدم الإفصاح"},
            "success": {"added": "تمت إضافة السجل.", "updated": "تم تحديث السجل.",
                        "deactivated": "تم تعطيل السجل.", "activated": "تم تفعيل السجل."},
            "confirm": {"deactivateTitle": "تعطيل هذا السجل؟",
                        "deactivateHint": "يبقى السجل في الملف ويمكن تفعيله مرة أخرى لاحقًا.",
                        "activateTitle": "تفعيل هذا السجل؟",
                        "activateHint": "سيظهر مرة أخرى في قائمة السجلات النشطة."},
            "errors": {
                "stale": "قام شخص آخر بتعديل هذا السجل أثناء تعديلك. أغلق هذه النافذة وأعد التحميل ثم حاول مرة أخرى.",
                "loadFailed": "تعذّر تحميل السجلات. تحقّق من الاتصال وأعد المحاولة.",
                "future": "لا يمكن أن يكون التاريخ في المستقبل.",
                "phone": "يجب أن يحتوي الهاتف على أرقام و + ( ) - ومسافات فقط.",
                "dateRange": "لا يمكن أن يسبق تاريخ الانتهاء تاريخ البدء.",
                "rules": {
                    "RELATION_INACTIVE": "صلة القرابة هذه لم تعد نشطة. اختر صلة أخرى.",
                    "EDUCATION_LEVEL_INACTIVE": "هذا المستوى التعليمي لم يعد نشطًا. اختر مستوى آخر.",
                    "LANGUAGE_INACTIVE": "هذه اللغة لم تعد نشطة. اختر لغة أخرى.",
                    "LANGUAGE_LEVEL_INACTIVE": "مستوى اللغة هذا لم يعد نشطًا. اختر مستوى آخر.",
                    "INVALID_DATE_RANGE": "لا يمكن أن يسبق تاريخ الانتهاء تاريخ البدء.",
                    "DUPLICATE_LANGUAGE": "هذه اللغة مسجلة بالفعل للموظف. إذا كانت معطّلة ففعّلها بدلًا من ذلك.",
                    "RECORD_INACTIVE": "لا يمكن تعديل سجل معطّل. فعّله أولًا.",
                },
            },
        },
        "fields": {
            "relationId": "صلة القرابة", "firstName": "الاسم الأول", "middleName": "الاسم الأوسط", "lastName": "اسم العائلة",
            "firstNameAr": "الاسم الأول (بالعربية)", "middleNameAr": "الاسم الأوسط (بالعربية)", "lastNameAr": "اسم العائلة (بالعربية)",
            "gender": "الجنس", "dateOfBirth": "تاريخ الميلاد", "birthPlace": "مكان الميلاد", "isAlive": "على قيد الحياة",
            "phone": "الهاتف", "address": "العنوان", "educationLevelId": "المستوى التعليمي", "qualification": "المؤهل",
            "qualificationAr": "المؤهل (بالعربية)", "institution": "المؤسسة التعليمية", "startDate": "تاريخ البدء",
            "endDate": "تاريخ الانتهاء", "courseName": "اسم الدورة", "courseNameAr": "اسم الدورة (بالعربية)",
            "provider": "الجهة المقدمة", "languageId": "اللغة", "readLevelId": "مستوى القراءة", "writeLevelId": "مستوى الكتابة",
            "speakLevelId": "مستوى المحادثة", "employerName": "جهة العمل", "employerNameAr": "جهة العمل (بالعربية)",
            "jobTitle": "المسمى الوظيفي", "place": "المكان", "email": "البريد الإلكتروني",
        },
        "sections": {
            "dependants": ("المُعالون", "إضافة معال", "تعديل معال", "لا يوجد معالون مسجلون",
                           "أضف الزوج أو الزوجة أو الأبناء أو غيرهم من المعالين."),
            "education": ("التعليم", "إضافة مؤهل علمي", "تعديل المؤهل العلمي", "لا توجد مؤهلات علمية مسجلة",
                          "أضف شهادة أو دبلومًا أو أي مؤهل آخر."),
            "training": ("الدورات التدريبية", "إضافة دورة تدريبية", "تعديل الدورة التدريبية", "لا توجد دورات تدريبية مسجلة",
                         "أضف دورة أو برنامجًا تدريبيًا."),
            "languages": ("اللغات", "إضافة لغة", "تعديل اللغة", "لا توجد لغات مسجلة",
                          "أضف اللغات التي يعرفها الموظف ومستوى إتقانه لكل منها."),
            "experience": ("الخبرة العملية", "إضافة خبرة عملية", "تعديل الخبرة العملية", "لا توجد خبرات عملية مسجلة",
                           "أضف جهات العمل السابقة."),
            "references": ("المراجع الشخصية", "إضافة مرجع", "تعديل المرجع", "لا توجد مراجع مسجلة",
                           "أضف أشخاصًا يمكنهم التزكية للموظف."),
        },
    },
}

for lang, t in T.items():
    path = I18N / f"{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    employee = data["employee"]
    for key, label in t["tabs"].items():
        employee["detail"]["tabs"][key] = label
    info = {k: v for k, v in t["common"].items()}
    info["fields"] = t["fields"]
    for key, (title, add, edit, empty, hint) in t["sections"].items():
        info[key] = {"title": title, "add": add, "edit": edit, "emptyTitle": empty, "emptyHint": hint}
    employee["info"] = info
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\r\n")
print("ok: employee.info in", ", ".join(T))
