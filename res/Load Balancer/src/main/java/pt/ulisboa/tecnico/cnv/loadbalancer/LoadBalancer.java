package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

import com.sun.net.httpserver.HttpServer;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;
import pt.ulisboa.tecnico.cnv.loadbalancer.Autoscaling.Autoscaler;


public class LoadBalancer {

    private static String AWS_REGION = System.getenv("AWS_DEFAULT_REGION");
    private static AmazonEC2 ec2;
    public static enum LoadBalancerStatus {STATUS_ON, STATUS_OFF};
    private static LoadBalancerStatus status = LoadBalancerStatus.STATUS_OFF;
    public static Object queueLock = new Object();

    public static LoadBalancerStatus getStatus() {
        return status;
    }

    public static void cleanShutdown(HttpServer server, ExecutorService threadPool, Thread autoscalingThread) {
        System.out.println("Stopping the loadbalancer...");
        LoadBalancer.status = LoadBalancerStatus.STATUS_OFF;
        server.stop(0);
        threadPool.shutdown();
        autoscalingThread.interrupt();
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

        AwsLambdaClient.init();
        DynamoClient.init();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        ExecutorService threadPool = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(threadPool);
        LoadBalancerHandler loadBalancerHandler = new LoadBalancerHandler();
        server.createContext("/simulate", loadBalancerHandler);
        server.createContext("/compressimage", loadBalancerHandler);
        server.createContext("/insectwar", loadBalancerHandler);
        Autoscaler.init(ec2, server);

        status = LoadBalancerStatus.STATUS_ON;
        WorkersOracle.init(threadPool);
        DynamoClient.initServiceTables(Arrays.asList(WorkersOracle.workerServiceNames));

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> cleanShutdown(server, threadPool, Autoscaler.getThread()))
        );
        server.start();
    }
}