package pt.ulisboa.tecnico.cnv.loadbalancer.Autoscaling;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.cnv.loadbalancer.WorkersOracle;

import com.sun.net.httpserver.HttpExchange;

import java.util.Map;
import java.util.HashMap;

import java.net.URI;

public class AutoscalerServiceImpl implements HttpHandler {

    @Override
    public void handle(HttpExchange he) throws IOException {

        if (he.getRequestHeaders().getFirst("Origin") != null) {
            he.getResponseHeaders().add("Access-Control-Allow-Origin", he.getRequestHeaders().getFirst("Origin"));
        }
        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "POST");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,API-Key");
            he.sendResponseHeaders(204, -1);

        } else {
            // parse request
            URI requestedUri = he.getRequestURI();
            String query = requestedUri.getRawQuery();
            Map<String, String> parameters = queryToMap(query);
            String instanceId = parameters.get("instanceId");
            Double avgCPU = Double.parseDouble(parameters.get("avgcpu"));
            WorkersOracle.updateWorkerAvgCPU(instanceId, avgCPU);
            System.out.println("Got request with uri: " + requestedUri + 
                ", parameters: " + parameters + ", instanceId: " + instanceId + ", avgCPU: " + avgCPU);
            
            he.sendResponseHeaders(200, 0);
            OutputStream os = he.getResponseBody();
            os.write(new byte[]{});
            os.close();
        }
    }

    public Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();

        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else{
                result.put(entry[0], "");
            }
        }

        return result;
    }
}
