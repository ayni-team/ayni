#!/usr/bin/env python3
"""
Generates the JSON documents of the simulated academic systems (one per university).

    python3 generate.py            writes UPC.json and USIL.json into backend/academic-directory
    python3 generate.py --check    only validates what is there

The curricula are the real mallas of the two careers of each university, typed in MALLAS below
(UPC with its course codes; USIL has none, so its codes are made up in a stable way). The students
are invented and deterministic: the same script always writes the same files.

Schema (version 1), per university:

  university  code, name, logoUrl, primaryColor, secondaryColor, emailDomains, timezone,
              minimumTeachingGrade, currentTerm
  careers     the names of the careers
  courses     code, name, careers: [{career, cycle}]   (a course shared by two careers is ONE course)
  students    email, studentCode, fullName, career, cycle, currentTerm, status, approvedCourses
              [{courseCode, grade, term}]
"""
import json
import random
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
# The folder the backend reads (ayni.identity.academic-directory), from tools/academic-data.
OUTPUT = HERE.parents[1] / "backend" / "academic-directory"
CURRENT_YEAR, CURRENT_SEMESTER = 2026, 2          # the term in course: 2026-2
GRADE_MIN, GRADE_MAX = 0.0, 20.0

SW = "Ingeniería de Software"
IS = "Ingeniería de Sistemas de Información"

# ------------------------------------------------------------------------------------------------
# UPC: (cycle, code, name). "Electivo" slots are not courses and are left out.
# ------------------------------------------------------------------------------------------------
UPC = {
    SW: [
        (1, "HU625", "Comprensión y Producción de Lenguaje I"),
        (1, "HU612", "Creatividad y Liderazgo (Ing)"),
        (1, "HU548", "Ética y Ciudadanía"),
        (1, "MA420", "Matemática Básica"),
        (1, "CC126", "Introducción a los Algoritmos"),
        (2, "MA262", "Cálculo I"),
        (2, "HU626", "Comprensión y Producción de Lenguaje II"),
        (2, "HU159", "Seminario de Investigación Académica I"),
        (2, "CC215", "Algoritmos"),
        (2, "AD99", "Organización y Dirección de Empresas"),
        (3, "MA466", "Física I"),
        (3, "CC182", "Algoritmos y Estructuras de Datos"),
        (3, "SI720", "Diseño y Patrones de Software"),
        (3, "SI397", "Especificación y Análisis de Requerimientos"),
        (3, "MA265", "Matemática Discreta"),
        (4, "SI725", "Arquitectura de Computadoras"),
        (4, "SI400", "Diseño de Base de Datos"),
        (4, "MA462", "Física II"),
        (4, "SI385", "IHC y Tecnologías Móviles"),
        (4, "MA475", "Matemática Computacional"),
        (5, "SI730", "Aplicaciones Web"),
        (5, "MA263", "Cálculo II"),
        (5, "SI729", "Desarrollo de Aplicaciones Open Source"),
        (5, "SI726", "Sistemas Operativos"),
        (6, "MA642", "Estadística Aplicada I"),
        (6, "CC238", "Aplicaciones para Dispositivos Móviles"),
        (6, "CC184", "Complejidad Algorítmica"),
        (6, "SI640", "Redes y Comunicaciones de Datos"),
        (7, "SI572", "Desarrollo de Soluciones IoT"),
        (7, "SI732", "Diseño de Experimentos de Ingeniería de Software"),
        (7, "SI642", "Finanzas e Ingeniería Económica"),
        (7, "SI657", "Fundamentos de Arquitectura de Software"),
        (8, "SI728", "Arquitecturas de Software Emergentes"),
        (8, "SI727", "Gerencia de Proyectos en Computación"),
        (8, "IN397", "Seminario de Investigación Académica II (Ing)"),
        (8, "SI639", "Taller de Desempeño Profesional"),
        (9, "SI438", "Calidad y Mejora de Procesos Software"),
        (9, "SI570", "Desarrollo Ágil de Productos de Software"),
        (9, "SI644", "Taller de Proyecto I"),
        (10, "SI646", "Curso de Trabajo de Investigación - Taller de Proyecto II"),
    ],
    IS: [
        (1, "HU625", "Comprensión y Producción de Lenguaje I"),
        (1, "HU612", "Creatividad y Liderazgo (Ing)"),
        (1, "HU548", "Ética y Ciudadanía"),
        (1, "MA420", "Matemática Básica"),
        (1, "CC200", "Fundamentos en Programación"),
        (2, "MA262", "Cálculo I"),
        (2, "HU626", "Comprensión y Producción de Lenguaje II"),
        (2, "HU159", "Seminario de Investigación Académica I"),
        (2, "AD99", "Organización y Dirección de Empresas"),
        (2, "CC201", "Programación Orientado a Objetos"),
        (3, "MA466", "Física I"),
        (3, "CA59", "Contabilidad y Presupuestos"),
        (3, "SI400", "Diseño de Base de Datos"),
        (3, "SI397", "Especificación y Análisis de Requerimientos"),
        (3, "MA265", "Matemática Discreta"),
        (4, "SI643", "Arquitectura de Computadoras y Sistemas Operativos"),
        (4, "MA462", "Física II"),
        (4, "SI385", "IHC y Tecnologías Móviles"),
        (4, "MA475", "Matemática Computacional"),
        (5, "MA642", "Estadística Aplicada I"),
        (5, "SI704", "Arquitectura de Negocio"),
        (5, "MA263", "Cálculo II"),
        (5, "SI393", "Fundamentos de Sistemas de Información"),
        (6, "SI705", "Arquitectura de Aplicaciones Web"),
        (6, "SI651", "Diseño de Experimentos en SI"),
        (6, "AD204", "Emprendimiento de Negocios Sostenibles: Formulación"),
        (6, "SI640", "Redes y Comunicaciones de Datos"),
        (7, "SI706", "Arquitectura Empresarial"),
        (7, "SI427", "Gerencia de Proyectos de SI/TI"),
        (7, "SI707", "Gestión de Seguridad de Información"),
        (7, "SI645", "Implementación de Sistemas Empresariales"),
        (8, "SI642", "Finanzas e Ingeniería Económica"),
        (8, "IN397", "Seminario de Investigación Académica II (Ing)"),
        (8, "SI639", "Taller de Desempeño Profesional"),
        (8, "SI708", "Technical Sales and Marketing"),
        (9, "SI709", "Business Predictive Analytics"),
        (9, "SI381", "Soluciones Móviles y Cloud"),
        (9, "SI644", "Taller de Proyecto I"),
        (10, "SI646", "Curso de Trabajo de Investigación - Taller de Proyecto II"),
        (10, "SI710", "Gestión de Tecnologías de Información"),
    ],
}

