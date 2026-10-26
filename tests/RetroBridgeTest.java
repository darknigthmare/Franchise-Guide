package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Real local bridge + real local HTTP upstream. All identities, credentials and data are fixtures. */
public final class RetroBridgeTest {
    private static final String KEY = "fixture_Retro_Key_1234567890";
    private static final String U1 = "00003EMFWR7XB8SDPEHB3K56ZQ";
    private static final String U2 = "00003EMFWR7XB8SDPEHB3K56ZR";
    private static final String ROOT = "/api/gaming/retro/";
    private static final String PROFILE_API = "/API/API_GetUserProfile.php";
    private static final String LIBRARY_API = "/API/API_GetUserCompletionProgress.php";
    private static final String GAME_API = "/API/API_GetGameInfoAndUserProgress.php";
    private static HttpClient client;
    private static int passed;

    private static void check(String description, boolean condition) {
        if (!condition) throw new AssertionError(description);
        passed++; System.out.println("PASS " + description);
    }
    private static void expectStatus(Fixture fixture, String path, int status, String description) throws Exception {
        HttpResponse<String> response = fixture.get(path);
        check(description, response.statusCode() == status);
        if (status >= 400 && response.body().contains(KEY)) throw new AssertionError("Key leaked in an error response");
    }
    private static HttpRequest request(String base, String path, String method, String version, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
        if (version != null) builder.header("X-FranchiseGuide-Client", version);
        headers.forEach(builder::header);
        return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
    }
    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }
    private static String id(int value) { return String.format(Locale.ROOT, "%026d", value); }
    private static String profile(String requested) {
        String user = requested.length() == 26 ? "Hunter" : requested;
        String ulid = requested.length() == 26 ? requested.toUpperCase(Locale.ROOT) : U1;
        return "{\"User\":\"" + user + "\",\"ULID\":\"" + ulid + "\",\"UserPic\":\"/UserPic/Hunter.png\",\"TotalPoints\":42}";
    }
    private static String library(Map<String, String> params) {
        List<String> rows = List.of(
            "{\"GameID\":1,\"Title\":\"Alpha\",\"MaxPossible\":2,\"NumAwarded\":1,\"NumAwardedHardcore\":1}",
            "{\"GameID\":2,\"Title\":\"Beta\",\"MaxPossible\":0,\"NumAwarded\":0,\"NumAwardedHardcore\":0}");
        int from = Math.min(2, Integer.parseInt(params.get("o")));
        int to = Math.min(2, from + Integer.parseInt(params.get("c")));
        return "{\"Count\":" + (to - from) + ",\"Total\":2,\"Results\":[" + String.join(",", rows.subList(from, to)) + "]}";
    }
    private static String game(String gameId) {
        if (gameId.equals("2")) return "{\"ID\":2,\"Title\":\"Beta\",\"NumAchievements\":0,\"Achievements\":[],\"NumAwardedToUser\":0,\"NumAwardedToUserHardcore\":0}";
        return "{\"ID\":" + gameId + ",\"Title\":\"Alpha\",\"NumAchievements\":2,\"Achievements\":{" +
            "\"9\":{\"ID\":9,\"Title\":\"First\",\"Description\":\"Finish the first area\",\"Points\":5,\"NumAwarded\":20,\"NumAwardedHardcore\":10,\"DateEarned\":\"2026-10-01 12:00:00\",\"DateEarnedHardcore\":\"2026-10-01 12:00:00\"}," +
            "\"10\":{\"ID\":10,\"Title\":\"Second\",\"Description\":\"Finish the second area\",\"Points\":10}},\"NumAwardedToUser\":1,\"NumAwardedToUserHardcore\":1}";
    }
    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }
    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }
    @FunctionalInterface private interface Reply { void handle(HttpExchange exchange) throws Exception; }

    private static final class Fixture implements AutoCloseable {
        final HttpServer upstream, bridge;
        final ExecutorService upstreamThreads = Executors.newCachedThreadPool();
        final ExecutorService bridgeThreads = Executors.newCachedThreadPool();
        final AtomicLong clock = new AtomicLong(1_800_000_000_000L);
        final ConcurrentLinkedQueue<URI> calls = new ConcurrentLinkedQueue<>();
        final AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
        final AtomicReference<Reply> responder = new AtomicReference<>();
        final String base;

        Fixture() throws Exception { this(KEY); }
        Fixture(String key) throws Exception {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            responder.set(this::normal);
            upstream.createContext("/", exchange -> {
                int concurrent = active.incrementAndGet(); peak.accumulateAndGet(concurrent, Math::max);
                try { responder.get().handle(exchange); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); exchange.close(); }
                catch (Exception failure) { exchange.close(); }
                finally { active.decrementAndGet(); }
            });
            upstream.setExecutor(upstreamThreads); upstream.start();
            String upstreamBase = "http://127.0.0.1:" + upstream.getAddress().getPort();
            RetroBridge provider = new RetroBridge(key, original -> {
                calls.add(original);
                if (!"https".equals(original.getScheme()) || !"retroachievements.org".equals(original.getHost())
                    || !List.of(PROFILE_API, LIBRARY_API, GAME_API).contains(original.getPath()))
                    throw new AssertionError("The bridge tried an unknown upstream route");
                return RetroBridge.readHttp(URI.create(upstreamBase + original.getRawPath() + "?" + original.getRawQuery()));
            }, clock::get);
            bridge = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            bridge.createContext("/api/gaming/", provider); bridge.setExecutor(bridgeThreads); bridge.start();
            base = "http://127.0.0.1:" + bridge.getAddress().getPort();
        }
        void normal(HttpExchange exchange) throws Exception {
            if (!exchange.getRequestMethod().equals("GET")) throw new AssertionError("Upstream mutation attempted");
            Map<String, String> params = query(exchange.getRequestURI());
            if (!KEY.equals(params.get("y"))) throw new AssertionError("Server key was not sent in y");
            String route = exchange.getRequestURI().getPath();
            if (route.equals(PROFILE_API)) send(exchange, 200, profile(params.get("u")));
            else if (route.equals(LIBRARY_API)) send(exchange, 200, library(params));
            else if (route.equals(GAME_API)) {
                if (!params.getOrDefault("a", "").equals("1")) throw new AssertionError("Award metadata flag missing");
                send(exchange, 200, game(params.get("g")));
            } else send(exchange, 404, "{}");
        }
        HttpResponse<String> get(String path) throws Exception {
            return client.send(request(base, path, "GET", "0.13", Map.of()), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> req(String path, String method, String version, Map<String, String> headers) throws Exception {
            return client.send(request(base, path, method, version, headers), HttpResponse.BodyHandlers.ofString());
        }
        CompletableFuture<HttpResponse<String>> async(String path) {
            return client.sendAsync(request(base, path, "GET", "0.13", Map.of()), HttpResponse.BodyHandlers.ofString());
        }
        long count(String path) { return calls.stream().filter(uri -> uri.getPath().equals(path)).count(); }
        void data(String route, String body) {
            clock.addAndGet(60_001);
            responder.set(exchange -> {
                if (exchange.getRequestURI().getPath().equals(route)) send(exchange, 200, body);
                else normal(exchange);
            });
        }
        @Override public void close() {
            bridge.stop(0); upstream.stop(0); bridgeThreads.shutdownNow(); upstreamThreads.shutdownNow();
        }
    }

    private static void requestSecurityAndHappyPath() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var status = fixture.get(ROOT + "status");
            check("Status identifies a configured RetroAchievements provider", status.statusCode() == 200 && status.body().equals("{\"provider\":\"retro\",\"configured\":true}"));
            check("Status and response headers do not expose the server credential", !status.body().contains(KEY) && fixture.calls.isEmpty());
            check("Status HEAD is bodyless and does not call upstream", fixture.req(ROOT + "status", "HEAD", "0.13", Map.of()).body().isEmpty() && fixture.calls.isEmpty());
            for (String version : List.of("0.12", "0.13", "0.14")) check("Client version " + version + " is accepted", fixture.req(ROOT + "status", "GET", version, Map.of()).statusCode() == 200);
            check("Obsolete client header is rejected", fixture.req(ROOT + "status", "GET", "0.11", Map.of()).statusCode() == 403);
            check("Missing client header is rejected", fixture.req(ROOT + "status", "GET", null, Map.of()).statusCode() == 403);
            check("Duplicate client headers are rejected", fixture.req(ROOT + "status", "GET", "0.13", Map.of("X-FranchiseGuide-Client", "0.14")).statusCode() == 403);
            check("Foreign Origin is rejected", fixture.req(ROOT + "status", "GET", "0.13", Map.of("Origin", "https://example.invalid")).statusCode() == 403);
            check("Cross-site fetch is rejected", fixture.req(ROOT + "status", "GET", "0.13", Map.of("Sec-Fetch-Site", "cross-site")).statusCode() == 403);
            check("Same-origin application request is accepted", fixture.req(ROOT + "status", "GET", "0.13", Map.of("Origin", fixture.base, "Sec-Fetch-Site", "same-origin")).statusCode() == 200);
            check("DNS rebinding Host is rejected", fixture.req(ROOT + "status", "GET", "0.13", Map.of("Host", "evil.invalid:" + fixture.bridge.getAddress().getPort())).statusCode() == 403);
            String local = "localhost:" + fixture.bridge.getAddress().getPort();
            check("Exact localhost Host with matching Origin is accepted", fixture.req(ROOT + "status", "GET", "0.13", Map.of("Host", local, "Origin", "http://" + local)).statusCode() == 200);
            check("Mismatching loopback Origin is rejected", fixture.req(ROOT + "status", "GET", "0.13", Map.of("Host", local, "Origin", fixture.base)).statusCode() == 403);
            for (String method : List.of("POST", "PUT", "PATCH", "DELETE", "OPTIONS")) check("Read-only endpoint rejects " + method, fixture.req(ROOT + "profile?user=Hunter", method, "0.13", Map.of()).statusCode() == 405);
            check("Data HEAD cannot perform a profile lookup", fixture.req(ROOT + "profile?user=Hunter", "HEAD", "0.13", Map.of()).statusCode() == 405 && fixture.calls.isEmpty());
            expectStatus(fixture, ROOT + "status?user=Hunter", 400, "Status rejects all query parameters");
            expectStatus(fixture, ROOT + "unknown?url=https://example.invalid", 404, "Unknown routes never become an arbitrary proxy");
            expectStatus(fixture, ROOT + "%70rofile?user=Hunter", 404, "Encoded route aliases are rejected");

            var profile = fixture.get(ROOT + "profile?user=Hunter");
            check("Profile lookup resolves an immutable ULID", profile.statusCode() == 200 && profile.body().contains("\"user\":\"" + U1 + "\"") && profile.body().contains("\"profile\":{\"User\":\"Hunter\""));
            check("A public profile is never described as authenticated", !profile.body().contains("authenticated") && !profile.body().contains("session"));
            check("Provider responses are no-store and not a CORS proxy", profile.headers().firstValue("Cache-Control").orElse("").equals("no-store") && profile.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            check("JSON responses include nosniff and no-referrer", profile.headers().firstValue("X-Content-Type-Options").orElse("").equals("nosniff") && profile.headers().firstValue("Referrer-Policy").orElse("").equals("no-referrer"));
            check("The personal key travels only to the fixed upstream endpoint", fixture.calls.size() == 1 && query(fixture.calls.peek()).get("y").equals(KEY) && !profile.body().contains(KEY));
            var page = fixture.get(ROOT + "library?user=" + U1);
            check("Verified profile is reused when reading the first completion page", page.statusCode() == 200 && fixture.count(PROFILE_API) == 1 && fixture.count(LIBRARY_API) == 1);
            check("Default completion window is exactly count 100 offset 0", fixture.calls.stream().anyMatch(uri -> uri.getPath().equals(LIBRARY_API) && query(uri).get("c").equals("100") && query(uri).get("o").equals("0")));
            check("The documented 500-entry maximum window is accepted", fixture.get(ROOT + "library?user=" + U1 + "&count=500").statusCode() == 200
                && fixture.calls.stream().anyMatch(uri -> uri.getPath().equals(LIBRARY_API) && query(uri).get("c").equals("500")));
            check("The final partial page is accepted when it matches Total", fixture.get(ROOT + "library?user=" + U1 + "&offset=1&count=100").body().contains("\"Count\":1"));
            check("A window past the known end can be empty", fixture.get(ROOT + "library?user=" + U1 + "&offset=2&count=1").body().contains("\"Count\":0"));
            var achievements = fixture.get(ROOT + "achievements?user=" + U1 + "&gameId=1");
            check("Game response includes the complete achievement definitions and account identity", achievements.statusCode() == 200 && achievements.body().contains("\"gameId\":\"1\"") && achievements.body().contains("\"NumAchievements\":2"));
            check("Award metadata is requested explicitly upstream", fixture.calls.stream().anyMatch(uri -> uri.getPath().equals(GAME_API) && query(uri).get("a").equals("1")));
            check("A genuine zero-achievement game accepts PHP's empty array", fixture.get(ROOT + "achievements?user=" + U1 + "&gameId=2").statusCode() == 200);
            check("Maximum supported game identifier is accepted", fixture.get(ROOT + "achievements?user=" + U1 + "&gameId=2147483647").statusCode() == 200);
            int beforeCase = fixture.calls.size();
            var lowercase = fixture.get(ROOT + "profile?user=" + U1.toLowerCase(Locale.ROOT));
            check("ULIDs normalize to uppercase and reuse the same profile cache", lowercase.body().contains("\"user\":\"" + U1 + "\"") && fixture.calls.size() == beforeCase);
            check("Different users do not share achievement snapshots", fixture.get(ROOT + "achievements?user=" + U2 + "&gameId=1").body().contains("\"user\":\"" + U2 + "\""));

            int beforeBad = fixture.calls.size();
            List<String> invalid = List.of(
                "profile", "profile?user=Hunter&user=Other", "profile?user=Hunter&%75ser=Other", "profile?user=Hunter&key=secret",
                "profile?user=Hunter&url=https://example.invalid", "profile?user=Hunter&", "profile?user=..", "profile?user=Hunter%00",
                "profile?user=Hunter%FF", "profile?user=" + "a".repeat(301), "library?user=Hunter", "library?user=" + U1 + "&count=0",
                "library?user=" + U1 + "&count=501", "library?user=" + U1 + "&count=01", "library?user=" + U1 + "&offset=-1",
                "library?user=" + U1 + "&offset=100001", "library?user=8" + U1.substring(1), "library?user=" + U1.replace("Z", "I"),
                "achievements?user=" + U1, "achievements?user=" + U1 + "&gameId=0", "achievements?user=" + U1 + "&gameId=01",
                "achievements?user=" + U1 + "&gameId=-1", "achievements?user=" + U1 + "&gameId=2147483648", "achievements?user=" + U1 + "&gameId=1e3",
                "achievements?user=" + U1 + "&gameId=1&count=100");
            for (int i = 0; i < invalid.size(); i++) expectStatus(fixture, ROOT + invalid.get(i), 400, "Invalid or unexpected query is refused (#" + (i + 1) + ")");
            check("Rejected input never consumes an upstream call", fixture.calls.size() == beforeBad);
        }
        for (String key : List.of("", "short", "a".repeat(15), "a".repeat(129), "has spaces and punctuation!")) try (Fixture fixture = new Fixture(key)) {
            check("Invalid server credential is reported as unconfigured", fixture.get(ROOT + "status").body().contains("\"configured\":false"));
            expectStatus(fixture, ROOT + "profile?user=Hunter", 503, "An unconfigured server refuses data retrieval");
            check("An unconfigured server never calls the API", fixture.calls.isEmpty());
        }
    }

    private static void validationAndSecrets() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String escapedKey = KEY.chars().mapToObj(c -> String.format(Locale.ROOT, "\\u%04x", c)).reduce("", String::concat);
            fixture.data(PROFILE_API, "{\"User\":\"Hunter\",\"ULID\":\"" + U1 + "\",\"Motto\":\"" + escapedKey + "\",\"metadata\":{\"\\u006b\\u0065\\u0079\":\"private value\",\"Authorization\":\"Bearer private\"},\"" + escapedKey + "\":\"hidden property\"}");
            var redacted = fixture.get(ROOT + "profile?user=Hunter");
            check("Unicode-escaped server credentials are decoded then redacted", redacted.statusCode() == 200 && redacted.body().contains("\"Motto\":\"[redacted]\"") && !redacted.body().contains(KEY) && !redacted.body().contains(escapedKey));
            check("Escaped sensitive property names are removed canonically", redacted.body().contains("\"metadata\":{}") && !redacted.body().contains("private value") && !redacted.body().contains("hidden property"));

            String valid = profile("Hunter");
            List<String> invalidJson = List.of(
                "[]", "{}", "null", "false", "<html>unavailable</html>", valid + "{}", valid + " trailing",
                valid.replace("\"User\":\"Hunter\"", "\"User\":\"Hunter\",\"\\u0055ser\":\"Other\""),
                valid.replace("\"TotalPoints\":42", "\"TotalPoints\":NaN"), valid.replace("\"TotalPoints\":42", "\"TotalPoints\":01"),
                valid.replace("\"TotalPoints\":42", "\"TotalPoints\":1e999999999"), valid.replace("\"TotalPoints\":42", "\"TotalPoints\":truefalse"),
                valid.replace("\"TotalPoints\":42", "\"TotalPoints\":42,"), valid.replace("\"TotalPoints\":42", "\"Motto\":\"\\uD800\""),
                valid.replace("\"TotalPoints\":42", "\"Motto\":\"\\uDE00\""), valid.replace("\"TotalPoints\":42", "\"Motto\":\"\\uＦＦＦＦ\""),
                valid.replace("\"TotalPoints\":42", "\"Motto\":\"unterminated"), valid.replace("\"TotalPoints\":42", "\"Motto\":\"bad\nline\""),
                valid.replace("\"TotalPoints\":42", "\"Padding\":" + "[".repeat(34) + "0" + "]".repeat(34)),
                valid.replace("\"TotalPoints\":42", "\"Padding\":[" + "0,".repeat(200_001) + "0]"),
                valid.replace("\"TotalPoints\":42", "\"Padding\":\"" + "x".repeat(262_145) + "\""),
                valid.replace("\"TotalPoints\":42", "\"" + "x".repeat(257) + "\":true"));
            for (int i = 0; i < invalidJson.size(); i++) {
                fixture.data(PROFILE_API, invalidJson.get(i));
                expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "Malformed, excessive, or ambiguous JSON fails closed (#" + (i + 1) + ")");
            }
            fixture.data(PROFILE_API, valid.replace("\"TotalPoints\":42", "\"Motto\":\"Progress 🚀 \\uD83D\\uDE00\""));
            check("Valid UTF-8 and paired Unicode escapes are preserved", fixture.get(ROOT + "profile?user=Hunter").body().contains("Progress 🚀 😀"));
            fixture.data(PROFILE_API, profile("Other"));
            expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "A different username cannot substitute for the requested profile");
            fixture.data(PROFILE_API, profile(U2));
            expectStatus(fixture, ROOT + "profile?user=" + U1, 502, "A different ULID is rejected");
            fixture.data(PROFILE_API, "{\"User\":\"Hunter\",\"TotalPoints\":1}");
            expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "A profile without an immutable ULID is rejected");
            fixture.data(PROFILE_API, "{\"User\":\"Hunter\",\"ULID\":\"" + U1 + "\",\"Success\":false}");
            expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "An explicit upstream failure cannot masquerade as a valid profile");
            fixture.data(PROFILE_API, "[]");
            long beforeLibrary = fixture.count(LIBRARY_API);
            expectStatus(fixture, ROOT + "library?user=" + U1, 502, "A missing or inaccessible profile never produces an empty library snapshot");
            check("Unverified profile prevents the completion API request", fixture.count(LIBRARY_API) == beforeLibrary);
            long attempts = fixture.count(PROFILE_API);
            expectStatus(fixture, ROOT + "library?user=" + U1, 502, "A failed identity check can be retried");
            check("Failed responses are never cached", fixture.count(PROFILE_API) == attempts + 1);

            fixture.data(LIBRARY_API, "{\"Count\":0,\"Total\":0,\"Results\":[]}");
            check("A verified user may have a genuine empty completion history", fixture.get(ROOT + "library?user=" + U1).statusCode() == 200);
            String page = library(Map.of("o", "0", "c", "100"));
            List<String> invalidPages = List.of(
                "[]", page.replace("\"Count\":2", "\"Count\":1"), page.replace("\"Total\":2", "\"Total\":3"),
                page.replace("\"Total\":2", "\"Total\":0"), page.replace("\"GameID\":2", "\"GameID\":1"),
                page.replace("\"GameID\":2", "\"GameID\":2147483648"), page.replace("\"Title\":\"Alpha\"", "\"Title\":\"\""),
                page.replace("\"Count\":2", "\"Count\":true"), page.replace("\"Count\":2", "\"Count\":\"02\""),
                page.replace("\"Total\":2", "\"Total\":2.5"), page.replace("\"MaxPossible\":2", "\"MaxPossible\":0"),
                page.replace("\"NumAwardedHardcore\":1", "\"NumAwardedHardcore\":2"));
            for (int i = 0; i < invalidPages.size(); i++) {
                fixture.data(LIBRARY_API, invalidPages.get(i));
                expectStatus(fixture, ROOT + "library?user=" + U1, 502, "Incomplete or inconsistent completion page is rejected (#" + (i + 1) + ")");
            }
            fixture.data(LIBRARY_API, page.replace("\"Count\":2", "\"Count\":\"2\"").replace("\"Total\":2", "\"Total\":\"2\"").replace("\"GameID\":1", "\"GameID\":\"1\""));
            check("Canonical decimal strings from the PHP API remain compatible", fixture.get(ROOT + "library?user=" + U1).statusCode() == 200);

            String definition = game("1");
            List<String> invalidGames = List.of(
                "[]", definition.replace("\"ID\":1,", "\"ID\":3,"), definition.replace("\"Title\":\"Alpha\"", "\"Title\":null"),
                definition.replace("\"NumAchievements\":2", "\"NumAchievements\":3"), definition.replace("\"ID\":9", "\"ID\":11"),
                definition.replace("\"9\":{", "\"09\":{"), definition.replace("\"ID\":10", "\"ID\":2147483648"),
                definition.replace("\"Title\":\"First\"", "\"Title\":\"\""), definition.replace("\"Points\":5", "\"Points\":-1"),
                definition.replace("\"Description\":\"Finish the first area\"", "\"Description\":[]"),
                definition.replace("\"NumAwardedToUser\":1", "\"NumAwardedToUser\":3"),
                definition.replace("\"NumAwardedToUserHardcore\":1", "\"NumAwardedToUserHardcore\":2"),
                definition.replace("\"NumAwardedHardcore\":10", "\"NumAwardedHardcore\":30"),
                "{\"ID\":1,\"Title\":\"Alpha\",\"NumAchievements\":1,\"Achievements\":[]}",
                "{\"ID\":1,\"Title\":\"Alpha\",\"NumAchievements\":0,\"Achievements\":[{}]}",
                definition.replace("\"ID\":9,", "\"ID\":9,\"\\u0049D\":9,"));
            for (int i = 0; i < invalidGames.size(); i++) {
                fixture.data(GAME_API, invalidGames.get(i));
                expectStatus(fixture, ROOT + "achievements?user=" + U1 + "&gameId=1", 502, "Incomplete or inconsistent achievement set is rejected (#" + (i + 1) + ")");
            }
            fixture.data(GAME_API, definition.replace("\"ID\":1,", "\"ID\":\"1\",").replace("\"NumAchievements\":2", "\"NumAchievements\":\"2\""));
            check("Achievement counts and identifiers accept canonical decimal strings", fixture.get(ROOT + "achievements?user=" + U1 + "&gameId=1").statusCode() == 200);
        }
    }

    private static void cacheAndBudgets() throws Exception {
        try (Fixture fixture = new Fixture()) {
            String path = ROOT + "profile?user=" + U1;
            String first = fixture.get(path).body();
            fixture.clock.addAndGet(55_000);
            check("Cache hits preserve the original fetchedAt and do not re-read the profile", fixture.get(path).body().equals(first) && fixture.calls.size() == 1);
            fixture.clock.addAndGet(5_000);
            check("A cache hit does not slide the 60-second identity TTL", !fixture.get(path).body().equals(first) && fixture.calls.size() == 2);
        }
        try (Fixture fixture = new Fixture()) {
            var failures = new ArrayList<Integer>();
            for (int i = 1; i <= 35; i++) if (fixture.get(ROOT + "profile?user=" + id(i)).statusCode() != 200) failures.add(i);
            check("Recent profile reads succeed within the upstream budget", failures.isEmpty() && fixture.calls.size() == 35);
            int before = fixture.calls.size();
            fixture.get(ROOT + "profile?user=" + id(1));
            check("The 32-entry cache evicts the oldest profile", fixture.calls.size() == before + 1);
            check("A recent cached profile stays available after exhausting the upstream budget", fixture.get(ROOT + "profile?user=" + id(35)).statusCode() == 200 && fixture.calls.size() == 36);
            var throttled = fixture.get(ROOT + "profile?user=" + id(99));
            check("The 37th upstream call is limited without contacting the provider", throttled.statusCode() == 429 && fixture.calls.size() == 36);
            check("Local rate limiting reports a Retry-After value", throttled.headers().firstValue("Retry-After").orElse("").equals("60"));
            fixture.clock.addAndGet(60_000);
            check("The upstream budget resets at the minute boundary", fixture.get(ROOT + "profile?user=" + id(99)).statusCode() == 200 && fixture.calls.size() == 37);
        }
        try (Fixture fixture = new Fixture()) {
            for (int i = 1; i <= 35; i++) if (fixture.get(ROOT + "achievements?user=" + U1 + "&gameId=" + (1000 + i)).statusCode() != 200) throw new AssertionError("Unexpected early game throttle");
            check("Profile verification consumes its own upstream budget call", fixture.calls.size() == 36 && fixture.count(PROFILE_API) == 1 && fixture.count(GAME_API) == 35);
            expectStatus(fixture, ROOT + "achievements?user=" + U1 + "&gameId=1999", 429, "Game retrieval cannot bypass the shared upstream budget");
        }
        try (Fixture fixture = new Fixture()) {
            fixture.responder.set(exchange -> send(exchange, 500, "{\"error\":\"unavailable\"}"));
            for (int i = 1; i <= 36; i++) if (fixture.get(ROOT + "profile?user=" + id(i)).statusCode() != 502) throw new AssertionError("Unexpected failure status");
            expectStatus(fixture, ROOT + "profile?user=" + id(100), 429, "Failed upstream requests also consume the rate budget");
            check("Failure retries cannot amplify past 36 upstream calls", fixture.calls.size() == 36);
        }
        try (Fixture fixture = new Fixture()) {
            String padding = String.join(",", Collections.nCopies(10, "\"" + "x".repeat(200_000) + "\""));
            fixture.responder.set(exchange -> send(exchange, 200, profile(query(exchange.getRequestURI()).get("u")).replace("\"TotalPoints\":42", "\"Padding\":[" + padding + "]")));
            for (int i = 1; i <= 5; i++) if (fixture.get(ROOT + "profile?user=" + id(i)).statusCode() != 200) throw new AssertionError("Large valid profile rejected");
            fixture.get(ROOT + "profile?user=" + id(1));
            check("The byte budget evicts old responses before the cache exceeds 8 MiB", fixture.calls.size() == 6);
            fixture.get(ROOT + "profile?user=" + id(5));
            check("A recent large response remains cached after byte-budget eviction", fixture.calls.size() == 6);
        }
    }

    private static void concurrencyAndFailures() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.get(ROOT + "profile?user=" + U1);
            CountDownLatch started = new CountDownLatch(2), release = new CountDownLatch(1);
            fixture.responder.set(exchange -> {
                if (exchange.getRequestURI().getPath().equals(GAME_API)) { started.countDown(); release.await(8, TimeUnit.SECONDS); }
                fixture.normal(exchange);
            });
            String one = ROOT + "achievements?user=" + U1 + "&gameId=777";
            var a = fixture.async(one);
            var b = fixture.async(one);
            var c = fixture.async(ROOT + "achievements?user=" + U1 + "&gameId=778");
            try {
                check("Two independent upstream reads can run concurrently", started.await(5, TimeUnit.SECONDS));
                expectStatus(fixture, ROOT + "achievements?user=" + U1 + "&gameId=779", 429, "A third upstream read is bounded instead of queued indefinitely");
                check("Identical in-flight requests share one upstream read", fixture.count(GAME_API) == 2);
            } finally { release.countDown(); }
            var first = a.get(10, TimeUnit.SECONDS); var duplicate = b.get(10, TimeUnit.SECONDS);
            check("Coalesced callers receive the same confirmed snapshot", first.statusCode() == 200 && duplicate.statusCode() == 200 && first.body().equals(duplicate.body()));
            check("Both independent callers finish successfully", c.get(10, TimeUnit.SECONDS).statusCode() == 200);
            check("Observed upstream concurrency never exceeds two", fixture.peak.get() <= 2);
            expectStatus(fixture, ROOT + "achievements?user=" + U1 + "&gameId=779", 200, "A rejected concurrent request can be retried after slots are released");
        }
        try (Fixture fixture = new Fixture()) {
            fixture.get(ROOT + "profile?user=" + U1);
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), limited = new CountDownLatch(4);
            fixture.responder.set(exchange -> {
                if (exchange.getRequestURI().getPath().equals(GAME_API)) { started.countDown(); release.await(10, TimeUnit.SECONDS); }
                fixture.normal(exchange);
            });
            String path = ROOT + "achievements?user=" + U1 + "&gameId=880";
            List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
            futures.add(fixture.async(path));
            check("The coalescence stress request reached upstream", started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) {
                var future = fixture.async(path);
                future.thenAccept(response -> { if (response.statusCode() == 429) limited.countDown(); }); futures.add(future);
            }
            try {
                check("Waiting duplicate callers are bounded under concurrent pressure", limited.await(6, TimeUnit.SECONDS));
                check("Many duplicate callers still cause exactly one upstream read", fixture.count(GAME_API) == 1);
            } finally { release.countDown(); }
            int ok = 0, denied = 0;
            for (var future : futures) {
                int status = future.get(10, TimeUnit.SECONDS).statusCode(); if (status == 200) ok++; else if (status == 429) denied++;
            }
            check("Bounded coalescence resolves all allowed callers without losing the snapshot", ok == 17 && denied == 4);
        }
        try (Fixture fixture = new Fixture()) {
            AtomicInteger redirected = new AtomicInteger();
            fixture.responder.set(exchange -> {
                if (exchange.getRequestURI().getPath().equals("/secret")) { redirected.incrementAndGet(); send(exchange, 200, profile("Hunter")); }
                else { exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + fixture.upstream.getAddress().getPort() + "/secret"); send(exchange, 302, "{}"); }
            });
            expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "Upstream redirects fail closed");
            check("The HTTP client never follows an upstream redirect", redirected.get() == 0);
            fixture.clock.addAndGet(60_001);
            fixture.responder.set(exchange -> send(exchange, 500, "{\"error\":\"" + KEY + "\"}"));
            var failed = fixture.get(ROOT + "profile?user=Hunter");
            check("HTTP errors cannot echo upstream credentials to the client", failed.statusCode() == 502 && !failed.body().contains(KEY));
            fixture.clock.addAndGet(60_001);
            fixture.responder.set(exchange -> send(exchange, 429, "{\"error\":\"rate limited\"}"));
            var rateLimited = fixture.get(ROOT + "profile?user=Hunter");
            check("Upstream rate limits remain retriable 429 responses", rateLimited.statusCode() == 429 && rateLimited.headers().firstValue("Retry-After").orElse("").equals("60"));
            fixture.clock.addAndGet(60_001);
            fixture.responder.set(exchange -> send(exchange, 200, new byte[] { '{', '"', 'x', '"', ':', '"', (byte) 0xc3, (byte) 0x28, '"', '}' }));
            expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "Malformed UTF-8 is rejected before JSON parsing");
            fixture.clock.addAndGet(60_001);
            fixture.responder.set(exchange -> send(exchange, 200, "x".repeat(4 * 1024 * 1024 + 1)));
            expectStatus(fixture, ROOT + "profile?user=Hunter", 502, "The real HTTP body reader enforces the 4 MiB limit");
        }
        for (String uri : List.of("http://retroachievements.org" + PROFILE_API, "https://example.invalid" + PROFILE_API,
                "https://retroachievements.org/API/Delete.php", "https://retroachievements.org:444" + PROFILE_API,
                "https://user@retroachievements.org" + PROFILE_API, "https://retroachievements.org" + PROFILE_API + "#fragment")) {
            boolean rejected = false;
            try { RetroBridge.readRetro(URI.create(uri)); } catch (IOException expected) { rejected = true; }
            check("Production reader refuses an alternate host, route, or authority", rejected);
        }
        try (Fixture fixture = new Fixture()) {
            CountDownLatch release = new CountDownLatch(1);
            fixture.responder.set(exchange -> {
                exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
                release.await(20, TimeUnit.SECONDS); exchange.close();
            });
            long began = System.nanoTime();
            try {
                var response = fixture.get(ROOT + "profile?user=Hunter");
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
                check("A stalled HTTP body reaches the bounded 14-second deadline", response.statusCode() == 502 && elapsed >= 12_000 && elapsed < 18_000);
            } finally { release.countDown(); }
            fixture.responder.set(fixture::normal);
            check("A timed-out read releases its slot and permits recovery", fixture.get(ROOT + "profile?user=Hunter").statusCode() == 200);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        requestSecurityAndHappyPath();
        validationAndSecrets();
        cacheAndBudgets();
        concurrencyAndFailures();
        System.out.println("Java RetroAchievements bridge: " + passed + " assertions passed with real local HTTP upstream fixtures; no real account or API credential used.");
    }
}
