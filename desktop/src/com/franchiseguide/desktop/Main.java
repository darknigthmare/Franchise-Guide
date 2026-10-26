package com.franchiseguide.desktop;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Main {
    private static HttpServer server;
    private static URI appUri;
    private static final Map<String,String> MIME = Map.of(
        "html","text/html; charset=utf-8",
        "js","text/javascript; charset=utf-8",
        "css","text/css; charset=utf-8",
        "webmanifest","application/manifest+json; charset=utf-8",
        "json","application/json; charset=utf-8",
        "png","image/png",
        "wav","audio/wav"
    );

    public static void main(String[] args) throws Exception {
        boolean probe = java.util.Arrays.asList(args).contains("--probe");
        boolean headless = java.util.Arrays.asList(args).contains("--headless");
        if (!probe && !headless) System.setProperty("java.awt.headless", "false");
        startServer();
        if (probe) {
            try {
                String health = readUrl(URI.create(appUri + "health"));
                String index = readUrl(appUri);
                String js = readUrl(URI.create(appUri + "app.js"));
                if (!health.contains("\"ok\":true") || !index.contains("FranchiseGuide Web 0.36") || !js.contains("franchiseguide-web-v8")) {
                    throw new IOException("Desktop probe failed: embedded assets not served correctly");
                }
                System.out.println("{\"ok\":true,\"app\":\"FranchiseGuide PC\",\"version\":\"0.36.0\",\"assets\":true}");
            } finally {
                if (server != null) server.stop(0);
            }
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if(server != null) server.stop(0); }));
        if(headless) { System.out.println("FranchiseGuide 0.36.0 · " + appUri); new java.util.concurrent.CountDownLatch(1).await(); return; }
        boolean noBrowser = java.util.Arrays.asList(args).contains("--no-browser");
        SwingUtilities.invokeLater(() -> createWindow(noBrowser));
    }

    private static String readUrl(URI uri) throws IOException {
        // A port can be occupied by an unrelated or stalled process. Never freeze startup.
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setInstanceFollowRedirects(false);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void startServer() throws IOException {
        final int stablePort = 47631;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), stablePort), 0);
        } catch (BindException occupied) {
            appUri = URI.create("http://127.0.0.1:" + stablePort + "/");
            final String health;
            try {
                health = readUrl(URI.create(appUri + "health"));
            } catch (IOException unavailable) {
                throw new IOException("Le port 47631 est occupé par un service non joignable. Ferme l’autre lanceur avant de réessayer.", unavailable);
            }
            if (health.contains("\"app\":\"FranchiseGuide PC\"") && health.contains("\"version\":\"0.36.0\"")) return;
            throw new IOException("Une autre application ou une ancienne version utilise le port 47631. Ferme son lanceur avant de démarrer la V0.36.", occupied);
        }
        SteamBridge bridge=new SteamBridge();
        SteamAuth auth=new SteamAuth(bridge);
        RetroBridge retro=new RetroBridge();
        server.createContext("/api/services/",new AutoServices());
        server.createContext("/api/gaming/", ex -> {String path=ex.getRequestURI().getPath();if(SteamAuth.accepts(path))auth.handle(ex);else if(RetroBridge.accepts(path))retro.handle(ex);else bridge.handle(ex);});
        server.createContext("/", Main::handleStatic);
        server.createContext("/health", ex -> {
            if(!validateRequest(ex)) return;
            reply(ex,200,"application/json; charset=utf-8",("{\"ok\":true,\"app\":\"FranchiseGuide PC\",\"version\":\"0.36.0\",\"time\":\""+Instant.now()+"\"}").getBytes(StandardCharsets.UTF_8));
        });
        server.setExecutor(Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "franchiseguide-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        appUri = URI.create("http://127.0.0.1:" + stablePort + "/");
    }

    private static boolean validateRequest(HttpExchange ex) throws IOException {
        String host=ex.getRequestHeaders().getFirst("Host");
        if(LocalRequest.ambiguousHeaders(ex)||!("127.0.0.1:47631".equalsIgnoreCase(host)||"localhost:47631".equalsIgnoreCase(host))) {
            ex.sendResponseHeaders(403,-1);ex.close();return false;
        }
        if(!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Allow","GET, HEAD");ex.sendResponseHeaders(405,-1);ex.close();return false;
        }
        return true;
    }

    private static void handleStatic(HttpExchange ex) throws IOException {
        if(!validateRequest(ex)) return;
        String raw = ex.getRequestURI().getPath();
        String path = raw.equals("/") ? "index.html" : raw.substring(1);
        if (path.contains("..") || path.contains("\\")) { ex.sendResponseHeaders(400, -1); ex.close(); return; }
        InputStream in = Main.class.getResourceAsStream("/web/" + path);
        if (in == null && !path.contains(".")) in = Main.class.getResourceAsStream("/web/index.html");
        if (in == null) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
        byte[] body;
        InputStream resource = in;
        try (resource) { body = resource.readAllBytes(); }
        String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "html";
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", MIME.getOrDefault(ext, "application/octet-stream"));
        h.set("Cache-Control", "no-cache");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        h.set("X-Frame-Options", "DENY");
        h.set("Content-Security-Policy", "default-src 'self' https://api.themoviedb.org https://api.rawg.io https://openlibrary.org; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' https://api.themoviedb.org https://api.rawg.io https://openlibrary.org https://api.trakt.tv https://auth.trakt.tv https://api.tvmaze.com https://itunes.apple.com https://www.freetogame.com; frame-ancestors 'none'; object-src 'none'; base-uri 'none'; media-src 'self';");
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); ex.close(); return; }
        reply(ex, 200, h.getFirst("Content-Type"), body);
    }

    private static void reply(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        if("HEAD".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(status,-1);ex.close();return; }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(body); }
    }

    private static void createWindow(boolean noBrowser) {
        JFrame frame = new JFrame("FranchiseGuide PC 0.36");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(560, 300);
        frame.setMinimumSize(new Dimension(480, 260));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel();
        root.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("FranchiseGuide PC 0.36");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel info = new JLabel("<html>Le moteur local est actif sur <b>127.0.0.1</b>.<br>Les données restent dans le profil local du navigateur/PWA.</html>");
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField url = new JTextField(appUri.toString());
        url.setEditable(false); url.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); url.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton open = new JButton("Ouvrir FranchiseGuide");
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.addActionListener(e -> openBrowser());
        JButton stop = new JButton("Quitter");
        stop.setAlignmentX(Component.LEFT_ALIGNMENT);
        stop.addActionListener(e -> { if (server != null) server.stop(0); System.exit(0); });

        root.add(title); root.add(Box.createVerticalStrut(14)); root.add(info); root.add(Box.createVerticalStrut(14)); root.add(url); root.add(Box.createVerticalStrut(18));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); buttons.setAlignmentX(Component.LEFT_ALIGNMENT); buttons.add(open); buttons.add(Box.createHorizontalStrut(12)); buttons.add(stop); root.add(buttons);
        frame.setContentPane(root); frame.setVisible(true);
        if (!noBrowser) openBrowser();
    }

    private static void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(appUri); return;
            }
        } catch (Exception ignored) {}
        JOptionPane.showMessageDialog(null, "Ouvre cette adresse dans ton navigateur :\n" + appUri,
                "FranchiseGuide PC", JOptionPane.INFORMATION_MESSAGE);
    }
}
