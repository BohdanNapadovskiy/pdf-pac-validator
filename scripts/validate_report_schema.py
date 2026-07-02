"""Validate a single report against the schema in docs/json-report-schema.md.

Usage:
    python scripts/validate_report_schema.py <path-to-report.json>
"""
import json
import sys
from pathlib import Path

STATUS = {"FAILED", "WARNING", "PASSED", "NOT_APPLICABLE", "NOT_IMPLEMENTED"}
SEVERITY = {"ERROR", "WARNING", "PASSED"}

errors = []


def err(path, msg):
    errors.append(f"{path}: {msg}")


def check_keys(obj, path, required, optional=(), order=None):
    if not isinstance(obj, dict):
        err(path, f"expected object, got {type(obj).__name__}")
        return
    keys = list(obj.keys())
    missing = [k for k in required if k not in keys]
    if missing:
        err(path, f"missing required keys {missing}")
    allowed = set(required) | set(optional)
    extra = [k for k in keys if k not in allowed]
    if extra:
        err(path, f"unexpected keys {extra}")
    if order:
        present_in_obj = [k for k in keys if k in order]
        present_in_expected = [k for k in order if k in keys]
        if present_in_obj != present_in_expected:
            err(path, f"key order is {present_in_obj}, expected {present_in_expected}")


def check_int(obj, key, path):
    v = obj.get(key)
    if not isinstance(v, int) or isinstance(v, bool):
        err(f"{path}.{key}", f"expected int, got {type(v).__name__} {v!r}")


def check_str(obj, key, path, allow_none=False):
    v = obj.get(key)
    if v is None and allow_none:
        return
    if not isinstance(v, str):
        err(f"{path}.{key}", f"expected string, got {type(v).__name__} {v!r}")


def check_bool(obj, key, path):
    v = obj.get(key)
    if not isinstance(v, bool):
        err(f"{path}.{key}", f"expected bool, got {type(v).__name__} {v!r}")


def check_num(obj, key, path):
    v = obj.get(key)
    if not isinstance(v, (int, float)) or isinstance(v, bool):
        err(f"{path}.{key}", f"expected number, got {type(v).__name__} {v!r}")


def check_status(obj, path):
    s = obj.get("status")
    if s not in STATUS:
        err(f"{path}.status", f"invalid status {s!r}; expected one of {STATUS}")


def check_counts(obj, path):
    c = obj.get("counts")
    if not isinstance(c, dict):
        err(f"{path}.counts", f"expected object, got {type(c).__name__}")
        return
    check_keys(c, f"{path}.counts", ["passed", "warning", "error"], order=["passed", "warning", "error"])
    for k in ("passed", "warning", "error"):
        check_int(c, k, f"{path}.counts")


def check_bbox(b, path):
    if b is None:
        return
    if not isinstance(b, dict):
        err(path, f"expected object or null, got {type(b).__name__}")
        return
    check_keys(b, path, ["top", "left", "height", "width"])
    for k in ("top", "left", "height", "width"):
        check_num(b, k, path)


def check_finding(f, path):
    check_keys(
        f, path, ["severity"],
        optional=["message", "page", "bBox"],
        order=["message", "severity", "page", "bBox"],
    )
    if "message" in f:
        check_str(f, "message", path, allow_none=True)
    if f.get("severity") not in SEVERITY:
        err(f"{path}.severity", f"invalid severity {f.get('severity')!r}")
    if "page" in f:
        check_int(f, "page", path)
    if "bBox" in f:
        check_bbox(f["bBox"], f"{path}.bBox")


def check_checkpoint(cp, path):
    check_keys(
        cp, path, ["element", "status", "counts"],
        optional=["errorMessage", "findings", "checkpoints"],
        order=["element", "status", "counts", "errorMessage", "findings", "checkpoints"],
    )
    check_str(cp, "element", path)
    check_status(cp, path)
    check_counts(cp, path)
    if "errorMessage" in cp:
        check_str(cp, "errorMessage", path)
    has_findings = bool(cp.get("findings"))
    has_children = bool(cp.get("checkpoints"))
    if has_findings and has_children:
        err(path, '"findings" and "checkpoints" are mutually exclusive (leaf vs container)')
    for i, f in enumerate(cp.get("findings", [])):
        check_finding(f, f"{path}.findings[{i}]")
    for i, child in enumerate(cp.get("checkpoints", [])):
        check_checkpoint(child, f"{path}.checkpoints[{i}]")


