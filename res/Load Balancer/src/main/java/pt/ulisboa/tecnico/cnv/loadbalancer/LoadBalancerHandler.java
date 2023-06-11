package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.services.ec2.model.Instance;

import java.io.IOException;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.ComplexityEstimator;

import com.sun.net.httpserver.HttpExchange;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.StringBuilder;


public class LoadBalancerHandler implements HttpHandler {

    private URI getForwardRequestURI(URI reqURI, String body) {
        try {
            Instance instance = WorkersOracle.findBestInstanceToHandleRequest(
                ComplexityEstimator.estimateRequestComplexity(reqURI, body)
            );
            return new URI("http://" + instance.getPrivateIpAddress() + ":" + 8001 + reqURI.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private HttpResponse<byte[]> forward(HttpExchange he) {
        try {       
            // Read request body     
            StringBuilder body = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(he.getRequestBody()));
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            // Build new request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(getForwardRequestURI(he.getRequestURI(), body.toString()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            
            // Forward request (Send the new request)
            return HttpClient.newHttpClient().send(httpRequest, BodyHandlers.ofByteArray());

        } catch (Exception e) {
            /* TODO: Forward to some other server in case of failure */
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
