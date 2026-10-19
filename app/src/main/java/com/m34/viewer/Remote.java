package com.m34.viewer;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

final class Remote {
    interface Result<T> { void success(T value); void failure(String message); }
    static final class PostPage {
        final List<Catalog.Art> items; final int received,filtered,unusable,mismatched;
        PostPage(List<Catalog.Art> items,int received,int filtered,int unusable,int mismatched) {this.items=items;this.received=received;this.filtered=filtered;this.unusable=unusable;this.mismatched=mismatched;}
        String emptyTitle(){return received==0?"No matching posts":mismatched>0?"Returned posts do not match your tags":filtered>0?"Posts hidden by Safe only":"Media unavailable";}
        String summary(){return "Server returned "+received+" posts · "+items.size()+" displayable · "+filtered+" hidden by rating · "+mismatched+" unrelated · "+unusable+" missing media."+(received==0?" Try fewer tags or a different rating filter.":mismatched>0?" The server response did not match this search. Retry or use Latest.":filtered>0?" General and Safe ratings are included; unknown ratings are hidden.":"");}
    }
    final ExecutorService workers=Executors.newFixedThreadPool(4);
    final ExecutorService transfers=Executors.newFixedThreadPool(2);
    final ExecutorService apiWorkers=Executors.newFixedThreadPool(2);
    final Handler main=new Handler(Looper.getMainLooper());
    final File cache;
    final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(24*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getByteCount(); }
    };
    Remote(File directory) { cache=new File(directory,"artwork");cache.mkdirs(); }
    static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    static HttpsURLConnection connect(String address) throws IOException {
        URL url=new URL(address);
        if(!url.getProtocol().equals("https")) throw new IOException("HTTPS is required");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("Pragma","no-cache");c.setRequestProperty("User-Agent","M34-Android/1");c.setRequestProperty("Accept","*/*");c.setRequestProperty("Referer","https://rule34.xxx/");
        return c;
    }
    static byte[] read(String address,int maximum) throws IOException {
        HttpsURLConnection c=connect(address);
        try {
            int code=c.getResponseCode();
            if(code!=200) {
                String body="";InputStream error=c.getErrorStream();
                if(error!=null)try(error){body=new String(error.readNBytes(8192),StandardCharsets.UTF_8);}
                throw new IOException(ApiConfig.failure(code,body));
            }
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[16384];int n,total=0;
                while((n=in.read(buffer))!=-1) { total+=n;if(total>maximum) throw new IOException("Response exceeds the size limit");out.write(buffer,0,n); }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
    void posts(String query,boolean safe,String sort,int page,String user,String key,Result<PostPage> result) {
        fetchPosts(query,safe,sort,page,30,user,key,result);
    }
    void randomTags(boolean safe,String user,String key,List<String> previous,Result<List<String>> result){
        fetchPosts("",safe,"Random",0,20,user,key,new Result<PostPage>(){
            public void success(PostPage page){result.success(SiteTags.pick(page.items,previous,new java.util.Random()));}
            public void failure(String message){result.failure(message);}
        });
    }
    void test(String user,String key,Result<PostPage> result) {
        fetchPosts("",true,"Latest",0,1,user,key,result);
    }
    private void fetchPosts(String query,boolean safe,String sort,int page,int limit,String user,String key,Result<PostPage> result) {
        apiWorkers.execute(()->{
            try {
                ApiConfig credentials=ApiConfig.credentials(user,key,"");
                String raw=new String(read(credentials.postsUrl(query,safe,sort,page,limit),8*1024*1024),StandardCharsets.UTF_8).trim();
                if(raw.startsWith("\uFEFF"))raw=raw.substring(1).trim();
                if(raw.isEmpty()||raw.equals("null"))throw new IOException("Rule34 returned an empty or null response, not a post list. Retry or test your credentials.");
                if(!raw.startsWith("["))throw new IOException(ApiConfig.failure(200,raw));
                JSONArray items=new JSONArray(raw);
                List<Catalog.Art> arts=new ArrayList<>();int filtered=0,unusable=0,mismatched=0;
                for(int i=0;i<items.length();i++) {
                    JSONObject o=items.getJSONObject(i);Map<String,String> fields=new HashMap<>();Iterator<String> keys=o.keys();
                    while(keys.hasNext()){String field=keys.next();if(!o.isNull(field))fields.put(field,o.optString(field,""));}
                    PostMapper.Mapped mapped=PostMapper.map(fields,safe,i);
                    if(mapped.filtered){filtered++;continue;}
                    if(mapped.art==null){unusable++;continue;}
                    if(!SearchTags.matches(mapped.art.tags,query)){mismatched++;continue;}
                    arts.add(mapped.art);
                }
                PostPage response=new PostPage(arts,items.length(),filtered,unusable,mismatched);
                main.post(()->result.success(response));
            } catch(Exception e) {
                String message;
                if(e instanceof UnknownHostException)message="Could not resolve Rule34. Check your internet connection, Private DNS, VPN or network filtering.";
                else if(e instanceof SocketTimeoutException)message="Rule34 connection timed out. Check your network and retry.";
                else if(e instanceof javax.net.ssl.SSLException)message="A secure connection to Rule34 could not be established. Check the device date and your network.";
                else if(e instanceof IOException||e instanceof IllegalArgumentException)message=e.getMessage();
                else message="Rule34 returned unreadable post data. Please test the connection in Settings.";
                final String safeMessage=message==null?"Connection failed. Please retry.":message;
                main.post(()->result.failure(safeMessage));
            }
        });
    }
    static String https(String s) { return ApiConfig.mediaUrl(s); }
    File file(String address) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format("%02x",v));return new File(cache,b.toString());
    }
    void image(String address,Result<Bitmap> result) {
        Bitmap cached=memory.get(address);if(cached!=null) { result.success(cached);return; }
        workers.execute(()->{
            try {
                File target=file(address);byte[] bytes;
                if(target.exists()) bytes=java.nio.file.Files.readAllBytes(target.toPath());
                else { bytes=read(address,24*1024*1024);synchronized(cache) { File temporary=File.createTempFile("image-",".pending",cache);try{java.nio.file.Files.write(temporary.toPath(),bytes);java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{temporary.delete();}trim(); } }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;while(bounds.outWidth/(Math.max(1,options.inSampleSize))>720 || bounds.outHeight/(Math.max(1,options.inSampleSize))>960) options.inSampleSize=Math.max(1,options.inSampleSize)*2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null) throw new IOException("Preview unavailable for this media type");
                memory.put(address,bitmap);main.post(()->result.success(bitmap));
            } catch(Exception e) { main.post(()->result.failure("Preview unavailable. Tap to open details or download the original.")); }
        });
    }
    void trim() {
        File[] files=cache.listFiles(f->!f.getName().endsWith(".pending"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;for(File f:files)size+=f.length();for(File f:files)if(size>64L*1024*1024){long len=f.length();if(f.delete())size-=len;}
    }
    long cacheSize() { long sum=0;File[] files=cache.listFiles();if(files!=null)for(File f:files)sum+=f.length();return sum; }
    void clear() { synchronized(cache){memory.evictAll();File[] files=cache.listFiles();if(files!=null)for(File f:files)f.delete();} }
    void close() { apiWorkers.shutdownNow();workers.shutdownNow();transfers.shutdown(); }
}
