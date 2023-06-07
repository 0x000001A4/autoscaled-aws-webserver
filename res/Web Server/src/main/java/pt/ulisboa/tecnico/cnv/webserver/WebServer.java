package pt.ulisboa.tecnico.cnv.webserver;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;


public class WebServer {
    static ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();

    public static ExecutorService getThreadPool() {
        return threadPool;
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);
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


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping webserver...");
            DynamoClient.changeWebServerStatus(DynamoClient.STATUS_OFF);
            server.stop(0);
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                System.out.println("Unexpected behaviour");
            }
        }));


        threadPool.execute(server::start);
    }
}
