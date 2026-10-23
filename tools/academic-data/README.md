# Academic documents

The simulated academic systems of ADR 0006: one JSON document per university, `UPC.json` and
`USIL.json`. Each one answers what a university would answer to Ayni: who it is, which courses it
teaches and who its students are.

```
tools/academic-data/generate.py      writes both documents; `--check` validates the ones there
backend/academic-directory/UPC.json  57 courses of two careers, 28 students
backend/academic-directory/USIL.json 64 courses of two careers, 28 students
web/public/logos/                    the logo of each university (see "Logos"); the documents only
                                     name the file, as a path of the frontend's origin
```

The documents are generated, never edited by hand. From the root of the repository:

```bash
python3 tools/academic-data/generate.py --check
python3 tools/academic-data/generate.py
```

The first validates what is in `backend/academic-directory`; the second writes both documents
there again. It needs Python 3 and nothing else, and writes the same files every time. The backend
reads them as they are (see `backend/academic-directory/README.md`).

## Schema (version 1)

```json
{
  "university": {
    "code": "UPC", "name": "Universidad Peruana de Ciencias Aplicadas",
    "logoUrl": "/logos/upc.png", "primaryColor": "#E42322", "secondaryColor": "#FFFFFF",
    "emailDomains": ["upc.edu.pe"], "timezone": "America/Lima",
    "minimumTeachingGrade": 15.0, "currentTerm": "2026-2"
  },
  "careers": ["Ingeniería de Software", "Ingeniería de Sistemas de Información"],
  "courses": [
    { "code": "SI400", "name": "Diseño de Base de Datos",
      "careers": [ { "career": "Ingeniería de Software", "cycle": 4 },
                   { "career": "Ingeniería de Sistemas de Información", "cycle": 3 } ] }
  ],
  "students": [
    { "email": "u202400001@upc.edu.pe", "studentCode": "U202400001", "fullName": "Ana Torres",
      "career": "Ingeniería de Software", "cycle": 4, "currentTerm": "2026-2", "status": "ACTIVE",
      "approvedCourses": [ { "courseCode": "SI400", "grade": 16.0, "term": "2025-2" } ] }
  ]
}
```

- **A course shared by two careers is one course**, with one entry per career and its cycle there.
- A student's approved courses name the course by code only: the name comes from `courses`.
- A student is found by the institutional email. `status` other than `ACTIVE` (`GRADUATED`,
  `INACTIVE`) means the university no longer considers them a student.
- Grades are on the 0 to 20 scale; 11 passes. `minimumTeachingGrade` is the grade needed in a course
  to teach it, and is what the university is registered with (the coordinator may change it later).
- Elective slots of the mallas are not courses and are not in the document.
- **UPC** has the codes of its real mallas. **USIL**'s source has none, so its codes (`USIL001`…) are
  made up in a stable way. The students are invented; the emails belong to no real person.

## Coherence

`python3 tools/academic-data/generate.py --check` fails when a course code is repeated or two
courses share a name, a student's email is not of a domain of the university, an email or a student
code is repeated, an approved course is not a course of the student's career, is not behind the
student's cycle, or has a grade outside 11 to 20.

The backend checks part of this again every time it reads a document (`MockAcademicSystemAdapter`):
unique course codes, unique emails and student codes, emails of a domain of the university, and
every approved course a course of the student's career. A document that fails is a mistake of the
platform. `AcademicDocumentsCoherenceTest` reads the two documents of the repository with that same
logic and also checks that every approved course is behind the student's cycle.

## Personas the demo refers to

| University | Person | Why |
|---|---|---|
| UPC | Ana Torres, Software, cycle 4 | the student who books and goes through the onboarding; mixed grades, some below the threshold |
| UPC | Bruno Salas, Software, cycle 8 | the tutor: grades above 16 in everything he took |
| UPC | Carla Mendoza, Sistemas de Información, cycle 3 | a student left to activate live |
| USIL | Lucía Paredes, Software, cycle 4 | the student of the second university |
| USIL | Mateo Ríos, Sistemas de Información, cycle 8 | the tutor of the second university |

Ana and Bruno keep the email, code and name they had in the first demo data.

## Logos

| File | Source | Licence on Commons |
|---|---|---|
| `web/public/logos/upc.png` | Wikimedia Commons, `File:UPC logo transparente.png` (300 × 300, transparent) | public domain (text logo), marked as a trademark |
| `web/public/logos/usil.jpg` | Wikimedia Commons, `File:Usil.jpg`, the 500 px version (500 × 498) | public domain, marked as a trademark; it is described there as a design inspired by the university's logo, not as its official file |

The logos and names belong to their universities and appear only to illustrate a course project with
invented data: Ayni has no affiliation with either university. The brand colours of the documents are
sampled from these files (UPC red `#E42322`, USIL navy `#07256D`); both secondary colours are white.
The logos are not square-safe: the web shows them inside a fixed box with `object-contain`.
