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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;
import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient.ServerStatus;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;


public class WebServer {
    private static ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();
    public static enum WebServerStatus {STATUS_ON, STATUS_OFF};
    private static WebServerStatus status = WebServerStatus.STATUS_OFF;
    private static String instanceId;
    private static String autoscalerPrivateIpAddress;
    private static boolean settingup = true;

    public static ExecutorService getThreadPool() {
        return threadPool;
    }

    public static WebServerStatus getStatus() {
        return status;
    }

    public static void setInstanceId(String id) {
        instanceId = id;
    }

    public static void setASPrivateIPAddress(String ip) {
        autoscalerPrivateIpAddress = ip;
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
            URI reqURI = he.getRequestURI();
            System.out.println("Got Request: " + reqURI);
            Map<String, String> reqArgs = getReqFeatures(reqURI);
            String response = "Got register request with query '" + reqURI.getQuery() + "'!";
            System.out.println("Got Request args: " + reqArgs.toString());

            setInstanceId(reqArgs.get("id"));
            setASPrivateIPAddress(he.getRemoteAddress().getHostName());

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
                ProcessBuilder processBuilder = new ProcessBuilder("/home/ec2-user/cputracker.sh");
                Process process = processBuilder.start(); /* Takes 10 seconds */
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String avgcpu = reader.readLine();
                System.out.println("CPU Usage: " + avgcpu);
                
                // Build and Forward request
                URI newURI = new URI("http://" + autoscalerPrivateIpAddress + ":" + 8000 + "/cputracker" +
                    String.format("?instanceId=%s&avgcpu=%s", instanceId, avgcpu)
                );
                System.out.println("Sending new request with uri: "+ newURI.toString());
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(newURI)
                    .build();

                
                if (!WebServer.settingup) HttpClient.newHttpClient().send(httpRequest, BodyHandlers.ofByteArray());
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

    public static void start_dynamo_thread(String[] args, ExecutorService threadPool) {
        boolean noDynamo = false;
        for (String arg : args) {
            if (arg.toLowerCase().contains("nodynamo")) {
                noDynamo = true;
                break;
            }
        }
        if (!noDynamo) {
            DynamoClient.init();
            Runnable updateDBTask = DynamoClient::updateDBWithInstrumentationMetrics;

            threadPool.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    if (status.equals(WebServerStatus.STATUS_ON)) {
                        updateDBTask.run();
                    }
                    else Thread.currentThread().interrupt();
                    try {
                        TimeUnit.SECONDS.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        WebServer.settingup = false;
    }


    private static Map<String,String> getReqFeatures(URI requestURI) {
        String query = requestURI.getQuery();
        Map<String, String> features = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            features.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return features;
    }
}