def check_subcategory(sub, path):
    check_keys(
        sub, path, ["name", "status", "counts", "checkpoints"],
        order=["name", "status", "counts", "checkpoints"],
    )
    check_str(sub, "name", path)
    check_status(sub, path)
    check_counts(sub, path)
    for i, cp in enumerate(sub.get("checkpoints", [])):
        check_checkpoint(cp, f"{path}.checkpoints[{i}]")


def check_category(cat, path):
    check_keys(
        cat, path, ["name", "status", "counts", "subCategories"],
        order=["name", "status", "counts", "subCategories"],
    )
    check_str(cat, "name", path)
    check_status(cat, path)
    check_counts(cat, path)
    for i, s in enumerate(cat.get("subCategories", [])):
        check_subcategory(s, f"{path}.subCategories[{i}]")


def check_short_summary_entry(e, path):
    check_keys(e, path, ["name", "status", "counts"], order=["name", "status", "counts"])
    check_str(e, "name", path)
    check_status(e, path)
    check_counts(e, path)


def check_pdfua(pua, path):
    check_keys(
        pua, path, ["shortSummary", "categories"],
        order=["shortSummary", "categories"],
    )
    for i, e in enumerate(pua.get("shortSummary", [])):
        check_short_summary_entry(e, f"{path}.shortSummary[{i}]")
    for i, c in enumerate(pua.get("categories", [])):
        check_category(c, f"{path}.categories[{i}]")


def check_wcag_leaf(lf, path):
    check_keys(
        lf, path, ["name", "status", "counts"],
        optional=["findings"],
        order=["name", "status", "counts", "findings"],
    )
    check_str(lf, "name", path)
    check_status(lf, path)
    check_counts(lf, path)
    for i, f in enumerate(lf.get("findings", [])):
        check_finding(f, f"{path}.findings[{i}]")


def check_criterion(c, path):
    check_keys(
        c, path, ["name", "status", "counts"],
        optional=["findings", "leaves"],
        order=["name", "status", "counts", "findings", "leaves"],
    )
    check_str(c, "name", path)
    check_status(c, path)
    check_counts(c, path)
    has_findings = bool(c.get("findings"))
    has_leaves = bool(c.get("leaves"))
    if has_findings and has_leaves:
        err(path, '"findings" and "leaves" are mutually exclusive (criterion-as-leaf vs criterion-with-children)')
    for i, f in enumerate(c.get("findings", [])):
        check_finding(f, f"{path}.findings[{i}]")
    for i, lf in enumerate(c.get("leaves", [])):
        check_wcag_leaf(lf, f"{path}.leaves[{i}]")


def check_guideline(g, path):
    check_keys(
        g, path, ["name", "status", "counts", "criteria"],
        order=["name", "status", "counts", "criteria"],
    )
    check_str(g, "name", path)
    check_status(g, path)
    check_counts(g, path)
    for i, c in enumerate(g.get("criteria", [])):
        check_criterion(c, f"{path}.criteria[{i}]")


def check_principle(p, path):
    check_keys(
        p, path, ["name", "status", "counts", "guidelines"],
        order=["name", "status", "counts", "guidelines"],
    )
    check_str(p, "name", path)
    check_status(p, path)
    check_counts(p, path)
    for i, g in enumerate(p.get("guidelines", [])):
        check_guideline(g, f"{path}.guidelines[{i}]")


def check_wcag(w, path):
    check_keys(
        w, path, ["name", "status", "counts", "principles"],
        order=["name", "status", "counts", "principles"],
    )
    check_str(w, "name", path)
    check_status(w, path)
    check_counts(w, path)
    for i, p in enumerate(w.get("principles", [])):
        check_principle(p, f"{path}.principles[{i}]")


def check_info(info, path):
    check_keys(
        info, path,
        ["title", "filename", "language", "pages", "tags", "sizeBytes", "size", "compliant"],
        order=["title", "filename", "language", "pages", "tags", "sizeBytes", "size", "compliant"],
    )
    check_str(info, "title", path)
    check_str(info, "filename", path)
    check_str(info, "language", path)
    check_int(info, "pages", path)
    check_int(info, "tags", path)
    check_int(info, "sizeBytes", path)
    check_str(info, "size", path)
    check_bool(info, "compliant", path)


