package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Steam-only OpenID RP. Personal loopback deployment, not a public multi-user service. */
public final class SteamAuth implements HttpHandler {
    static final String ENDPOINT="https://steamcommunity.com/openid/login", NS="http://specs.openid.net/auth/2.0";
    static final long PENDING_MS=600000L, SESSION_MS=7L*86400000L;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String key; private final SteamBridge bridge; private final SteamBridge.Reader reader; private final Verifier verifier; private final LongSupplier clock;
    private Vault vault; private boolean unavailable;
    private final ConcurrentHashMap<String,Pending> pending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Long> nonces=new ConcurrentHashMap<>(),cancelled=new ConcurrentHashMap<>();
    private final LinkedHashMap<String,Cached> cache=new LinkedHashMap<>();
    private final ArrayDeque<Long> starts=new ArrayDeque<>(),reads=new ArrayDeque<>();
    private final Semaphore slots=new Semaphore(2);
    record Pending(String binding,String returnTo,long expiresAt){}
    record Session(String steamid,long expiresAt){}
    record Assertion(String steamid,String nonce){}
    record Cached(long at,String data){}
    @FunctionalInterface interface Verifier { boolean verify(Map<String,String> response) throws Exception; }
    static class Fault extends IOException { final int status;Fault(int status,String msg){super(msg);this.status=status;} }
    public SteamAuth(SteamBridge bridge){this(System.getenv("FG_STEAM_API_KEY"),bridge,SteamBridge::readSteam,SteamAuth::verifySteam,System::currentTimeMillis,Path.of(System.getProperty("user.home"),".franchiseguide","steam-sessions-pc.properties"));}
    SteamAuth(String key,SteamBridge bridge,SteamBridge.Reader reader,Verifier verifier,LongSupplier clock,Path vaultFile){
        this.key=key==null?"":key.trim();this.bridge=bridge;this.reader=reader;this.verifier=verifier;this.clock=clock;
        try{this.vault=new Vault(vaultFile,clock);}catch(Exception ex){unavailable=true;}
    }
    static boolean accepts(String path){return path.startsWith("/api/gaming/auth/steam/")||Set.of("/api/gaming/steam/library","/api/gaming/steam/achievements").contains(path);}
    private static String random(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean same(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String query(Map<String,String> q){return q.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).reduce((a,b)->a+"&"+b).orElse("");}
    static Map<String,String> parseQuery(String raw,int max) throws Fault {
        Map<String,String> q=new LinkedHashMap<>();if(raw==null||raw.isEmpty())return q;if(raw.length()>max)throw new Fault(400,"Response too large");
        try{for(String pair:raw.split("&",-1)){String[] p=pair.split("=",2);String k=URLDecoder.decode(p[0],StandardCharsets.UTF_8),v=p.length==2?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"";if(k.length()>100||v.length()>8192||q.putIfAbsent(k,v)!=null)throw new Fault(400,"Duplicate or invalid parameters");}}catch(IllegalArgumentException e){throw new Fault(400,"Invalid parameters");}return q;
    }
    static Map<String,String> cookies(String raw){Map<String,String> out=new HashMap<>();for(String part:Objects.toString(raw,"").split(";")){int i=part.indexOf('=');if(i<0)continue;String k=part.substring(0,i).trim(),v=part.substring(i+1).trim();if(out.containsKey(k))out.put(k,"");else out.put(k,v);}return out;}
    static Assertion validate(Map<String,String> q,String returnTo,long now) throws Fault {
        String endpoint=q.get("openid.op_endpoint"),identity=q.get("openid.claimed_id");
        if(!NS.equals(q.get("openid.ns"))||!"id_res".equals(q.get("openid.mode"))||!(ENDPOINT.equals(endpoint)||"https://steamcommunity.com/openid/".equals(endpoint)))throw new Fault(400,"Invalid Steam OpenID response");
        if(!returnTo.equals(q.get("openid.return_to")))throw new Fault(400,"Return URL mismatch");
        if(identity==null||!identity.equals(q.get("openid.identity"))||!identity.matches("https://steamcommunity\\.com/openid/id/[0-9]{17}"))throw new Fault(400,"Invalid Steam identity");
        List<String> signed=Arrays.asList(q.getOrDefault("openid.signed","").split(",",-1));
        if(new HashSet<>(signed).size()!=signed.size()||!signed.containsAll(List.of("op_endpoint","claimed_id","identity","return_to","response_nonce","assoc_handle"))||q.getOrDefault("openid.sig","").isEmpty()||q.getOrDefault("openid.assoc_handle","").isEmpty())throw new Fault(400,"Incomplete OpenID signature");
        String nonce=q.getOrDefault("openid.response_nonce","");if(!nonce.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z.+"))throw new Fault(400,"Invalid OpenID nonce");
        try{String stamp=nonce.substring(0,20);Instant at=Instant.parse(stamp);if(!at.toString().equals(stamp)||now-at.toEpochMilli()>PENDING_MS||at.toEpochMilli()-now>60000)throw new Fault(400,"Expired Steam response");}catch(DateTimeException e){throw new Fault(400,"Invalid nonce timestamp");}
        for(String k:q.keySet())if(!k.equals("state")&&!k.startsWith("openid."))throw new Fault(400,"Unexpected parameter");
        return new Assertion(identity.substring(identity.lastIndexOf('/')+1),nonce);
    }
    private synchronized boolean takeRate(ArrayDeque<Long> q,long period,int max){long now=clock.getAsLong();while(!q.isEmpty()&&now-q.peekFirst()>=period)q.removeFirst();if(q.size()>=max)return false;q.add(now);return true;}
    private void clean(){long now=clock.getAsLong();pending.entrySet().removeIf(e->e.getValue().expiresAt<=now);nonces.entrySet().removeIf(e->now-e.getValue()>PENDING_MS);cancelled.entrySet().removeIf(e->now-e.getValue()>PENDING_MS);}
    @Override public void handle(HttpExchange ex) throws IOException {
        String host=ex.getRequestHeaders().getFirst("Host"),route=ex.getRequestURI().getPath(),method=ex.getRequestMethod();int port=ex.getLocalAddress().getPort();
        String origin="http://"+host,sessionName="FGSteamSession"+port,pendingName="FGSteamPending"+port;
        Map<String,String> jar=cookies(ex.getRequestHeaders().getFirst("Cookie"));
        try{
            clean();if(LocalRequest.ambiguousHeaders(ex)||!Set.of("127.0.0.1:"+port,"localhost:"+port).contains(Objects.toString(host,"")))throw new Fault(403,"Invalid local host or ambiguous headers");
            if(!Set.of("/api/gaming/auth/steam/begin","/api/gaming/auth/steam/callback","/api/gaming/auth/steam/session","/api/gaming/auth/steam/disconnect","/api/gaming/steam/library","/api/gaming/steam/achievements").contains(route))throw new Fault(404,"Unknown endpoint");
            boolean callback=route.endsWith("/callback");String requestOrigin=ex.getRequestHeaders().getFirst("Origin");
            if(!callback&&(!"0.12".equals(ex.getRequestHeaders().getFirst("X-FranchiseGuide-Client"))||"cross-site".equals(ex.getRequestHeaders().getFirst("Sec-Fetch-Site"))||requestOrigin!=null&&!requestOrigin.equals(origin)))throw new Fault(403,"Same-origin application required");
            if(unavailable)throw new Fault(503,"Local session file could not be opened. No automatic replacement.");
            if(route.endsWith("/begin")){
                if(!method.equals("POST")||ex.getRequestURI().getRawQuery()!=null)throw new Fault(405,"POST required");
                if(!bridge.configured())throw new Fault(503,"Configure FG_STEAM_API_KEY once on this local server");
                if(pending.size()>=64||!takeRate(starts,PENDING_MS,8))throw new Fault(429,"Too many login attempts");
                String state=random(),binding=random(),returnTo=origin+"/api/gaming/auth/steam/callback?state="+state;
                pending.put(state,new Pending(binding,returnTo,clock.getAsLong()+PENDING_MS));
                Map<String,String> q=new LinkedHashMap<>();q.put("openid.ns",NS);q.put("openid.mode","checkid_setup");q.put("openid.return_to",returnTo);q.put("openid.realm",origin+"/");q.put("openid.identity",NS+"/identifier_select");q.put("openid.claimed_id",NS+"/identifier_select");
                ex.getResponseHeaders().add("Set-Cookie",cookie(pendingName,binding,600));reply(ex,200,"{\"authorizeUrl\":\""+ENDPOINT+"?"+query(q)+"\",\"expiresAt\":"+(clock.getAsLong()+PENDING_MS)+"}");return;
            }
            if(callback){
                if(!method.equals("GET"))throw new Fault(405,"GET callback required");Map<String,String> q=parseQuery(ex.getRequestURI().getRawQuery(),16384);Pending p=pending.get(q.getOrDefault("state",""));
                if(p==null||p.expiresAt<=clock.getAsLong()||!same(jar.get(pendingName),p.binding)||!pending.remove(q.get("state"),p))throw new Fault(401,"Login missing, expired or from another browser");
                if("cancel".equals(q.get("openid.mode"))){redirect(ex,"/?steam_cancelled=1",cookie(pendingName,"",0));return;}
                Assertion a=validate(q,p.returnTo,clock.getAsLong());if(nonces.putIfAbsent(a.nonce,clock.getAsLong())!=null)throw new Fault(401,"Replayed Steam response");
                if(!verifier.verify(q))throw new Fault(401,"Steam signature rejected");
                if(cancelled.containsKey(p.binding)||p.expiresAt<=clock.getAsLong())throw new Fault(401,"Login cancelled during verification");
                String token=random();vault.put(token,new Session(a.steamid,clock.getAsLong()+SESSION_MS),jar.get(sessionName));
                redirect(ex,"/?steam_connected=1",cookie(sessionName,token,SESSION_MS/1000),cookie(pendingName,"",0));return;
            }
            if(route.endsWith("/disconnect")){
                if(!method.equals("POST")||ex.getRequestURI().getRawQuery()!=null)throw new Fault(405,"POST required");
                String binding=jar.get(pendingName);if(binding!=null)cancelled.put(binding,clock.getAsLong());pending.entrySet().removeIf(e->same(e.getValue().binding,binding));vault.remove(jar.get(sessionName));synchronized(cache){cache.clear();}
                ex.getResponseHeaders().add("Set-Cookie",cookie(sessionName,"",0));ex.getResponseHeaders().add("Set-Cookie",cookie(pendingName,"",0));reply(ex,200,"{\"authenticated\":false}");return;
            }
            if(!method.equals("GET"))throw new Fault(405,"GET required");Session session=vault.get(jar.get(sessionName));
            if(route.endsWith("/session")){if(ex.getRequestURI().getRawQuery()!=null)throw new Fault(400,"No parameters expected");reply(ex,200,session==null?"{\"authenticated\":false}":"{\"authenticated\":true,\"steamid\":\""+session.steamid+"\",\"expiresAt\":"+session.expiresAt+"}");return;}
            if(!Set.of("/api/gaming/steam/library","/api/gaming/steam/achievements").contains(route))throw new Fault(404,"Unknown endpoint");
            if(session==null)throw new Fault(401,"Reconnect your Steam account");if(!bridge.configured())throw new Fault(503,"Steam API key is not configured");
            Map<String,String> q=parseQuery(ex.getRequestURI().getRawQuery(),300);if(!Set.of("appid","language").containsAll(q.keySet()))throw new Fault(400,"Disallowed parameter");
            if(route.endsWith("/achievements")){
                q.put("steamid",session.steamid);String data=bridge.lookup(q);if(vault.get(jar.get(sessionName))==null)throw new Fault(401,"Session ended during read");reply(ex,200,data);return;
            }
            if(q.containsKey("appid"))throw new Fault(400,"No App ID expected");
            synchronized(cache){Cached c=cache.get(session.steamid);if(c!=null&&clock.getAsLong()-c.at<60000){reply(ex,200,c.data);return;}}
            if(!takeRate(reads,60000,6)||!slots.tryAcquire())throw new Fault(429,"Local library read limit");
            try{
                String data=reader.read(URI.create("https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key="+enc(key)+"&steamid="+session.steamid+"&include_appinfo=true&include_played_free_games=true"));String profile="null";
                try{profile=reader.read(URI.create("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key="+enc(key)+"&steamids="+session.steamid));}catch(Exception optional){}
                for(String json:List.of(data,profile)){if(json.getBytes(StandardCharsets.UTF_8).length>4*1024*1024)throw new Fault(502,"Malformed Steam response");Object value=RetroBridge.Json.parse(json);if(value!=null&&!(value instanceof Map<?,?>))throw new Fault(502,"Malformed Steam response");}
                if(vault.get(jar.get(sessionName))==null)throw new Fault(401,"Session ended during read");
                String body=AutoServices.redactResponse("{\"provider\":\"steam\",\"steamid\":\""+session.steamid+"\",\"fetchedAt\":"+clock.getAsLong()+",\"library\":"+data+",\"profile\":"+profile+"}",List.of(key));
                synchronized(cache){cache.put(session.steamid,new Cached(clock.getAsLong(),body));while(cache.size()>8)cache.remove(cache.keySet().iterator().next());}reply(ex,200,body);
            }finally{slots.release();}
        }catch(Fault e){if(e.status==429)ex.getResponseHeaders().set("Retry-After","60");reply(ex,e.status,"{\"error\":\""+e.getMessage()+"\"}");}
        catch(SteamBridge.Failure e){if(e.status==429)ex.getResponseHeaders().set("Retry-After","60");reply(ex,e.status,"{\"error\":\"Local Steam read limit or missing key\"}");}
        catch(IllegalArgumentException e){reply(ex,400,"{\"error\":\"Invalid parameters\"}");}
        catch(Exception e){reply(ex,502,"{\"error\":\"Steam verification unavailable or local session persistence failed. Existing library is unchanged.\"}");}
    }
    private static String cookie(String name,String value,long seconds){return name+"="+value+"; Path=/; HttpOnly; SameSite=Lax; Max-Age="+seconds;}
    private static void redirect(HttpExchange ex,String location,String... cookies) throws IOException {for(String c:cookies)ex.getResponseHeaders().add("Set-Cookie",c);ex.getResponseHeaders().set("Location",location);ex.getResponseHeaders().set("Cache-Control","no-store");ex.getResponseHeaders().set("Referrer-Policy","no-referrer");ex.sendResponseHeaders(303,-1);ex.close();}
    private void reply(HttpExchange ex,int status,String body) throws IOException {
        boolean html=status>=400&&ex.getRequestURI().getPath().endsWith("/callback")&&Objects.toString(ex.getRequestHeaders().getFirst("Accept"),"").contains("text/html");
        if(html){body="<!doctype html><html lang=\"fr\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Connexion Steam interrompue</title><style>body{font:18px system-ui;max-width:42rem;margin:10vh auto;padding:24px;line-height:1.6}a{display:inline-block;padding:14px;background:#6750a4;color:white;border-radius:12px}</style><h1>Connexion Steam non confirmée</h1><p>La réponse a été refusée, a expiré ou le service est indisponible. Aucun compte n’a été connecté par cette tentative et ta bibliothèque reste intacte.</p><p>Code HTTP : "+status+"</p><a href=\"/#games\">Revenir à FranchiseGuide et réessayer</a></html>";ex.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'");}
        if(!key.isEmpty())body=body.replace(key,"[redacted]");ex.getResponseHeaders().set("Content-Type",html?"text/html; charset=utf-8":"application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.getResponseHeaders().set("X-Frame-Options","DENY");ex.getResponseHeaders().set("Referrer-Policy","no-referrer");
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(status,bytes.length);try(OutputStream out=ex.getResponseBody()){out.write(bytes);}
    }
    static boolean verifySteam(Map<String,String> response) throws Exception {
        Map<String,String> body=new LinkedHashMap<>();response.forEach((k,v)->{if(k.startsWith("openid."))body.put(k,v);});body.put("openid.mode","check_authentication");
        HttpRequest request=HttpRequest.newBuilder(URI.create(ENDPOINT)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/x-www-form-urlencoded").header("Accept","text/plain").header("User-Agent","FranchiseGuide/0.12 (Steam OpenID)").POST(HttpRequest.BodyPublishers.ofString(query(body))).build();
        CompletableFuture<HttpResponse<byte[]>> f=HTTP.sendAsync(request,info->new LimitedBody());HttpResponse<byte[]> r;
        try{r=f.get(14,TimeUnit.SECONDS);}catch(Exception e){f.cancel(true);throw e;}if(r.statusCode()!=200)throw new Fault(502,"Steam validation unavailable");
        Map<String,String> lines=new HashMap<>();for(String line:new String(r.body(),StandardCharsets.UTF_8).trim().split("\\r?\\n")){int i=line.indexOf(':');if(i<=0||lines.putIfAbsent(line.substring(0,i),line.substring(i+1))!=null)throw new Fault(502,"Invalid verification response");}
        return "true".equals(lines.get("is_valid"))&&(!lines.containsKey("ns")||NS.equals(lines.get("ns")));
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();final ByteArrayOutputStream out=new ByteArrayOutputStream();Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> blocks){for(ByteBuffer b:blocks){if(out.size()+b.remaining()>16384){subscription.cancel();result.completeExceptionally(new IOException("OpenID response too large"));return;}byte[] p=new byte[b.remaining()];b.get(p);out.writeBytes(p);}subscription.request(1);}
        public void onError(Throwable e){result.completeExceptionally(e);}public void onComplete(){result.complete(out.toByteArray());}
    }
    /** Local persistent opaque sessions. Owner-only file when POSIX permissions are supported. */
    static final class Vault {
        private final Path file;private final LongSupplier clock;private Map<String,Session> sessions=new HashMap<>();
        Vault(Path file,LongSupplier clock) throws IOException {this.file=file;this.clock=clock;if(file!=null&&Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>1024*1024)throw new IOException("Unsafe session file");Properties p=new Properties();try(InputStream in=Files.newInputStream(file)){p.load(in);}if(!"1".equals(p.getProperty("version"))||p.size()>129)throw new IOException("Unknown session file");
            for(String k:p.stringPropertyNames()){if(k.equals("version"))continue;String[] v=p.getProperty(k).split(":");if(!k.matches("[a-f0-9]{64}")||v.length!=2||!v[0].matches("[0-9]{17}"))throw new IOException("Invalid session");long expiry;try{expiry=Long.parseLong(v[1]);}catch(NumberFormatException e){throw new IOException("Invalid expiry");}if(expiry>clock.getAsLong())sessions.put(k,new Session(v[0],expiry));}
        }}
        synchronized Session get(String token){if(token==null||!token.matches("[a-f0-9]{64}"))return null;Session s=sessions.get(hash(token));return s!=null&&s.expiresAt>clock.getAsLong()?s:null;}
        synchronized void put(String token,Session s,String old) throws IOException {Map<String,Session> next=new HashMap<>(sessions);next.entrySet().removeIf(e->e.getValue().expiresAt<=clock.getAsLong());if(old!=null)next.remove(hash(old));if(next.size()>=128)throw new IOException("Too many sessions");next.put(hash(token),s);persist(next);}
        synchronized void remove(String token) throws IOException {Map<String,Session> next=new HashMap<>(sessions);if(token!=null)next.remove(hash(token));persist(next);}
        private void persist(Map<String,Session> next) throws IOException {
            if(file!=null){Files.createDirectories(file.toAbsolutePath().getParent());Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"fgsteam-",".tmp");
                try{try{Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
                    Properties p=new Properties();p.setProperty("version","1");next.forEach((k,v)->p.setProperty(k,v.steamid+":"+v.expiresAt));
                    try(FileOutputStream out=new FileOutputStream(temp.toFile())){p.store(out,"FranchiseGuide local Steam sessions - no raw cookie tokens");out.getFD().sync();}
                    Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                }finally{Files.deleteIfExists(temp);}}
            sessions=next;
        }
    }
}
