# pdf-pac-validator

PDF/UA and WCAG 2.2 accessibility validator with a PAC-compatible JSON report.
Exposes a Spring Boot REST API and a legacy CLI entry point.

Under the hood: iText 9 for native rules, veraPDF as an adapter for the
remaining coverage, plus a native WCAG 1.4.3 contrast check.

## Quick start

**Local (Java 17+ required)**

```bash
mvn -DskipTests package
java -jar target/pdf-validator-1.0-SNAPSHOT.jar
```

**Docker**

```bash
docker build -t pdf-validator:latest .
docker run --rm -p 8080:8080 \
  -v /host/pdfs:/pdfs:ro \
  -v /host/reports:/reports \
  pdf-validator:latest
```

**Validate a PDF**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"/pdfs/sample.pdf","outputFolder":"/reports"}'
```

The response inlines the PAC-shaped simple report and returns a `jobId` for
retrieving the detailed report:

```json
{
  "jobId": "6a9754b2-3fe5-405b-95c0-f71c3d861e23",
  "sourceFileName": "sample.pdf",
  "simpleReportPath": "/reports/sample.simple.json",
  "detailedReportPath": "/reports/sample.detailed.json",
  "status": "success",
  "simpleReport": { "body": { ... }, "version": { "major": 2, "minor": 0 } }
}
```

```bash
# Fetch the detailed report (per-instance page + bboxes for viewer highlighting)
curl http://localhost:8080/api/report/6a9754b2-3fe5-405b-95c0-f71c3d861e23/detailed
```

## Documentation

- **[docs/api.md](docs/api.md)** — REST API, payloads, CLI usage, configuration, EC2 deployment.
- **[docs/json-report-schema.md](docs/json-report-schema.md)** — output JSON schema and semantics.
- **[docs/remaining-parity-plan.md](docs/remaining-parity-plan.md)** — living plan for the residual per-rule count-parity gaps against PAC.
- `docs/` — additional design notes (veraPDF adapter, PAC parity, coverage checklist).

## Layout

```
src/main/java/
├── PDFValidator.java                    (legacy CLI wrapper)
└── com/netralabs/
    ├── ValidatorApplication.java        (Spring Boot entry point)
    ├── api/
    │   ├── controller/                  (REST controllers, jobId lookup)
    │   ├── service/                     (validation orchestration, jobId registry)
    │   └── dto/                         (request/response records)
    ├── Runner.java                      (four-phase rule pipeline)
    ├── basic/ logicalstructure/ ...     (native iText rules)
    ├── vera/                            (veraPDF adapter)
    ├── report/
    │   ├── pac/                         (PAC simple + detailed report builders)
    │   └── ...                          (legacy combined report — --legacy flag)
    └── wcag/                            (WCAG 2.2 view + contrast rule)
```

## License

Internal — Netra Labs.