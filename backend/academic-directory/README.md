# Academic directory

The simulated academic system of ADR 0006: one document per university, named with its code in
upper case (`UPC.json`, `USIL.json`). Each document says who the university is, which courses it
teaches and who its students are, and it is the source of truth of all three:

| What | Read by |
|---|---|
| `university`: code, name, logo, colours, email domains, time zone, minimum teaching grade, current term | registering the university from its document (`POST /api/v1/admin/universities/from-academic-system`) |
| `courses`: the curriculum, each course once with its careers and its cycle in each | synchronising the catalogue (`POST /api/v1/coordinator/academic-catalog/sync`) |
| `students`: who they are, their career, cycle, term, status and approved courses | activating a student (US38) and importing their record (US40, US41) |

Identity reads the document of a university every time it is asked, never at startup, so a
document added or edited here is used at once, without restarting the backend. The folder is
`ayni.identity.academic-directory` (`AYNI_ACADEMIC_DIRECTORY`). `./mvnw` run from `backend/` reads
this one; `docker compose` mounts it into the backend container.

A deployment can read the same documents from another service instead: with
`AYNI_ACADEMIC_DIRECTORY_URL` set, each one is fetched from `<address>/UPC.json`, for instance a
GitHub Pages site that publishes copies of these files (`deploy/README.md`, step 7). Each answer is
kept a minute, and the last one is used while the address does not answer; Pages itself lets its
answers be kept ten minutes, so an edit there can take that long to arrive. `<CODE>.local.json`
is still read from the folder.

## The documents are generated

Do not edit `UPC.json` or `USIL.json` by hand. They are written by
`tools/academic-data/generate.py`, whose `README.md` describes the schema (version 1), the personas
of the demonstration and where the logos come from:

```bash
python3 tools/academic-data/generate.py --check
python3 tools/academic-data/generate.py
```

In short, a student names each approved course by its code only, and its name is the one of the
curriculum:

```json
{
  "university": { "code": "UPC", "name": "Universidad Peruana de Ciencias Aplicadas",
                  "logoUrl": "/logos/upc.png", "primaryColor": "#E42322", "secondaryColor": "#FFFFFF",
                  "emailDomains": ["upc.edu.pe"], "timezone": "America/Lima",
                  "minimumTeachingGrade": 15.0, "currentTerm": "2026-2" },
  "careers": ["Ingeniería de Software", "Ingeniería de Sistemas de Información"],
  "courses": [ { "code": "SI400", "name": "Diseño de Base de Datos",
                 "careers": [ { "career": "Ingeniería de Software", "cycle": 4 } ] } ],
  "students": [ { "email": "u202300002@upc.edu.pe", "studentCode": "U202300002",
                  "fullName": "Bruno Salas", "career": "Ingeniería de Software", "cycle": 8,
                  "currentTerm": "2026-2", "status": "ACTIVE",
                  "approvedCourses": [ { "courseCode": "SI400", "grade": 17.24, "term": "2024-2" } ] } ]
}
```

`logoUrl` is a path relative to the origin of the frontend: the files are in `web/public/logos/`,
and the API answers the path as it is.

## What makes a document usable

Every time it reads a document, `MockAcademicSystemAdapter` checks it: the document is of the
university of its file name; course codes are unique; emails (whatever their case) and student
codes are unique; every email is of a domain of the university; every approved course is a course of
the curriculum of the student's career; a status is `ACTIVE`, `GRADUATED` or `INACTIVE`.

| What happens | Answer |
|---|---|
| The university has no document here | the academic system is not configured: `400` at activation and import, the link stays unused; `404` when registering from the document |
| The document has no student with that email, whatever its case | `400`, the student was not found; the link stays unused |
| The student's `status` is not `ACTIVE` | `400`, the student is not currently enrolled at the university; the link stays unused |
| The document cannot be read or breaks a rule above | `500`: a mistake of the platform, written to the log with the university's code and the rule, never a student |

A student is found by their institutional email. Their `studentCode` is the university's and Ayni
copies it as it is, with no rule about its format.

## Adding yourself: `<CODE>.local.json`

To sign in with a real address without committing it, put a file next to the document named
`<CODE>.local.json` (`UPC.local.json`). Git ignores it (`backend/academic-directory/*.local.json` in
`.gitignore`), so personal data never reaches the repository. It has the schema of the document but
only its `students` are read: they are added to the ones of the document, and a student whose email
is in both is the local one. They are checked like the others, so the address must be of a domain
of the university and the approved courses must be of the curriculum of the career.

```json
{
  "students": [
    { "email": "your.name@upc.edu.pe", "studentCode": "U202699999", "fullName": "Your Name",
      "career": "Ingeniería de Software", "cycle": 5, "currentTerm": "2026-2", "status": "ACTIVE",
      "approvedCourses": [ { "courseCode": "SI400", "grade": 18.0, "term": "2026-1" } ] }
  ]
}
```

The data of the documents is invented, and the emails belong to no real person. The demonstration
tells its story with Ana, Bruno and Carla of UPC and Lucía and Mateo of USIL (`docs/demo.md`).
