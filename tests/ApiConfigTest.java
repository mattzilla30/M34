package com.m34.viewer;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ApiConfigTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void invalid(String user,String key,String existing){boolean rejected=false;try{ApiConfig.credentials(user,key,existing);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid credentials rejected without exposing their contents");}
    public static void main(String[] args){
        ApiConfig separate=ApiConfig.credentials(" 12345 "," test-key ","");
        check(separate.user.equals("12345")&&separate.key.equals("test-key"),"trim separately entered values");
        ApiConfig pasted=ApiConfig.credentials("","&api_key=test-key&user_id=12345","");
        check(pasted.user.equals("12345")&&pasted.key.equals("test-key"),"paste complete account-options credentials into key field");
        ApiConfig encoded=ApiConfig.credentials("","https://api.rule34.xxx/index.php?user_id=12345&api_key=test%2Bkey%3D","");
        check(encoded.key.equals("test+key="),"decode URL-encoded key");
        ApiConfig html=ApiConfig.credentials("","&amp;api_key=test-key&amp;user_id=12345","");
        check(html.user.equals("12345")&&html.key.equals("test-key"),"accept HTML-escaped copied parameters");
        check(ApiConfig.credentials("12345","","existing-key").key.equals("existing-key"),"preserve saved key when blank");
        check(ApiConfig.credentials("&user_id=12345&api_key=test-key","","").key.equals("test-key"),"complete string takes precedence over blank key fallback");
        invalid("name","key","");invalid("0","key","");invalid("12345","","");invalid("12345","not a key","");invalid("12345","broken\nkey","");
        String request=pasted.postsUrl("landscape rating:explicit",true,"Latest",2,30);
        URI uri=URI.create(request);Map<String,String> params=new HashMap<>();
        for(String pair:uri.getRawQuery().split("&")){String[] parts=pair.split("=",2);check(!params.containsKey(parts[0]),"request parameters not duplicated");params.put(parts[0],URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}
        check(uri.getScheme().equals("https")&&uri.getHost().equals("api.rule34.xxx"),"credentials only go to official HTTPS API");
        check(params.get("user_id").equals("12345")&&params.get("api_key").equals("test-key"),"copied credentials encoded as separate parameters");
        check(params.get("tags").equals("landscape -rating:questionable -rating:explicit"),"safe mode enforced independently of credentials");
        check(params.get("pid").equals("2")&&params.get("limit").equals("30"),"pagination contract");
        check(ApiConfig.mediaUrl("api-cdn.rule34.xxx/images/example.jpg").equals("https://api-cdn.rule34.xxx/images/example.jpg"),"scheme-less API CDN links accepted");
        check(ApiConfig.mediaUrl("//api-cdn.rule34.xxx/images/example.jpg").startsWith("https://"),"protocol-relative media accepted");
        check(ApiConfig.mediaUrl("http://api-cdn.rule34.xxx/images/example.jpg").startsWith("https://"),"legacy HTTP media upgraded to HTTPS");
        check(ApiConfig.mediaUrl("https://api-cdn.rule34.xxx/images/example.jpg").endsWith("example.jpg"),"HTTPS media preserved");
        check(ApiConfig.mediaUrl("null").isEmpty()&&ApiConfig.mediaUrl("").isEmpty(),"missing media ignored");
        check(ApiConfig.mediaUrl("javascript:alert(1)").isEmpty()&&ApiConfig.mediaUrl("https://secret@example.com/a.jpg").isEmpty(),"invalid or credential-bearing media rejected");
        check(ApiConfig.failure(200,"Invalid api_key: SECRET").contains("rejected the credentials"),"HTTP 200 auth errors surfaced");
        check(!ApiConfig.failure(200,"Invalid api_key: SECRET").contains("SECRET"),"server responses never echo credentials");
        check(ApiConfig.failure(200,"Missing authentication. Go to api.rule34.xxx for more information").contains("rejected the credentials"),"real HTTP 200 authentication error identified");
        check(ApiConfig.failure(403,"cloudflare").contains("browser verification"),"network blocking distinguished from wrong key");
        check(ApiConfig.failure(429,"").contains("Rate limited"),"rate limiting distinguished");
        check(ApiConfig.failure(503,"").contains("temporarily unavailable"),"service outage distinguished");
        System.out.println("Passed "+checks+" API request and credential checks");
    }
}
