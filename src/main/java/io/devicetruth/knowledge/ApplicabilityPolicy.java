package io.devicetruth.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Evidence-based applicability; a family/manufacturer match remains a limited lead. */
public final class ApplicabilityPolicy {
    public enum Status { EXACT_MATCH, FAMILY_MATCH, MANUFACTURER_MATCH, DOMAIN_GENERAL, UNKNOWN_APPLICABILITY, NOT_APPLICABLE }
    public record Target(String model, String family, String manufacturer, String firmwareVersion,
                         String softwareVersion, String modelYear, String market) {
        public static final Target UNKNOWN = new Target(null,null,null,null,null,null,null);
        public Target {
            for(String s:Arrays.asList(model,family,manufacturer,firmwareVersion,softwareVersion,modelYear,market))
                if(s!=null && (s.length()>128 || s.chars().anyMatch(Character::isISOControl)))
                    throw KnowledgeException.invalid("invalid applicability target");
        }
    }
    public record Decision(Status status, boolean verifiedApplicable, List<String> limitations) {
        public Decision { limitations=List.copyOf(limitations); }
    }
    public Decision assess(JsonNode m, Target target) {
        var t=target==null?Target.UNKNOWN:target;
        var limits=new ArrayList<String>();
        // Only typed, verified constraints can establish a conflict, never document edition strings.
        JsonNode constraints=m.path("verified_applicability");
        boolean unresolved=false;
        for(var pair:List.of(new String[]{"firmware_versions",value(t.firmwareVersion())},
                new String[]{"software_versions",value(t.softwareVersion())},new String[]{"model_years",value(t.modelYear())},
                new String[]{"markets",value(t.market())})) {
            var allowed=strings(constraints.path(pair[0]));
            if(!allowed.isEmpty()) {
                if(pair[1].isEmpty()) {limits.add("MISSING_"+pair[0].toUpperCase(Locale.ROOT));unresolved=true;}
                else if(!contains(allowed,pair[1]))return no("CONFLICT_"+pair[0].toUpperCase(Locale.ROOT));
            }
        }
        var excluded=strings(constraints.path("excluded_models"));
        if(t.model()!=null && contains(excluded,t.model()))return no("EXPLICIT_MODEL_EXCLUSION");
        String scope=m.path("applicability_scope").path("scope_level").asText(m.path("scope_level").asText());
        var models=strings(m.path("device_models")).stream().filter(x->!unknown(x)).toList();
        boolean exact=t.model()!=null && contains(models,t.model());
        boolean family=t.family()!=null && (contains(strings(constraints.path("families")),t.family())
                || models.stream().anyMatch(x->IdentifierExtractor.normalize(x).equals(IdentifierExtractor.normalize(t.family()))));
        boolean general=Set.of("DOMAIN_GENERAL","domain_general","GENERAL","general","cross_model","project_rule","PROJECT","PROTOCOL").contains(scope);
        String maker=m.path("manufacturer").asText();
        if(t.manufacturer()!=null && !unknown(maker) && !maker.equals("PROJECT") && !same(maker,t.manufacturer()) && !general)
            return no("MANUFACTURER_CONFLICT");
        if(t.model()!=null && !exact && !models.isEmpty() && !general) {
            // Family metadata alone cannot prove a different model is incompatible or compatible.
            if(!family && Set.of("MODEL","MODEL_MARKET").contains(scope)
                    && models.stream().noneMatch(x->IdentifierExtractor.normalize(x).startsWith(IdentifierExtractor.normalize(t.model()))))
                return no("MODEL_CONFLICT");
        }
        var versions=strings(m.path("applicable_versions"));
        if(versions.stream().anyMatch(ApplicabilityPolicy::unknown) && !general && constraints.isMissingNode()) {
            limits.add("SOURCE_VERSION_UNKNOWN");unresolved=true;
        }
        if(t.firmwareVersion()!=null && strings(constraints.path("firmware_versions")).isEmpty()) {
            limits.add("FIRMWARE_APPLICABILITY_UNVERIFIED");unresolved=true;
        }
        if(t.softwareVersion()!=null && strings(constraints.path("software_versions")).isEmpty()) {
            limits.add("SOFTWARE_APPLICABILITY_UNVERIFIED");unresolved=true;
        }
        if(t.modelYear()!=null && strings(constraints.path("model_years")).isEmpty()) {
            limits.add("YEAR_APPLICABILITY_UNVERIFIED");unresolved=true;
        }
        if(t.market()!=null && strings(constraints.path("markets")).isEmpty()) {
            limits.add("MARKET_APPLICABILITY_UNVERIFIED");unresolved=true;
        }
        boolean unclassified=m.path("applicability_scope").path("version_semantics").asText().startsWith("UNCLASSIFIED")
                && versions.stream().anyMatch(v->!unknown(v));
        if(unclassified) {limits.add("UNCLASSIFIED_SOURCE_VERSIONS_REQUIRE_REVIEW");unresolved=true;}
        if(exact && !unresolved)return new Decision(Status.EXACT_MATCH,true,limits);
        if(exact) {limits.add("MODEL_MATCH_WITH_UNRESOLVED_CONDITIONS");return new Decision(Status.UNKNOWN_APPLICABILITY,false,limits);}
        if(family) {limits.add("FAMILY_DOES_NOT_PROVE_MODEL_APPLICABILITY");return new Decision(Status.FAMILY_MATCH,false,limits);}
        if(general && !unresolved)return new Decision(Status.DOMAIN_GENERAL,true,limits);
        if(t.manufacturer()!=null && same(maker,t.manufacturer())) {
            limits.add("MANUFACTURER_DOES_NOT_PROVE_MODEL_APPLICABILITY");return new Decision(Status.MANUFACTURER_MATCH,false,limits);
        }
        limits.add("MODEL_OR_VERSION_NOT_VERIFIED");return new Decision(Status.UNKNOWN_APPLICABILITY,false,limits);
    }
    private static Decision no(String reason){return new Decision(Status.NOT_APPLICABLE,false,List.of(reason));}
    private static boolean unknown(String s){return s==null || Set.of("","UNKNOWN","UNSPECIFIED","未确认","未知").contains(s.toUpperCase(Locale.ROOT));}
    private static String value(String s){return s==null?"":s;}
    private static boolean same(String a,String b){return IdentifierExtractor.normalize(a).equals(IdentifierExtractor.normalize(b));}
    private static boolean contains(List<String> a,String b){return a.stream().anyMatch(x->same(x,b));}
    public static List<String> strings(JsonNode n) {var a=new ArrayList<String>();if(n.isArray())n.forEach(x->a.add(x.asText()));return a;}
}
