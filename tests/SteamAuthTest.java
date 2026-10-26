package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the actual Java handlers on loopback; only Steam's remote responses are fixtures. */
public class SteamAuthTest {
    static final String ID="76561198000000001", KEY="1".repeat(32);
    static final long NOW=Instant.parse("2026-10-05T12:00:00Z").toEpochMilli();
    static int assertions=0;
    static void check(boolean v,String name){if(!v)throw new AssertionError(name);assertions++;System.out.println("PASS "+name);}
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    static String query(Map<String,String> m){return m.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).reduce((a,b)->a+"&"+b).orElse("");}
    static Map<String,String> assertion(String rt,String nonce){Map<String,String> m=new LinkedHashMap<>();m.put("state",URI.create(rt).getRawQuery().substring(6));m.put("openid.ns",SteamAuth.NS);m.put("openid.mode","id_res");m.put("openid.op_endpoint",SteamAuth.ENDPOINT);m.put("openid.claimed_id","https://steamcommunity.com/openid/id/"+ID);m.put("openid.identity","https://steamcommunity.com/openid/id/"+ID);m.put("openid.return_to",rt);m.put("openid.response_nonce","2026-10-05T12:00:00Z"+nonce);m.put("openid.assoc_handle","fixture-association");m.put("openid.signed","op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle");m.put("openid.sig","fixture-not-a-real-signature");return m;}
    record Begin(String cookie,String returnTo,String url){}
    record Login(HttpResponse<String> response,String cookie,Begin begin,Map<String,String> q){}
    static class Fixture implements AutoCloseable {
        final HttpServer server;final String base;final int port;final AtomicInteger verifications=new AtomicInteger();boolean accept=true;
        final List<URI> calls=Collections.synchronizedList(new ArrayList<>());
        final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        Fixture() throws Exception {
            SteamBridge.Reader reader=uri->{calls.add(uri);if(uri.getPath().contains("GetOwnedGames"))return "{\"response\":{\"game_count\":1,\"games\":[{\"appid\":10,\"name\":\"Jeu de test\",\"playtime_forever\":180}]}}";if(uri.getPath().contains("GetPlayerSummaries"))return "{\"response\":{\"players\":[{\"steamid\":\""+ID+"\",\"personaname\":\"Compte de test\"}]}}";if(uri.getPath().contains("GetSchemaForGame"))return "{\"game\":{\"gameName\":\"No exposed stats\"}}";throw new IllegalStateException("Unexpected upstream");};
            SteamBridge bridge=new SteamBridge(KEY,reader);SteamAuth auth=new SteamAuth(KEY,bridge,reader,q->{verifications.incrementAndGet();return accept;},()->NOW,null);
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/gaming/",ex->{if(SteamAuth.accepts(ex.getRequestURI().getPath()))auth.handle(ex);else bridge.handle(ex);});server.setExecutor(Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));server.start();port=server.getAddress().getPort();base="http://127.0.0.1:"+port;
        }
        HttpResponse<String> request(String route,String method,String cookie,Map<String,String> headers) throws Exception {
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+route)).timeout(Duration.ofSeconds(5)).header("X-FranchiseGuide-Client","0.12");if(cookie!=null)b.header("Cookie",cookie);headers.forEach(b::header);b.method(method,HttpRequest.BodyPublishers.noBody());return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String route,String cookie) throws Exception{return request(route,"GET",cookie,Map.of());}
        Begin begin() throws Exception {
            HttpResponse<String> r=request("/api/gaming/auth/steam/begin","POST",null,Map.of());check(r.statusCode()==200,"Java OpenID begin HTTP 200");String url=r.body().split("\"authorizeUrl\":\"")[1].split("\"")[0];Map<String,String> q=SteamAuth.parseQuery(URI.create(url).getRawQuery(),16384);return new Begin(r.headers().allValues("set-cookie").get(0).split(";")[0],q.get("openid.return_to"),url);
        }
        Login login(String nonce) throws Exception {Begin b=begin();Map<String,String> q=assertion(b.returnTo,nonce);HttpResponse<String> r=get("/api/gaming/auth/steam/callback?"+query(q),b.cookie);String c=r.headers().allValues("set-cookie").stream().filter(v->v.startsWith("FGSteamSession")).findFirst().map(v->v.split(";")[0]).orElse(null);return new Login(r,c,b,q);}
        public void close(){server.stop(0);}
    }
    static void invalid(Map<String,String> q,String rt,String name) throws Exception {boolean refused=false;try{SteamAuth.validate(q,rt,NOW);}catch(SteamAuth.Fault e){refused=true;}check(refused,name);}
    public static void main(String[] args) throws Exception {
        String rt="http://127.0.0.1:47631/api/gaming/auth/steam/callback?state=fixture";
        check(SteamAuth.validate(assertion(rt,"one"),rt,NOW).steamid().equals(ID),"signed ID extracted only from Steam identity");
        for(String field:List.of("op_endpoint","claimed_id","identity","return_to","response_nonce","assoc_handle")){
            Map<String,String> q=assertion(rt,"one");q.put("openid.signed",q.get("openid.signed").replace(field+",","").replace(","+field,""));invalid(q,rt,"reject unsigned "+field);
        }
        for(Map.Entry<String,String> e:Map.of("openid.op_endpoint","https://evil.example/openid","openid.ns","evil","openid.identity","https://steamcommunity.com/openid/id/76561198000000002","openid.response_nonce","2026-10-05T11:00:00Zold","openid.return_to","http://evil.example").entrySet()){
            Map<String,String> q=assertion(rt,"one");q.put(e.getKey(),e.getValue());invalid(q,rt,"reject changed "+e.getKey());
        }
        boolean dup=false;try{SteamAuth.parseQuery("x=1&x=2",100);}catch(SteamAuth.Fault e){dup=true;}check(dup,"reject duplicated query parameters");
        check(SteamAuth.cookies("x=1;x=2").get("x").isEmpty(),"duplicate cookie invalidated");
        try(Fixture f=new Fixture()){
            check(f.get("/api/gaming/steam/library",null).statusCode()==401,"unauthenticated library rejected");
            check(f.request("/api/gaming/auth/steam/begin","POST",null,Map.of("Origin","https://evil.example")).statusCode()==403,"cross-origin POST rejected");
            check(f.get("/api/gaming/auth/steam/begin",null).statusCode()==405,"GET cannot initiate login");
            check(f.get("/api/gaming/auth/steam/callback?state=missing",null).statusCode()==401,"missing callback state rejected");
            HttpResponse<String> page=f.request("/api/gaming/auth/steam/callback?state=missing&extra=PRIVATE","GET",null,Map.of("Accept","text/html"));check(page.statusCode()==401&&page.headers().firstValue("content-type").orElse("").startsWith("text/html")&&page.body().contains("Revenir à FranchiseGuide")&&!page.body().contains("PRIVATE"),"browser callback error offers safe recovery without echoed inputs");
            Login x=f.login("one");check(x.response.statusCode()==303,"verified login redirects");check(x.response.headers().firstValue("location").orElse("").equals("/?steam_connected=1"),"fixed return destination");check(x.cookie!=null,"session cookie issued");
            check(x.response.headers().allValues("set-cookie").get(0).contains("HttpOnly; SameSite=Lax"),"cookie HttpOnly and SameSite");
            check(f.get("/api/gaming/auth/steam/session",x.cookie).body().contains("\"authenticated\":true"),"session readable by current browser");
            check(f.get("/api/gaming/auth/steam/session",x.cookie).body().contains(ID),"session binds correct identity");
            HttpResponse<String> library=f.get("/api/gaming/steam/library",x.cookie);check(library.statusCode()==200,"connected library HTTP 200");check(library.body().contains("\"game_count\":1"),"owned games included");check(library.body().contains("Compte de test"),"profile metadata included");check(!library.body().contains(KEY),"API secret absent from response");
            int calls=f.calls.size();f.get("/api/gaming/steam/library",x.cookie);check(f.calls.size()==calls,"per-account library cache reuses response");
            check(f.get("/api/gaming/steam/library?steamid=76561198000000002",x.cookie).statusCode()==400,"cannot request another identity on connected route");
            check(f.get("/api/gaming/steam/achievements?appid=10",x.cookie).body().contains("\"noAchievements\":true"),"no exposed achievements is explicit");
            check(f.calls.stream().noneMatch(u->u.getPath().contains("GetPlayerAchievements")),"no player read for absent achievement schema");
            check(f.get("/api/gaming/auth/steam/callback?"+query(x.q),x.begin.cookie).statusCode()==401,"same callback cannot be consumed twice");
            Login again=f.login("one");check(again.response.statusCode()==401,"nonce cannot be reused for a second login");
            check(f.verifications.get()==1,"replay stopped before verifier");
            check(f.request("/api/gaming/auth/steam/disconnect","POST",x.cookie,Map.of()).statusCode()==200,"disconnect succeeds");
            check(f.get("/api/gaming/auth/steam/session",x.cookie).body().contains("false"),"old cookie revoked");check(f.get("/api/gaming/steam/library",x.cookie).statusCode()==401,"old cookie cannot read library");
        }
        try(Fixture f=new Fixture()){
            Begin b=f.begin();Map<String,String> q=assertion(b.returnTo,"fresh");check(f.get("/api/gaming/auth/steam/callback?"+query(q),null).statusCode()==401,"no binding cookie rejected");check(f.verifications.get()==0,"binding failure stopped before Steam verifier");
            q.put("openid.mode","cancel");HttpResponse<String> cancelled=f.get("/api/gaming/auth/steam/callback?"+query(q),b.cookie);check(cancelled.statusCode()==303&&cancelled.headers().firstValue("location").orElse("").contains("steam_cancelled"),"Steam cancel redirects without session");
            f.accept=false;Login x=f.login("invalid");check(x.response.statusCode()==401&&x.cookie==null,"unverified assertion cannot create session");
            check(f.request("/api/gaming/auth/steam/bad/begin","POST",null,Map.of()).statusCode()==404,"unknown login endpoint rejected");
        }
        Path dir=Files.createTempDirectory("fg-java-session-");Path file=dir.resolve("vault.properties");String token="f".repeat(64);
        try{
            SteamAuth.Vault v=new SteamAuth.Vault(file,()->NOW);v.put(token,new SteamAuth.Session(ID,NOW+10000),null);check(!Files.readString(file).contains(token),"raw bearer cookie not persisted");
            check(new SteamAuth.Vault(file,()->NOW).get(token).steamid().equals(ID),"session survives Java process restart");
            check(new SteamAuth.Vault(file,()->NOW+20000).get(token)==null,"expired session not resurrected");
            v.remove(token);check(new SteamAuth.Vault(file,()->NOW).get(token)==null,"logout revocation survives restart");
            Files.writeString(file,"broken");boolean refused=false;try{new SteamAuth.Vault(file,()->NOW);}catch(Exception e){refused=true;}check(refused&&Files.readString(file).equals("broken"),"corrupt vault left intact");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
        System.out.println("{\"assertions\":"+assertions+",\"passed\":true,\"scope\":\"Java loopback handlers, simulated Steam verifier and responses\"}");
    }
}
