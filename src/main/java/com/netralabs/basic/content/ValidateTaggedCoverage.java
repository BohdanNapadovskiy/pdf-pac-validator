package com.netralabs.basic.content;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNull;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.netralabs.Rule;
import com.netralabs.basic.pdfsyntax.StructUtils;
import com.netralabs.domain.Severity;
import com.netralabs.report.BBoxDTO;
import com.netralabs.report.FindingDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netralabs.basic.content.ContentWalker.walkPage;
import static com.netralabs.domain.PDFUACheckpoint.TAGGED_CONTENT_ARTIFACTS;

public class ValidateTaggedCoverage implements Rule {

    private enum Kind { ARTIFACT, TAGGED, OTHER }

    private record Frame(Kind kind, int mcid) {
        static Frame artifact()          { return new Frame(Kind.ARTIFACT, -1); }
        static Frame tagged(int mcid)    { return new Frame(Kind.TAGGED,   mcid); }
        static Frame other()             { return new Frame(Kind.OTHER,    -1); }
    }

    @Override
    public List<FindingDTO> run(Context ctx) {
        List<FindingDTO> out = new ArrayList<>();
        PdfDocument pdf = ctx.pdf();

        Map<Integer, PdfObject> parentNums = buildParentTreeNums(pdf);

        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            final int pageNum = page;
            final Set<Integer> resolvedMcids = resolvedMcidsForPage(pdf, page, parentNums);
            final Deque<Frame> scope = new ArrayDeque<>();

            walkPage(pdf, page, new Hook() {
                @Override public void onBeginArtifact()                              { scope.push(Frame.artifact()); }
                @Override public void onBeginTaggedMcid(int mcid, PdfDictionary pg)  { scope.push(Frame.tagged(mcid)); }
                @Override public void onBeginOtherMarked(PdfName tag)                { scope.push(Frame.other()); }
                @Override public void onEndMarked()                                   { if (!scope.isEmpty()) scope.pop(); }

                @Override
                public void onPainted(BBoxDTO bbox) {
                    boolean inArtifact = false;
                    int innermostTaggedMcid = -1;
                    for (Frame f : scope) {
                        if (f.kind() == Kind.ARTIFACT) inArtifact = true;
                        if (f.kind() == Kind.TAGGED && innermostTaggedMcid == -1) innermostTaggedMcid = f.mcid();
                    }

                    if (inArtifact) {
                        out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_ARTIFACTS, pageNum, null));
                        return;
                    }
                    if (innermostTaggedMcid >= 0 && resolvedMcids.contains(innermostTaggedMcid)) {
                        out.add(new FindingDTO(Severity.PASSED, TAGGED_CONTENT_ARTIFACTS, pageNum, null));
                        return;
                    }
                    out.add(new FindingDTO(Severity.ERROR, TAGGED_CONTENT_ARTIFACTS, pageNum, bbox,
                            "Object is not tagged"));
                }
            });
        }
        return out;
    }

    private static Map<Integer, PdfObject> buildParentTreeNums(PdfDocument pdf) {
        PdfDictionary str = StructUtils.structTreeRoot(pdf);
        if (str == null) return Collections.emptyMap();
        PdfDictionary parentTree = str.getAsDictionary(new PdfName("ParentTree"));
        if (parentTree == null) return Collections.emptyMap();
        Map<Integer, PdfObject> nums = new HashMap<>();
        collectNums(parentTree, nums);
        return nums;
    }

    private static void collectNums(PdfDictionary node, Map<Integer, PdfObject> nums) {
        PdfArray n = node.getAsArray(PdfName.Nums);
        if (n != null) {
            for (int i = 0; i + 1 < n.size(); i += 2) {
                PdfNumber key = n.getAsNumber(i);
                PdfObject val = n.get(i + 1);
                if (key != null) nums.put(key.intValue(), val);
            }
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                PdfDictionary kid = kids.getAsDictionary(i);
                if (kid != null) collectNums(kid, nums);
            }
        }
    }

    private static Set<Integer> resolvedMcidsForPage(PdfDocument pdf, int pageNo, Map<Integer, PdfObject> parentNums) {
        if (parentNums.isEmpty()) return Collections.emptySet();
        PdfPage page = pdf.getPage(pageNo);
        if (page == null) return Collections.emptySet();
        PdfNumber sp = page.getPdfObject().getAsNumber(new PdfName("StructParents"));
        if (sp == null) return Collections.emptySet();
        PdfObject entry = parentNums.get(sp.intValue());
        if (!(entry instanceof PdfArray arr)) return Collections.emptySet();

        Set<Integer> resolved = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            PdfObject o = arr.get(i);
            if (o == null || o instanceof PdfNull) continue;
            if (o.isIndirectReference() || o.isDictionary()) resolved.add(i);
        }
        return resolved;
    }
}
