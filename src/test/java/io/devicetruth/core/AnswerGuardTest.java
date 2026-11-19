package io.devicetruth.core;

import io.devicetruth.core.contract.AgentResult;
import io.devicetruth.core.contract.AgentResult.Certainty;
import io.devicetruth.core.contract.AgentResult.Claim;
import io.devicetruth.core.contract.AgentResult.Reference;
import io.devicetruth.core.contract.AgentTaskInput;
import io.devicetruth.core.decision.AnswerGuard;
import io.devicetruth.core.reasoning.ActionProposal.Answer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * 通用答案守卫的边界：没有现场核验不得确诊、危险动作建议不得进入答案、
 * 混合资料的 dataNature 取保守值、claim 中单独声明的引用必须出现在答案引用里。
 */
class AnswerGuardTest {
    private final AnswerGuard guard = new AnswerGuard();

    private static Reference reference(String id, String dataNature) {
        return new Reference(id, "KNOWLEDGE_DOCUMENT", "doc-" + id, "2026.1", "chunk-" + id,
                "充电系统检修手册（演示资料）", null, "3 直流快充中断", "直流快充中断通常发生在通信超时之后。", dataNature);
    }

    private static AgentTaskInput input(String assetId) {
        Instant now = Instant.now();
        return new AgentTaskInput("owner-a", "project-a", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "新能源汽车充电中断，没有故障码", List.of(new AgentTaskInput.Message("USER", "新能源汽车充电中断，没有故障码")),
                assetId, now, now, 0, 0, 12);
    }

    private static Answer answer(String category, String summary, List<Claim> claims, List<String> referenceIds) {
        return new Answer(category, summary, claims, List.of(), List.of(), List.of(), referenceIds);
    }

    @Test void claimKindAndRelevanceAreIndependentOfCertaintyAndLegacyValuesStayUnknown() throws Exception {
        var collected = List.of(reference("r1", "SIMULATED"));
        var observation = new Claim("模拟观测显示胎压信号", List.of("r1"), Certainty.SUPPORTED,
                Claim.Kind.OBSERVATION, Claim.Relevance.CONTEXT);
        var valid = guard.review(input(null), collected,
                answer("POSSIBLE_CAUSES", "资料只支持背景观测。", List.of(observation), List.of("r1")));
        assertThat(valid.allowed()).isTrue();
        assertThat(valid.answer().claims().getFirst().kind()).isEqualTo(Claim.Kind.OBSERVATION);
        assertThat(valid.answer().claims().getFirst().relevance()).isEqualTo(Claim.Relevance.CONTEXT);

        var unrelatedCause = new Claim("无关原因", List.of("r1"), Certainty.POSSIBLE,
                Claim.Kind.CAUSE, Claim.Relevance.UNRELATED);
        assertThat(guard.review(input(null), collected,
                answer("POSSIBLE_CAUSES", "候选分析", List.of(unrelatedCause), List.of("r1"))).code())
                .isEqualTo("INVALID_ANSWER");
        var unknownDirect = new Claim("分类不明的直接判断", List.of("r1"), Certainty.POSSIBLE,
                Claim.Kind.UNKNOWN, Claim.Relevance.DIRECT);
        assertThat(guard.review(input(null), collected,
                answer("KNOWLEDGE", "资料回答", List.of(unknownDirect), List.of("r1"))).code())
                .isEqualTo("INVALID_ANSWER");
        var knowledgeCause = new Claim("一般原因", List.of("r1"), Certainty.POSSIBLE,
                Claim.Kind.CAUSE, Claim.Relevance.DIRECT);
        assertThat(guard.review(input(null), collected,
                answer("KNOWLEDGE", "资料可以解释一般原因", List.of(knowledgeCause), List.of("r1"))).allowed()).isTrue();

        var legacy = io.devicetruth.adapter.Json.MAPPER.readValue(
                "{\"text\":\"旧判断\",\"referenceIds\":[],\"certainty\":\"POSSIBLE\"}", Claim.class);
        assertThat(legacy.kind()).isEqualTo(Claim.Kind.UNKNOWN);
        assertThat(legacy.relevance()).isEqualTo(Claim.Relevance.UNKNOWN);
        assertThat(guard.review(input(null), collected,
                answer("POSSIBLE_CAUSES", "旧结果仍可读取", List.of(legacy), List.of("r1"))).allowed()).isTrue();
    }

