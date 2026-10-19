package com.m34.viewer;

import java.util.*;

final class SiteTags {
    static List<String> pick(List<Catalog.Art> posts,List<String> previous,Random random){
        Set<String> unique=new LinkedHashSet<>();
        for(Catalog.Art art:posts)for(String tag:art.tags.split("\\s+"))if(!tag.isBlank()&&!tag.contains(":")&&!tag.startsWith("-"))unique.add(tag);
        List<String> fresh=new ArrayList<>(),repeat=new ArrayList<>();
        for(String tag:unique)(previous.contains(tag)?repeat:fresh).add(tag);
        Collections.shuffle(fresh,random);Collections.shuffle(repeat,random);fresh.addAll(repeat);
        return new ArrayList<>(fresh.subList(0,Math.min(12,fresh.size())));
    }
}
