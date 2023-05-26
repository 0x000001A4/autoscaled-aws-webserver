package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.IOException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;

public class LoadBalancerHandler implements HttpHandler {

    private int nextInstance;

    public LoadBalancerHandler() {
        nextInstance = 0;
    }

    private HttpResponse<byte[]> forward(HttpExchange he) {
        try {
            String nextWorkerId = Autoscaler.getActiveInstances().values().toArray(String[]::new)[nextInstance];
            nextInstance = (nextInstance+1) % Autoscaler.getActiveInstances().size();
            
            URI uri = he.getRequestURI();
            HttpRequest req = HttpRequest.newBuilder(
                new URI(uri.getScheme(),
                  uri.getUserInfo(), 
                  nextWorkerId, 
                  uri.getPort(),
                  uri.getPath(), 
                  uri.getQuery(),
                  uri.getFragment())
            ).build();
            
            HttpClient client = HttpClient.newHttpClient();
            return client.send(req, BodyHandlers.ofByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        // Handling CORS
        try {
            he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                he.sendResponseHeaders(204, -1);
                return;
            }
            he.getResponseBody().write(forward(he).body());
            he.sendResponseHeaders(200, 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}