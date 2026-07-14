# PDF PAC Validator — Usage Guide

REST + CLI PDF/UA & WCAG 2.2 accessibility validator. Produces PAC-style JSON
reports.

## Table of contents

- [Running the app](#running-the-app)
- [REST API](#rest-api)
- [Legacy CLI](#legacy-cli)
- [Report output](#report-output)
- [Configuration](#configuration)
- [Deployment (EC2)](#deployment-ec2)

---

## Running the app

### Local (Java 17+)

```bash
mvn -DskipTests package         # produces target/pdf-validator-1.0-SNAPSHOT.jar (executable)
java -jar target/pdf-validator-1.0-SNAPSHOT.jar
```

Or, for dev iteration:

```bash
mvn spring-boot:run
```

Server listens on `http://localhost:8080`.

### Docker

```bash
docker build -t pdf-validator:latest .

docker run --rm -p 8080:8080 \
  -v /host/pdfs:/pdfs:ro \
  -v /host/reports:/reports \
  pdf-validator:latest
```

Paths passed in the JSON body must be visible **inside the container** — use
the mount points (e.g. `/pdfs/foo.pdf`, `/reports`), not host paths.

---

## REST API

### `POST /api/validate`

Runs the full PDF/UA + WCAG + Quality pipeline and writes a JSON report to
disk. Response contains the resolved report file path and a status flag.

**Request headers**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Request body**

```json
{
  "pdfPath": "C:/projects/pdf/sample.pdf",
  "outputFolder": "C:/projects/pdf/report"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `pdfPath` | string | yes | Absolute path to the source PDF, readable by the server process. MSYS/Git-Bash style (`/c/foo/bar.pdf`) is auto-normalized to Windows form. |
| `outputFolder` | string | no | Absolute path to the folder where the report is written. If omitted or blank, the report is written next to the source PDF as `<basename>.report.json`. |

The report filename is always `<pdf-basename>.report.json`. Existing files at
that path are overwritten.

**Response body** — always the same shape, regardless of outcome.

```json
{
  "sourceFileName": "sample.pdf",
  "reportPath": "C:\\projects\\pdf\\report\\sample.report.json",
  "status": "success"
}
```

| Field | Type | Description |
|---|---|---|
| `sourceFileName` | string \| null | Filename portion of `pdfPath`. `null` when `pdfPath` was missing/blank. |
| `reportPath` | string \| null | Absolute path of the written JSON report. `null` on failure. |
| `status` | string | `"success"` or `"failed"`. |

**Status codes**

| Code | Meaning |
|---|---|
| `200 OK` | Report generated and written. |
| `400 Bad Request` | `pdfPath` missing or blank. |
| `500 Internal Server Error` | Validation failed (file not found, unreadable PDF, iText/veraPDF error). Server log contains the stack trace. |

### Examples

**cURL — success**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"C:/projects/pdf/sample.pdf","outputFolder":"C:/projects/pdf/report"}'
```

```json
{"sourceFileName":"sample.pdf","reportPath":"C:\\projects\\pdf\\report\\sample.report.json","status":"success"}
```

**cURL — default output folder (next to source)**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"C:/projects/pdf/sample.pdf"}'
```

**cURL — Docker with volume mount**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"/pdfs/sample.pdf","outputFolder":"/reports"}'
```

**cURL — failure (missing file)**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"/does-not-exist.pdf"}'
```

Returns `HTTP 500` with `{"sourceFileName":"does-not-exist.pdf","reportPath":null,"status":"failed"}`.

---

## Legacy CLI

The pre-REST CLI entry point is still available:

```bash
java -cp "target/classes;$(cat /tmp/cp.txt)" PDFValidator \
  <path-to-pdf> [-o <output.json>]
```

`-o` accepts a full file path (folder + filename), not just a folder. When
omitted, the report is written next to the source PDF.

---

## Report output

Each report is a single JSON document with the shape:

```json
{
  "reports": {
    "document": { ... },
    "info":     { ... },
    "PDF/UA":   { ... },
    "WCAG":     { ... },
    "quality":  { ... }
  }
}
```

Full schema and semantics: [`docs/json-report-schema.md`](json-report-schema.md).
The four report branches mirror PAC's tree structure.

---

## Configuration

Standard Spring Boot properties apply. The most useful ones:

| Property / env var | Default | Purpose |
|---|---|---|
| `SERVER_PORT` / `--server.port=` | `8080` | HTTP port. |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0` (in Docker) | JVM tuning. Large PDFs are memory-hungry — the full finding list is held in memory until report write. |
| `LOGGING_LEVEL_COM_NETRALABS` | `INFO` | Bump to `DEBUG` for per-rule tracing. |

Example — run on port 9000 with more heap:

```bash
JAVA_TOOL_OPTIONS="-Xmx2g" \
SERVER_PORT=9000 \
java -jar target/pdf-validator-1.0-SNAPSHOT.jar
```

---

## Deployment (EC2)

1. Push the image to ECR:

   ```bash
   aws ecr get-login-password --region <region> | \
     docker login --username AWS --password-stdin <acct>.dkr.ecr.<region>.amazonaws.com
   docker tag pdf-validator:latest <acct>.dkr.ecr.<region>.amazonaws.com/pdf-validator:latest
   docker push <acct>.dkr.ecr.<region>.amazonaws.com/pdf-validator:latest
   ```

2. On the EC2 host (Docker installed):

   ```bash
   docker pull <acct>.dkr.ecr.<region>.amazonaws.com/pdf-validator:latest
   docker run -d --restart unless-stopped \
     --name pdf-validator \
     -p 8080:8080 \
     -v /data/pdfs:/pdfs:ro \
     -v /data/reports:/reports \
     <acct>.dkr.ecr.<region>.amazonaws.com/pdf-validator:latest
   ```

3. Open port **8080** in the instance security group (or place the instance
   behind an ALB and target port 8080).

4. Recommended instance size: `t3.medium` or larger. Validation of a
   ~1 MB PDF can produce an ~11 MB report and needs ~1 GB of heap for the
   worst-case corpus we've seen.

### Path safety note

`/api/validate` accepts arbitrary local filesystem paths. This is fine when the
service is only reachable inside a trusted network (VPC, private subnet, or
behind auth). Do **not** expose the endpoint publicly without adding an
allow-list or authenticating the request — a caller could ask the service to
read or overwrite any file the container user has access to.