    @Test void subjectDeclarationIsValidatedAndMultipleCandidatesStayAmbiguous() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var declared = new Answer("POSSIBLE_CAUSES", "资料只支持候选原因。", List.of(), List.of(), List.of(),
                List.of(), List.of("r1"), "EV-DEMO-017");
        var confirmed = guard.review(input(null), collected, declared, false, java.util.Set.of("EV-DEMO-017"));
        assertThat(confirmed.allowed()).isTrue();
        assertThat(confirmed.answer().subjectAssetId()).isEqualTo("EV-DEMO-017");
        assertThat(confirmed.answer().assetResolution()).isEqualTo("CONFIRMED");

        assertThat(guard.review(input("EV-DEMO-001"), collected, declared, false,
                java.util.Set.of("EV-DEMO-001", "EV-DEMO-017")).code()).isEqualTo("SUBJECT_MISMATCH");
        assertThat(guard.review(input(null), collected, declared, false, java.util.Set.of("EV-DEMO-001")).code())
                .isEqualTo("SUBJECT_MISMATCH");

        var undeclared = answer("POSSIBLE_CAUSES", "资料只支持候选原因。", List.of(), List.of("r1"));
        var ambiguous = guard.review(input(null), collected, undeclared, false,
                java.util.Set.of("EV-DEMO-001", "EV-DEMO-017"));
        assertThat(ambiguous.allowed()).isTrue();
        assertThat(ambiguous.answer().subjectAssetId()).isNull();
        assertThat(ambiguous.answer().assetResolution()).isEqualTo("AMBIGUOUS");
        var single = guard.review(input(null), collected, undeclared, false, java.util.Set.of("EV-DEMO-017"));
        assertThat(single.answer().subjectAssetId()).isNull();
        assertThat(single.answer().assetResolution()).isNull();
    }

    @Test void confirmedFindingIsRejectedWithoutFieldVerification() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("CONFIRMED_FINDING", "已确诊为充电控制器损坏，可以直接更换。",
                List.of(new Claim("控制器损坏", List.of("r1"), Certainty.CONFIRMED)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNCONFIRMED_FINDING");
        // 拒绝时不会返回任何可展示正文，杜绝“降级但仍写确诊”
        assertThat(verdict.answer()).isNull();
    }

    @Test void confirmedClaimAloneIsRejectedEvenUnderKnowledgeCategory() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("KNOWLEDGE", "资料说明可以先读充电桩日志。",
                List.of(new Claim("已确诊为桩侧中止", List.of("r1"), Certainty.CONFIRMED)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNCONFIRMED_FINDING");
    }

    @Test void fabricatedOrUnauthorizedAssetCannotConfirmEither() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("CONFIRMED_FINDING", "已确诊：该车充电控制器型号为 D120B。",
                List.of(new Claim("控制器为 D120B", List.of("r1"), Certainty.SUPPORTED)), List.of("r1"));

        // 任意合法字符串都不是授权证据：格式校验通过也不能确诊
        var fabricated = guard.review(input("CAR-NOT-AUTHORIZED"), collected, draft);
        assertThat(fabricated.allowed()).isFalse();
        assertThat(fabricated.code()).isEqualTo("UNCONFIRMED_FINDING");

        // 只有服务器判定“已核验”才允许确诊，并且答案必须带现场核验处置
        var verified = guard.review(input("CAR-B"), collected, draft, true);
        assertThat(verified.allowed()).isTrue();
        assertThat(verified.answer().category()).isEqualTo(AgentResult.Category.CONFIRMED_FINDING);
        assertThat(verified.answer().safetyDisposition()).isEqualTo("REQUIRES_FIELD_VERIFICATION");
    }

    @Test void knowledgeOnlyAnswerStaysPossibleCausesAndSimulated() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "资料建议先读取充电桩故障日志。",
                List.of(new Claim("先读取充电桩故障日志", List.of("r1"), Certainty.SUPPORTED)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().category()).isEqualTo(AgentResult.Category.POSSIBLE_CAUSES);
        assertThat(verdict.answer().claims()).allMatch(claim -> claim.certainty() != Certainty.CONFIRMED);
        assertThat(verdict.answer().riskLevel()).isEqualTo(AgentResult.RiskLevel.MEDIUM);
        assertThat(verdict.answer().dataNature()).isEqualTo("SIMULATED");
        assertThat(verdict.answer().summary()).doesNotContain("已确诊");
    }

    @Test void dangerousControlAdviceIsRejectedInSeveralWordings() {
        var collected = List.of(reference("r1", "SIMULATED"));
        for (String dangerous : List.of(
                "请将高压互锁端子短接后再充电",
                "先给车辆上电，再观察充电状态",
                "可以绕过互锁继续充电",
                "把电池包拆装下来检查接触器",
                "直接强制闭合充电接触器")) {
            var verdict = guard.review(input(null), collected,
                    answer("POSSIBLE_CAUSES", dangerous, List.of(), List.of("r1")));
            assertThat(verdict.allowed()).as(dangerous).isFalse();
            assertThat(verdict.code()).as(dangerous).isEqualTo("UNSAFE_ANSWER");
        }
        // 建议里夹带危险动作同样拒绝，不能只检查 summary
        var inSuggestion = new Answer("POSSIBLE_CAUSES", "资料建议先读日志。", List.of(), List.of(
                "将高压互锁端子短接以复现故障"), List.of(), List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, inSuggestion).code()).isEqualTo("UNSAFE_ANSWER");
        // 断言正文里的危险动作也拒绝
        var inClaim = new Answer("POSSIBLE_CAUSES", "资料建议先读日志。",
                List.of(new Claim("将高压互锁端子短接即可恢复", List.of("r1"), Certainty.SUPPORTED)),
                List.of(), List.of(), List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, inClaim).code()).isEqualTo("UNSAFE_ANSWER");
    }

    @Test void safetyBoundaryStatementWithCitationIsAllowedAtHighRisk() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("KNOWLEDGE",
                "任何涉及高压上电、短接互锁或拆装电池包的操作，都必须由具备资质的人员在现场执行。",
                List.of(new Claim("高压作业必须由具备资质的人员现场执行", List.of("r1"), Certainty.SUPPORTED)),
                List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().riskLevel()).isEqualTo(AgentResult.RiskLevel.HIGH);
        assertThat(verdict.answer().safetyDisposition()).isEqualTo("REQUIRES_QUALIFIED_PERSONNEL");
    }

    @Test void mixedOrMissingDataNatureStaysSimulated() {
        var mixed = List.of(reference("r1", "AUTHORIZED"), reference("r2", "SIMULATED"));
        var draft = answer("KNOWLEDGE", "资料说明先读日志。",
                List.of(new Claim("先读日志", List.of("r1"), Certainty.SUPPORTED),
                        new Claim("再看温度", List.of("r2"), Certainty.SUPPORTED)), List.of("r1", "r2"));
        assertThat(guard.review(input(null), mixed, draft).answer().dataNature()).isEqualTo("SIMULATED");

        var authorized = List.of(reference("r1", "AUTHORIZED"));
        var authorizedDraft = answer("KNOWLEDGE", "资料说明先读日志。",
                List.of(new Claim("先读日志", List.of("r1"), Certainty.SUPPORTED)), List.of("r1"));
        assertThat(guard.review(input(null), authorized, authorizedDraft).answer().dataNature()).isEqualTo("AUTHORIZED");

        var noReference = answer("KNOWLEDGE", "没有可引用的资料，建议补充资产编号后重试。", List.of(), List.of());
        var noReferenceVerdict = guard.review(input(null), List.of(), noReference);
        assertThat(noReferenceVerdict.allowed()).isTrue();
        assertThat(noReferenceVerdict.answer().dataNature()).isEqualTo("SIMULATED");
        assertThat(noReferenceVerdict.answer().limitations()).anyMatch(text -> text.contains("未使用项目知识资料"));
    }

    @Test void claimOnlyCitationsAppearInTheAnswerReferences() {
        var collected = List.of(reference("r1", "SIMULATED"), reference("r2", "SIMULATED"));
        // 顶层只声明 r1，断言里还用了 r2：两者都必须出现在答案引用列表中
        var draft = answer("POSSIBLE_CAUSES", "资料说明先读日志再核对温度。",
                List.of(new Claim("先读日志", List.of("r1"), Certainty.SUPPORTED),
                        new Claim("再核对最高单体温度", List.of("r2"), Certainty.SUPPORTED)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().referenceIds()).containsExactly("r1", "r2");
        for (String id : verdict.answer().referenceIds())
            assertThat(collected).anyMatch(reference -> reference.referenceId().equals(id));
    }

    @Test void fabricatedClaimCitationIsStillRejected() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("KNOWLEDGE", "资料说明先读日志。",
                List.of(new Claim("先读日志", List.of("r1", "not-collected"), Certainty.SUPPORTED)), List.of("r1"));
        assertThat(guard.review(input(null), collected, draft).code()).isEqualTo("INVALID_CITATION");
    }

    @Test void unsupportedCertaintyOrUnknownCategoryIsRejected() {
        var collected = List.of(reference("r1", "SIMULATED"));
        var unknownCategory = answer("PROBABLY_FINE", "资料说明先读日志。", List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, unknownCategory).code()).isEqualTo("INVALID_ANSWER");

        var supportedWithoutCitation = answer("KNOWLEDGE", "资料说明先读日志。",
                List.of(new Claim("先读日志", List.of(), Certainty.SUPPORTED)), List.of());
        assertThat(guard.review(input(null), collected, supportedWithoutCitation).code()).isEqualTo("INVALID_CITATION");
    }

    @Test void confirmedWordingInSummaryBypassingEnumsIsRejected() {
        // P1-1：类别填 KNOWLEDGE、claims 为空，只在摘要写“已确诊”，枚举校验拦不住，正文级检查必须拒绝
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("KNOWLEDGE", "已确诊该车控制器损坏，可以直接更换控制器。", List.of(), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNCONFIRMED_FINDING");
        assertThat(verdict.answer()).isNull();
    }

    @Test void confirmedWordingInPossibleClaimTextIsRejected() {
        // P1-1：断言标为 POSSIBLE、类别也是 POSSIBLE_CAUSES，正文写“已确诊”同样不得放行
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "资料说明先读日志。",
                List.of(new Claim("已确诊该车控制器损坏", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNCONFIRMED_FINDING");
    }

    @Test void unconfirmedWordingAndVerifiedAnswerStayAllowed() {
        var collected = List.of(reference("r1", "SIMULATED"));
        // 否定/待查/建议现场核实的表述不构成确诊陈述
        var softened = answer("POSSIBLE_CAUSES", "目前无法确诊，需要现场读取日志后再判断。",
                List.of(new Claim("疑似控制器损坏，建议现场确认", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));
        assertThat(guard.review(input(null), collected, softened).allowed()).isTrue();

        // 服务器判定 fieldVerified 后，确诊正文与 CONFIRMED_FINDING 都放行
        var confirmed = answer("CONFIRMED_FINDING", "已确诊该车控制器损坏。",
                List.of(new Claim("控制器损坏", List.of("r1"), Certainty.CONFIRMED)), List.of("r1"));
        var verified = guard.review(input("CAR-B"), collected, confirmed, true);
        assertThat(verified.allowed()).isTrue();
        assertThat(verified.answer().safetyDisposition()).isEqualTo("REQUIRES_FIELD_VERIFICATION");
    }

    @Test void adversativeClauseCannotSmuggleDangerousAction() {
        var collected = List.of(reference("r1", "SIMULATED"));
        // P1-2：同一句里“有风险”与肯定操作用转折并列，安全词不得为动作子句放行
        var draft = answer("POSSIBLE_CAUSES", "存在热失控风险，但请短接高压互锁端子。", List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, draft).code()).isEqualTo("UNSAFE_ANSWER");

        // 冒号后夹带动作同样按独立子句判断
        var colon = answer("POSSIBLE_CAUSES", "风险提示：短接端子可复现故障。", List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, colon).code()).isEqualTo("UNSAFE_ANSWER");
    }

    @Test void dangerousActionInUncertaintiesOrLimitationsIsRejected() {
        // P1-2：uncertainties 与 limitations 也会返回给用户，夹带危险动作必须拒绝
        var collected = List.of(reference("r1", "SIMULATED"));
        var inUncertainties = new Answer("POSSIBLE_CAUSES", "资料说明先读日志。", List.of(), List.of(),
                List.of("紧急时可先上电观察"), List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, inUncertainties).code()).isEqualTo("UNSAFE_ANSWER");

        var inLimitations = new Answer("POSSIBLE_CAUSES", "资料说明先读日志。", List.of(), List.of(),
                List.of(), List.of("必要时可短接互锁端子"), List.of("r1"));
        assertThat(guard.review(input(null), collected, inLimitations).code()).isEqualTo("UNSAFE_ANSWER");
    }

    @Test void prohibitiveSafetyHintsStayAllowed() {
        // 正常禁止性安全提示不因含有动作词而被误伤
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("KNOWLEDGE",
                "严禁短接高压互锁端子，涉及上电或拆装电池包必须由具备资质的专业人员断电验电后处理。",
                List.of(new Claim("不得绕过互锁，不得强制闭合接触器", List.of("r1"), Certainty.SUPPORTED)),
                List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().riskLevel()).isEqualTo(AgentResult.RiskLevel.HIGH);
    }

    @Test void weakSafetyWordsCannotAuthorizeDangerousAction() {
        // P1：弱安全词（注意风险/验电/断电）不能让肯定的操作指令变得安全
        var collected = List.of(reference("r1", "SIMULATED"));
        for (String dangerous : List.of(
                "请短接高压互锁端子，注意风险。",
                "验电后短接互锁端子。",
                "断电后可先上电观察充电状态。",
                "存在一定风险，但短接互锁端子即可恢复充电。")) {
            var verdict = guard.review(input(null), collected,
                    answer("POSSIBLE_CAUSES", dangerous, List.of(), List.of("r1")));
            assertThat(verdict.allowed()).as(dangerous).isFalse();
            assertThat(verdict.code()).as(dangerous).isEqualTo("UNSAFE_ANSWER");
            assertThat(verdict.answer()).as(dangerous).isNull();
        }
        // 危险操作出现在任意返回字段都不能绕过：五个字段逐一验证
        var inClaim = new Answer("POSSIBLE_CAUSES", "资料说明先读日志。",
                List.of(new Claim("请短接互锁端子以复现故障", List.of("r1"), Certainty.SUPPORTED)),
                List.of(), List.of(), List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, inClaim).code()).isEqualTo("UNSAFE_ANSWER");
        var inUncertainty = new Answer("POSSIBLE_CAUSES", "资料说明先读日志。", List.of(), List.of(),
                List.of("实在不行可短接互锁端子"), List.of(), List.of("r1"));
        assertThat(guard.review(input(null), collected, inUncertainty).code()).isEqualTo("UNSAFE_ANSWER");
    }

    @Test void qualifiedBoundaryStatementStaysAllowedWithServerNotice() {
        // “必须由具备资质人员执行”的边界声明可放行，但高风险处置要附服务端受控安全提示
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("KNOWLEDGE", "高压上电与拆装电池包必须由具备资质的专业人员现场执行。",
                List.of(), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().riskLevel()).isEqualTo(AgentResult.RiskLevel.HIGH);
        assertThat(verdict.answer().safetyDisposition()).isEqualTo("REQUIRES_QUALIFIED_PERSONNEL");
        assertThat(verdict.answer().limitations())
                .anyMatch(text -> text.contains("本系统不提供具体操作步骤"));
        // 幂等：模型自己抄了一遍同样的服务端提示也不重复追加
        var copied = new Answer("KNOWLEDGE", "高压上电与拆装电池包必须由具备资质的专业人员现场执行。",
                List.of(), List.of(), List.of(), List.of("涉及高压等高风险处置：本系统不提供具体操作步骤，"
                        + "必须由具备资质的专业人员按厂家规程断电、验电后现场处理。"), List.of("r1"));
        var again = guard.review(input(null), collected, copied);
        assertThat(again.allowed()).isTrue();
        assertThat(again.answer().limitations().stream()
                .filter(text -> text.contains("本系统不提供具体操作步骤")).count()).isEqualTo(1);
    }

    @Test void deviceDefectAssertionWithoutFieldEvidenceIsRejected() {
        // P1：没有“确诊”二字，但把知识资料当成眼前设备的现场证据，同样关闭输出路径
        var collected = List.of(reference("r1", "SIMULATED"));
        var inSummary = answer("KNOWLEDGE", "该设备的故障原因是充电控制器损坏。", List.of(), List.of("r1"));
        var verdict = guard.review(input(null), collected, inSummary);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNCONFIRMED_FINDING");
        assertThat(verdict.answer()).isNull();

        // 断言字段里的设备确定性故障同样拒绝
        var inClaim = answer("POSSIBLE_CAUSES", "资料说明先读日志。",
                List.of(new Claim("该车充电控制器已损坏", List.of("r1"), Certainty.SUPPORTED)), List.of("r1"));
        assertThat(guard.review(input(null), collected, inClaim).code()).isEqualTo("UNCONFIRMED_FINDING");

        // 服务端判定 fieldVerified 后才允许这类断言
        var verified = guard.review(input("CAR-B"), collected, inSummary, true);
        assertThat(verified.allowed()).isTrue();
    }

    @Test void possibleCauseWithCitationStaysFullyUsable() {
        // 可能原因 + 知识引用：保留引用、结构与限制，正常展示
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "控制器异常可能导致充电中断。",
                List.of(new Claim("控制器异常可能导致中断", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().category()).isEqualTo(AgentResult.Category.POSSIBLE_CAUSES);
        assertThat(verdict.answer().claims()).allMatch(claim -> claim.certainty() == Certainty.POSSIBLE);
        assertThat(verdict.answer().referenceIds()).containsExactly("r1");
        assertThat(verdict.answer().riskLevel()).isEqualTo(AgentResult.RiskLevel.MEDIUM);
    }

    @Test void negativeFindingWordingIsNotMisjudged() {
        // “尚未确诊/需要现场核实”是正常限制说明，不得误判为确诊
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "尚未确诊，需要现场核实。",
                List.of(new Claim("无法确诊，建议现场确认后再判断", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));

        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.answer().summary()).contains("尚未确诊");
    }

    @Test void mixedProhibitionAndActionInSameSentenceIsRejected() {
        // 02-02 复审 P1 原文复现：“不要短接互锁端子，随后短接高压互锁端子。”
        // 同一句里前半句的禁止词不能放行后半句的肯定动作
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "不要短接互锁端子，随后短接高压互锁端子。",
                List.of(new Claim("不要短接互锁端子，随后短接高压互锁端子", List.of("r1"), Certainty.POSSIBLE)),
                List.of("r1"));
        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNSAFE_ANSWER");

        // 纯禁止性提示（每个子句都带禁止词）仍然允许展示
        var safe = answer("POSSIBLE_CAUSES", "不要短接高压互锁端子，也不要拆装电池包。",
                List.of(new Claim("不要短接高压互锁端子", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));
        var allowed = guard.review(input(null), collected, safe);
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.answer().riskLevel()).isEqualTo(AgentResult.RiskLevel.HIGH);
    }

    @Test void definiteFaultCompletionWithoutFieldEvidenceIsRejectedEvenWithTentativePrefix() {
        // 02-02 复审 P1 原文复现：“该设备可能有多种故障，充电控制器已经损坏。”
        // 前半句的“可能”不得软化后半句的完成时态故障断言
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "该设备可能有多种故障，充电控制器已经损坏。",
                List.of(new Claim("该设备可能有多种故障，充电控制器已经损坏", List.of("r1"), Certainty.POSSIBLE)),
                List.of("r1"));
        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("UNCONFIRMED_FINDING");

        // 同样的内容在有服务器现场核验后才允许
        assertThat(guard.review(input("CAR-B"), collected, draft, true).allowed()).isTrue();
    }

    @Test void negatedDefiniteFindingWordingIsNotMisjudged() {
        // 02-02 复审 P2 原文复现：“尚未确诊为控制器损坏”不得被当成“已确诊”
        var collected = List.of(reference("r1", "SIMULATED"));
        var draft = answer("POSSIBLE_CAUSES", "尚未确诊为控制器损坏，建议现场核验后再判断。",
                List.of(new Claim("尚未确诊为控制器损坏", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));
        var verdict = guard.review(input(null), collected, draft);
        assertThat(verdict.allowed()).isTrue();

        // 肯定表述仍必须拒绝
        var affirmative = answer("POSSIBLE_CAUSES", "已确诊为控制器损坏。",
                List.of(new Claim("已确诊为控制器损坏", List.of("r1"), Certainty.POSSIBLE)), List.of("r1"));
        assertThat(guard.review(input(null), collected, affirmative).code()).isEqualTo("UNCONFIRMED_FINDING");
    }

    @Test void documentedIdentityAndObservationAreNotDefectAssertions() {
        var collected = List.of(reference("r1", "SIMULATED"));
        for (String text : List.of("模拟配置记录显示该车登记型号为 Model 3 2024+。",
                "历史模拟观测中，该车充电状态为 STOPPED。")) {
            var draft = answer("KNOWLEDGE", text,
                    List.of(new Claim(text, List.of("r1"), Certainty.SUPPORTED)), List.of("r1"));
            assertThat(guard.review(input("EV-DEMO-004"), collected, draft).allowed()).isTrue();
        }
    }

    @Test void identityWordingDoesNotExemptDefectAssertions() {
        var collected = List.of(reference("r1", "SIMULATED"));
        for (String text : List.of("该车登记型号为 Model 3，充电控制器已经损坏。",
                "该车是充电控制器损坏。", "该车为充电控制器故障。")) {
            var draft = answer("POSSIBLE_CAUSES", text, List.of(), List.of("r1"));
            assertThat(guard.review(input("EV-DEMO-004"), collected, draft).code())
                    .isEqualTo("UNCONFIRMED_FINDING");
        }
    }

    @Test void chargingFailureSymptomIsNotAnEnergizingInstruction() {
        var draft = answer("POSSIBLE_CAUSES", "交流充不上电还可能有多种解释，现有记录不足以确定原因。",
                List.of(), List.of());
        assertThat(guard.review(input(null), List.of(), draft).allowed()).isTrue();
        assertThat(AnswerGuard.unsafeFollowUp("是刚插枪充不上电，还是充电中途停止？", List.of())).isFalse();
    }

    @Test void chargingFailureDoesNotExemptActualControlInstructions() {
        for (String text : List.of("充不上电，请先上电再观察。", "充不上电时请强制复位。", "充不上电，请拆装电池包。")) {
            var draft = answer("POSSIBLE_CAUSES", text, List.of(), List.of());
            assertThat(guard.review(input(null), List.of(), draft).code()).isEqualTo("UNSAFE_ANSWER");
            assertThat(AnswerGuard.unsafeFollowUp(text, List.of())).isTrue();
        }
    }

    @Test void followUpQuestionsShareTheSameSafetyBoundary() {
        // 02-02 复审 P1：追问同样展示给用户，不得夹带设备操作指令或确诊断言
        assertThat(AnswerGuard.unsafeFollowUp("请短接高压互锁端子后告诉我现象。", List.of("现象")))
                .isTrue();
        assertThat(AnswerGuard.unsafeFollowUp("请补充现场情况。", List.of("短接互锁端子")))
                .isTrue();
        assertThat(AnswerGuard.unsafeFollowUp("已确诊为控制器损坏，请确认是否更换。", List.of()))
                .isTrue();
        assertThat(AnswerGuard.unsafeFollowUp("是交流慢充还是直流快充？SOC 大约多少？",
                List.of("充电方式", "SOC"))).isFalse();
    }
}
