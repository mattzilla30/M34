package com.m34.viewer;

/** Late responses must never replace results for a newer query/filter/page. */
final class SearchSession {
    private int revision;
    private String key="";
    int begin(String key){this.key=key;return ++revision;}
    boolean accepts(int revision,String key){return this.revision==revision&&this.key.equals(key);}
    static String key(String query,boolean safe,String order){return SearchTags.normalize(query)+"\n"+safe+"\n"+order;}
}
