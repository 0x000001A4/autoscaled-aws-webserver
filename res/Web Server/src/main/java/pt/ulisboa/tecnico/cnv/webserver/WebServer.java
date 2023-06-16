package pt.ulisboa.tecnico.cnv.webserver;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;


public class WebServer {
    private static ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();
    public static enum WebServerStatus {STATUS_ON, STATUS_OFF};
    private static WebServerStatus status = WebServerStatus.STATUS_OFF;
    private static String instanceId;
    private static String autoscalerPrivateIpAddress;

    public static ExecutorService getThreadPool() {
        return threadPool;
    }

    public static WebServerStatus getStatus() {
        return status;
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(threadPool);

        SimulationHandler foxesAndRabbitsHandler = new SimulationHandler();
        CompressImageHandlerImpl compressImageHandler = new CompressImageHandlerImpl();
        WarSimulationHandler warSimulationHandler = new WarSimulationHandler();
        server.createContext("/simulate", foxesAndRabbitsHandler);
        server.createContext("/compressimage", compressImageHandler);
        server.createContext("/insectwar", warSimulationHandler);
        server.createContext("/register", he -> {
            he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                he.sendResponseHeaders(204, -1);
                return;
            }

            // parse request
            String query = he.getRequestURI().getQuery();

            instanceId = query.substring("id=".length());

            System.out.println("Got register query " + query + ", instanceId = " + instanceId);

            String response = "Got register request with query '" + query + "'!";

            he.sendResponseHeaders(200, response.length());
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes());

            os.close();
        });
        server.createContext("/test", he -> {
            he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                he.sendResponseHeaders(204, -1);
                return;
            }

            // parse request
            String query = he.getRequestURI().getRawQuery();

            String response = "Got test request with query '" + query + "'!";

            he.sendResponseHeaders(200, response.length());
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes());

            os.close();
        });
        

        Thread cpuTrackerThread = new Thread(() -> trackAndReportWorkerCPU());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping webserver...");
            status = WebServerStatus.STATUS_OFF;
            DynamoClient.updateStatus(DynamoClient.ServerStatus.STATUS_OFF);
            server.stop(0);
            threadPool.shutdown();
            cpuTrackerThread.interrupt();
            try {
                threadPool.awaitTermination(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                System.out.println("Unexpected behaviour");
            }
        }));

        status = WebServerStatus.STATUS_ON;
        threadPool.execute(server::start);
        threadPool.execute(cpuTrackerThread);
    }

    private static void trackAndReportWorkerCPU() {
        while (status.equals(WebServerStatus.STATUS_ON) && !Thread.currentThread().isInterrupted()) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("/home/ricky420/school/cnv/cnv/scripts/cputracker.sh");
                Process process = processBuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("CPU Usage: " + line);
                }

            
            // Build and Forward request
            URI newURI = new URI("http://" + autoscalerPrivateIpAddress + ":" + 8000 + "/cputracker" +
                String.format("?instanceId=%s&avgcpu=%s", instanceId, line)
            );
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(newURI)
                    .build();

                HttpClient.newHttpClient().send(httpRequest, BodyHandlers.ofByteArray());
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();

            } catch (IOException e) {
                e.printStackTrace();
                break;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
