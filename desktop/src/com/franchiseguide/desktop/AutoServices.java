package com.franchiseguide.desktop;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Local gateway for keyless catalogues and publisher-owned credentials. Not a public SaaS proxy. */
public final class AutoServices implements HttpHandler {
    static final String PREFIX="/api/services/";static final int LIMIT=4*1024*1024;
    static final Map<String,String> HOSTS=Map.of("tvmaze","https://api.tvmaze.com","itunes","https://itunes.apple.com","freetogame","https://www.freetogame.com","tmdb","https://api.themoviedb.org","rawg","https://api.rawg.io","trakt","https://api.trakt.tv","trakt-auth","https://auth.trakt.tv");
    record Rule(String regex,Set<String> query){}
    static Rule rule(String regex,String query){return new Rule(regex,query.isBlank()?Set.of():Set.of(query.split(",")));}
    static final Map<String,Rule> RULES=Map.of(
        "tvmaze",rule("/(search/shows|shows|shows/[1-9]\\d*(?:/(episodes|seasons|cast))?|episodes/[1-9]\\d*(?:/(guestcast|guestcrew))?|people/[1-9]\\d*(?:/(castcredits|crewcredits))?)","q,page,specials,embed"),
        "itunes",rule("/(search|lookup)","term,country,entity,media,limit,lang,explicit,id"),
        "freetogame",rule("/api/(games|game)","id,sort-by"),
        "tmdb",rule("/3/(search/(multi|tv|movie|person)|discover/(tv|movie)|trending/(tv|movie|all)/(day|week)|(?:tv|movie)/[1-9]\\d*(?:/(external_ids|credits|aggregate_credits|videos|watch/providers|content_ratings|release_dates|season/\\d+(?:/(watch/providers|episode/\\d+))?))?|person/[1-9]\\d*(?:/(combined_credits|external_ids))?|collection/[1-9]\\d*|find/(tt\\d+|\\d+)|watch/providers/regions|configuration(?:/(countries|languages))?)","api_key,language,query,page,include_adult,sort_by,region,watch_region,with_original_language,first_air_date.gte,first_air_date.lte,primary_release_date.gte,primary_release_date.lte,append_to_response,external_source"),
        "rawg",rule("/api/games(?:/[a-zA-Z0-9_-]+)?","key,search,page,page_size,ordering,dates"),
        "trakt",rule("/(users/settings|sync/(last_activities|(?:watched|collection|watchlist|ratings|history)(?:/(movies|shows|episodes|remove))?)|search/tmdb/\\d+|(?:shows|movies)/[a-zA-Z0-9_-]+/comments(?:/(likes|newest|oldest|highest|lowest|replies))?|comments(?:/\\d+)?|checkin)","type,extended,page,limit,start_at,end_at"),
        "trakt-auth",rule("/oauth/(device/code|device/token|token)","")
    );
    record Plan(String provider,URI uri,String method,Map<String,String> headers,String body){}
    record Reply(int status,String body,String retryAfter,String pages){}
    
