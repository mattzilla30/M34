package com.m34.viewer;

import java.util.Map;

/** Maps API metadata without silently discarding General-rated posts. */
final class PostMapper {
    static final class Mapped {
        final Catalog.Art art;
        final boolean filtered;
        Mapped(Catalog.Art art,boolean filtered){this.art=art;this.filtered=filtered;}
    }
    static Mapped map(Map<String,String> post,boolean safe,int seed) {
        String rating=post.getOrDefault("rating","unknown").trim().toLowerCase(java.util.Locale.ROOT);
        if(safe&&!Catalog.isSafe(rating))return new Mapped(null,true);
        String id=post.getOrDefault("id","").trim();
        if(!id.matches("[0-9]+"))return new Mapped(null,false);
        String original=media(post,"file_url","fileUrl","large_file_url");
        String preview=media(post,"sample_url","sampleUrl","preview_url","previewUrl");
        if(preview.isEmpty())preview=original;
        if(original.isEmpty())original=preview;
        if(original.isEmpty())return new Mapped(null,false);
        return new Mapped(new Catalog.Art("r34-"+id,"Post #"+id,post.getOrDefault("owner","Rule34"),post.getOrDefault("tags",""),seed,number(post,"score"),preview,original,rating,number(post,"width"),number(post,"height")),false);
    }
    private static String media(Map<String,String> post,String... keys){for(String key:keys){String address=ApiConfig.mediaUrl(post.getOrDefault(key,""));if(!address.isEmpty())return address;}return "";}
    private static int number(Map<String,String> post,String key){try{return Integer.parseInt(post.getOrDefault(key,"0"));}catch(NumberFormatException e){return 0;}}
}
