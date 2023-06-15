package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.io.IOException;
import java.io.InputStream;

import com.sun.net.httpserver.HttpHandler;

import javassist.bytecode.ByteArray;
import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.ComplexityEstimator;

import com.sun.net.httpserver.HttpExchange;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.Map.Entry;
import java.net.http.HttpRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.StringBuilder;

import pt.ulisboa.tecnico.cnv.webserver.Worker;

public class LoadBalancerHandler implements HttpHandler {

    private static Worker prevWorker = null;

    public static void setPrevWorker(Worker worker) {
        prevWorker = worker;
    }

    private String readRequestBody(InputStream reqBody) {
        try {
            StringBuilder bodyBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(reqBody));
            String line;
            while ((line = reader.readLine()) != null) {
                bodyBuilder.append(line);
            }
            return bodyBuilder.toString();

        } catch (IOException e) {
            System.out.println("Error reading request body");
            e.printStackTrace();
            return "";
        }
    }

    private HttpResponse<byte[]> forwardRequestToWorker(Worker worker, HttpRequest httpRequest, Double complexity)
        throws IOException, InterruptedException {

        worker.loadWork(complexity);
        HttpResponse<byte[]> res = HttpClient.newHttpClient().send(httpRequest, BodyHandlers.ofByteArray());
        worker.unloadWork(complexity);
        return res;
    }

    private byte[] forward(HttpExchange he) {
        // Read request body
        URI reqURI = he.getRequestURI();
        String body = readRequestBody(he.getRequestBody());
        System.out.println("Got request " + reqURI + " with body : ...");

        Entry<Double, Map<String,String>> reqInfo;
        // Get request complexity and respective arguments (that can be in header/body)
        try {
            reqInfo = ComplexityEstimator.unfoldRequest(reqURI, body);
        } catch (InvalidArgumentException e) {
            /* In case arguments are not correct do not forward request */
            return new byte[]{};
        }        
        Double complexity = reqInfo.getKey();
        Map<String, String> reqArgs = reqInfo.getValue();
        System.out.println("Request info: " + reqInfo.toString());

        /* Here we try to find the best Worker to handle a request.
        
            -> In case there is no worker with enough resources to 
            handle a request (NoAvailableWorkerException) we issue 
            Lambdas while we launch another worker.
        
            -> In case there is a problem handling the request and
            another exception is catched in this attempt to forward
            the request should be forwarded to some other worker.
        */
        while (true) {
            try {
                Worker worker = WorkersOracle.findNextWorkerToHandleRequest(prevWorker, complexity);
    
                URI newURI = new URI("http://" + worker.getEC2Instance().getPrivateIpAddress() + ":" + 8000 + reqURI.toString());
                System.out.println("Forwarding request to " + newURI);
                System.out.println("Worker handling request: " + worker.toString());
    
                // Build and Forward request
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(newURI)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                return forwardRequestToWorker(worker, httpRequest, complexity).body();
    
            } catch (NoAvailableWorkerException e) {
                /* Launch AWS Lambda in case there is no worker available to handle request */
                String lambdaName = String.format("%s-lambda", reqURI.getPath()).substring(1);
                System.out.println(String.format("Invoking lambda  %s  with args: %s ", lambdaName, reqArgs.toString()));
                Autoscaler.updateActiveWorkers();
                return AwsLambdaClient.invokeLambda(lambdaName, reqArgs);
    
            } catch (Exception e) {
                /* Forward to some other server in case of failure on forward of request */
                System.out.println(String.format("Failed forwarding request to worker: %s. \n Retrying...", prevWorker.toString()));
                e.printStackTrace();
            }
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
            he.getResponseBody().write(forward(he));
            he.getResponseBody().flush();
            he.getResponseBody().close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
