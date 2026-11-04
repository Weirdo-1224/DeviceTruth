package io.devicetruth.core.reasoning;

import io.devicetruth.core.contract.Models.*;
import io.devicetruth.core.reasoning.ActionProposal.*;
import java.util.List;
import java.util.Map;

/** 脚本化推理器：固定策略，只依据可见状态决定下一步，不读仓库、不看预期结果。
 *  策略顺序即"标准调查流程"：查规则 → 读出厂档案 → 提假设 → 查更换历史 →
 *  全量发现证据 → 逐条取回 → 满足条件则请求下结论，否则请求人工补充信息。 */
public final class ScriptedReasoner implements Reasoner {
    @Override public String name() { return "scripted"; }
    @Override public ActionProposal propose(Context context) {
        var s=context.state(); var task=s.task();
        // 第一步总是先查规则，明确诊断目标需要哪些前提与证据
        if(!called(context,"diagnosis.rules.query")) return call("diagnosis.rules.query",Map.of("domain",task.domain(),"symptomCode",task.symptomCode()),"Discover rule prerequisites");
        // 第二步读出厂档案，建立基线配置
        if(!called(context,"asset.profile.get")) return call("asset.profile.get",Map.of("assetId",task.assetId()),"Read baseline configuration");
        var supported=s.facts().stream().filter(f->f.status()==FactStatus.SUPPORTED).toList();
        // 有已成立事实但尚无假设时，先提一个试探性假设
        if(s.hypotheses().isEmpty()&&!supported.isEmpty()) {
            var fact=supported.getFirst();
            return new ProposeHypothesis("Tentative configuration: "+fact.subject()+" "+fact.predicate()+"="+fact.value(),List.of(fact.factId()),fact.evidenceIds());
        }
        // 查更换历史，确认基线之后是否发生过变更
        if(!called(context,"asset.history.query")) return call("asset.history.query",Map.of("assetId",task.assetId()),"Check changes since baseline");
        // 全量发现（不限定组件/来源）可用证据
        if(!called(context,"evidence.search")) return call("evidence.search",Map.of("assetId",task.assetId()),"Discover available confirmation records");
        // 逐条取回已发现但尚未读取的证据
        for(var meta:s.discoveredEvidence()) {
            if(context.evidence().stream().noneMatch(e->e.evidenceId().equals(meta.evidenceId())))
                return call("evidence.get",Map.of("assetId",task.assetId(),"evidenceId",meta.evidenceId()),"Read discovered evidence");
        }
        // 条件全部满足的规则：请求下结论
        for(var rule:s.knownRules()) {
            if(rule.conditions().stream().allMatch(c->supported.stream().anyMatch(c::matches))) {
                var facts=supported.stream().filter(f->rule.conditions().stream().anyMatch(c->c.matches(f))).toList();
                return new ProposeDecision(rule.ruleId(),facts.stream().map(Fact::factId).toList(),
                        facts.stream().flatMap(f->f.evidenceIds().stream()).distinct().toList(),List.of(),rule.allowedAdviceLevel(),"Check visible facts against candidate prerequisites");
            }
        }
        // 没有任何规则的条件被满足：承认证据不足，请求人工补充
        return new RequestInformation("Available evidence does not satisfy a candidate rule",List.of("current configuration confirmation or conflict adjudication"));
    }
    /** 某能力是否已成功调用过（以行动历史为准）。 */
    private boolean called(Context context,String id) {
        return context.state().actionHistory().stream().anyMatch(h->h.proposal() instanceof CallCapability c&&
                c.capabilityId().equals(id)&&h.observation()!=null&&h.observation().success());
    }
    private CallCapability call(String id,Map<String,String> args,String purpose) { return new CallCapability(id,args,purpose,List.of()); }
}
