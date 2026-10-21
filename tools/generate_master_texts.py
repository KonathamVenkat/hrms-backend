"""Adds the screen texts of the 16 new masters to the Angular translation files (en, fr, ar).

Run from anywhere: python tools/generate_master_texts.py. It only adds or overwrites keys under admin.masters, so it
can be run again after editing the lists below; run Prettier on the three files afterwards. The texts follow the
existing banks / bank branches block (key set and tone). French and Arabic are written by hand here and need a native
read before go-live."""
import json
import pathlib

I18N = pathlib.Path(r"C:\angular-workspace\projects\ehrms-v2\public\i18n")

# key: (en singular, en plural, fr singular, fr plural, fr gender m|f, ar singular, ar plural,
#       parent = (en, fr, fr gender, ar) | None)
MASTERS = {
    "regions": ("Region", "Regions", "région", "régions", "f", "إقليم", "الأقاليم", None),
    "wilayas": ("Wilaya", "Wilayas", "wilaya", "wilayas", "f", "ولاية", "الولايات", ("Region", "Région", "f", "إقليم")),
    "provinces": ("Province", "Provinces", "province", "provinces", "f", "مقاطعة", "المقاطعات",
                  ("Wilaya", "Wilaya", "f", "ولاية")),
    "cities": ("City", "Cities", "ville", "villes", "f", "مدينة", "المدن", ("Province", "Province", "f", "مقاطعة")),
    "religions": ("Religion", "Religions", "religion", "religions", "f", "ديانة", "الديانات", None),
    "nationalities": ("Nationality", "Nationalities", "nationalité", "nationalités", "f", "جنسية", "الجنسيات", None),
    "contractTypes": ("Contract type", "Contract types", "type de contrat", "types de contrat", "m",
                      "نوع عقد", "أنواع العقود", None),
    "professions": ("Profession", "Professions", "profession", "professions", "f", "مهنة", "المهن", None),
    "educationLevels": ("Education level", "Education levels", "niveau d'études", "niveaux d'études", "m",
                        "مستوى تعليمي", "المستويات التعليمية", None),
    "languages": ("Language", "Languages", "langue", "langues", "f", "لغة", "اللغات", None),
    "languageLevels": ("Language level", "Language levels", "niveau de langue", "niveaux de langue", "m",
                       "مستوى لغة", "مستويات اللغة", None),
    "houseTypes": ("House type", "House types", "type de logement", "types de logement", "m",
                   "نوع سكن", "أنواع السكن", None),
    "relationTypes": ("Relation type", "Relation types", "type de relation", "types de relation", "m",
                      "نوع قرابة", "أنواع القرابة", None),
    "relations": ("Relation", "Relations", "lien de parenté", "liens de parenté", "m", "صلة قرابة", "صلات القرابة",
                  ("Relation type", "Type de relation", "m", "نوع قرابة")),
    "directorates": ("Directorate", "Directorates", "direction", "directions", "f", "مديرية", "المديريات",
                     ("Company branch", "Agence", "f", "فرع الشركة")),
    "grades": ("Grade", "Grades", "grade", "grades", "m", "درجة", "الدرجات", None),
}


def cap(text):
    return text[0].upper() + text[1:]


