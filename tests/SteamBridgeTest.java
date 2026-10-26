package com.franchiseguide.desktop;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Real loopback HTTP server, injected Steam reader. No actual Steam account or key. */
public class SteamBridgeTest {
 static int passed;static HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 static void check(String name,boolean value){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);passed++;}
 static HttpResponse<String> req(String base,String path,String method,Map<String,String> headers)throws Exception{var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(5)).header("X-FranchiseGuide-Client","0.11");headers.forEach(b::header);return client.send(b.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());}
 public static void main(String[] args)throws Exception {
  System.setProperty("jdk.httpclient.allowRestrictedHeaders","host");
  var calls=new ArrayList<URI>();String key="b".repeat(32);var count=new AtomicInteger();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/gaming/",new SteamBridge(key,uri->{calls.add(uri);count.incrementAndGet();if(uri.getQuery().contains("appid=999"))throw new Exception(key);if(uri.getQuery().contains("appid=997"))return "{";if(uri.getQuery().contains("appid=998"))return "{\"echo\":\""+"\\u0062".repeat(32)+"\"}";return "{\"fixture\":true,\"echo\":\""+key+"\"}";}));server.start();
  String base="http://127.0.0.1:"+server.getAddress().getPort(),q="/api/gaming/steam?steamid=76561198000000001&appid=123&language=french";
  try {
   var r=req(base,"/api/gaming/status","GET",Map.of());check("Status identifies bridge",r.statusCode()==200&&r.body().contains("FranchiseGuide Gaming Bridge"));check("Status never exposes key",!r.body().contains(key));check("Status explicitly read-only",r.body().contains("\"writes\":false"));
   r=req(base,q,"GET",Map.of());check("Java returns schema player rarity bundle",r.statusCode()==200&&r.body().contains("\"steamid\":\"76561198000000001\""));check("All three upstream paths read-only",calls.size()==3&&calls.stream().allMatch(u->u.getHost().equals("api.steampowered.com")&&!u.getPath().contains("Set")));check("Browser receives no API key even if upstream echoes",!r.body().contains(key));check("Account responses never cache in browser",r.headers().firstValue("Cache-Control").orElse("").equals("no-store"));check("No wildcard CORS",r.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
   req(base,q,"GET",Map.of());check("Recent identical data reuses local cache",calls.size()==3);
   for(String method:List.of("POST","PUT","DELETE","PATCH"))check("Java rejects write "+method,req(base,q,method,Map.of()).statusCode()==405);
   check("Java rejects foreign Origin",req(base,q,"GET",Map.of("Origin","https://evil.test")).statusCode()==403);
   check("Java rejects cross-site request",req(base,q,"GET",Map.of("Sec-Fetch-Site","cross-site")).statusCode()==403);
   check("Missing application header rejected",client.send(HttpRequest.newBuilder(URI.create(base+q)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()==403);
   check("Unknown path cannot become arbitrary proxy",req(base,"/api/gaming/psn?url=https://evil.test","GET",Map.of()).statusCode()==404);
   check("HEAD cannot trigger Steam fetch",req(base,q,"HEAD",Map.of()).statusCode()==405);
   for(String suffix:List.of("&url=https://evil.test","&key=secret","&appid=22"))check("Unknown or duplicated query refused "+suffix,req(base,q+suffix,"GET",Map.of()).statusCode()==400);
   check("Invalid numeric identifier refused",req(base,q.replace("appid=123","appid=4294967296"),"GET",Map.of()).statusCode()==400);
   check("Only known localization language accepted",req(base,q.replace("french","../../secret"),"GET",Map.of()).statusCode()==400);
   r=req(base,q.replace("appid=123","appid=999"),"GET",Map.of());check("Java upstream failure yields generic failure",r.statusCode()==502);check("Java error never contains key",!r.body().contains(key));
   req(base,q.replace("00000001","00000002"),"GET",Map.of());check("Accounts not conflated in cache",calls.size()>4);
   r=req(base,q.replace("appid=123","appid=997"),"GET",Map.of());check("Malformed Steam JSON is rejected before caching",r.statusCode()==502);int invalidCalls=calls.size();
   r=req(base,q.replace("appid=123","appid=997"),"GET",Map.of());check("Failed Steam response is never cached",r.statusCode()==502&&calls.size()>invalidCalls);
   r=req(base,q.replace("appid=123","appid=998"),"GET",Map.of());check("JSON-escaped Steam key cannot reach browser",r.statusCode()==200&&!AutoServices.json(RetroBridge.Json.parse(r.body())).contains(key));
   var duplicate=HttpRequest.newBuilder(URI.create(base+q)).header("X-FranchiseGuide-Client","0.11").header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Site","cross-site").GET().build();
   int before=calls.size();r=client.send(duplicate,HttpResponse.BodyHandlers.ofString());check("Duplicate security headers refused before Steam read",r.statusCode()==403&&calls.size()==before);
  } finally {server.stop(0);}
  var noKey=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);noKey.createContext("/api/gaming/",new SteamBridge("",u->{throw new AssertionError("No request without key");}));noKey.start();
  try{String b="http://127.0.0.1:"+noKey.getAddress().getPort();check("Java no-key status accurately false",req(b,"/api/gaming/status","GET",Map.of()).body().contains("\"steam\":false"));check("Java missing key yields 503",req(b,q,"GET",Map.of()).statusCode()==503);}finally{noKey.stop(0);}
  for(String uri:List.of("https://api.steampowered.com:8443/test","https://user:password@api.steampowered.com/test","https://api.steampowered.com/test#fragment")){
   boolean refused=false;try{SteamBridge.readSteam(URI.create(uri));}catch(java.io.IOException expected){refused=expected.getMessage().contains("Disallowed");}check("Noncanonical upstream refused "+uri,refused);
  }
  System.out.println("Java Steam bridge: "+passed+" HTTP assertions passed with injected upstream.");
 }
}
