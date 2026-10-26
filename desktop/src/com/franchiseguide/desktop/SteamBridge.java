package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Read-only, loopback-only Steam bridge. No password, no CORS proxy, no write endpoint. */
public final class SteamBridge implements HttpHandler {
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private static final Set<String> LANGUAGES = Set.of("english", "french", "german", "spanish", "italian", "japanese", "portuguese");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String key;
    private final Reader reader;
    private final Semaphore slots = new Semaphore(2);
    private final ArrayDeque<Long> requests = new ArrayDeque<>();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>();
    record Cached(long at, String json) {}
    @FunctionalInterface interface Reader { String read(URI uri) throws Exception; }
    public SteamBridge() { this(System.getenv("FG_STEAM_API_KEY"), SteamBridge::readSteam); }
    SteamBridge(String key, Reader reader) { this.key = key == null ? "" : key.trim(); this.reader = reader; }
    boolean configured() { return key.matches("[0-9a-fA-F]{32}"); }

    @Override public void handle(HttpExchange ex) throws IOException {
        String host = ex.getRequestHeaders().getFirst("Host");
        int port = ex.getLocalAddress().getPort();
        if (LocalRequest.ambiguousHeaders(ex)||!(Objects.equals(host, "127.0.0.1:"+port) || Objects.equals(host, "localhost:"+port))) { reply(ex,403,"{\"error\":\"Invalid host or ambiguous headers\"}");return; }
        if (!Set.of("GET","HEAD").contains(ex.getRequestMethod())) { ex.getResponseHeaders().set("Allow","GET, HEAD");reply(ex,405,"{\"error\":\"Read only\"}");return; }
        String origin = ex.getRequestHeaders().getFirst("Origin"), site=ex.getRequestHeaders().getFirst("Sec-Fetch-Site");
        if ((origin!=null&&!origin.equals("http://"+host)) || "cross-site".equals(site) || !Set.of("0.11","0.12").contains(Objects.toString(ex.getRequestHeaders().getFirst("X-FranchiseGuide-Client"),""))) { reply(ex,403,"{\"error\":\"Same-origin application request required\"}");return; }
        String path = ex.getRequestURI().getPath();
        if ("/api/gaming/status".equals(path)) { reply(ex,200,"{\"app\":\"FranchiseGuide Gaming Bridge\",\"version\":\"0.16.0\",\"steamSignIn\":true,\"steam\":"+configured()+",\"writes\":false}");return; }
        if (!"/api/gaming/steam".equals(path)) { reply(ex,404,"{\"error\":\"Unknown endpoint\"}");return; }
        if ("HEAD".equals(ex.getRequestMethod())) { reply(ex,405,"{\"error\":\"GET required\"}");return; }
        final Map<String,String> q;
        try { q=parseQuery(ex.getRequestURI().getRawQuery()); } catch (Exception bad) { reply(ex,400,"{\"error\":\"Invalid parameters\"}");return; }
        if (!configured()) { reply(ex,503,"{\"error\":\"FG_STEAM_API_KEY is not configured on this local server\"}");return; }
        try { reply(ex,200,lookup(q)); }
        catch (Failure e) { if(e.status==429)ex.getResponseHeaders().set("Retry-After","60");reply(ex,e.status,"{\"error\":\""+(e.status==429?"Local rate limit":"Steam not configured")+"\"}"); }
        catch (Exception e) { reply(ex,502,"{\"error\":\"Steam unavailable. Existing local data is unchanged.\"}"); }
    }
    static final class Failure extends IOException { final int status; Failure(int status,String message){super(message);this.status=status;} }
    String lookup(Map<String,String> params) throws Exception {
        String encoded="steamid="+enc(params.getOrDefault("steamid",""))+"&appid="+enc(params.getOrDefault("appid",""))+"&language="+enc(params.getOrDefault("language","english"));
        Map<String,String> q=parseQuery(encoded);
        if(!configured())throw new Failure(503,"Steam key missing");
        String cacheKey=q.get("steamid")+":"+q.get("appid")+":"+q.get("language");
        synchronized(cache){Cached c=cache.get(cacheKey);if(c!=null&&System.currentTimeMillis()-c.at<60000)return c.json;}
        if(!takeRate()||!slots.tryAcquire())throw new Failure(429,"Local rate limit");
        try {
            String common="appid="+q.get("appid")+"&l="+q.get("language")+"&key="+enc(key);
            String schema=reader.read(URI.create("https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?"+common));
            boolean noAchievements=schema.contains("\"gameName\"") && (!schema.contains("\"achievements\"") || java.util.regex.Pattern.compile("\"achievements\"\\s*:\\s*\\[\\s*\\]").matcher(schema).find());
            String player="null",rarity="null";
            if(!noAchievements){
                player=reader.read(URI.create("https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?"+common+"&steamid="+q.get("steamid")));
                try{rarity=reader.read(URI.create("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid="+q.get("appid")));}catch(Exception ignored){}
            }
            for(String json:List.of(schema,player,rarity)){if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BODY)throw new IOException("Invalid upstream response");Object value=RetroBridge.Json.parse(json);if(value!=null&&!(value instanceof Map<?,?>))throw new IOException("Invalid upstream response");}
            String result="{\"provider\":\"steam\",\"steamid\":\""+q.get("steamid")+"\",\"appid\":\""+q.get("appid")+"\",\"fetchedAt\":"+System.currentTimeMillis()+",\"noAchievements\":"+noAchievements+",\"schema\":"+schema+",\"player\":"+player+",\"rarity\":"+rarity+"}";
            result=AutoServices.redactResponse(result,List.of(key));
            synchronized(cache){cache.put(cacheKey,new Cached(System.currentTimeMillis(),result));while(cache.size()>32)cache.remove(cache.keySet().iterator().next());}
            return result;
        } finally { slots.release(); }
    }
    private synchronized boolean takeRate() { long now=System.currentTimeMillis();while(!requests.isEmpty()&&requests.peekFirst()<now-60000)requests.removeFirst();if(requests.size()>=12)return false;requests.add(now);return true; }
    static Map<String,String> parseQuery(String raw) {
        if(raw==null||raw.length()>300)throw new IllegalArgumentException();Map<String,String> q=new HashMap<>();
        for(String pair:raw.split("&")) { String[] parts=pair.split("=",2);if(parts.length!=2)throw new IllegalArgumentException();String k=URLDecoder.decode(parts[0],StandardCharsets.UTF_8),v=URLDecoder.decode(parts[1],StandardCharsets.UTF_8);if(!Set.of("steamid","appid","language").contains(k)||q.put(k,v)!=null)throw new IllegalArgumentException(); }
        if(!q.getOrDefault("steamid","").matches("[0-9]{17}"))throw new IllegalArgumentException();
        String app=q.getOrDefault("appid","");if(!app.matches("[1-9][0-9]{0,9}")||Long.parseLong(app)>4294967295L)throw new IllegalArgumentException();
        q.putIfAbsent("language","english");if(!LANGUAGES.contains(q.get("language")))throw new IllegalArgumentException();return q;
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String readSteam(URI uri) throws Exception {
        // Host and routes are built internally, never supplied by the browser.
        if(!"https".equals(uri.getScheme())||!"api.steampowered.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null)throw new IOException("Disallowed host");
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("Accept","application/json").header("User-Agent","FranchiseGuide/0.12 (personal read-only tracker)").GET().build();
        CompletableFuture<HttpResponse<byte[]>> future=HTTP.sendAsync(request,info -> new BoundedBody(MAX_BODY));
        HttpResponse<byte[]> response;
        try { response=future.get(14,TimeUnit.SECONDS); } catch(Exception e) { future.cancel(true);throw e; }
        if(response.statusCode()!=200)throw new IOException("Upstream HTTP error");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;private final ByteArrayOutputStream out=new ByteArrayOutputStream();private final CompletableFuture<byte[]> body=new CompletableFuture<>();private Flow.Subscription subscription;
        BoundedBody(int max){this.max=max;}
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> blocks){for(ByteBuffer b:blocks){if((long)out.size()+b.remaining()>max){subscription.cancel();body.completeExceptionally(new IOException("Response too large"));return;}byte[] part=new byte[b.remaining()];b.get(part);out.writeBytes(part);}subscription.request(1);}
        public void onError(Throwable t){body.completeExceptionally(t);}
        public void onComplete(){body.complete(out.toByteArray());}
    }
    private static void reply(HttpExchange ex,int status,String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.getResponseHeaders().set("Referrer-Policy","no-referrer");
        if("HEAD".equals(ex.getRequestMethod())){ex.sendResponseHeaders(status,-1);ex.close();return;}byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(status,bytes.length);try(OutputStream out=ex.getResponseBody()){out.write(bytes);}
    }
}