    @FunctionalInterface interface Reader {Reply read(Plan p)throws Exception;}
    static final class Fault extends IOException{final int code;Fault(int code,String message){super(message);this.code=code;}}
    private final Map<String,String> cfg;private final Reader reader;private final LongSupplier clock;private final Semaphore slots=new Semaphore(4);
    private final Map<String,ArrayDeque<Long>> rates=new HashMap<>();private final Map<String,Long> cooldown=new HashMap<>();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();
    public AutoServices(){this(System.getenv(),AutoServices::read,System::currentTimeMillis);}
    AutoServices(Map<String,String> env,Reader reader,LongSupplier clock){this.cfg=config(env);this.reader=reader;this.clock=clock;}
    static String valid(Map<String,String> env,String key,String pattern){String v=env.getOrDefault(key,"").trim();return v.matches(pattern)?v:"";}
    static Map<String,String> config(Map<String,String> env){return Map.of("tmdb",valid(env,"FG_TMDB_API_KEY","(?i)[a-f0-9]{32}"),"tmdbToken",valid(env,"FG_TMDB_READ_TOKEN","[a-zA-Z0-9._~-]{30,2048}"),"rawg",valid(env,"FG_RAWG_API_KEY","[a-zA-Z0-9_-]{16,128}"),"traktId",valid(env,"FG_TRAKT_CLIENT_ID","[a-zA-Z0-9_-]{16,256}"),"traktSecret",valid(env,"FG_TRAKT_CLIENT_SECRET","[a-zA-Z0-9_-]{16,256}"),"steam",valid(env,"FG_STEAM_API_KEY","(?i)[a-f0-9]{32}"),"retro",valid(env,"FG_RETRO_API_KEY","[a-zA-Z0-9_-]{16,256}"));}
    static Map<String,Object> status(Map<String,String> c){return Map.of("contract",1,"app","FranchiseGuide Auto Services","keyless",List.of("tvmaze","itunes","freetogame","openlibrary"),"tmdb",!c.get("tmdb").isBlank()||!c.get("tmdbToken").isBlank(),"rawg",!c.get("rawg").isBlank(),"trakt",!c.get("traktId").isBlank()&&!c.get("traktSecret").isBlank(),"steam",!c.get("steam").isBlank(),"retro",!c.get("retro").isBlank());}
    private static Fault bad(int code,String message){return new Fault(code,message);}
    private static Long retrySeconds(String value,long now){
        if(value==null||value.isBlank())return null;
        try{long seconds=value.matches("\\d{1,9}")?Long.parseLong(value):Math.floorDiv(ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now+999,1000);return Math.max(1,Math.min(86400,seconds));}catch(Exception invalid){return null;}
    }
    static String redactResponse(String body,Collection<String> configuredSecrets)throws IOException{
        List<String> secrets=configuredSecrets.stream().filter(s->s!=null&&!s.isEmpty()).toList();
        return json(redactValue(RetroBridge.Json.parse(body),secrets));
    }
    private static Object redactValue(Object value,List<String> secrets){
        if(value instanceof String text){for(String secret:secrets)text=text.replace(secret,"[redacted]");return text;}
        if(value instanceof Map<?,?> source){Map<String,Object> safe=new LinkedHashMap<>();for(var entry:source.entrySet()){String name=(String)entry.getKey();if(secrets.stream().noneMatch(name::contains))safe.put(name,redactValue(entry.getValue(),secrets));}return safe;}
        if(value instanceof List<?> items)return items.stream().map(item->redactValue(item,secrets)).toList();
        return value instanceof Number&&secrets.stream().anyMatch(value.toString()::contains)?"[redacted]":value;
    }
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    static Plan plan(URI request,String method,String authorization,String body,Map<String,String> c)throws Exception{
        String raw=request.getRawPath();if(raw==null||!raw.startsWith(PREFIX)||raw.contains("%")||raw.contains(".."))throw bad(400,"Chemin non autorisé.");
        String suffix=raw.substring(PREFIX.length());int slash=suffix.indexOf('/');if(slash<1)throw bad(404,"Service inconnu.");String provider=suffix.substring(0,slash),path=suffix.substring(slash);Rule rule=RULES.get(provider);if(rule==null)throw bad(404,"Service inconnu.");if(!path.matches(rule.regex))throw bad(400,"Chemin non autorisé.");
        Map<String,String> params=new LinkedHashMap<>();String query=request.getRawQuery();if(query!=null){if(query.length()>12000)throw bad(400,"Paramètres trop longs.");for(String pair:query.split("&")){String[] kv=pair.split("=",2);String k=URLDecoder.decode(kv[0],StandardCharsets.UTF_8),v=kv.length>1?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"";if(!rule.query.contains(k)||params.containsKey(k)||v.length()>2048||v.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw bad(400,"Paramètre non autorisé.");params.put(k,v);}}
        boolean oauth=provider.equals("trakt-auth");if(oauth?!method.equals("POST"):!provider.equals("trakt")&&!method.equals("GET"))throw bad(405,"Méthode non autorisée.");
        if(provider.equals("trakt")&&!method.equals("GET")&&!((method.equals("POST")&&path.matches("/(sync/(history|collection|watchlist|ratings)(/remove)?|comments|checkin)"))||(Set.of("PUT","DELETE").contains(method)&&path.matches("/comments/\\d+"))||(method.equals("DELETE")&&path.equals("/checkin"))))throw bad(405,"Écriture non autorisée.");
        Map<String,String> headers=new LinkedHashMap<>();headers.put("Accept","application/json");headers.put("User-Agent","FranchiseGuide/0.27 (personal catalogue app)");String data=method.equals("GET")?null:body;
        if(provider.equals("itunes")){params.put("entity","movie");if(path.equals("/search"))params.put("media","movie");String limit=params.get("limit");if(limit!=null&&(!limit.matches("\\d{1,3}")||Integer.parseInt(limit)>200||Integer.parseInt(limit)<1))throw bad(400,"Limite Apple invalide.");}
        if(provider.equals("tvmaze")&&params.containsKey("specials")&&!params.get("specials").equals("1"))throw bad(400,"Paramètre specials invalide.");
        if(provider.equals("tvmaze")&&params.containsKey("embed")&&(!params.get("embed").equals("show")||!path.matches("/(episodes/[1-9]\\d*|people/[1-9]\\d*/(castcredits|crewcredits))")))throw bad(400,"Inclusion TVmaze non autorisée.");
        if(provider.equals("tmdb")){if(!(boolean)status(c).get("tmdb"))throw bad(503,"Accès TMDb non activé par l’éditeur.");params.remove("api_key");if(!c.get("tmdbToken").isBlank())headers.put("Authorization","Bearer "+c.get("tmdbToken"));else params.put("api_key",c.get("tmdb"));}
        if(provider.equals("rawg")){if(c.get("rawg").isBlank())throw bad(503,"Accès RAWG non activé par l’éditeur.");params.put("key",c.get("rawg"));}
        if(oauth||provider.equals("trakt")){
            if(!(boolean)status(c).get("trakt"))throw bad(503,"Connexion Trakt non activée par l’éditeur.");headers.put("trakt-api-key",c.get("traktId"));headers.put("trakt-api-version","2");headers.put("Content-Type","application/json");
            if(!oauth&&authorization!=null){if(!authorization.matches("Bearer [a-zA-Z0-9._~-]{1,4096}"))throw bad(400,"Jeton utilisateur invalide.");headers.put("Authorization",authorization);}
            if(!oauth&&(!method.equals("GET")||path.startsWith("/sync/")||path.equals("/users/settings"))&&authorization==null)throw bad(401,"Connecte ton compte Trakt.");
            if(oauth){Object parsed=RetroBridge.Json.parse(body);if(!(parsed instanceof Map<?,?> source))throw bad(400,"Corps OAuth invalide.");Map<String,String> x=new LinkedHashMap<>();Set<String> allowed=Set.of("client_id","client_secret","code","refresh_token","redirect_uri","grant_type");for(var e:source.entrySet()){if(!(e.getKey() instanceof String k)||!allowed.contains(k)||!(e.getValue() instanceof String v)||v.length()>4096||v.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw bad(400,"Corps OAuth invalide.");x.put(k,v);}
                if(path.equals("/oauth/token")&&(!"refresh_token".equals(x.get("grant_type"))||x.getOrDefault("refresh_token","").isBlank()))throw bad(400,"Renouvellement attendu.");
                if(path.equals("/oauth/device/token")&&x.getOrDefault("code","").isBlank())throw bad(400,"Code appareil manquant.");x.put("client_id",c.get("traktId"));if(!path.equals("/oauth/device/code"))x.put("client_secret",c.get("traktSecret"));else x.remove("client_secret");data=json(x);
            }
        }
        String q=String.join("&",params.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).toList());return new Plan(provider,URI.create(HOSTS.get(provider)+path+(q.isEmpty()?"":"?"+q)),method,headers,data);
    }
    static Reply read(Plan p)throws Exception{
        var b=HttpRequest.newBuilder(p.uri).timeout(Duration.ofSeconds(25));p.headers.forEach(b::header);b.method(p.method,p.body==null||p.body.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(p.body));
        var f=HTTP.sendAsync(b.build(),info->new BoundedBody(LIMIT));HttpResponse<byte[]> r;try{r=f.get(28,TimeUnit.SECONDS);}catch(Exception e){f.cancel(true);throw e;}
        String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(r.body())).toString();
        if(r.statusCode()>=200&&r.statusCode()<300&&r.statusCode()!=204)RetroBridge.Json.parse(body);
        return new Reply(r.statusCode(),body,r.headers().firstValue("Retry-After").orElse(null),r.headers().firstValue("X-Pagination-Page-Count").orElse(null));
    }
    @Override public void handle(HttpExchange ex)throws IOException{
        String host=ex.getRequestHeaders().getFirst("Host"),origin=ex.getRequestHeaders().getFirst("Origin"),site=ex.getRequestHeaders().getFirst("Sec-Fetch-Site");int port=ex.getLocalAddress().getPort();
        if(LocalRequest.ambiguousHeaders(ex)||!Set.of("127.0.0.1:"+port,"localhost:"+port).contains(host==null?"":host)||!"1".equals(ex.getRequestHeaders().getFirst("X-FranchiseGuide-Services"))||"cross-site".equals(site)||origin!=null&&!origin.equals("http://"+host)){send(ex,403,"{\"error\":\"Application locale requise.\"}");return;}
        if(ex.getRequestURI().getPath().equals(PREFIX+"config")){if(!ex.getRequestMethod().equals("GET")||ex.getRequestURI().getRawQuery()!=null){send(ex,405,"{}");return;}send(ex,200,json(status(cfg)));return;}
        boolean acquired=false;
        try {
            byte[] bytes=ex.getRequestBody().readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw bad(413,"Requête trop grande.");String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            Plan p=plan(ex.getRequestURI(),ex.getRequestMethod(),ex.getRequestHeaders().getFirst("Authorization"),body,cfg);long now=clock.getAsLong();
            synchronized(this){long hold=cooldown.getOrDefault(p.provider,0L);if(hold>now){ex.getResponseHeaders().set("Retry-After",Long.toString((hold-now+999)/1000));throw bad(429,"Délai fournisseur en cours.");}ArrayDeque<Long> times=rates.computeIfAbsent(p.provider,k->new ArrayDeque<>());while(!times.isEmpty()&&now-times.peekFirst()>=60000)times.removeFirst();if(times.size()>=(p.provider.equals("itunes")?18:90)||!slots.tryAcquire()){ex.getResponseHeaders().set("Retry-After","60");throw bad(429,"Lecture limitée.");}times.addLast(now);acquired=true;}
            Reply r=reader.read(p);long receivedAt=clock.getAsLong();Long wait=retrySeconds(r.retryAfter,receivedAt);if(wait==null&&r.status==429)wait=60L;
            if(wait!=null){ex.getResponseHeaders().set("Retry-After",Long.toString(wait));if(r.status==429)synchronized(this){cooldown.merge(p.provider,receivedAt+wait*1000,Math::max);}}
            if(r.pages!=null&&r.pages.matches("\\d{1,9}"))ex.getResponseHeaders().set("X-Pagination-Page-Count",r.pages);
            if(r.status<200||r.status>=300){send(ex,r.status>=400?r.status:502,"{\"error\":\"Service fournisseur temporairement indisponible.\"}");return;}
            if(r.body==null||r.body.getBytes(StandardCharsets.UTF_8).length>LIMIT)throw bad(502,"Réponse trop volumineuse.");
            String safe=r.status==204?"":redactResponse(r.body,cfg.values());

            send(ex,r.status,safe);
        }catch(Fault e){send(ex,e.code,json(Map.of("error",e.getMessage())));}catch(Exception e){send(ex,502,"{\"error\":\"Lecture indisponible. Aucune donnée locale modifiée.\"}");}finally{if(acquired)slots.release();}
    }
    static String json(Object value){if(value==null)return "null";if(value instanceof String s){StringBuilder text=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'||c=='\"')text.append('\\').append(c);else if(c<0x20)text.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else text.append(c);}return text.append('"').toString();}if(value instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey())+":"+json(e.getValue())).toList())+"}";if(value instanceof Collection<?> l)return "["+String.join(",",l.stream().map(AutoServices::json).toList())+"]";return value.toString();}
    private static void send(HttpExchange ex,int code,String body)throws IOException{ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.getResponseHeaders().set("Referrer-Policy","no-referrer");byte[] b=body.getBytes(StandardCharsets.UTF_8);if(code==204||ex.getRequestMethod().equals("HEAD")){ex.sendResponseHeaders(code,-1);ex.close();return;}ex.sendResponseHeaders(code,b.length);try(OutputStream out=ex.getResponseBody()){out.write(b);}}
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {private final int max;private final ByteArrayOutputStream out=new ByteArrayOutputStream();private final CompletableFuture<byte[]> body=new CompletableFuture<>();private Flow.Subscription subscription;BoundedBody(int max){this.max=max;}public CompletionStage<byte[]> getBody(){return body;}public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}public void onNext(List<ByteBuffer> blocks){for(ByteBuffer b:blocks){if((long)out.size()+b.remaining()>max){subscription.cancel();body.completeExceptionally(new IOException("Response too large"));return;}byte[] part=new byte[b.remaining()];b.get(part);out.writeBytes(part);}subscription.request(1);}public void onError(Throwable t){body.completeExceptionally(t);}public void onComplete(){body.complete(out.toByteArray());}}
}