# ------------------------------------------------------------------------------------------------
# USIL: (cycle, name). No codes in the source: they are assigned below.
# ------------------------------------------------------------------------------------------------
_USIL_FIRST_YEAR = [
    "Fundamentos de Programación", "Fundamentos en Competencias Digitales", "Matemática",
    "Lenguaje y Comunicación I", "Realidad Nacional y Globalización", "English I",
]
_USIL_LAST_YEARS = {
    8: ["Agentes Inteligentes", "Seguridad de la Información", "Gestión de Proyectos para Computación",
        "Cloud Computing", "Oportunidades de Negocios"],
    9: ["Internet of Things", "Visualización de Datos", "Proyecto para Computación I",
        "Tecnologías Emergentes", "Computación en la Sociedad"],
    10: ["Big Data y Analítica de Datos", "Estrategias de Sistemas de Información",
         "Proyecto para Computación II", "Emprendimiento e Innovación Tecnológica",
         "Desarrollo de Negocios Electrónicos"],
}


def _usil(by_cycle):
    return [(cycle, name) for cycle in sorted(by_cycle) for name in by_cycle[cycle]]


USIL = {
    SW: _usil({
        1: _USIL_FIRST_YEAR,
        2: ["Programación Orientada a Objetos I", "Matemática Discreta", "Cálculo de Una Variable",
            "Lenguaje y Comunicación II", "Administración para los Negocios", "English II"],
        3: ["Programación y Estructuras de Datos", "Ingeniería de Software I",
            "Álgebra Lineal Computacional", "Teoría de Computación", "Principios de Economía", "English III"],
        4: ["Estructuras de Datos Avanzada", "Estadística Descriptiva e Inferencia Estadística",
            "Gerenciamiento de Datos I", "Experiencia de Usuario (UX)", "Fundamentos Contables y Financieros",
            "English IV"],
        5: ["Programación Orientada a Objetos II", "Ingeniería de Software II", "Gerenciamiento de Datos II",
            "Interacción Humano Computador", "Ética y Ciudadanía", "Marketing"],
        6: ["Ingeniería de Software III", "Programación Competitiva", "Análisis y Diseño de Algoritmos",
            "Sistemas Operativos", "Metodología de la Investigación Científica"],
        7: ["Desarrollo Basado en Plataformas", "Gestión de Sistemas de Información", "Compiladores",
            "Redes y Telecomunicaciones I", "Fundamentos del Liderazgo Sostenible"],
        **_USIL_LAST_YEARS,
    }),
    IS: _usil({
        1: _USIL_FIRST_YEAR,
        2: ["Programación Orientada a Objetos I", "Matemática Discreta", "Cálculo de una Variable",
            "Administración para los Negocios", "Lenguaje y comunicación II", "English II"],
        3: ["Programación y Estructuras de Datos", "Gestión de Procesos", "Algebra Lineal Computacional",
            "Electricidad y Ondas", "Principios de Economía", "English III"],
        4: ["Análisis y Diseño de Sistemas I", "Estadística Descriptiva e Inferencia Estadística",
            "Gerenciamiento de Datos I", "Arquitectura de Computadoras", "Fundamentos Contables y Financieros",
            "English IV"],
        5: ["Programación Orientada a Objetos II", "Arquitectura Empresarial", "Gerenciamiento de Datos II",
            "Interacción Humano Computador", "Ética y Ciudadanía", "Marketing"],
        6: ["Análisis y Diseño de Sistemas II", "Gobierno de TI", "Gobierno de Datos", "Sistemas Operativos",
            "Metodología de la Investigación Científica"],
        7: ["Desarrollo basado en Plataformas", "Gestión de Sistemas de Información", "Gestión del Conocimiento",
            "Redes y Telecomunicaciones I", "Fundamentos del Liderazgo Sostenible"],
        **_USIL_LAST_YEARS,
    }),
}

