package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real loopback HTTP; provider reader injected. No real keys/accounts. */
public class AutoServicesTest {
    static int checks;static final String KEY="1".repeat(32),ID="2".repeat(32),SECRET="3".repeat(32);
    static final Map<String,String> ENV=Map.of("FG_TMDB_API_KEY",KEY,"FG_RAWG_API_KEY",KEY,"FG_TRAKT_CLIENT_ID",ID,"FG_TRAKT_CLIENT_SECRET",SECRET);
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    static class Rig implements AutoCloseable {
        final HttpServer server;final ExecutorService workers=Executors.newCachedThreadPool();final List<AutoServices.Plan> calls=Collections.synchronizedList(new ArrayList<>());final AtomicLong clock=new AtomicLong(1000000);
        volatile AutoServices.Reply reply=new AutoServices.Reply(200,"{}",null,null);volatile long latency;final String base;
        Rig(Map<String,String> env)throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);server.createContext("/api/services/",new AutoServices(env,p->{calls.add(p);clock.addAndGet(latency);return reply;},clock::get));server.start();base="http://127.0.0.1:"+server.getAddress().getPort()+"/api/services/";}
        HttpResponse<String> call(String path,String method,String body,Map<String,String> headers)throws Exception{var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(5));headers.forEach(b::header);b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));return HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> get(String p)throws Exception{return call(p,"GET",null,Map.of("X-FranchiseGuide-Services","1"));}
        HttpResponse<String> post(String p,String body)throws Exception{return call(p,"POST",body,Map.of("X-FranchiseGuide-Services","1"));}
        public void close(){server.stop(0);workers.shutdownNow();}
    }
    public static void main(String[] args)throws Exception {
        try(var r=new Rig(Map.of())){
            var c=r.get("config");check("No keys required for capability discovery",c.statusCode()==200&&c.body().contains("\"tmdb\":false"));
            check("Public catalogues listed",c.body().contains("tvmaze")&&c.body().contains("freetogame")&&c.body().contains("itunes"));
            check("Public TVmaze forwarded without key",r.get("tvmaze/search/shows?q=Fixture").statusCode()==200&&r.calls.get(0).uri().getHost().equals("api.tvmaze.com"));
            check("Unconfigured TMDb not simulated",r.get("tmdb/3/movie/1").statusCode()==503);
            check("Unconfigured Trakt not simulated",r.post("trakt-auth/oauth/device/code","{}").statusCode()==503);
            check("Browser without application header refused",r.call("config","GET",null,Map.of()).statusCode()==403);
            check("Cross origin refused",r.call("config","GET",null,Map.of("X-FranchiseGuide-Services","1","Origin","https://evil.test")).statusCode()==403);
            check("Cross site refused",r.call("config","GET",null,Map.of("X-FranchiseGuide-Services","1","Sec-Fetch-Site","cross-site")).statusCode()==403);
        }
        try(var r=new Rig(Map.of())) {
            for(String path:List.of("tvmaze/people/88","tvmaze/people/88/castcredits?embed=show","tvmaze/people/88/crewcredits?embed=show","tvmaze/episodes/101?embed=show","tvmaze/episodes/101/guestcast","tvmaze/episodes/101/guestcrew")) {
                check("Public detail route without key "+path,r.get(path).statusCode()==200);
                var p=r.calls.get(r.calls.size()-1);check("Public detail no credential injected",p.uri().getHost().equals("api.tvmaze.com")&&!p.headers().containsKey("Authorization")&&!p.headers().containsKey("trakt-api-key"));
            }
            for(String path:List.of("tvmaze/people/0","tvmaze/people/88?embed=show","tvmaze/episodes/101/guestcast?embed=show","tvmaze/people/88/castcredits?embed=http://evil.test","tvmaze/people/88/castcredits?embed=show&embed=show"))check("Refuse unsupported public expansion "+path,r.get(path).statusCode()==400);
            check("Public detail route rejects POST",r.post("tvmaze/episodes/101/guestcast","{}").statusCode()==405);
        }
        try(var r=new Rig(ENV)){
            var c=r.get("config");check("Config contains only capability flags",!c.body().contains(KEY)&&!c.body().contains(ID)&&!c.body().contains(SECRET)&&c.body().contains("\"trakt\":true"));
            check("Config not cached by browser",c.headers().firstValue("Cache-Control").orElse("").equals("no-store"));
            r.get("tmdb/3/search/tv?query=Fixture&api_key=wrong");var request=r.calls.get(r.calls.size()-1);
            check("TMDb key injected only upstream",request.uri().getQuery().contains("api_key="+KEY)&&!request.uri().getQuery().contains("wrong"));
            r.post("trakt-auth/oauth/device/code","{\"client_id\":\"marker\",\"client_secret\":\"wrong\"}");request=r.calls.get(r.calls.size()-1);
            check("Device start binds publisher client id",request.body().contains(ID)&&!request.body().contains("marker")&&!request.body().contains("wrong")&&!request.body().contains(SECRET));
            r.post("trakt-auth/oauth/device/token","{\"client_id\":\"marker\",\"code\":\"user-code\"}");request=r.calls.get(r.calls.size()-1);
            check("Device token binds secret and user code",request.body().contains(SECRET)&&request.body().contains("user-code"));
            r.reply=new AutoServices.Reply(200,"{\"access_token\":\"user-access\",\"echo\":\""+SECRET+"\"}",null,null);
            var token=r.post("trakt-auth/oauth/device/token","{\"code\":\"test\"}");check("App secret redacted while user token preserved",!token.body().contains(SECRET)&&token.body().contains("user-access"));
            r.reply=new AutoServices.Reply(200,"[]",null,"4");var privateRead=r.call("trakt/sync/watched/shows","GET",null,Map.of("X-FranchiseGuide-Services","1","Authorization","Bearer user-token"));
            check("Private token not replaced with app credential",r.calls.get(r.calls.size()-1).headers().get("Authorization").equals("Bearer user-token"));
            check("Trakt app key injected",r.calls.get(r.calls.size()-1).headers().get("trakt-api-key").equals(ID));
            check("Pagination count preserved",privateRead.headers().firstValue("X-Pagination-Page-Count").orElse("").equals("4"));
            check("Private API requires bearer",r.get("trakt/sync/history/movies").statusCode()==401);
            check("Bearer shape validated",r.call("trakt/users/settings","GET",null,Map.of("X-FranchiseGuide-Services","1","Authorization","Basic secret")).statusCode()==400);
            check("OAuth refresh only",r.post("trakt-auth/oauth/token","{\"grant_type\":\"password\",\"refresh_token\":\"x\"}").statusCode()==400);
            check("Unknown OAuth body fields rejected",r.post("trakt-auth/oauth/device/code","{\"password\":\"no\"}").statusCode()==400);
            check("OAuth control characters rejected",r.post("trakt-auth/oauth/device/token","{\"code\":\"a\\u0001b\"}").statusCode()==400);
            check("Only movie storefront entity permitted",r.get("itunes/search?term=fixture&entity=song&limit=20").statusCode()==200&&r.calls.get(r.calls.size()-1).uri().getQuery().contains("entity=movie"));
            check("Cookie not relayed",r.call("freetogame/api/games","GET",null,Map.of("X-FranchiseGuide-Services","1","Cookie","private=no")).statusCode()==200&&!r.calls.get(r.calls.size()-1).headers().containsKey("Cookie"));
            check("Trakt public query does not share authenticated cache",r.get("trakt/search/tmdb/3?type=show").statusCode()==200);
        }
        for(String bad:List.of("tvmaze/shows/1?url=https://evil.test","tvmaze/shows/%31","tmdb/3/authentication/token/new","itunes/search?term=a&term=b","freetogame/api/games?token=bad","itunes/search?limit=0","itunes/search?limit=201","trakt/users/arbitrary")){
            try(var r=new Rig(ENV)){check("Rejected endpoint "+bad,r.get(bad).statusCode()==400);check("No upstream call on bad endpoint",r.calls.isEmpty());}
        }
        try(var r=new Rig(ENV)){
            check("No public writes",r.post("tvmaze/shows/1","{}").statusCode()==405);
            r.get("tvmaze/shows/1");r.get("tvmaze/shows/1");check("Public GET has no hidden gateway cache",r.calls.size()==2);
            r.post("trakt-auth/oauth/device/code","{}");r.post("trakt-auth/oauth/device/code","{}");check("OAuth POST is neither cached nor auto-retried",r.calls.size()==4);
            r.reply=new AutoServices.Reply(400,"private details",null,null);check("Device pending code preserved",r.post("trakt-auth/oauth/device/token","{\"code\":\"x\"}").statusCode()==400);
            r.reply=new AutoServices.Reply(429,"too many",null,null);var limited=r.get("rawg/api/games");int before=r.calls.size();var again=r.get("rawg/api/games/2");check("429 default cooldown persisted",limited.statusCode()==429&&again.statusCode()==429&&r.calls.size()==before&&again.headers().firstValue("Retry-After").orElse("").equals("60"));
            r.clock.addAndGet(61000);r.reply=new AutoServices.Reply(200,"{}",null,null);check("Cooldown permits later retry",r.get("rawg/api/games").statusCode()==200);
        }
        try(var r=new Rig(ENV)){
            r.reply=new AutoServices.Reply(200,"malformed",null,null);check("Bad provider JSON is not cached",r.get("tvmaze/shows/1").statusCode()==502);
            r.reply=new AutoServices.Reply(503,"<html>"+KEY+"</html>",null,null);var bad=r.get("rawg/api/games");check("Provider errors sanitized",bad.statusCode()==503&&!bad.body().contains(KEY)&&!bad.body().contains("<html>"));
            r.reply=new AutoServices.Reply(302,"",null,null);check("No provider redirect followed",r.get("itunes/search?term=x").statusCode()==502);
        }
        try(var r=new Rig(ENV)){
            String controls="line\b\f\u0001\nend";
            String escaped=("{\"access_token\":\"personal-access-token\",\"nested\":[{\"echo\":\""+SECRET+"\",\"note\":\"line\\b\\f\\u0001\\nend\"}],\""+SECRET+"\":\"secret-key\"}").replace("3","\\u0033");
            r.reply=new AutoServices.Reply(200,escaped,null,null);
            var response=r.post("trakt-auth/oauth/device/token","{\"code\":\"fixture\"}");check("Escaped publisher secrets stay server-side",response.statusCode()==200&&!AutoServices.json(RetroBridge.Json.parse(response.body())).contains(SECRET));
            Map<?,?> data=(Map<?,?>)RetroBridge.Json.parse(response.body());Map<?,?> child=(Map<?,?>)((List<?>)data.get("nested")).get(0);
            check("User OAuth token preserved after secret redaction",data.get("access_token").equals("personal-access-token"));
            check("Escaped nested credential redacted",child.get("echo").equals("[redacted]"));
            check("Control characters survive JSON roundtrip",child.get("note").equals(controls));
            r.reply=new AutoServices.Reply(200,"{\"duplicate\":1,\"duplicate\":2}",null,null);check("Ambiguous provider JSON fails safely",r.get("tvmaze/shows/1").statusCode()==502);
        }
        try(var r=new Rig(ENV)){
            r.reply=new AutoServices.Reply(429,"private", "not-a-date",null);var response=r.get("tvmaze/shows/1");
            check("Invalid Retry-After has safe default",response.statusCode()==429&&response.headers().firstValue("Retry-After").orElse("").equals("60"));
            int before=r.calls.size();r.clock.addAndGet(1000);response=r.get("tvmaze/shows/2");
            check("Malformed provider retry cannot skip cooldown",response.statusCode()==429&&r.calls.size()==before&&response.headers().firstValue("Retry-After").orElse("").equals("59"));
            r.clock.addAndGet(60000);r.reply=new AutoServices.Reply(200,"{}",null,null);check("Malformed retry cooldown eventually ends",r.get("tvmaze/shows/2").statusCode()==200&&r.calls.size()==before+1);
        }
        try(var r=new Rig(ENV)){
            r.latency=25000;r.reply=new AutoServices.Reply(429,"private","60",null);r.get("tvmaze/shows/1");int before=r.calls.size();var response=r.get("tvmaze/shows/2");
            check("Provider latency does not shorten cooldown",response.statusCode()==429&&r.calls.size()==before&&response.headers().firstValue("Retry-After").orElse("").equals("60"));
        }
        try(var r=new Rig(ENV)){
            for(String name:List.of("Sec-Fetch-Site","Origin","X-FranchiseGuide-Services","Authorization")){
                var request=HttpRequest.newBuilder(URI.create(r.base+"tvmaze/shows/1")).header("X-FranchiseGuide-Services","1");
                if(name.equals("Sec-Fetch-Site"))request.header(name,"same-origin").header(name,"cross-site");
                else if(name.equals("Origin"))request.header(name,r.base.substring(0,r.base.indexOf("/api/"))).header(name,"https://evil.test");
                else if(name.equals("Authorization"))request.header(name,"Bearer one").header(name,"Bearer two");
                else request.header(name,"1");
                var response=HTTP.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());check("Duplicate security header rejected "+name,response.statusCode()==403&&r.calls.isEmpty());
            }
        }
        System.out.println("Auto Services Java: "+checks+" assertions, actual loopback HTTP with synthetic upstream and fake credentials; no real account.");
    }
}
