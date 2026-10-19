package com.m34.viewer;

import java.util.*;
import java.util.regex.Pattern;

final class SearchTags {
    static String normalize(String query) {return query.trim().replace(',', ' ').replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static boolean matches(String postTags,String query) {
        String normalized=normalize(query);
        // Complex booru expressions remain server-controlled. For ordinary
        // tag searches, reject an unrelated front-page/cache response.
        if(normalized.matches(".*[()~].*"))return true;
        Set<String> tags=new HashSet<>(Arrays.asList(normalize(postTags).split(" ")));
        for(String token:normalized.split(" ")) {
            if(token.isEmpty()||token.contains(":"))continue;
            boolean exclude=token.startsWith("-");String value=exclude?token.substring(1):token;
            if(value.isEmpty())continue;
            String regex=Arrays.stream(value.split("\\*",-1)).map(Pattern::quote).collect(java.util.stream.Collectors.joining(".*"));
            boolean found=tags.stream().anyMatch(tag->tag.matches(regex));
            if(exclude?found:!found)return false;
        }
        return true;
    }
}
