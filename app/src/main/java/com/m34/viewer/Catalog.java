package com.m34.viewer;

import java.util.*;

/** Platform-independent catalog and query rules, also exercised by JVM tests. */
public final class Catalog {
    public static final class Art {
        public final String id, title, artist, tags, preview, original, rating;
        public final int seed, score, width, height;
        public Art(String id, String title, String artist, String tags, int seed, int score,
                   String preview, String original, String rating, int width, int height) {
            this.id=id; this.title=title; this.artist=artist; this.tags=tags; this.seed=seed;
            this.score=score; this.preview=preview; this.original=original; this.rating=rating;
            this.width=width; this.height=height;
        }
        public boolean live() { return id.startsWith("r34-"); }
    }
    public static boolean isSafe(String rating) {
        if(rating==null)return false;
        return switch(rating.trim().toLowerCase(Locale.ROOT)) {
            case "s","safe","g","general" -> true;
            default -> false;
        };
    }
    public static boolean matches(Art art, String query) {
        String hay=(art.title+" "+art.artist+" "+art.tags).toLowerCase(Locale.ROOT);
        for(String token:query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if(token.isEmpty()) continue;
            boolean exclude=token.startsWith("-");
            String term=exclude?token.substring(1):token;
            if(term.isEmpty()) continue;
            if(exclude ? hay.contains(term) : !hay.contains(term)) return false;
        }
        return true;
    }
    public static String safeQuery(String query, boolean safeOnly) {
        if(!safeOnly) return query.trim();
        List<String> tokens=new ArrayList<>();
        for(String t:query.trim().split("\\s+")) if(!t.isEmpty() && !t.toLowerCase(Locale.ROOT).matches("-?rating:.*")) tokens.add(t);
        tokens.add("-rating:questionable");
        tokens.add("-rating:explicit");
        return String.join(" ",tokens);
    }
    public static List<Art> filter(List<Art> input, String query, boolean safeOnly, String order) {
        List<Art> result=new ArrayList<>();
        for(Art a:input) if(matches(a,query) && (!safeOnly || isSafe(a.rating))) result.add(a);
        if(order.equals("Top rated") || order.equals("Trending")) result.sort((a,b)->Integer.compare(b.score,a.score));
        else if(order.equals("Latest")) Collections.reverse(result);
        else if(order.equals("Title")) result.sort(Comparator.comparing(a->a.title));
        return result;
    }
}
