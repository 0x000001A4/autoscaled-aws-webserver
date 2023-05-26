package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler;
import pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl;
import pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

public class LoadBalancer {

    private static String AWS_REGION;
    private static AmazonEC2 ec2;

    public static void main(String[] args) throws Exception {
        ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new EnvironmentVariableCredentialsProvider()).build();

        DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
        System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() + " Availability Zones.");
        System.out.println("You have " + ec2.describeInstances().getReservations().size() + " Amazon EC2 instance(s) running.");

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        LoadBalancerHandler loadBalancerHandler = new LoadBalancerHandler();
        server.createContext("/simulate", loadBalancerHandler);
        server.createContext("/compressimage", loadBalancerHandler);
        server.createContext("/insectwar", loadBalancerHandler);
        server.start();
    }
}