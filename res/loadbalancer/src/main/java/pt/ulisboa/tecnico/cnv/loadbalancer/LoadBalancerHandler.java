package pt.ulisboa.tecnico.cnv.insectwar;

import java.io.IOException;
import java.io.OutputStream;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;

public class LoadBalancerHandler implements HttpHandler {

    private int nextInstance;

    public LoadBalancerHandler() {
        nextInstance = 0;
    }

    private HttpResponse<byte[]> forward(HttpExchange he) {
        HttpExchange heForward = he;
        he.setAttribute(Autoscaler.getActiveInstances().values()[nextInstance])
        nextInstance = (nextInstance+1) % Autoscaler.getActiveInstances().size();
        HttpRequest req = HttpRequest.newBuilder(heForward.getRequestURI()).build();
        client.send(req, BodyHandlers.ofByteArray());
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

            HttpClient client = HttpClient.newHttpClient();
            he.getResponseBody().write(forward(he).body());
            he.sendResponseHeaders(200, 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}