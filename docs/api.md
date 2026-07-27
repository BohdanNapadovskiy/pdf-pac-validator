# PDF PAC Validator — Usage Guide

REST + CLI PDF/UA & WCAG 2.2 accessibility validator. Produces PAC-style JSON
reports and uploads them to S3.

## Table of contents

- [Running the app](#running-the-app)
- [REST API](#rest-api)
- [Legacy CLI](#legacy-cli)
- [Report output](#report-output)
- [Configuration](#configuration)
- [Deployment (EC2)](#deployment-ec2)

---

## Running the app

Both the source PDF and the generated reports live in S3. The process needs
AWS credentials with `s3:GetObject` on the source key and `s3:PutObject` on
the destination prefix.

### Local (Java 17+)

```bash
mvn -DskipTests package         # produces target/pdf-validator-1.0-SNAPSHOT.jar (executable)
java -jar target/pdf-validator-1.0-SNAPSHOT.jar
```

Or, for dev iteration:

```bash
mvn spring-boot:run
```

Server listens on `http://localhost:8080`. AWS credentials are picked up via
the default provider chain (`~/.aws/credentials`, `AWS_*` env vars, or an
instance profile).

### Docker

```bash
docker build -t pdf-validator:latest .

docker run --rm -p 8080:8080 \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=... \
  -e AWS_SECRET_ACCESS_KEY=... \
  pdf-validator:latest
```

On EC2 with an instance profile, drop the key env vars — the SDK reads
credentials from IMDS automatically.

---

## REST API

### `POST /api/validate`

Downloads the PDF from `s3://{bucketName}/{pdfPath}`, runs the full PDF/UA +
WCAG pipeline, and uploads **both** the simple and detailed PAC reports to
`s3://{bucketName}/{outputFolderPath}/`. The response returns the S3 URIs
where the reports were written — clients read the JSON directly from S3.

**Request headers**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Request body**

```json
{
  "bucketName": "pdf-tagging-data-381490270597",
  "pdfPath": "input/sample.pdf",
  "outputFolderPath": "reports"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `bucketName` | string | yes | S3 bucket that holds both the source PDF and the destination reports. |
| `pdfPath` | string | yes | S3 key of the source PDF inside `bucketName`. |
| `outputFolderPath` | string | no | S3 key prefix under `bucketName` where the two reports are written. Blank / omitted means the bucket root. |

Report keys are `{outputFolderPath}/{pdf-basename}.simple.json` and
`{outputFolderPath}/{pdf-basename}.detailed.json`. Existing objects at those
keys are overwritten.

**Response body**

```json
{
  "jobId": "6a9754b2-3fe5-405b-95c0-f71c3d861e23",
  "sourceFileName": "sample.pdf",
  "simpleReportS3Uri": "s3://pdf-tagging-data-381490270597/reports/sample.simple.json",
  "detailedReportS3Uri": "s3://pdf-tagging-data-381490270597/reports/sample.detailed.json",
  "status": "success"
}
```

| Field | Type | Description |
|---|---|---|
| `jobId` | string \| null | UUID for the run. `null` on failure. |
| `sourceFileName` | string \| null | Filename portion of `pdfPath`. `null` when the request was invalid. |
| `simpleReportS3Uri` | string \| null | `s3://…` URI of the simple report. `null` on failure. |
| `detailedReportS3Uri` | string \| null | `s3://…` URI of the detailed report. `null` on failure. |
| `status` | string | `"success"` or `"failed"`. |
| `message` | string \| null | Human-readable error message on failure; omitted on success. |

**Status codes**

| Code | Meaning |
|---|---|
| `200 OK` | Both reports generated and uploaded. |
| `400 Bad Request` | `bucketName` or `pdfPath` missing / blank. |
| `500 Internal Server Error` | Download, validation, or upload failed. Server log contains the stack trace. |

### Examples

**cURL**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "bucketName": "pdf-tagging-data-381490270597",
    "pdfPath": "input/sample.pdf",
    "outputFolderPath": "reports"
  }'
```

**cURL — read the report back from S3**

```bash
resp=$(curl -sX POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"bucketName":"pdf-tagging-data-381490270597","pdfPath":"input/sample.pdf","outputFolderPath":"reports"}')
uri=$(echo "$resp" | jq -r .detailedReportS3Uri)
# strip the s3:// scheme and split bucket / key
key="${uri#s3://*/}"
bucket=$(echo "$uri" | sed -E 's#^s3://([^/]+)/.*#\1#')
aws s3 cp "s3://${bucket}/${key}" ./detailed.json
```

**cURL — failure (missing bucket)**

```bash
curl -X POST http://localhost:8080/api/validate \
  -H "Content-Type: application/json" \
  -d '{"pdfPath":"input/sample.pdf"}'
```

Returns `HTTP 400` with `{"status":"failed","message":"bucketName and pdfPath are required"}` (other fields omitted via `@JsonInclude(NON_NULL)`).

---

## Legacy CLI

The pre-REST CLI entry point is still available as a thin wrapper around
`ValidationService`:

```bash
java -cp "target/classes;$(cat /tmp/cp.txt)" PDFValidator \
  <s3-pdf-key> [-o <s3-output-folder>] [-b <bucket>]
```

- `<s3-pdf-key>` — S3 key of the source PDF (positional, required).
- `-o` — S3 key prefix under the bucket where the reports are written; omitted means the bucket root.
- `-b` — S3 bucket name. Defaults to `aod-main-v1-staging`.

The CLI prints both S3 URIs and the `jobId` on success. AWS credentials come
from the default provider chain — the same as the REST server.

---

## Report output

Each run uploads **two JSON objects** side by side to
`s3://{bucketName}/{outputFolderPath}/`:

- `<pdf-basename>.simple.json` — hierarchical PAC-shaped tree (`{ body: { jobId, name, documentInformation, reports: [{type: "PDF/UA", uaIndex, report}, {type: "WCAG2CheckSet", report}], creationDate }, version }`). Every node carries PAC-canonical `checkId` + `caption` and counts (`errorCount / warningCount / passedCount / terminationCount`) plus severity (`Error=0 / Warning=1 / Passed=2 / Skipped=3`).
- `<pdf-basename>.detailed.json` — flat, failure-only issue list per report type. Each issue has `checkId`, `issueId`, aggregate `count`, plus a `details[]` array of `{ pageIndex, rectangle: {top, bottom, left, right} }` — one per instance so a viewer can highlight the offending region. Also carries `cropBoxRanges` (contiguous same-CropBox pages coalesced) so `rectangle` coordinates map cleanly back to the page.

Full schema and semantics: [`docs/json-report-schema.md`](json-report-schema.md).

---

## Configuration

Standard Spring Boot properties apply. The most useful ones:

| Property / env var | Default | Purpose |
|---|---|---|
| `SERVER_PORT` / `--server.port=` | `8080` | HTTP port. |
| `AWS_REGION` / `aws.region=` | `us-east-1` (in `application.properties`) | S3 client region. Must match the bucket region. |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0` (in Docker) | JVM tuning. Large PDFs are memory-hungry — the full finding list is held in memory until report upload. |
| `LOGGING_LEVEL_COM_NETRALABS` | `INFO` | Bump to `DEBUG` for per-rule tracing. |

Example — run on port 9000 with more heap:

```bash
JAVA_TOOL_OPTIONS="-Xmx2g" \
SERVER_PORT=9000 \
AWS_REGION=us-east-1 \
java -jar target/pdf-validator-1.0-SNAPSHOT.jar
```

---

## Deployment (EC2)

Current production deployment: `pdf-tagging-prod` (`i-02b30d9e5d1d74061`,
`t3.medium`, us-east-1). Public IP `18.233.161.171`, port `8080` reachable
only from the owner IP via SG `pdf-tagging-sg`. IAM role
`pdf-tagging-ec2-role` grants `AmazonS3FullAccess` +
`AmazonSSMManagedInstanceCore`.

### Redeploying

The build runs on the box itself (multi-stage `Dockerfile` bundles Maven).
Source is staged in S3 rather than pushed to ECR — simpler for this scale.
Shell access is via SSM Session Manager (no SSH key needed).

1. **Package the working tree and stage it in S3:**

   ```bash
   tar --exclude=./target --exclude=./.git --exclude=./.idea \
       -czf /tmp/pdf-pac-src.tar.gz .
   aws s3 cp /tmp/pdf-pac-src.tar.gz \
       s3://pdf-tagging-data-381490270597/build-artifacts/pdf-pac-src.tar.gz
   ```

2. **Build + redeploy on the box** (via SSM `RunShellScript` or an
   interactive `aws ssm start-session --target i-02b30d9e5d1d74061`):

   ```bash
   sudo -u ec2-user bash -c '
     set -euxo pipefail
     mkdir -p /home/ec2-user/pdf-pac-validator
     cd /home/ec2-user/pdf-pac-validator
     rm -rf ./*
     aws s3 cp s3://pdf-tagging-data-381490270597/build-artifacts/pdf-pac-src.tar.gz .
     tar xzf pdf-pac-src.tar.gz
   '
   cd /home/ec2-user/pdf-pac-validator
   docker build -t pdf-pac-validator:latest .
   docker rm -f pdf-pac-validator 2>/dev/null || true
   docker run -d --name pdf-pac-validator --restart unless-stopped \
       -p 8080:8080 -e AWS_REGION=us-east-1 \
       pdf-pac-validator:latest
   ```

3. **Smoke test** from a workstation whose IP is allowed by the SG:

   ```bash
   curl -X POST http://18.233.161.171:8080/api/validate \
     -H "Content-Type: application/json" \
     -d '{"bucketName":"pdf-tagging-data-381490270597","pdfPath":"input/sample.pdf","outputFolderPath":"reports"}'
   ```

### Networking / IAM invariants

- Ports 22 and 8080 on `pdf-tagging-sg` are restricted to a single owner-IP
  CIDR. Rotate the CIDR when the owner IP changes; do not open to `0.0.0.0/0`
  without an auth layer in front (see path safety note below).
- The `AmazonS3FullAccess` policy on `pdf-tagging-ec2-role` is broader than
  strictly needed — the app only touches `pdf-tagging-data-381490270597`.
  Tighten to a bucket-scoped policy when convenient.
- Recommended instance size: `t3.medium` or larger. Validation of a
  ~1 MB PDF can produce a ~10 MB detailed report and needs ~1 GB of heap
  for the worst-case corpus we've seen.

### Path safety note

`/api/validate` accepts arbitrary S3 keys under a caller-supplied bucket.
When the bucket is caller-controlled, the service will happily read and
overwrite any object the instance profile has access to. Do **not** expose
the endpoint publicly without adding an allow-list on `bucketName` or
authenticating the request.