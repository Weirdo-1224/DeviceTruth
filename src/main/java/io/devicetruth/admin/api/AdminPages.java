package io.devicetruth.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.devicetruth.adapter.Json;
import io.devicetruth.admin.authorization.AdminAuthorization.Context;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 进程密钥只驻内存，重启后旧游标失效。页码不接受客户端偏移。 */
public final class AdminPages {
    public record Page<T>(List<T> items,String nextCursor,Instant snapshotAt) {}
    public record Position(Instant snapshotAt,String lastTime,String lastId,Instant from,Instant to) {}
    private final byte[] key=new byte[32];
    public AdminPages() { new SecureRandom().nextBytes(key); }
    public Position position(String endpoint,Context context,Map<String,String> query) {
        if(!query.containsKey("cursor")) return new Position(Instant.now(),null,null,null,null);
        try {
            String cursor=query.get("cursor");
            if(cursor.length()>8192) throw new IllegalArgumentException();
            String[] parts=cursor.split("\\.");
            if(parts.length!=2 || !MessageDigest.isEqual(sign(parts[0]),Base64.getUrlDecoder().decode(parts[1]))) throw new IllegalArgumentException();
            JsonNode n=Json.MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
            Instant snapshot=Instant.parse(n.path("snapshot").asText());
            if(snapshot.isBefore(Instant.now().minus(Duration.ofMinutes(30))) || snapshot.isAfter(Instant.now().plusSeconds(2))
                    || !n.path("binding").asText().equals(binding(endpoint,context,query))) throw new IllegalArgumentException();
            return new Position(snapshot,n.path("lastTime").isNull()?null:n.path("lastTime").asText(),n.path("lastId").asText(),
                    n.path("from").isNull()?null:Instant.parse(n.path("from").asText()),n.path("to").isNull()?null:Instant.parse(n.path("to").asText()));
        } catch(Exception e) { throw new AdminFailure(400,"INVALID_CURSOR"); }
    }
    public <T> Page<T> page(String endpoint,Context context,Map<String,String> query,Position pos,List<T> rows,
            java.util.function.Function<T,String> time,java.util.function.Function<T,String> id) {
        int limit=limit(query); boolean more=rows.size()>limit;
        List<T> items=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));
        String next=null;
        if(more) {
            T last=items.getLast(); var data=new LinkedHashMap<String,Object>();
            data.put("binding",binding(endpoint,context,query)); data.put("snapshot",pos.snapshotAt().toString());
            data.put("lastTime",time.apply(last)); data.put("lastId",id.apply(last)); data.put("from",pos.from()); data.put("to",pos.to());
            String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(Json.write(data).getBytes(StandardCharsets.UTF_8));
            next=encoded+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
        }
        return new Page<>(items,next,pos.snapshotAt());
    }
    public static int limit(Map<String,String> query) {
        try { int n=Integer.parseInt(query.getOrDefault("limit","20")); if(n<1 || n>100) throw new NumberFormatException(); return n; }
        catch(NumberFormatException e) { throw AdminFailure.input(); }
    }
    private static String binding(String endpoint,Context context,Map<String,String> query) {
        var filters=new TreeMap<>(query); filters.remove("cursor");
        return Json.write(List.of(endpoint,context.userId(),context.revision(),filters));
    }
    private byte[] sign(String value) {
        try { var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch(GeneralSecurityException e) { throw new IllegalStateException("Cursor signing unavailable"); }
    }
    public static void validate(Map<String,String> query,String... allowed) {
        var keys=new HashSet<>(List.of(allowed)); keys.addAll(List.of("limit","cursor"));
        if(!keys.containsAll(query.keySet()) || query.values().stream().anyMatch(Objects::isNull)) throw AdminFailure.input();
        limit(query);
    }
    public static UUID uuid(String value) {
        try { if(value==null || !value.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException();return UUID.fromString(value); }
        catch(IllegalArgumentException e) { throw AdminFailure.input(); }
    }
    public static String choice(String value,String... allowed) {
        if(value!=null && !List.of(allowed).contains(value)) throw AdminFailure.input(); return value;
    }
    public static Position window(Position pos,Map<String,String> query) {
        try {
            if(query.containsKey("from")!=query.containsKey("to")) throw new IllegalArgumentException();
            Instant to=query.containsKey("to")?OffsetDateTime.parse(query.get("to")).toInstant():pos.to()==null?pos.snapshotAt():pos.to();
            Instant from=query.containsKey("from")?OffsetDateTime.parse(query.get("from")).toInstant():pos.from()==null?to.minus(Duration.ofDays(7)):pos.from();
            ZoneId.of(query.getOrDefault("timezone","Asia/Shanghai"));
            if(!from.isBefore(to) || Duration.between(from,to).compareTo(Duration.ofDays(31))>0) throw new IllegalArgumentException();
            return new Position(pos.snapshotAt(),pos.lastTime(),pos.lastId(),from,to);
        } catch(RuntimeException e) { throw new AdminFailure(400,"INVALID_TIME_RANGE"); }
    }
}
