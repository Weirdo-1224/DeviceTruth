package io.devicetruth.knowledge;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Typed literal identifiers. No edit distance, prefix expansion or inferred equipment aliases. */
public final class IdentifierExtractor {
    public enum Type { MODEL, FAMILY, MANUFACTURER, PART, DTC, ALARM, FIRMWARE, SOFTWARE, SPECIFICATION, BULLETIN, PROTOCOL, ACRONYM }
    public record Identifier(Type type, String value, String original, String matchMethod) {
        public String key() { return type + ":" + value; }
    }
    private static final Map<String,Type> TERMS = Map.ofEntries(
            Map.entry("ICCU",Type.PART),Map.entry("OBC",Type.PART),Map.entry("BMS",Type.PART),
            Map.entry("PLC",Type.ACRONYM),Map.entry("HVIL",Type.ACRONYM),Map.entry("DTC",Type.ACRONYM),
            Map.entry("PROFINET",Type.PROTOCOL),Map.entry("PROFIBUS",Type.PROTOCOL),
            Map.entry("MODBUS",Type.PROTOCOL),Map.entry("CAN",Type.PROTOCOL),Map.entry("ETHERNET/IP",Type.PROTOCOL));
    private static final Pattern STRUCTURED = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:CPU[ -]*\\d{4}[A-Z]|S7[ -]*\\d{4}|MERV[ -]*\\d{1,2}|[PBCU][0-3][0-9A-F]{3}|SC[ -]*\\d{3}[A-Z]?|\\d{2}[ -]\\d{2}[ -]\\d{3}[A-Z]?|(?=[0-9A-F]*[A-F])(?=[0-9A-F]*[0-9])[0-9A-F]{3})(?![a-z0-9])");
    private static final Pattern VERSION = Pattern.compile("(?i)(firmware|fw|固件|software|软件)[ :：vV]*([0-9]+(?:\\.[0-9]+){1,3}(?:[A-Za-z][0-9]*)?)(?![A-Za-z0-9.])");

    public static String normalize(String text) {
        if (text == null) return "";
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).toUpperCase(Locale.ROOT)
                .replaceAll("[‐‑–−]","-").strip().replaceAll("\\s+"," ");
        // Only documented technical forms accept omission of separators. Arbitrary part IDs do not.
        if(normalized.matches("CPU[ -]*[0-9]{4}[A-Z]|S7[ -]*[0-9]{4}|MERV[ -]*[0-9]{1,2}|SC[ -]*[0-9]{3}[A-Z]?|[0-9]{2}[ -][0-9]{2}[ -][0-9]{3}[A-Z]?"))
            return normalized.replaceAll("[ -]","");
        return normalized;
    }

    public List<Identifier> extract(String text) { return extract(text,Map.of()); }
    public List<Identifier> extract(String text, Map<String,Type> verifiedTerms) {
        var result = new TreeMap<String,Identifier>();
        if (text == null) return List.of();
        var words = new LinkedHashMap<>(verifiedTerms); words.putAll(TERMS);
        for (var entry:words.entrySet()) {
            String term=entry.getKey(); if(term.isBlank() || term.equalsIgnoreCase("unknown"))continue;
            boolean legalSeparator=term.toUpperCase(Locale.ROOT).matches("CPU[ -]*[0-9]{4}[A-Z]|S7[ -]*[0-9]{4}|MERV[ -]*[0-9]{1,2}");
            String pattern=Arrays.stream(term.split(legalSeparator?"[\\s-]+":"\\s+"))
                    .map(Pattern::quote).collect(java.util.stream.Collectors.joining(legalSeparator?"[\\s-]*":"\\s+"));
            var m=Pattern.compile("(?i)(?<![A-Za-z0-9])"+pattern+"(?![A-Za-z0-9])").matcher(text);
            while(m.find())add(result,entry.getValue(),m.group(),"VERIFIED_LITERAL");
        }
        var m=STRUCTURED.matcher(text);
        while(m.find()) {
            String raw=m.group(),n=normalize(raw); Type type;
            if(n.matches("[PBCU][0-3][0-9A-F]{3}"))type=Type.DTC;
            else if(n.matches("[0-9A-F]{3}"))type=Type.ALARM;
            else if(n.matches("SC\\d{3}[A-Z]?|\\d{7}[A-Z]?"))type=Type.BULLETIN;
            else if(n.matches("S7\\d{4}"))type=Type.FAMILY;
            else if(n.startsWith("CPU"))type=Type.MODEL;
            else if(n.startsWith("MERV"))type=Type.SPECIFICATION;
            else {
                // An arbitrary alphanumeric compound is only accepted if declared by the corpus.
                if(words.keySet().stream().noneMatch(t->normalize(t).equals(n)))continue;
                type=words.entrySet().stream().filter(e->normalize(e.getKey()).equals(n)).findFirst().orElseThrow().getValue();
            }
            add(result,type,raw,"NORMALIZED_EXACT");
        }
        m=VERSION.matcher(text);while(m.find())add(result,m.group(1).matches("(?i)software|软件")?Type.SOFTWARE:Type.FIRMWARE,m.group(2),"EXPLICIT_VERSION");
        return List.copyOf(result.values());
    }
    private static void add(Map<String,Identifier> out,Type type,String original,String method) {
        var id=new Identifier(type,normalize(original),original,method);out.putIfAbsent(id.key(),id);
    }
    public Map<String,Type> lexicon(com.fasterxml.jackson.databind.JsonNode metadata) {
        var result=new TreeMap<String,Type>();
        for(var n:metadata.path("device_models"))if(!Set.of("UNKNOWN","GENERAL","PROJECT_SCOPE").contains(n.asText().toUpperCase(Locale.ROOT)))result.put(n.asText(),n.asText().matches("(?i)S7[- ]?\\d{4}")?Type.FAMILY:Type.MODEL);
        String maker=metadata.path("manufacturer").asText();if(!maker.isBlank()&&!Set.of("PROJECT","unknown").contains(maker))result.put(maker,Type.MANUFACTURER);
        for(var n:metadata.path("components"))if(n.asText().matches("[A-Za-z][A-Za-z0-9 /-]{1,40}"))result.put(n.asText(),Type.PART);
        // Protocol/acronym aliases are never synthesized from an arbitrary keyword.
        return result;
    }
}