def block(lang, spec):
    es, ep, fs, fp, g, as_, ap, parent = spec
    if lang == "en":
        s, p = es.lower(), ep.lower()
        out = {
            "title": ep, "add": f"Add {es}", "edit": f"Edit {es}", "search": f"Search {p}...",
            "emptyTitle": f"No {p} found", "emptyHint": f"Add a {s} above", "loadFailed": f"Failed to load {p}.",
            "activeHint": "Inactive entries cannot be chosen on employee records",
            "deleteTitle": f"Delete {es}?", "deactivateTitle": f"Deactivate {es}?", "activateTitle": f"Activate {es}?",
        }
        if parent:
            out["parent"] = parent[0]
            out["parentPlaceholder"] = f"Select a {parent[0].lower()}"
        return out
    if lang == "fr":
        un, le = ("une", "la") if g == "f" else ("un", "le")
        none, found = ("Aucune", "trouvée") if g == "f" else ("Aucun", "trouvé")
        out = {
            "title": cap(fp), "add": f"Ajouter {un} {fs}", "edit": f"Modifier {le} {fs}",
            "search": f"Rechercher {fp}...", "emptyTitle": f"{none} {fs} {found}",
            "emptyHint": f"Ajoutez {un} {fs} ci-dessus", "loadFailed": f"Échec du chargement : {fp}.",
            "activeHint": "Une entrée inactive ne peut pas être choisie dans les dossiers des employés",
            "deleteTitle": f"Supprimer {le} {fs} ?", "deactivateTitle": f"Désactiver {le} {fs} ?",
            "activateTitle": f"Activer {le} {fs} ?",
        }
        if parent:
            pun = "une" if parent[2] == "f" else "un"
            out["parent"] = parent[1]
            out["parentPlaceholder"] = f"Sélectionner {pun} {parent[1].lower()}"
        return out
    out = {
        "title": ap, "add": f"إضافة {as_}", "edit": f"تعديل {as_}", "search": f"ابحث في {ap}...",
        "emptyTitle": f"لم يتم العثور على {ap}", "emptyHint": f"أضف {as_} من الأعلى", "loadFailed": f"تعذّر تحميل {ap}.",
        "activeHint": "لا يمكن اختيار العناصر غير النشطة في سجلات الموظفين",
        "deleteTitle": f"حذف {as_}؟", "deactivateTitle": f"تعطيل {as_}؟", "activateTitle": f"تفعيل {as_}؟",
    }
    if parent:
        out["parent"] = parent[3]
        out["parentPlaceholder"] = f"اختر {parent[3]}"
    return out


SHARED = {
    "en": {
        "sortOrder": "Sort order",
        "sortOrderHint": "Lower numbers come first",
        "MASTER_HAS_ACTIVE_CHILDREN": "{{code}} still has active entries below it. Deactivate them first.",
        "MASTER_HAS_CHILDREN": "{{code}} has entries below it, so it cannot move to another parent.",
    },
    "fr": {
        "sortOrder": "Ordre de tri",
        "sortOrderHint": "Les nombres les plus petits viennent en premier",
        "MASTER_HAS_ACTIVE_CHILDREN": "{{code}} a encore des éléments actifs en dessous. Désactivez-les d'abord.",
        "MASTER_HAS_CHILDREN": "{{code}} a des éléments en dessous : il ne peut pas être déplacé vers un autre parent.",
    },
    "ar": {
        "sortOrder": "ترتيب العرض",
        "sortOrderHint": "الأرقام الأصغر تظهر أولاً",
        "MASTER_HAS_ACTIVE_CHILDREN": "لا يزال {{code}} يحتوي على عناصر نشطة تابعة له. عطّلها أولاً.",
        "MASTER_HAS_CHILDREN": "لدى {{code}} عناصر تابعة له، لذلك لا يمكن نقله إلى عنصر أب آخر.",
    },
}

for lang in ("en", "fr", "ar"):
    path = I18N / f"{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    masters = data["admin"]["masters"]
    masters["sortOrder"] = SHARED[lang]["sortOrder"]
    masters["sortOrderHint"] = SHARED[lang]["sortOrderHint"]
    rules = masters["errors"]["rules"]
    rules["MASTER_HAS_ACTIVE_CHILDREN"] = SHARED[lang]["MASTER_HAS_ACTIVE_CHILDREN"]
    rules["MASTER_HAS_CHILDREN"] = SHARED[lang]["MASTER_HAS_CHILDREN"]
    for key, spec in MASTERS.items():
        masters[key] = block(lang, spec)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\r\n")
print("ok:", len(MASTERS), "masters x 3 languages")
