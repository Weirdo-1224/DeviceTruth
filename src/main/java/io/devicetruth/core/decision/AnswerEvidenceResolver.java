package io.devicetruth.core.decision;

import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.contract.AnswerReview.*;
import io.devicetruth.core.reasoning.ActionProposal.Answer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 宿主从本执行段解析证据；不接受模型提供的权限、来源性质或“已核验”标记。 */
public final class AnswerEvidenceResolver {
    public record CandidateUnit(String unitId, Position position, int index, String text) {}
    public static List<CandidateUnit> candidateUnits(Answer draft) {
        var units = new ArrayList<CandidateUnit>();
        units.add(new CandidateUnit("summary.0", Position.SUMMARY, 0, draft.summary()));
        for (int index = 0; index < draft.claims().size(); index++)
            units.add(new CandidateUnit("claim." + index, Position.CLAIM, index, draft.claims().get(index).text()));
        addCandidates(units, Position.SUGGESTION, draft.suggestions());
        addCandidates(units, Position.UNCERTAINTY, draft.uncertainties());
        addCandidates(units, Position.LIMITATION, draft.limitations());
        if (units.size() > 100) throw new IllegalArgumentException("Too many answer units");
        return List.copyOf(units);
    }
    private static void addCandidates(List<CandidateUnit> units, Position position, List<String> values) {
        for (int index = 0; index < values.size(); index++)
            units.add(new CandidateUnit(position.name().toLowerCase(java.util.Locale.ROOT) + "." + index, position, index, values.get(index)));
    }
    public record Evidence(String sourceId, String sourceType, String sourceVersion, String text,
                           String dataNature) {}
    public record Resolved(List<Unit> units, List<Evidence> evidence, List<Decision> hardDecisions) {}

    public Resolved resolve(AgentTaskInput input, List<Reference> collected, Answer draft) {
        if (!Integer.valueOf(1).equals(draft.reviewVersion()))
            throw new IllegalArgumentException("Versioned units required");
        var evidence = new LinkedHashMap<String, Evidence>();
        for (var reference : collected) {
            if (evidence.putIfAbsent(reference.referenceId(), new Evidence(reference.referenceId(),
                    reference.sourceType(), reference.sourceVersion(), reference.excerpt(), reference.dataNature())) != null)
                throw new IllegalArgumentException("Duplicate evidence ID");
        }
        Set<String> referenceIds = Set.copyOf(evidence.keySet());
        for (int index = 0; index < input.messages().size(); index++) {
            var message = input.messages().get(index);
            if (!"USER".equals(message.role())) continue;
            String id = "message." + index;
            if (evidence.putIfAbsent(id, new Evidence(id, "USER_REPORT", "1", message.content(),
                    "USER_REPORTED_UNVERIFIED")) != null)
                throw new IllegalArgumentException("Conflicting evidence namespace");
        }
        Map<String, String> content = new LinkedHashMap<>();
        content.put("SUMMARY:0", draft.summary());
        for (int index = 0; index < draft.claims().size(); index++)
            content.put("CLAIM:" + index, draft.claims().get(index).text());
        add(content, "SUGGESTION", draft.suggestions());
        add(content, "UNCERTAINTY", draft.uncertainties());
        add(content, "LIMITATION", draft.limitations());
        var decisions = new ArrayList<Decision>();
        var resolvedUnits = new ArrayList<Unit>();
        var covered = new java.util.HashSet<String>();
        for (var proposed : draft.units()) {
            var supports = proposed.supports().stream().map(support -> {
                var source = evidence.get(support.sourceId());
                String original = source == null ? null : locateQuote(source.text(), support.quote());
                return original == null ? support : new Support(support.sourceId(), original);
            }).toList();
            var unit = new Unit(proposed.unitId(), proposed.position(), proposed.index(), proposed.text(),
                    proposed.nature(), proposed.subjectId(), proposed.observedAt(), supports, proposed.dependsOn());
            resolvedUnits.add(unit);
            String position = unit.position() + ":" + unit.index();
            if (!unit.text().equals(content.get(position)))
                throw new IllegalArgumentException("Unit text does not match visible answer");
            covered.add(position);
            String rule = hardRule(input, evidence, unit);
            if (rule == null && unit.position() == Position.SUMMARY && !referenceIds.containsAll(draft.referenceIds()))
                rule = "INVALID_CITATION";
            if (rule == null && unit.position() == Position.CLAIM
                    && !referenceIds.containsAll(draft.claims().get(unit.index()).referenceIds()))
                rule = "INVALID_CITATION";
            if (rule != null) decisions.add(new Decision(unit.unitId(), Disposition.REMOVED, rule, List.of()));
        }
        if (!covered.equals(content.keySet())) throw new IllegalArgumentException("Unreviewed visible answer content");
        return new Resolved(List.copyOf(resolvedUnits), List.copyOf(evidence.values()), List.copyOf(decisions));
    }

