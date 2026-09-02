# pdf-pac-validator

PDF/UA and WCAG 2.2 accessibility validator with a PAC-compatible JSON report.
Exposes a Spring Boot REST API and a legacy CLI entry point. Reads source
PDFs from S3 and uploads the generated reports back to S3.

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
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=... \
  -e AWS_SECRET_ACCESS_KEY=... \
  pdf-validator:latest
```

On EC2 with an instance profile, drop the key env vars — the AWS SDK reads
credentials from IMDS automatically.

**Validate a PDF**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "bucketName": "pdf-tagging-data-381490270597",
    "pdfPath": "input/sample.pdf",
    "outputFolderPath": "reports"
  }'
```

The response returns a `jobId` and the S3 URIs where the two PAC reports
were uploaded:

```json
{
  "jobId": "6a9754b2-3fe5-405b-95c0-f71c3d861e23",
  "sourceFileName": "sample.pdf",
  "simpleReportS3Uri": "s3://pdf-tagging-data-381490270597/reports/sample.simple.json",
  "detailedReportS3Uri": "s3://pdf-tagging-data-381490270597/reports/sample.detailed.json",
  "status": "success"
}
```

Fetch the JSON directly from S3 — the API only reports where it landed.

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
    │   ├── controller/                  (REST controller)
    │   ├── service/                     (validation orchestration)
    │   └── dto/                         (request/response records)
    ├── service/                         (S3 client + upload/download helpers)
    ├── Runner.java                      (four-phase rule pipeline)
    ├── basic/ logicalstructure/ ...     (native iText rules)
    ├── vera/                            (veraPDF adapter)
    ├── report/
    │   ├── pac/                         (PAC simple + detailed report builders + S3 writer)
    │   └── ...                          (legacy combined report DTOs — no longer wired)
    └── wcag/                            (WCAG 2.2 view + contrast rule)
```

## License

Internal — Netra Labs.
