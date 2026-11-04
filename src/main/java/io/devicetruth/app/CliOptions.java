package io.devicetruth.app;

import io.devicetruth.core.contract.Models.Budget;
import java.util.HashMap;
import java.util.Set;

public record CliOptions(boolean help,String scenario,String reasoner,Budget budget) {
    public static CliOptions parse(String[] args) {
        var values=new HashMap<String,String>(); boolean help=false;
        Set<String> allowed=Set.of("scenario","reasoner","max-steps","max-repeated-actions");
        for(String arg:args) {
            if(arg.equals("--help")||arg.equals("-h")) {help=true;continue;}
            if(!arg.startsWith("--")||!arg.contains("=")) throw new IllegalArgumentException("Use --name=value or --help");
            int equals=arg.indexOf('=');String key=arg.substring(2,equals);
            if(!allowed.contains(key)||values.putIfAbsent(key,arg.substring(equals+1))!=null) throw new IllegalArgumentException("Unknown or duplicate option: "+key);
        }
        String scenario=values.getOrDefault("scenario","A"); String reasoner=values.getOrDefault("reasoner","scripted");
        if(!Set.of("A","B","C","D").contains(scenario)) throw new IllegalArgumentException("scenario must be A, B, C or D");
        if(!Set.of("scripted","qwen").contains(reasoner)) throw new IllegalArgumentException("reasoner must be scripted or qwen");
        return new CliOptions(help,scenario,reasoner,new Budget(Integer.parseInt(values.getOrDefault("max-steps","12")),Integer.parseInt(values.getOrDefault("max-repeated-actions","2"))));
    }
}
