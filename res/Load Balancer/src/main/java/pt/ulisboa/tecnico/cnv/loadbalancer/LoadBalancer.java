package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;

import com.sun.net.httpserver.HttpServer;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;

public class LoadBalancer {

    private static String AWS_REGION = System.getenv("AWS_DEFAULT_REGION");
    private static AmazonEC2 ec2;

    public static void cleanShutdown(HttpServer server, ExecutorService threadPool, Thread metricsThread) {
        server.stop(0);
        threadPool.shutdown();
        metricsThread.interrupt();
        try {
            threadPool.awaitTermination(15, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.out.println("Unexpected behaviour");
        }
        Autoscaler.terminateAllInstances();
    }


    public static void main(String[] args) throws Exception {
        ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();

        DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
        System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() + " Availability Zones.");
        System.out.println("You have " + ec2.describeInstances().getReservations().size() + " Amazon EC2 instance(s) running.");
        
        Autoscaler.init(ec2);
        DynamoClient.init(AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion(AWS_REGION)
            .build()
        );
        DynamoClient.initServiceTables(new ArrayList<String>(
            Arrays.asList("compress-image", "foxes-and-rabbits", "war-simulator")
        ));
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(threadPool);
        LoadBalancerHandler loadBalancerHandler = new LoadBalancerHandler();
        server.createContext("/simulate", loadBalancerHandler);
        server.createContext("/compressimage", loadBalancerHandler);
        server.createContext("/insectwar", loadBalancerHandler);
        

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> cleanShutdown(server, threadPool, Autoscaler.getThread()))
        );
        server.start();
    }
}