    /** 只兼容空白排版，返回实际连续原文；不改标点、数字、单位或否定词。 */
    static String locateQuote(String source, String quote) {
        if (source.contains(quote)) return quote;
        var original = normalizeLayout(source);
        var requested = normalizeLayout(quote);
        if (requested.text().isBlank()) return null;
        int start = original.text().indexOf(requested.text());
        if (start < 0) return null;
        int end = start + requested.text().length() - 1;
        return source.substring(original.starts().get(start), original.ends().get(end));
    }

    private record Layout(String text, List<Integer> starts, List<Integer> ends) {}
    private static Layout normalizeLayout(String value) {
        var text = new StringBuilder();
        var starts = new ArrayList<Integer>();
        var ends = new ArrayList<Integer>();
        for (int i = 0; i < value.length();) {
            int start = i;
            if (Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i))) {
                while (i < value.length() && (Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i)))) i++;
                int left = start == 0 ? -1 : value.codePointBefore(start);
                int right = i == value.length() ? -1 : value.codePointAt(i);
                // 仅中文相邻字/中文标点间省略排版空白，ASCII词和数字边界仍保留空格。
                if (chineseBoundary(left) && chineseBoundary(right)) continue;
                text.append(' '); starts.add(start); ends.add(i);
            } else {
                text.append(value.charAt(i)); starts.add(i); ends.add(++i);
            }
        }
        return new Layout(text.toString(), starts, ends);
    }
    private static boolean chineseBoundary(int point) {
        return point >= 0 && (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN
                || "，。；：！？、（）【】《》“”‘’".indexOf(point) >= 0);
    }

    private static String hardRule(AgentTaskInput input, Map<String, Evidence> evidence, Unit unit) {
        if (unit.subjectId() != null && !unit.subjectId().equals(input.selectedAssetId())) return "SUBJECT_MISMATCH";
        if (unit.observedAt() != null && unit.observedAt().isAfter(input.asOf())) return "TIME_OUTSIDE_SNAPSHOT";
        for (var support : unit.supports()) {
            var source = evidence.get(support.sourceId());
            if (source == null) return "INVALID_CITATION";
            if (!source.text().contains(support.quote())) return "UNSUPPORTED_QUOTE";
        }
        if (unit.nature() == Nature.INDEPENDENT_VERIFICATION
                && (unit.supports().isEmpty() || unit.supports().stream().anyMatch(s -> {
                    var source = evidence.get(s.sourceId());
                    return !"FIELD_VERIFICATION".equals(source.sourceType())
                            || "SIMULATED".equals(source.dataNature());
                }))) return "VERIFICATION_SOURCE_REQUIRED";
        if (unit.nature() == Nature.USER_REPORT && (unit.supports().isEmpty()
                || unit.supports().stream().anyMatch(s -> !"USER_REPORT".equals(evidence.get(s.sourceId()).sourceType()))))
            return "USER_SOURCE_REQUIRED";
        if (unit.nature() == Nature.DEVICE_INFERENCE && (unit.supports().isEmpty()
                || unit.supports().stream().noneMatch(s -> {
                    var source = evidence.get(s.sourceId());
                    return Set.of("USER_REPORT", "FIELD_OBSERVATION", "FIELD_VERIFICATION").contains(source.sourceType())
                            && !"SIMULATED".equals(source.dataNature());
                }))) return "CURRENT_DEVICE_EVIDENCE_REQUIRED";
        return null;
    }

    private static void add(Map<String, String> content, String position, List<String> values) {
        for (int index = 0; index < values.size(); index++) content.put(position + ":" + index, values.get(index));
    }

    /** 删除坏单元的完整依赖闭包，不能留下依赖已删结论的建议或摘要。 */
    public static List<Decision> closeDependencies(List<Unit> units, List<Decision> decisions) {
        var byId = new LinkedHashMap<String, Decision>();
        Set<String> ids = units.stream().map(Unit::unitId).collect(java.util.stream.Collectors.toSet());
        for (var decision : decisions)
            if (!ids.contains(decision.unitId()) || byId.putIfAbsent(decision.unitId(), decision) != null)
                throw new IllegalArgumentException("Invalid semantic decision IDs");
        if (!byId.keySet().equals(ids)) throw new IllegalArgumentException("Missing semantic decisions");
        boolean changed;
        do {
            changed = false;
            for (var unit : units) {
                var decision = byId.get(unit.unitId());
                if (decision.disposition() != Disposition.RETAINED) continue;
                if (unit.dependsOn().stream().anyMatch(id -> byId.get(id).disposition() != Disposition.RETAINED)) {
                    byId.put(unit.unitId(), new Decision(unit.unitId(), Disposition.REMOVED, "DEPENDENCY_REMOVED", List.of()));
                    changed = true;
                }
            }
        } while (changed);
        return List.copyOf(byId.values());
    }
}
