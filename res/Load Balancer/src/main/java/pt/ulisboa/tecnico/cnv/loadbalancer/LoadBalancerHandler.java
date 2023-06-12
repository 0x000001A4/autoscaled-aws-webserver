package pt.ulisboa.tecnico.cnv.loadbalancer;

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

import pt.ulisboa.tecnico.cnv.webserver.Worker;

public class LoadBalancerHandler implements HttpHandler {

    private Worker prevWorker = null;

    private HttpResponse<byte[]> forward(HttpExchange he) {
        try {       
            // Read request body     
            StringBuilder bodyBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(he.getRequestBody()));
            String line;
            while ((line = reader.readLine()) != null) {
                bodyBuilder.append(line);
            }
            String body = bodyBuilder.toString();
            URI reqURI = he.getRequestURI();

            System.out.println("Got request " + reqURI + " with body : ...");

            Double complexity = ComplexityEstimator.estimateRequestComplexity(reqURI, body);
            System.out.println("Estimated complexity: " + complexity);
            Worker worker = complexity.equals(0.0) ? WorkersOracle.roundRobin(prevWorker) : WorkersOracle.getTopWorker();
            prevWorker = worker;
            System.out.println(worker.toString());
            URI newURI = new URI("http://" + worker.getEC2Instance().getPrivateIpAddress() + ":" + 8000 + reqURI.toString());

            System.out.println("Forwarding request to " + newURI);

            // Build new request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(newURI)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            // Forward request (Send the new request)
            worker.loadWork(complexity);
            HttpResponse<byte[]> res = HttpClient.newHttpClient().send(httpRequest, BodyHandlers.ofByteArray());
            worker.unloadWork(complexity);
            return res;

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
