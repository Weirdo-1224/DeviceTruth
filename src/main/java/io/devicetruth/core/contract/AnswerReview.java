package io.devicetruth.core.contract;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** 复核 v1：提议与宿主采纳结果共用结构，模型填写这些字段不代表已经通过复核。 */
public final class AnswerReview {
    private AnswerReview() {}

    public enum Nature { DOCUMENT_FACT, USER_REPORT, HISTORICAL_RECORD, DEVICE_INFERENCE, INDEPENDENT_VERIFICATION }
    public enum Position { SUMMARY, CLAIM, SUGGESTION, UNCERTAINTY, LIMITATION, PROCEDURE_STEP }
    public enum Disposition { RETAINED, REMOVED, PENDING }

    /** 批量模型响应只回给定单元的判定，不重复输出草案正文。 */
    public record Assessment(String unitId, Nature nature, String subjectId, Instant observedAt,
                             List<Support> supports, List<String> dependsOn,
                             Disposition disposition, String rule) {
        public Assessment {
            Checks.id(unitId, "assessment.unitId");
            Checks.required(nature, "assessment.nature");
            Checks.required(disposition, "assessment.disposition");
            Checks.id(rule, "assessment.rule");
            supports = Checks.list(supports);
            dependsOn = Checks.list(dependsOn);
            if (supports.size() > 20 || dependsOn.size() > 100) throw new IllegalArgumentException("Assessment too large");
        }
    }
    public record Batch(List<Assessment> assessments) {
        public Batch {
            assessments = Checks.list(assessments);
            if (assessments.size() > 100) throw new IllegalArgumentException("Review batch too large");
        }
    }

    /** sourceId 只定位宿主给定证据；quote 必须是该证据实际原文的连续片段。 */
    public record Support(String sourceId, String quote) {
        public Support {
            Checks.id(sourceId, "support.sourceId");
            bounded(quote, "support.quote", 2000);
        }
    }

    public record Unit(String unitId, Position position, int index, String text, Nature nature,
                       String subjectId, Instant observedAt, List<Support> supports, List<String> dependsOn) {
        public Unit {
            Checks.id(unitId, "unitId");
            Checks.required(position, "position");
            if (index < 0 || index > 99) throw new IllegalArgumentException("Invalid unit index");
            bounded(text, "unit.text", 8000);
            Checks.required(nature, "nature");
            if (subjectId != null) Checks.id(subjectId, "subjectId");
            supports = Checks.list(supports);
            dependsOn = Checks.list(dependsOn);
            if (supports.size() > 20 || dependsOn.size() > 100)
                throw new IllegalArgumentException("Unit too large");
        }
    }

    /** 仅规则和定位进入公开审计；不携带原始草案或任意模型解释。 */
    public record Decision(String unitId, Disposition disposition, String rule, List<String> sourceIds) {
        public Decision {
            Checks.id(unitId, "decision.unitId");
            Checks.required(disposition, "disposition");
            Checks.id(rule, "decision.rule");
            sourceIds = Checks.list(sourceIds);
            if (sourceIds.size() > 20) throw new IllegalArgumentException("Too many decision sources");
        }
    }

    public record Issue(String location, String code, String reason, List<String> referenceIds) {
        public Issue {
            Checks.id(location, "issue.location");
            if (!java.util.Set.of("UNSUPPORTED_CONCLUSION", "MISLEADING_SOURCE", "UNSAFE_GUIDANCE", "CONTRADICTION").contains(code))
                throw new IllegalArgumentException("Unknown delivery issue");
            bounded(reason, "issue.reason", 2000);
            referenceIds = Checks.list(referenceIds);
        }
    }
    public record DeliveryBatch(List<Issue> issues) {
        public DeliveryBatch {
            Checks.required(issues, "delivery.issues");
            issues = Checks.list(issues);
            if (issues.size() > 30) throw new IllegalArgumentException("Too many delivery issues");
        }
    }
    public record Review(int version, List<Unit> units, List<Decision> decisions, boolean limited, List<Issue> issues) {
        public Review(int version, List<Unit> units, List<Decision> decisions, boolean limited) {
            this(version, units, decisions, limited, List.of());
        }
        public Review {
            if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported review version");
            units = Checks.list(units);
            decisions = Checks.list(decisions);
            issues = Checks.list(issues);
            if (issues.size() > 30 || version == 1 && !issues.isEmpty()
                    || version == 2 && (!units.isEmpty() || !decisions.isEmpty()))
                throw new IllegalArgumentException("Invalid versioned review");
            validateUnits(units);
            var ids = units.stream().map(Unit::unitId).collect(java.util.stream.Collectors.toSet());
            var seen = new HashSet<String>();
            for (var decision : decisions)
                if (!ids.contains(decision.unitId()) || !seen.add(decision.unitId()))
                    throw new IllegalArgumentException("Unknown or duplicate reviewed unit");
            if (!seen.equals(ids)) throw new IllegalArgumentException("Missing unit decision");
            for (var decision : decisions) {
                var unit = units.stream().filter(value -> value.unitId().equals(decision.unitId())).findFirst().orElseThrow();
                var allowed = unit.supports().stream().map(Support::sourceId).collect(java.util.stream.Collectors.toSet());
                if (!allowed.containsAll(decision.sourceIds()))
                    throw new IllegalArgumentException("Decision source outside unit evidence");
            }
        }
    }

    /** 只检查提议内部结构，来源、对象、时点和支持强度须另由宿主解析验证。 */
    public static void validateUnits(List<Unit> units) {
        if (units.size() > 100) throw new IllegalArgumentException("Too many units");
        var ids = new HashSet<String>();
        var positions = new HashSet<String>();
        for (var unit : units)
            if (!ids.add(unit.unitId()) || !positions.add(unit.position() + ":" + unit.index()))
                throw new IllegalArgumentException("Duplicate unit or position");
        for (var unit : units)
            if (!ids.containsAll(unit.dependsOn()) || unit.dependsOn().contains(unit.unitId()))
                throw new IllegalArgumentException("Unknown or self dependency");
        var resolved = new HashSet<String>();
        boolean changed;
        do {
            changed = false;
            for (var unit : units)
                if (resolved.containsAll(unit.dependsOn())) changed |= resolved.add(unit.unitId());
        } while (changed);
        if (resolved.size() != units.size()) throw new IllegalArgumentException("Cyclic unit dependencies");
    }

    private static void bounded(String text, String field, int maximum) {
        Checks.text(text, field);
        if (text.codePointCount(0, text.length()) > maximum)
            throw new IllegalArgumentException(field + " too long");
    }
}
