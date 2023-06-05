package pt.ulisboa.tecnico.cnv.webserver;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;

public class WebServer {

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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
