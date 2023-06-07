package pt.ulisboa.tecnico.cnv.webserver;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;

import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;

public class WebServer {
    private static String AWS_REGION = System.getenv("AWS_DEFAULT_REGION");

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(threadPool);

        SimulationHandler foxesAndRabbitsHandler = new SimulationHandler();
        CompressImageHandlerImpl compressImageHandler = new CompressImageHandlerImpl();
        WarSimulationHandler warSimulationHandler = new WarSimulationHandler();
        server.createContext("/simulate", foxesAndRabbitsHandler);
        server.createContext("/compressimage", compressImageHandler);
        server.createContext("/insectwar", warSimulationHandler);
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

        boolean noDynamo = false;
        for (String arg : args) {
            if (arg.toLowerCase().contains("nodynamo")) {
                noDynamo = true;
                break;
            }
        }

        if (!noDynamo) {
            DynamoClient.init(AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build()
            );
            DynamoClient.initServiceTables(new ArrayList<String>(
                Arrays.asList("compression", "foxrabbit", "insectwar")
            ));
            Runnable task = DynamoClient::updateDBWithInstrumentationMetrics;
            threadPool.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    task.run();
                    try {
                        TimeUnit.MINUTES.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping webserver...");
            server.stop(0);
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                System.out.println("Unexpected behaviour");
            }
        }));


        server.start();
    }
}
