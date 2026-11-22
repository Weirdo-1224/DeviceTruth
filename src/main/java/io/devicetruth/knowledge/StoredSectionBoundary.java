package io.devicetruth.knowledge;

import java.util.*;
import java.util.regex.Pattern;

/** Derive section occurrence identity from verified original Parent spans, never document title. */
final class StoredSectionBoundary {
    record Span(int start,int end) {}
    private record Heading(int level,String title,int offset) {}
    static String identity(ScopedKnowledgeStore.Child child,ContextRecoveryService.Parent parent) {
        if(parent==null||child.parentId()==null||child.section()==null||child.section().isBlank())return null;
        String body=parent.content();Span span=parent.spans().get(child.id());
        if(span==null) {
            int first=body.indexOf(child.content());
            if(first<0||body.indexOf(child.content(),first+1)>=0)return null;
            span=new Span(first,first+child.content().length());
        }
        if(span.start()<0||span.end()>body.length()||span.end()<=span.start()
                ||!body.substring(span.start(),span.end()).equals(child.content()))return null;
        var headings=new ArrayList<Heading>();
        int offset=0;String fence=null;
        var pattern=Pattern.compile("^(#{1,6})[ \\t]+([^\\r\\n]+)");
        for(String line:body.split("(?<=\\n)",-1)) {
            String stripped=line.stripLeading();
            if(stripped.startsWith("```")||stripped.startsWith("~~~")) {
                String kind=stripped.substring(0,3);if(fence==null)fence=kind;else if(fence.equals(kind))fence=null;
            }else if(fence==null) {
                var m=pattern.matcher(line);
                if(m.find())headings.add(new Heading(m.group(1).length(),m.group(2).strip(),offset));
            }
            offset+=line.length();
        }
        var stack=new ArrayList<Heading>();
        for(var heading:headings) {
            // A child crossing a heading is not wholly within one reliable section.
            if(heading.offset()>span.start()&&heading.offset()<span.end())return null;
            if(heading.offset()>span.start())break;
            while(!stack.isEmpty()&&stack.getLast().level()>=heading.level())stack.removeLast();
            stack.add(heading);
        }
        String path=stack.stream().map(Heading::title).reduce((a,b)->a+" / "+b).orElse("正文");
        if(!path.equals(child.section().strip()))return null;
        return stack.stream().map(h->h.level()+":"+h.offset()+":"+h.title()).reduce((a,b)->a+"/"+b).orElse("ROOT:0");
    }
}
