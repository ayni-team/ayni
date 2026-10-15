# Academic directory

The simulated academic system of ADR 0006: one file per university, named with its code in upper
case (`UPC.json`, `PUCP.json`). Identity reads the file of a university every time it looks a
student up, at activation (US38) and when the academic record is imported (US40), so a file added
or edited here is used at once, without restarting the backend.

The folder is `ayni.identity.academic-directory` (`AYNI_ACADEMIC_DIRECTORY`). `./mvnw` run from
`backend/` reads this one; `docker compose` mounts it into the backend container.

A student is found by their institutional email, whatever its case. Their `studentCode` is the
university's and Ayni copies it as it is, with no rule about its format.

```json
{
  "students": [
    {
      "email": "u202400001@upc.edu.pe",
      "studentCode": "U202400001",
      "fullName": "Ana Torres",
      "career": "Software Engineering",
      "currentTerm": "2026-2",
      "approvedCourses": [
        { "courseCode": "1ASI0616", "courseName": "Databases I", "grade": 16.00, "term": "2025-2" }
      ]
    }
  ]
}
```

The four students here are the demonstration ones: Ana, Bruno and Carla of UPC, and Lucia of
PUCP. The data is invented.