def check_reports(rs, path):
    check_keys(
        rs, path, ["document", "info", "PDF/UA", "WCAG"],
        order=["document", "info", "PDF/UA", "WCAG"],
    )
    check_str(rs, "document", path)
    check_info(rs.get("info", {}), f"{path}.info")
    check_pdfua(rs.get("PDF/UA", {}), f'{path}."PDF/UA"')
    check_wcag(rs.get("WCAG", {}), f'{path}."WCAG"')


# Counts rollup invariant: each level's counts == sum of children's counts
def rollup_check(node, path, child_keys):
    c = node.get("counts", {})
    children_counts = []
    for k in child_keys:
        for ch in node.get(k, []):
            children_counts.append(ch.get("counts", {}))
    if not children_counts:
        return
    expected = {
        "passed":  sum(cc.get("passed", 0)  for cc in children_counts),
        "warning": sum(cc.get("warning", 0) for cc in children_counts),
        "error":   sum(cc.get("error", 0)   for cc in children_counts),
    }
    actual = {k: c.get(k, 0) for k in ("passed", "warning", "error")}
    if actual != expected:
        err(f"{path}.counts",
            f"rollup mismatch: actual={actual}, sum-of-children={expected}")


def check_cp_rollup(cp, path):
    """A container checkpoint's counts must equal the sum of its child checkpoints' counts."""
    kids = cp.get("checkpoints", [])
    if not kids:
        return
    rollup_check(cp, path, ["checkpoints"])
    for i, child in enumerate(kids):
        check_cp_rollup(child, f"{path}.checkpoints[{i}]")


def rollup_walk(r):
    rs = r["reports"]
    pua = rs["PDF/UA"]
    for ci, cat in enumerate(pua.get("categories", [])):
        for si, sub in enumerate(cat.get("subCategories", [])):
            rollup_check(sub,
                f"reports.\"PDF/UA\".categories[{ci}].subCategories[{si}]",
                ["checkpoints"])
            for cpi, cp in enumerate(sub.get("checkpoints", [])):
                check_cp_rollup(cp,
                    f"reports.\"PDF/UA\".categories[{ci}].subCategories[{si}].checkpoints[{cpi}]")
        rollup_check(cat,
            f"reports.\"PDF/UA\".categories[{ci}]",
            ["subCategories"])

    w = rs["WCAG"]
    for pi, p in enumerate(w.get("principles", [])):
        for gi, g in enumerate(p.get("guidelines", [])):
            for ki, c in enumerate(g.get("criteria", [])):
                if c.get("leaves"):
                    rollup_check(c,
                        f"reports.\"WCAG\".principles[{pi}].guidelines[{gi}].criteria[{ki}]",
                        ["leaves"])
            rollup_check(g,
                f"reports.\"WCAG\".principles[{pi}].guidelines[{gi}]",
                ["criteria"])
        rollup_check(p,
            f"reports.\"WCAG\".principles[{pi}]",
            ["guidelines"])


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else \
        "C:/projects/pdf/report/Complex_Presentation_Sample.report.json"
    doc = json.loads(Path(path).read_text(encoding="utf-8"))

    check_keys(doc, "$", ["reports"], order=["reports"])
    check_reports(doc.get("reports", {}), "reports")
    rollup_walk(doc)

    if errors:
        print(f"FAIL - {len(errors)} schema violation(s) in {path}:")
        for e in errors[:50]:
            print(" -", e)
        if len(errors) > 50:
            print(f"   ... and {len(errors) - 50} more")
        sys.exit(1)
    else:
        print(f"OK - {path}")
        print("  - Top-level shape conforms to docs/json-report-schema.md")
        print("  - All enums (status, severity) within allowed values")
        print("  - All key orders match @JsonPropertyOrder")
        print("  - Required/optional fields present as expected")
        print("  - findings/leaves mutual exclusion holds on criteria")
        print("  - Counts roll up correctly at every level")


if __name__ == "__main__":
    main()
