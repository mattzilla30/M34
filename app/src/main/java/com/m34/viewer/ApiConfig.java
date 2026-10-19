package com.m34.viewer;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.*;

/** Request and credential normalization; independent of the Android runtime. */
final class ApiConfig {
    final String user,key;
    ApiConfig(String user,String key) {this.user=user;this.key=key;}
    static ApiConfig credentials(String userInput,String keyInput,String existingKey) {
        String user=userInput.trim(),key=keyInput.trim();
        Pattern parameters=Pattern.compile("(?:^|[?&])(?:amp;)?(user_id|api_key)=([^&#]*)",Pattern.CASE_INSENSITIVE);
        boolean extractedKey=false;
        for(String input:new String[]{userInput,keyInput}) {
            Matcher m=parameters.matcher(input.trim());
            while(m.find()) {
                String value;
                try {value=URLDecoder.decode(m.group(2),StandardCharsets.UTF_8).trim();}
                catch(IllegalArgumentException e) {throw new IllegalArgumentException("The copied credentials contain invalid URL encoding.");}
                if(m.group(1).equalsIgnoreCase("user_id"))user=value;
                else {key=value;extractedKey=true;}
            }
        }
        if(keyInput.isBlank()&&!extractedKey)key=existingKey;
        if(!user.matches("[0-9]+")||user.matches("0+"))throw new IllegalArgumentException("Enter the numeric user_id from API Access Credentials, not your username.");
        if(key.isEmpty())throw new IllegalArgumentException("Enter your API key or paste the complete API Access Credentials string.");
        if(key.chars().anyMatch(Character::isWhitespace)||key.contains("&")||key.contains("#")||key.contains("?")||key.contains("api_key="))throw new IllegalArgumentException("Paste just the key, or the complete &api_key=…&user_id=… string.");
        return new ApiConfig(user,key);
    }
    static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    String postsUrl(String query,boolean safe,String sort,int page,int limit) {
        if(page<0||limit<1||limit>1000)throw new IllegalArgumentException("Invalid pagination");
        String tags=Catalog.safeQuery(SearchTags.normalize(query),safe);
        if(sort.equals("Random"))tags+=" sort:random";
        else if(sort.equals("Trending")||sort.equals("Top rated"))tags+=" sort:score:desc";
        return "https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit="+limit+"&pid="+page+"&tags="+encode(tags.trim())+"&user_id="+encode(user)+"&api_key="+encode(key);
    }
    static String mediaUrl(String input) {
        String s=input.trim();if(s.isEmpty()||s.equalsIgnoreCase("null"))return "";
        if(s.startsWith("//"))s="https:"+s;
        if(s.startsWith("http://"))s="https://"+s.substring(7);
        if(!s.contains("://")&&s.matches("(?i)[a-z0-9.-]+\\.rule34\\.xxx/.*"))s="https://"+s;
        try {URI uri=URI.create(s);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)return "";return uri.toASCIIString();}catch(IllegalArgumentException e){return "";}
    }
    static String failure(int status,String body) {
        String lower=body.toLowerCase(Locale.ROOT);
        if(status==429||lower.contains("rate limit")||lower.contains("too many requests"))return "Rate limited by Rule34. Wait a minute, then retry.";
        if(lower.contains("cloudflare")||lower.contains("just a moment")||lower.contains("enable javascript"))return "Rule34 blocked this connection with browser verification. Try another network or open the site in your browser.";
        if(status==401||status==403||lower.contains("api key")||lower.contains("api_key")||lower.contains("user_id")||lower.contains("unauthorized")||lower.contains("authentication")||lower.contains("credentials"))return "Rule34 rejected the credentials. Copy API Access Credentials from account options, then use Save & connect.";
        if(status>=500||lower.contains("search down"))return "Rule34 is temporarily unavailable. Please retry later.";
        if(status!=200)return "Rule34 returned HTTP "+status+". Please retry or test the connection in Settings.";
        return "Rule34 returned an unexpected response. Test the connection in Settings.";
    }
}
