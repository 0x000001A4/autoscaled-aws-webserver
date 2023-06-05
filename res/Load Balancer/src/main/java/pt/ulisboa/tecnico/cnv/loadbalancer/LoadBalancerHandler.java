package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.services.ec2.model.Instance;

import java.io.IOException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.URI;

import java.util.Map;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.StringBuilder;


public class LoadBalancerHandler implements HttpHandler {

    private int nextInstance;

    public LoadBalancerHandler() {
        nextInstance = 0;
    }

    private HttpResponse<byte[]> forward(HttpExchange he) {
        try {
            Map<String, Instance> activeInstances = Autoscaler.getActiveInstances();
            Instance instance = activeInstances.values().toArray(Instance[]::new)[nextInstance];
            nextInstance = (nextInstance+1) % activeInstances.size();
            
            URI uri = he.getRequestURI();
            URI _uri = new URI("http://" + instance.getPrivateIpAddress() + ":" + 8000 + uri.toString());
            System.out.println(_uri);
            System.out.println(_uri.getScheme());   

            InputStream in = he.getRequestBody();
            StringBuilder body = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            // Now body.toString() contains the body of the HttpExchange
            HttpRequest req = HttpRequest.newBuilder()
                .uri(_uri)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            
            HttpClient client = HttpClient.newHttpClient();
            return client.send(req, BodyHandlers.ofByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        try {
            he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                he.sendResponseHeaders(204, -1);
                return;
            }
            he.sendResponseHeaders(200, 0);
            he.getResponseBody().write(forward(he).body());
            he.getResponseBody().flush();
            he.getResponseBody().close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