UNIVERSITIES = {
    "UPC": {
        "name": "Universidad Peruana de Ciencias Aplicadas",
        "logoUrl": "/logos/upc.png", "primaryColor": "#E42322", "secondaryColor": "#FFFFFF",
        "emailDomains": ["upc.edu.pe"], "timezone": "America/Lima", "minimumTeachingGrade": 15.0,
        "mallas": UPC,
    },
    "USIL": {
        "name": "Universidad San Ignacio de Loyola",
        "logoUrl": "/logos/usil.jpg", "primaryColor": "#07256D", "secondaryColor": "#FFFFFF",
        "emailDomains": ["usil.edu.pe"], "timezone": "America/Lima", "minimumTeachingGrade": 16.0,
        "mallas": USIL,
    },
}


def plain(text):
    """Lower case without accents or doubled spaces: two spellings of one course compare equal."""
    stripped = unicodedata.normalize("NFD", text)
    stripped = "".join(ch for ch in stripped if unicodedata.category(ch) != "Mn")
    return re.sub(r"\s+", " ", stripped).strip().lower()


def build_courses(code, mallas, has_codes):
    """One entry per course; a course in two careers is a single course with two (career, cycle)."""
    courses, by_key, next_number = {}, {}, 1
    for career, rows in mallas.items():
        for row in rows:
            cycle, name = (row[0], row[2]) if has_codes else (row[0], row[1])
            key = row[1] if has_codes else plain(name)
            if key not in by_key:
                course_code = row[1] if has_codes else f"{code}{next_number:03d}"
                if not has_codes:
                    next_number += 1
                by_key[key] = course_code
                courses[course_code] = {"code": course_code, "name": name, "careers": []}
            else:
                existing = courses[by_key[key]]
                if has_codes and plain(existing["name"]) != plain(name):
                    raise SystemExit(f"{code}: {key} is named '{existing['name']}' and '{name}'")
                if any(ch in name for ch in "áéíóúÁÉÍÓÚ") and not any(ch in existing["name"] for ch in "áéíóúÁÉÍÓÚ"):
                    existing["name"] = name
            courses[by_key[key]]["careers"].append({"career": career, "cycle": cycle})
    return sorted(courses.values(), key=lambda c: (min(x["cycle"] for x in c["careers"]), c["code"]))


