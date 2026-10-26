package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Public-profile, read-only RetroAchievements bridge for the local PC application.
 * The server key never belongs to the browser. Selecting a profile is not sign-in.
 * Only the three fixed, documented API routes below can be called upstream.
 */
public final class RetroBridge implements HttpHandler {
    private static final String ROOT = "/api/gaming/retro";
    private static final String PROFILE = ROOT + "/profile";
    private static final String LIBRARY = ROOT + "/library";
    private static final String ACHIEVEMENTS = ROOT + "/achievements";
    private static final String STATUS = ROOT + "/status";
    private static final String PROFILE_API = "/API/API_GetUserProfile.php";
    private static final String LIBRARY_API = "/API/API_GetUserCompletionProgress.php";
    private static final String ACHIEVEMENTS_API = "/API/API_GetGameInfoAndUserProgress.php";
    private static final Set<String> API_PATHS = Set.of(PROFILE_API, LIBRARY_API, ACHIEVEMENTS_API);
    private static final Set<String> CLIENT_VERSIONS = Set.of("0.12", "0.13", "0.14");
    private static final Pattern ULID = Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}", Pattern.CASE_INSENSITIVE);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{2,32}");
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private static final int MAX_CACHE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 32;
    private static final int MAX_FLIGHTS = 8;
    private static final int MAX_WAITERS = 16;
    private static final long TTL_MS = 60_000L;
    private static final int UPSTREAM_PER_MINUTE = 36;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    private final String key;
    private final Reader reader;
    private final LongSupplier clock;
    private final Semaphore slots = new Semaphore(2);
    private final Object lock = new Object();
    private final ArrayDeque<Long> requests = new ArrayDeque<>();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(32, 0.75f, true);
    private final Map<String, CompletableFuture<Result>> flights = new LinkedHashMap<>();
    private long cacheBytes;
    private int waiters;

    @FunctionalInterface interface Reader { String read(URI uri) throws Exception; }
    @FunctionalInterface private interface Loader { Result load() throws Exception; }
    private record Result(String json, String user) {}
    private record Cached(long at, int bytes, Result value) {}
    private static final class Failure extends IOException {
        final int status;
        Failure(int status) { super("RetroAchievements request unavailable"); this.status = status; }
    }

    public RetroBridge() { this(System.getenv("FG_RETRO_API_KEY"), RetroBridge::readRetro, System::currentTimeMillis); }
    RetroBridge(String key, Reader reader) { this(key, reader, System::currentTimeMillis); }
    RetroBridge(String key, Reader reader, LongSupplier clock) {
        this.key = key == null ? "" : key.trim();
        this.reader = Objects.requireNonNull(reader);
        this.clock = Objects.requireNonNull(clock);
    }

    public static boolean accepts(String path) {
        return path != null && (path.equals(ROOT) || path.startsWith(ROOT + "/"));
    }
    boolean configured() { return key.matches("[A-Za-z0-9_-]{16,128}"); }

    @Override public void handle(HttpExchange ex) throws IOException {
        String host = oneHeader(ex, "Host");
        int port = ex.getLocalAddress().getPort();
        if (host == null || !(host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port)
                || host.equals("[::1]:" + port)) || !ex.getRemoteAddress().getAddress().isLoopbackAddress()) {
            reply(ex, 403, "{\"error\":\"Invalid host\"}"); return;
        }
        String origin = oneHeader(ex, "Origin"), site = oneHeader(ex, "Sec-Fetch-Site");
        if ((ex.getRequestHeaders().containsKey("Origin") && !Objects.equals(origin, "http://" + host))
                || (ex.getRequestHeaders().containsKey("Sec-Fetch-Site") && site == null)
                || "cross-site".equalsIgnoreCase(site)
                || !CLIENT_VERSIONS.contains(Objects.toString(oneHeader(ex, "X-FranchiseGuide-Client"), ""))) {
            reply(ex, 403, "{\"error\":\"Same-origin application request required\"}"); return;
        }
        String path = ex.getRequestURI().getPath();
        if (!Set.of(STATUS, PROFILE, LIBRARY, ACHIEVEMENTS).contains(path)
                || !path.equals(ex.getRequestURI().getRawPath())) {
            reply(ex, 404, "{\"error\":\"Unknown endpoint\"}"); return;
        }
        String method = ex.getRequestMethod();
        if (!"GET".equals(method) && !(STATUS.equals(path) && "HEAD".equals(method))) {
            ex.getResponseHeaders().set("Allow", STATUS.equals(path) ? "GET, HEAD" : "GET");
            reply(ex, 405, "{\"error\":\"Read only\"}"); return;
        }
        final Map<String, String> params;
        try { params = parseQuery(path, ex.getRequestURI().getRawQuery()); }
        catch (IllegalArgumentException bad) {
            reply(ex, 400, "{\"error\":\"Invalid parameters\"}"); return;
        }
        if (STATUS.equals(path)) {
            reply(ex, 200, "{\"provider\":\"retro\",\"configured\":" + configured() + "}"); return;
        }
        if (!configured()) {
            reply(ex, 503, "{\"error\":\"FG_RETRO_API_KEY is not configured on this local server\"}"); return;
        }
        try {
            Result value;
            if (PROFILE.equals(path)) value = profile(params.get("user"));
            else if (LIBRARY.equals(path)) value = library(params.get("user"), params.get("offset"), params.get("count"));
            else value = achievements(params.get("user"), params.get("gameId"));
            reply(ex, 200, value.json());
        } catch (Failure problem) {
            if (problem.status == 429) ex.getResponseHeaders().set("Retry-After", "60");
            reply(ex, problem.status, "{\"error\":\"" + (problem.status == 429
                ? "Local rate limit. Retry later." : "RetroAchievements unavailable. Existing local data is unchanged.") + "\"}");
        } catch (Exception problem) {
            reply(ex, 502, "{\"error\":\"RetroAchievements unavailable. Existing local data is unchanged.\"}");
        }
    }

    private static String oneHeader(HttpExchange ex, String name) {
        List<String> values = ex.getRequestHeaders().get(name);
        return values != null && values.size() == 1 ? values.get(0) : null;
    }

    static Map<String, String> parseQuery(String path, String raw) {
        if (raw != null && raw.length() > 300) throw new IllegalArgumentException();
        Set<String> allowed = switch (path) {
            case STATUS -> Set.of();
            case PROFILE -> Set.of("user");
            case LIBRARY -> Set.of("user", "offset", "count");
            case ACHIEVEMENTS -> Set.of("user", "gameId");
            default -> throw new IllegalArgumentException();
        };
        Map<String, String> params = new LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) for (String pair : raw.split("&", -1)) {
            String[] pieces = pair.split("=", 2);
            if (pieces.length != 2) throw new IllegalArgumentException();
            String name = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pieces[1], StandardCharsets.UTF_8);
            if (!allowed.contains(name) || params.putIfAbsent(name, value) != null) throw new IllegalArgumentException();
        }
        if (STATUS.equals(path)) return params;
        String user = params.getOrDefault("user", "");
        if (ULID.matcher(user).matches()) params.put("user", user.toUpperCase(Locale.ROOT));
        else if (!PROFILE.equals(path) || !USERNAME.matcher(user).matches()) throw new IllegalArgumentException();
        if (LIBRARY.equals(path)) {
            params.putIfAbsent("offset", "0"); params.putIfAbsent("count", "100");
            decimalParameter(params.get("offset"), 0, 100_000);
            decimalParameter(params.get("count"), 1, 500);
        } else if (ACHIEVEMENTS.equals(path)) decimalParameter(params.getOrDefault("gameId", ""), 1, Integer.MAX_VALUE);
        return params;
    }

    private static long decimalParameter(String value, long min, long max) {
        if (!value.matches("0|[1-9][0-9]{0,9}")) throw new IllegalArgumentException();
        long number = Long.parseLong(value);
        if (number < min || number > max) throw new IllegalArgumentException();
        return number;
    }

    private Result profile(String user) throws Exception {
        String identity = ULID.matcher(user).matches() ? user.toUpperCase(Locale.ROOT) : user.toLowerCase(Locale.ROOT);
        return cached("profile:" + identity, () -> {
            Map<String, Object> data = upstream(PROFILE_API, "u=" + enc(user));
            String username = requiredText(data.get("User"), 32);
            String ulid = requiredText(data.get("ULID"), 26).toUpperCase(Locale.ROOT);
            if (!USERNAME.matcher(username).matches() || !ULID.matcher(ulid).matches()
                    || (ULID.matcher(user).matches() ? !ulid.equalsIgnoreCase(user) : !username.equalsIgnoreCase(user)))
                throw new Failure(502);
            Result result = envelope("profile", ulid, null, data);
            // A verified username lookup also proves its immutable ULID for the following read.
            putCache("profile:" + ulid, result);
            return result;
        });
    }

    private Result library(String user, String offset, String count) throws Exception {
        return cached("library:" + user + ":" + offset + ":" + count, () -> {
            String ulid = profile(user).user();
            Map<String, Object> data = upstream(LIBRARY_API, "u=" + enc(ulid) + "&c=" + count + "&o=" + offset);
            long returned = integer(data.get("Count"), 0, 500);
            long total = integer(data.get("Total"), 0, Integer.MAX_VALUE);
            if (!(data.get("Results") instanceof List<?> results) || returned != results.size()
                    || returned != Math.min(Long.parseLong(count), Math.max(0, total - Long.parseLong(offset))))
                throw new Failure(502);
            Set<Long> ids = new HashSet<>();
            for (Object item : results) {
                Map<String, Object> game = object(item);
                long id = integer(game.get("GameID"), 1, Integer.MAX_VALUE);
                if (!ids.add(id)) throw new Failure(502);
                requiredText(game.get("Title"), 1_000);
                optionalCounters(game, "MaxPossible", "NumAwarded", "NumAwardedHardcore", Integer.MAX_VALUE);
            }
            return envelope("library", ulid, null, data);
        });
    }

    private Result achievements(String user, String gameId) throws Exception {
        return cached("achievements:" + user + ":" + gameId, () -> {
            String ulid = profile(user).user();
            Map<String, Object> data = upstream(ACHIEVEMENTS_API, "g=" + gameId + "&u=" + enc(ulid) + "&a=1");
            if (integer(data.get("ID"), 1, Integer.MAX_VALUE) != Long.parseLong(gameId)) throw new Failure(502);
            requiredText(data.get("Title"), 1_000);
            long total = integer(data.get("NumAchievements"), 0, 20_000);
            Object definitions = data.get("Achievements");
            if (definitions instanceof List<?> empty && empty.isEmpty() && total == 0) definitions = Map.of();
            Map<String, Object> achievements = object(definitions);
            if (achievements.size() != total) throw new Failure(502);
            for (Map.Entry<String, Object> item : achievements.entrySet()) {
                long id;
                try { id = decimalParameter(item.getKey(), 1, Integer.MAX_VALUE); }
                catch (IllegalArgumentException invalid) { throw new Failure(502); }
                Map<String, Object> achievement = object(item.getValue());
                if (integer(achievement.get("ID"), 1, Integer.MAX_VALUE) != id) throw new Failure(502);
                requiredText(achievement.get("Title"), 1_000);
                if (achievement.containsKey("Points")) integer(achievement.get("Points"), 0, Integer.MAX_VALUE);
                if (achievement.containsKey("Description") && !(achievement.get("Description") instanceof String)) throw new Failure(502);
                optionalCounters(achievement, null, "NumAwarded", "NumAwardedHardcore", Integer.MAX_VALUE);
            }
            optionalCounters(data, "NumAchievements", "NumAwardedToUser", "NumAwardedToUserHardcore", total);
            return envelope("game", ulid, gameId, data);
        });
    }

    private static void optionalCounters(Map<String, Object> data, String maximum, String softcore, String hardcore, long limit) throws Failure {
        long max = maximum != null && data.containsKey(maximum) ? integer(data.get(maximum), 0, limit) : limit;
        Long soft = data.containsKey(softcore) ? integer(data.get(softcore), 0, max) : null;
        Long hard = data.containsKey(hardcore) ? integer(data.get(hardcore), 0, max) : null;
        if (soft != null && hard != null && hard > soft) throw new Failure(502);
    }

    private static String requiredText(Object value, int max) throws Failure {
        if (!(value instanceof String text) || text.isBlank() || text.length() > max) throw new Failure(502);
        return text;
    }
    private static long integer(Object value, long min, long max) throws Failure {
        try {
            BigDecimal number;
            if (value instanceof BigDecimal decimal) number = decimal;
            else if (value instanceof String text && text.matches("0|[1-9][0-9]{0,9}")) number = new BigDecimal(text);
            else throw new Failure(502);
            if (number.compareTo(BigDecimal.valueOf(min)) < 0 || number.compareTo(BigDecimal.valueOf(max)) > 0) throw new Failure(502);
            return number.longValueExact();
        } catch (ArithmeticException invalid) { throw new Failure(502); }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) throws Failure {
        if (!(value instanceof Map<?, ?>)) throw new Failure(502);
        return (Map<String, Object>) value;
    }

    private Map<String, Object> upstream(String path, String query) throws Exception {
        if (!slots.tryAcquire()) throw new Failure(429);
        try {
            synchronized (lock) {
                long now = clock.getAsLong();
                while (!requests.isEmpty() && requests.peekFirst() <= now - TTL_MS) requests.removeFirst();
                if (requests.size() >= UPSTREAM_PER_MINUTE) throw new Failure(429);
                requests.addLast(now);
            }
            // No caller-controlled host, route, credentials, or redirect target.
            URI uri = URI.create("https://retroachievements.org" + path + "?" + query + "&y=" + enc(key));
            Map<String, Object> parsed = object(Json.parse(reader.read(uri)));
            if (Boolean.FALSE.equals(parsed.get("Success")) || parsed.containsKey("Error") || parsed.containsKey("error")) throw new Failure(502);
            return parsed;
        } finally { slots.release(); }
    }

    private Result envelope(String field, String user, String gameId, Map<String, Object> data) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("provider", "retro"); response.put("user", user);
        if (gameId != null) response.put("gameId", gameId);
        response.put(field, data); response.put("fetchedAt", clock.getAsLong());
        String json = Json.write(response, key);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BODY + 4_096 || json.contains(key)) throw new Failure(502);
        return new Result(json, user);
    }

    private Result cached(String name, Loader loader) throws Exception {
        CompletableFuture<Result> flight;
        boolean leader;
        synchronized (lock) {
            Cached previous = cache.get(name);
            long now = clock.getAsLong();
            if (previous != null) {
                long age = now - previous.at();
                if (age >= 0 && age < TTL_MS) return previous.value();
                cache.remove(name); cacheBytes -= previous.bytes();
            }
            flight = flights.get(name); leader = flight == null;
            if (leader) {
                if (flights.size() >= MAX_FLIGHTS) throw new Failure(429);
                flight = new CompletableFuture<>(); flights.put(name, flight);
            } else {
                if (waiters >= MAX_WAITERS) throw new Failure(429);
                waiters++;
            }
        }
        if (!leader) try {
            // A data fetch may first perform a bounded profile verification (2 x 14 s).
            return flight.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof Failure failure) throw failure;
            throw new Failure(502);
        } finally { synchronized (lock) { waiters--; } }
        try {
            Result value = loader.load(); putCache(name, value); flight.complete(value); return value;
        } catch (Exception failed) {
            flight.completeExceptionally(failed); throw failed;
        } finally { synchronized (lock) { flights.remove(name, flight); } }
    }

    private void putCache(String name, Result result) {
        int bytes = result.json().getBytes(StandardCharsets.UTF_8).length;
        synchronized (lock) {
            Cached previous = cache.remove(name);
            if (previous != null) cacheBytes -= previous.bytes();
            long now = clock.getAsLong();
            var iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Cached value = iterator.next().getValue(); long age = now - value.at();
                if (age < 0 || age >= TTL_MS) { cacheBytes -= value.bytes(); iterator.remove(); }
            }
            cache.put(name, new Cached(now, bytes, result)); cacheBytes += bytes;
            while (cache.size() > MAX_CACHE_ENTRIES || cacheBytes > MAX_CACHE_BYTES) {
                String first = cache.keySet().iterator().next(); cacheBytes -= cache.remove(first).bytes();
            }
        }
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    static String readRetro(URI uri) throws Exception {
        if (!"https".equals(uri.getScheme()) || !"retroachievements.org".equals(uri.getHost())
                || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !API_PATHS.contains(uri.getPath())) throw new IOException("Disallowed upstream route");
        return readHttp(uri);
    }

    /** Package-private network seam lets tests use a real loopback upstream without a real API key. */
    static String readHttp(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(14))
            .header("Accept", "application/json")
            .header("User-Agent", "FranchiseGuide/0.16 (personal read-only tracker)").GET().build();
        CompletableFuture<HttpResponse<byte[]>> future = HTTP.sendAsync(request, ignored -> new BoundedBody(MAX_BODY));
        HttpResponse<byte[]> response;
        try { response = future.get(14, TimeUnit.SECONDS); }
        catch (Exception problem) { future.cancel(true); throw problem; }
        if (response.statusCode() == 429) throw new Failure(429);
        if (response.statusCode() != 200) throw new IOException("Upstream HTTP error");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBody(int max) { this.max = max; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> blocks) {
            if (body.isDone()) return;
            for (ByteBuffer block : blocks) {
                if ((long) out.size() + block.remaining() > max) {
                    subscription.cancel(); body.completeExceptionally(new IOException("Response too large")); return;
                }
                byte[] part = new byte[block.remaining()]; block.get(part); out.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
        @Override public void onComplete() { body.complete(out.toByteArray()); }
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Cross-Origin-Resource-Policy", "same-origin");
        if ("HEAD".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(status, -1); ex.close(); return; }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }

    /** Strict JSON parser: duplicate decoded keys, invalid UTF-16, and unbounded shapes fail closed. */
    static final class Json {
        private static final int MAX_VALUES = 200_000;
        private static final int MAX_STRING = 262_144;
        private static final Set<String> SECRETS = Set.of("y", "key", "apikey", "fgretroapikey", "authorization", "token", "accesstoken", "refreshtoken", "password");
        private final String text;
        private int position, values;
        private Json(String text) { this.text = text; }

        static Object parse(String text) throws IOException {
            if (text == null || text.length() > MAX_BODY || text.getBytes(StandardCharsets.UTF_8).length > MAX_BODY) throw invalid();
            Json parser = new Json(text);
            Object value = parser.value(0); parser.space();
            if (parser.position != text.length()) throw invalid();
            return value;
        }
        private Object value(int depth) throws IOException {
            space();
            if (depth > 32 || ++values > MAX_VALUES || position >= text.length()) throw invalid();
            char token = text.charAt(position);
            if (token == '{') {
                position++; space(); Map<String, Object> map = new LinkedHashMap<>();
                if (take('}')) return map;
                do {
                    space(); String name = string(256); space(); expect(':');
                    if (map.containsKey(name)) throw invalid();
                    map.put(name, value(depth + 1)); space();
                    if (take('}')) return map;
                    expect(',');
                } while (true);
            }
            if (token == '[') {
                position++; space(); List<Object> list = new ArrayList<>();
                if (take(']')) return list;
                do { list.add(value(depth + 1)); space(); if (take(']')) return list; expect(','); } while (true);
            }
            if (token == '"') return string(MAX_STRING);
            if (token == 't' && literal("true")) return Boolean.TRUE;
            if (token == 'f' && literal("false")) return Boolean.FALSE;
            if (token == 'n' && literal("null")) return null;
            int start = position;
            take('-');
            if (!take('0')) {
                if (position >= text.length() || text.charAt(position) < '1' || text.charAt(position) > '9') throw invalid();
                digits();
            }
            if (take('.')) { int fraction = position; digits(); if (fraction == position) throw invalid(); }
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                int exponent = position; digits(); if (exponent == position) throw invalid();
            }
            if (position - start == 0 || position - start > 64) throw invalid();
            try {
                BigDecimal number = new BigDecimal(text.substring(start, position));
                if (number.scale() < -308 || number.scale() > 308 || number.precision() > 64) throw invalid();
                return number;
            } catch (NumberFormatException bad) { throw invalid(); }
        }
        private String string(int max) throws IOException {
            expect('"'); StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char ch = text.charAt(position++);
                if (ch == '"') {
                    for (int i = 0; i < value.length(); i++) {
                        char current = value.charAt(i);
                        if (Character.isHighSurrogate(current)) {
                            if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
                        } else if (Character.isLowSurrogate(current)) throw invalid();
                    }
                    return value.toString();
                }
                if (ch < 0x20) throw invalid();
                if (ch == '\\') {
                    if (position == text.length()) throw invalid();
                    ch = text.charAt(position++);
                    switch (ch) {
                        case '"', '\\', '/' -> value.append(ch);
                        case 'b' -> value.append('\b'); case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n'); case 'r' -> value.append('\r'); case 't' -> value.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) throw invalid();
                            int code = 0;
                            for (int i = 0; i < 4; i++) {
                                char digit = text.charAt(position++);
                                int hex = digit >= '0' && digit <= '9' ? digit - '0'
                                    : digit >= 'a' && digit <= 'f' ? digit - 'a' + 10
                                    : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10 : -1;
                                if (hex < 0) throw invalid(); code = code * 16 + hex;
                            }
                            value.append((char) code);
                        }
                        default -> throw invalid();
                    }
                } else value.append(ch);
                if (value.length() > max) throw invalid();
            }
            throw invalid();
        }
        private void digits() { while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') position++; }
        private boolean literal(String expected) { if (!text.startsWith(expected, position)) return false; position += expected.length(); return true; }
        private boolean take(char expected) { if (position == text.length() || text.charAt(position) != expected) return false; position++; return true; }
        private void expect(char expected) throws IOException { if (!take(expected)) throw invalid(); }
        private void space() { while (position < text.length() && " \r\n\t".indexOf(text.charAt(position)) >= 0) position++; }
        private static IOException invalid() { return new IOException("Invalid upstream JSON"); }

        static String write(Object value, String secret) {
            StringBuilder json = new StringBuilder(); writeValue(value, json, secret); return json.toString();
        }
        private static void writeValue(Object value, StringBuilder json, String secret) {
            if (value == null) { json.append("null"); return; }
            if (value instanceof String text) { quote(text.replace(secret, "[redacted]"), json); return; }
            if (value instanceof Map<?, ?> map) {
                json.append('{'); boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String name = (String) entry.getKey();
                    String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                    if (SECRETS.contains(normalized) || name.contains(secret)) continue;
                    if (!first) json.append(','); first = false;
                    quote(name, json); json.append(':'); writeValue(entry.getValue(), json, secret);
                }
                json.append('}'); return;
            }
            if (value instanceof List<?> list) {
                json.append('['); boolean first = true;
                for (Object item : list) { if (!first) json.append(','); first = false; writeValue(item, json, secret); }
                json.append(']'); return;
            }
            String literal = value.toString();
            if (literal.contains(secret)) quote("[redacted]", json); else json.append(literal);
        }
        private static void quote(String text, StringBuilder json) {
            json.append('"');
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                switch (ch) {
                    case '"' -> json.append("\\\""); case '\\' -> json.append("\\\\");
                    case '\b' -> json.append("\\b"); case '\f' -> json.append("\\f");
                    case '\n' -> json.append("\\n"); case '\r' -> json.append("\\r"); case '\t' -> json.append("\\t");
                    default -> {
                        if (ch < 0x20 || ch == 0x2028 || ch == 0x2029) json.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                        else json.append(ch);
                    }
                }
            }
            json.append('"');
        }
    }
}
