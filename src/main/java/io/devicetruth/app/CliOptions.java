package io.devicetruth.app;

import io.devicetruth.core.contract.Models.Budget;
import java.util.HashMap;
import java.util.Set;

/** 命令行选项：解析并校验 --name=value 形式的参数，帮助信息对应 --help。 */
public record CliOptions(boolean help,String scenario,String reasoner,Budget budget) {
    /** 解析参数；非法选项、重复选项或非法取值统一抛 IllegalArgumentException，由调用方转为退出码 2。 */
    public static CliOptions parse(String[] args) {
        var values=new HashMap<String,String>(); boolean help=false;
        // 允许出现的选项白名单，其余一律拒绝
        Set<String> allowed=Set.of("scenario","reasoner","max-steps","max-repeated-actions");
        for(String arg:args) {
            if(arg.equals("--help")||arg.equals("-h")) {help=true;continue;}
            if(!arg.startsWith("--")||!arg.contains("=")) throw new IllegalArgumentException("Use --name=value or --help");
            int equals=arg.indexOf('=');String key=arg.substring(2,equals);
            if(!allowed.contains(key)||values.putIfAbsent(key,arg.substring(equals+1))!=null) throw new IllegalArgumentException("Unknown or duplicate option: "+key);
        }
        // 默认值：场景 A、脚本推理器、预算 12 步/最多重复 2 次
        String scenario=values.getOrDefault("scenario","A"); String reasoner=values.getOrDefault("reasoner","scripted");
        if(!Set.of("A","B","C","D").contains(scenario)) throw new IllegalArgumentException("scenario must be A, B, C or D");
        if(!Set.of("scripted","qwen").contains(reasoner)) throw new IllegalArgumentException("reasoner must be scripted or qwen");
        // Budget 构造器自身会校验取值范围（1..200，且重复上限不超过步数上限）
        return new CliOptions(help,scenario,reasoner,new Budget(Integer.parseInt(values.getOrDefault("max-steps","12")),Integer.parseInt(values.getOrDefault("max-repeated-actions","2"))));
    }
}
