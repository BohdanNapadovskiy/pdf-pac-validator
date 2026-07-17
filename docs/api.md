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

Runs the full PDF/UA + WCAG pipeline, writes **two JSON files** to disk (the
PAC-shaped simple + detailed reports), and returns the simple report inline
alongside a `jobId` the client uses to fetch the detailed one later.

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
| `outputFolder` | string | no | Absolute path to the folder where the reports are written. If omitted or blank, they are written next to the source PDF. |

The output filenames are always `<pdf-basename>.simple.json` and
`<pdf-basename>.detailed.json`. Existing files at those paths are overwritten.

**Response body**

```json
{
  "jobId": "6a9754b2-3fe5-405b-95c0-f71c3d861e23",
  "sourceFileName": "sample.pdf",
  "simpleReportPath": "C:\\projects\\pdf\\report\\sample.simple.json",
  "detailedReportPath": "C:\\projects\\pdf\\report\\sample.detailed.json",
  "status": "success",
  "simpleReport": {
    "body": { "jobId": "...", "name": "sample", "documentInformation": { ... },
              "reports": [ { "type": "PDF/UA", "uaIndex": 94.5, "report": { ... } },
                           { "type": "WCAG2CheckSet", "report": { ... } } ],
              "creationDate": "2026-07-17T09:00:00Z" },
    "version": { "major": 2, "minor": 0 }
  }
}
```

| Field | Type | Description |
|---|---|---|
| `jobId` | string \| null | UUID for the run. Use with `GET /api/report/{jobId}/…`. `null` on failure. |
| `sourceFileName` | string \| null | Filename portion of `pdfPath`. `null` when `pdfPath` was missing/blank. |
| `simpleReportPath` | string \| null | Absolute path of the written simple report. `null` on failure. |
| `detailedReportPath` | string \| null | Absolute path of the written detailed report. `null` on failure. |
| `status` | string | `"success"` or `"failed"`. |
| `simpleReport` | object \| null | Inline copy of the simple report body — the PAC-shaped hierarchical result. `null` on failure. |

**Status codes**

| Code | Meaning |
|---|---|
| `200 OK` | Reports generated and written. |
| `400 Bad Request` | `pdfPath` missing or blank. |
| `500 Internal Server Error` | Validation failed (file not found, unreadable PDF, iText/veraPDF error). Server log contains the stack trace. |

### `GET /api/report/{jobId}/detailed`

Streams the detailed report file (per-instance page + bbox for viewer
highlighting) associated with a previously generated `jobId`.

- `200 OK` with `Content-Type: application/json` — the detailed JSON body.
- `404 Not Found` — unknown `jobId` (process restarted, or job never ran).

The registry is in-memory (a `ConcurrentHashMap` in `ValidationService`) — a
process restart clears it, so persist the paths client-side if you need
durability across restarts.

### `GET /api/report/{jobId}/simple`

Same as above, but streams the simple report file. Kept mostly for symmetry
— the POST response already inlines the simple report.

### Examples

**cURL — POST + fetch detailed**

```bash
resp=$(curl -sX POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"C:/projects/pdf/sample.pdf","outputFolder":"C:/projects/pdf/report"}')
jobId=$(echo "$resp" | jq -r .jobId)
curl -s "http://localhost:8080/api/report/${jobId}/detailed" > detailed.json
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

Returns `HTTP 500` with `{"jobId":null,"sourceFileName":"does-not-exist.pdf","simpleReportPath":null,"detailedReportPath":null,"status":"failed","simpleReport":null}`.

---

## Legacy CLI

The pre-REST CLI entry point is still available:

```bash
java -cp "target/classes;$(cat /tmp/cp.txt)" PDFValidator \
  <path-to-pdf> [-o <output-folder>] [--legacy]
```

- `-o` accepts an output folder (or a full path — only the parent folder is used). When omitted, the reports are written next to the source PDF.
- `--legacy` additionally writes the pre-PAC combined `<name>.report.json` alongside the two new files.

The CLI prints the `jobId`, `simpleReportPath`, and `detailedReportPath` on success.

---

## Report output

Each run produces **two JSON files** side by side:

- `<pdf-basename>.simple.json` — hierarchical PAC-shaped tree (`{ body: { jobId, name, documentInformation, reports: [{type: "PDF/UA", uaIndex, report}, {type: "WCAG2CheckSet", report}], creationDate }, version }`). Every node carries PAC-canonical `checkId` + `caption` and counts (`errorCount / warningCount / passedCount / terminationCount`) plus severity (`Error=0 / Warning=1 / Passed=2 / Skipped=3`).
- `<pdf-basename>.detailed.json` — flat, failure-only issue list per report type. Each issue has `checkId`, `issueId`, aggregate `count`, plus a `details[]` array of `{ pageIndex, rectangle: {top, bottom, left, right} }` — one per instance so a viewer can highlight the offending region. Also carries `cropBoxRanges` (contiguous same-CropBox pages coalesced) so `rectangle` coordinates map cleanly back to the page.

Passing `--legacy` (CLI) additionally emits the pre-PAC combined
`<pdf-basename>.report.json` with the older shape
(`{ reports: { document, info, "PDF/UA", "WCAG", quality } }`).

Full schema and semantics: [`docs/json-report-schema.md`](json-report-schema.md).

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