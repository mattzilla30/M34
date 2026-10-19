package com.m34.viewer;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class Library {
    final SharedPreferences prefs;
    final LinkedHashMap<String,Catalog.Art> saved=new LinkedHashMap<>();
    final LinkedHashMap<String,Set<String>> collections=new LinkedHashMap<>();
    final LinkedHashSet<String> likes=new LinkedHashSet<>();
    Library(Context c) {
        prefs=c.getSharedPreferences("m34",Context.MODE_PRIVATE);
        try {
            JSONArray list=new JSONArray(prefs.getString("saved","[]"));
            for(int i=0;i<list.length();i++) { Catalog.Art a=decode(list.getJSONObject(i)); saved.put(a.id,a); }
            JSONObject groups=new JSONObject(prefs.getString("collections","{\"Favorites\":[],\"Wallpapers\":[],\"Inspiration\":[]}"));
            Iterator<String> keys=groups.keys();
            while(keys.hasNext()) { String k=keys.next(); Set<String> ids=new LinkedHashSet<>(); JSONArray v=groups.getJSONArray(k); for(int i=0;i<v.length();i++) ids.add(v.getString(i)); collections.put(k,ids); }
            likes.addAll(prefs.getStringSet("likes",Collections.emptySet()));
        } catch(JSONException e) { collections.put("Favorites",new LinkedHashSet<>()); }
    }
    static JSONObject encode(Catalog.Art a) throws JSONException {
        return new JSONObject().put("id",a.id).put("title",a.title).put("artist",a.artist).put("tags",a.tags).put("seed",a.seed).put("score",a.score).put("preview",a.preview).put("original",a.original).put("rating",a.rating).put("width",a.width).put("height",a.height);
    }
    static Catalog.Art decode(JSONObject o) throws JSONException {
        return new Catalog.Art(o.getString("id"),o.getString("title"),o.getString("artist"),o.getString("tags"),o.getInt("seed"),o.getInt("score"),o.optString("preview"),o.optString("original"),o.optString("rating","s"),o.optInt("width",1600),o.optInt("height",2000));
    }
    void persist() {
        try {
            JSONArray array=new JSONArray(); for(Catalog.Art a:saved.values()) array.put(encode(a));
            JSONObject groups=new JSONObject(); for(Map.Entry<String,Set<String>> e:collections.entrySet()) groups.put(e.getKey(),new JSONArray(e.getValue()));
            prefs.edit().putString("saved",array.toString()).putString("collections",groups.toString()).putStringSet("likes",new LinkedHashSet<>(likes)).apply();
        } catch(JSONException e) { throw new IllegalStateException(e); }
    }
    boolean toggleSave(Catalog.Art a) {
        if(saved.remove(a.id)!=null) { for(Set<String> group:collections.values()) group.remove(a.id); persist(); return false; }
        saved.put(a.id,a); persist(); return true;
    }
    void setCollection(Catalog.Art a,String collection,boolean add) {
        if(add) { saved.put(a.id,a); collections.get(collection).add(a.id); }
        else collections.get(collection).remove(a.id);
        persist();
    }
    boolean flag(String key,boolean fallback) { return prefs.getBoolean(key,fallback); }
    String value(String key,String fallback) { return prefs.getString(key,fallback); }
    void putFlag(String key,boolean value) { prefs.edit().putBoolean(key,value).apply(); }
    void putValue(String key,String value) { prefs.edit().putString(key,value).apply(); }
    void history(String query) {
        if(query.isBlank()) return;
        List<String> h=history(); h.remove(query); h.add(0,query); if(h.size()>10) h=h.subList(0,10);
        putValue("history",new JSONArray(h).toString());
    }
    List<String> history() {
        List<String> h=new ArrayList<>();
        try { JSONArray a=new JSONArray(value("history","[]")); for(int i=0;i<a.length();i++) h.add(a.getString(i)); } catch(JSONException ignored) {}
        return h;
    }
    private javax.crypto.SecretKey encryptionKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias("m34-api")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("m34-api",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey("m34-api",null);
    }
    void apiKey(String value) throws Exception {
        if(value.isEmpty()) { prefs.edit().remove("apiKey").remove("apiIv").apply(); return; }
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,encryptionKey());
        prefs.edit().putString("apiKey",Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP)).putString("apiIv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).apply();
    }
    String apiKey() throws Exception {
        String text=value("apiKey",""); if(text.isEmpty()) return "";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,encryptionKey(),new GCMParameterSpec(128,Base64.decode(value("apiIv",""),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(text,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
}