# ------------------------------------------------------------------------------------------------
# Students
# ------------------------------------------------------------------------------------------------
FIRST = ["Ana", "Bruno", "Carla", "Diego", "Valeria", "Mateo", "Lucía", "Sebastián", "Camila", "Andrés",
         "Daniela", "Gabriel", "Fernanda", "Joaquín", "Renata", "Nicolás", "Paula", "Santiago", "Mariana",
         "Adrián", "Sofía", "Rodrigo", "Isabella", "Gonzalo", "Alejandra", "Matías", "Natalia", "Renzo",
         "Claudia", "Bruno", "Jimena", "Piero", "Rocío", "Álvaro", "Milagros", "Julián"]
LAST = ["Torres", "Salas", "Mendoza", "Quispe", "Ríos", "Paredes", "Huamán", "Flores", "Castillo", "Vargas",
        "Rojas", "Chávez", "Medina", "Ramos", "Gutiérrez", "Soto", "Cárdenas", "Navarro", "Espinoza",
        "Valdivia", "Zegarra", "Cornejo", "Delgado", "Aguilar", "Bustamante", "Lazo", "Palomino", "Ortiz",
        "Montalvo", "Salazar", "Tapia", "Arce", "Benavides", "Campos", "Yupanqui"]

# Fixed personas: the demo seeds refer to Ana and Bruno by identifier, so they must exist as written.
PERSONAS = {
    "UPC": [
        dict(name="Ana Torres", career=SW, cycle=4, email="u202400001@upc.edu.pe", code="U202400001", style="mixed"),
        dict(name="Bruno Salas", career=SW, cycle=8, email="u202300002@upc.edu.pe", code="U202300002", style="strong"),
        dict(name="Carla Mendoza", career=IS, cycle=3, email="u202500003@upc.edu.pe", code="U202500003", style="mixed"),
    ],
    "USIL": [
        dict(name="Lucía Paredes", career=SW, cycle=4, email="lucia.paredes@usil.edu.pe", code="2023201101", style="mixed"),
        dict(name="Mateo Ríos", career=IS, cycle=8, email="mateo.rios@usil.edu.pe", code="2022201102", style="strong"),
    ],
}
STUDENTS_PER_CAREER = 14


def term_of(cycle_taken, cycle_now):
    """The term in which a course of `cycle_taken` was done by a student who is in `cycle_now`."""
    index = CURRENT_YEAR * 2 + (CURRENT_SEMESTER - 1) - (cycle_now - cycle_taken)
    return f"{index // 2}-{index % 2 + 1}"


def grade(rng, style):
    if style == "strong":
        value = rng.gauss(17.0, 1.2)
    elif style == "mixed":
        value = rng.gauss(14.2, 2.6)
    else:
        value = rng.gauss(15.0, 2.4)
    return round(min(20.0, max(11.0, value)), 2)           # 11 is the passing grade; 0 to 20 scale


def make_student(rng, persona, courses_of_career, term=f"{CURRENT_YEAR}-{CURRENT_SEMESTER}"):
    approved = []
    for course in courses_of_career:
        if course["cycle"] >= persona["cycle"]:
            continue
        # someone who retook a course has it approved later; a few are simply missing from the record
        if persona["style"] != "strong" and rng.random() < 0.06:
            continue
        approved.append({
            "courseCode": course["code"],
            "grade": grade(rng, persona["style"]),
            "term": term_of(course["cycle"], persona["cycle"]),
        })
    return {
        "email": persona["email"], "studentCode": persona["code"], "fullName": persona["name"],
        "career": persona["career"], "cycle": persona["cycle"], "currentTerm": term,
        "status": persona.get("status", "ACTIVE"), "approvedCourses": approved,
    }


def email_for(code, name, used, entry_year, serial):
    if code == "UPC":
        return f"u{entry_year}{serial:05d}@upc.edu.pe", f"U{entry_year}{serial:05d}"
    base = plain(name).replace(" ", ".")
    address = f"{base}@usil.edu.pe"
    suffix = 2
    while address in used:
        address = f"{base}{suffix}@usil.edu.pe"
        suffix += 1
    return address, f"{entry_year}20{serial:04d}"


