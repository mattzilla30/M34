package com.m34.viewer;

import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class SearchFlowTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static Map<String,String> post(String rating){return new HashMap<>(Map.of("id","123","rating",rating,"tags","forest blue_hair landscape","file_url","https://api-cdn.rule34.xxx/images/test.jpg","score","42","width","1000","height","1500"));}
    public static void main(String[] args){
        for(String rating:new String[]{"g","general","G","General","s","safe","SAFE"," safe "}){
            check(Catalog.isSafe(rating),"General and Safe recognized across casing");
            PostMapper.Mapped mapped=PostMapper.map(post(rating),true,0);
            check(mapped.art!=null&&!mapped.filtered,"safe mode retains General/Safe API posts");
        }
        for(String rating:new String[]{"q","questionable","e","explicit","unknown",""}){
            PostMapper.Mapped mapped=PostMapper.map(post(rating),true,0);check(mapped.art==null&&mapped.filtered,"safe mode still rejects disallowed and unknown ratings");
        }
        check(PostMapper.map(post("e"),false,0).art!=null,"disabled safe mode permits all ratings");
        var sample=post("g");sample.remove("file_url");sample.put("sample_url","api-cdn.rule34.xxx/samples/test.jpg");
        check(PostMapper.map(sample,true,0).art!=null,"usable sample retained when original missing");
        sample.put("sample_url","null");check(PostMapper.map(sample,true,0).art==null,"missing media distinguished from safe filtering");
        check(SearchTags.normalize(" Blue_Hair,   FOREST ").equals("blue_hair forest"),"comma-separated tags normalized");
        check(SearchTags.matches("blue_hair forest","blue_hair forest"),"AND search");
        check(!SearchTags.matches("forest sunset","blue_hair"),"unrelated front-page posts rejected");
        check(!SearchTags.matches("blue_hair forest","blue"),"tags matched exactly rather than substrings");
        check(SearchTags.matches("blue_hair forest","blue* -sunset"),"wildcard and exclusion");
        check(!SearchTags.matches("blue_hair forest","-forest"),"excluded tags hidden");
        check(SearchTags.matches("forest","( forest ~ sunset )"),"advanced OR expressions remain server-controlled");
        var websitePosts=List.of(new Catalog.Art("r34-1","Post #1","Rule34","forest ocean forest sunset",0,0,"","","g",1,1));
        var tags=SiteTags.pick(websitePosts,List.of(),new Random(1));
        check(tags.size()==3&&new HashSet<>(tags).size()==3,"site tags deduplicated");
        check(tags.containsAll(List.of("forest","ocean","sunset")),"tags come from fetched website metadata");
        check(SiteTags.pick(List.of(),List.of("old"),new Random(1)).isEmpty(),"failed or empty feed does not fall back to search history");
        check(SiteTags.pick(websitePosts,List.of("forest","ocean"),new Random(1)).get(0).equals("sunset"),"fresh website tags preferred on refresh");
        check(!SiteTags.pick(websitePosts,List.of(),new Random(1)).equals(SiteTags.pick(websitePosts,List.of(),new Random(4))),"tags randomized on refresh");
        var many=List.of(new Catalog.Art("r34-2","","","a b c d e f g h i j k l m n o",0,0,"","","s",1,1));
        check(SiteTags.pick(many,List.of(),new Random(1)).size()==12,"tag chip count bounded");
        check(CollectionNames.error("",null,Set.of())!=null,"empty collection names rejected");
        check(CollectionNames.error("ALL SAVED",null,Set.of())!=null,"reserved collection name rejected ignoring case");
        check(CollectionNames.error("favorites",null,Set.of("Favorites"))!=null,"duplicate collection names rejected ignoring case");
        check(CollectionNames.error("Favorites","Favorites",Set.of("Favorites"))==null,"unchanged rename accepted");
        check(CollectionNames.error("New name","Favorites",Set.of("Favorites"))==null,"valid rename accepted");
        check(CollectionNames.error("x".repeat(41),null,Set.of())!=null,"collection name length enforced");
        SearchSession session=new SearchSession();String a=SearchSession.key("forest",true,"Latest"),b=SearchSession.key("ocean",true,"Latest");
        int first=session.begin(a);int second=session.begin(b);
        check(!session.accepts(first,a),"late response cannot overwrite newer tag results");
        check(session.accepts(second,b),"current search response accepted");
        String filtered=SearchSession.key("ocean",false,"Latest");int third=session.begin(filtered);
        check(!session.accepts(second,b)&&session.accepts(third,filtered),"rating changes invalidate old results");
        check(!SearchSession.key("forest",true,"Latest").equals(SearchSession.key("forest",true,"Top rated")),"sort is part of result identity");
        ApiConfig config=ApiConfig.credentials("12345","test-key","");
        String forest=config.postsUrl("forest",false,"Latest",0,30),ocean=config.postsUrl("ocean",false,"Latest",0,30);
        check(!forest.equals(ocean),"different searches send different URLs");
        check(URLDecoder.decode(URI.create(forest).getRawQuery(),StandardCharsets.UTF_8).contains("tags=forest&"),"search tags present as their own request parameter");
        String safe=config.postsUrl("forest rating:explicit",true,"Latest",0,30);
        check(URLDecoder.decode(URI.create(safe).getRawQuery(),StandardCharsets.UTF_8).contains("tags=forest -rating:questionable -rating:explicit"),"safe query no longer excludes General posts");
        System.out.println("Passed "+checks+" search, recommendation and rating checks");
    }
}
