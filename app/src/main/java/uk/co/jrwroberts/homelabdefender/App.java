package uk.co.jrwroberts.homelabdefender;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class App {
    private static final GameEngine GAME = new GameEngine();

    private App() {}

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", App::index);
        server.createContext("/healthz", exchange -> text(exchange, 200, "ok"));
        server.createContext("/api/incidents", App::incidents);
        server.createContext("/api/resolve", App::resolve);
        server.start();
        System.out.println("Homelab Defender running on http://localhost:8080");
    }

    private static void index(HttpExchange exchange) throws IOException {
        html(exchange, 200, """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Homelab Defender</title>
            <style>
            body{margin:0;background:#09111d;color:#e6edf6;font-family:system-ui,sans-serif}
            main{max-width:960px;margin:auto;padding:48px 24px}h1{font-size:clamp(2.8rem,7vw,5rem);margin:0}
            h1 span{color:#5db8ff}.lead{color:#9dacbf;font-size:1.15rem;max-width:700px}
            .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:14px;margin-top:32px}
            .card{background:#111d2e;border:1px solid #263b57;border-radius:14px;padding:18px}.card p{color:#b6c4d5}
            .actions{display:flex;flex-wrap:wrap;gap:8px}.actions button{background:#183553;color:#dff1ff;border:1px solid #3877ad;border-radius:7px;padding:7px 9px;cursor:pointer}
            .result{min-height:26px;margin-top:28px;padding:14px;border-left:3px solid #5db8ff;background:#111d2e}.good{border-color:#6ee7b7}.bad{border-color:#fca5a5}
            </style></head><body><main><p>Build & Delivery Lab</p><h1>Homelab <span>Defender</span></h1>
            <p class="lead">Choose the best first response to each incident. The game is small; the delivery pipeline is the real project.</p>
            <div id="game" class="grid"></div><div id="result" class="result">Choose an action to begin.</div></main>
            <script>
            const actions=['INVESTIGATE','RESTART','PATCH','BLOCK','RESTORE','IGNORE'];
            const labels={INVESTIGATE:'Investigate',RESTART:'Restart',PATCH:'Patch',BLOCK:'Block',RESTORE:'Restore',IGNORE:'Ignore'};
            fetch('/api/incidents').then(r=>r.json()).then(items=>{document.querySelector('#game').innerHTML=items.map(i=>'<article class="card"><h2>'+i.title+'</h2><p>'+i.description+'</p><div class="actions">'+actions.map(a=>'<button onclick="play(\\''+i.id+'\\',\\''+a+'\\')">'+labels[a]+'</button>').join('')+'</div></article>').join('')});
            function play(id,action){fetch('/api/resolve?id='+id+'&action='+action).then(r=>r.json()).then(x=>{const box=document.querySelector('#result');box.textContent=x.message;box.className='result '+(x.successful?'good':'bad')})}
            </script></body></html>
            """);
    }

    private static void incidents(HttpExchange exchange) throws IOException {
        String json = GAME.incidents().stream()
            .map(incident -> "{\"id\":\"" + incident.id() + "\",\"title\":\"" + incident.title() + "\",\"description\":\"" + incident.description() + "\"}")
            .collect(Collectors.joining(",", "[", "]"));
        json(exchange, 200, json);
    }

    private static void resolve(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI());
        String json = GAME.resolve(query.get("id"), query.get("action"))
            .map(resolution -> "{\"successful\":" + resolution.successful() + ",\"message\":\"" + resolution.message() + "\"}")
            .orElse("{\"successful\":false,\"message\":\"Unknown incident or action.\"}");
        json(exchange, 200, json);
    }

    private static Map<String, String> query(URI uri) {
        if (uri.getQuery() == null) {
            return Map.of();
        }
        return Arrays.stream(uri.getQuery().split("&"))
            .map(pair -> pair.split("=", 2))
            .filter(pair -> pair.length == 2)
            .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (first, ignored) -> first));
    }

    private static void html(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        send(exchange, status, body);
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, body);
    }

    private static void text(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        send(exchange, status, body);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