def make_university(code):
    spec = UNIVERSITIES[code]
    has_codes = code == "UPC"
    courses = build_courses(code, spec["mallas"], has_codes)
    by_career = {}
    for course in courses:
        for placement in course["careers"]:
            by_career.setdefault(placement["career"], []).append({"code": course["code"], "cycle": placement["cycle"]})
    rng = random.Random(f"ayni-{code}")
    students, used_emails, used_names = [], set(), set()
    serial = 10
    for career in (SW, IS):
        personas = [p for p in PERSONAS[code] if p["career"] == career]
        for persona in personas:
            students.append(make_student(rng, persona, by_career[career]))
            used_emails.add(persona["email"])
            used_names.add(persona["name"])
        wanted = STUDENTS_PER_CAREER - len(personas)
        for position in range(wanted):
            while True:
                name = f"{rng.choice(FIRST)} {rng.choice(LAST)}"
                if name not in used_names:
                    break
            used_names.add(name)
            cycle = rng.choice([2, 3, 4, 4, 5, 5, 6, 6, 7, 8, 9, 10])
            entry_year = CURRENT_YEAR - (cycle + 1) // 2
            serial += 1
            email, student_code = email_for(code, name, used_emails, entry_year, serial)
            used_emails.add(email)
            persona = dict(name=name, career=career, cycle=cycle, email=email, code=student_code,
                           style=rng.choice(["mixed", "normal", "normal", "strong"]))
            # one student per career is not in force: they must be refused when activating
            if position == wanted - 1:
                persona["status"] = "GRADUATED" if career == SW else "INACTIVE"
                persona["cycle"] = 10
            students.append(make_student(rng, persona, by_career[career]))
    students.sort(key=lambda s: (s["career"], s["cycle"], s["fullName"]))
    return {
        "university": {
            "code": code, "name": spec["name"], "logoUrl": spec["logoUrl"],
            "primaryColor": spec["primaryColor"], "secondaryColor": spec["secondaryColor"],
            "emailDomains": spec["emailDomains"], "timezone": spec["timezone"],
            "minimumTeachingGrade": spec["minimumTeachingGrade"], "currentTerm": f"{CURRENT_YEAR}-{CURRENT_SEMESTER}",
        },
        "careers": [SW, IS],
        "courses": courses,
        "students": students,
    }


# ------------------------------------------------------------------------------------------------
# Validation: the files must agree with themselves
# ------------------------------------------------------------------------------------------------
def validate(code, doc):
    problems = []
    university = doc["university"]
    codes = {c["code"] for c in doc["courses"]}
    if len(codes) != len(doc["courses"]):
        problems.append("repeated course code")
    names = [plain(c["name"]) for c in doc["courses"]]
    if len(set(names)) != len(names):
        problems.append("two courses with the same name")
    cycles = {(c["code"], p["career"]): p["cycle"] for c in doc["courses"] for p in c["careers"]}
    seen_email, seen_code = set(), set()
    for student in doc["students"]:
        label = student["email"]
        if not any(label.endswith("@" + d) for d in university["emailDomains"]):
            problems.append(f"{label}: not of a domain of the university")
        if label in seen_email or student["studentCode"] in seen_code:
            problems.append(f"{label}: repeated email or student code")
        seen_email.add(label), seen_code.add(student["studentCode"])
        if student["career"] not in doc["careers"]:
            problems.append(f"{label}: unknown career")
        if not 1 <= student["cycle"] <= 10:
            problems.append(f"{label}: cycle out of range")
        taken = set()
        for approved in student["approvedCourses"]:
            key = (approved["courseCode"], student["career"])
            if key not in cycles:
                problems.append(f"{label}: {approved['courseCode']} is not a course of {student['career']}")
                continue
            if cycles[key] >= student["cycle"]:
                problems.append(f"{label}: {approved['courseCode']} is of cycle {cycles[key]}, not behind the student")
            if not 11 <= approved["grade"] <= GRADE_MAX:
                problems.append(f"{label}: grade {approved['grade']} outside 11 to 20")
            if approved["courseCode"] in taken:
                problems.append(f"{label}: {approved['courseCode']} twice")
            taken.add(approved["courseCode"])
    return problems


def main():
    check_only = "--check" in sys.argv
    failed = False
    for code in UNIVERSITIES:
        path = OUTPUT / f"{code}.json"
        doc = json.loads(path.read_text()) if check_only else make_university(code)
        problems = validate(code, doc)
        if not check_only:
            path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
        taught = sum(1 for s in doc["students"] for a in s["approvedCourses"]
                     if a["grade"] >= doc["university"]["minimumTeachingGrade"])
        print(f"{code}: {len(doc['courses'])} courses, {len(doc['students'])} students, "
              f"{sum(len(s['approvedCourses']) for s in doc['students'])} approved courses "
              f"({taught} clear the threshold of {doc['university']['minimumTeachingGrade']})")
        for problem in problems:
            failed = True
            print("  PROBLEM:", problem)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
