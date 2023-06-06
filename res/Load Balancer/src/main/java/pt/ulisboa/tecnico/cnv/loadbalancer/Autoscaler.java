package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Autoscaler {


    private static String AMI_ID = GET_AMI_ID();
    private static String KEY_NAME = System.getenv("AWS_KEYPAIR_NAME");
    private static String SEC_GROUP_ID = System.getenv("AWS_SG_ID");
    private static Map<String, Instance> activeWorkers = new ConcurrentHashMap<String, Instance>();
    private static Thread metricsThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CloudWatchMetrics.updateWorkerMetrics();
                updateActiveWorkers(CloudWatchMetrics.avgCPUUtilization
                                            .values()
                                            .stream()
                                            .mapToDouble(Double::doubleValue)
                                            .average()
                                            .orElse(0.0)
                );
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });

    // Policies
    private static double MIN_AVG_CPU_UTILIZATION = 0.4;
    private static double MAX_AVG_CPU_UTILIZATION = 0.9;
    
    public static String GET_AMI_ID() {
        try {
            return Files.readString(Paths.get("/home/ec2-user/image.id"), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void init() {
        launchEC2Instance();
        metricsThread.start();
    }

    public static void updateActiveWorkers(double avgCPUUtilization) {
        if (avgCPUUtilization > MAX_AVG_CPU_UTILIZATION) {
            launchEC2Instance();

        } else if (activeWorkers.size() > 0 && avgCPUUtilization < MIN_AVG_CPU_UTILIZATION) {
            double maxAvgCpuUtilization = CloudWatchMetrics.avgCPUUtilization
                .values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

            String instanceId = CloudWatchMetrics.avgCPUUtilization
                .entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), maxAvgCpuUtilization))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
            
            if (instanceId != null) terminateEC2instance(instanceId);
        }
    }

    public static Thread getThread() {
        return metricsThread;
    }

    public static Map<String, Instance> getActiveInstances() {
        return activeWorkers;
    }

    public static void terminateAllInstances() {
        for (String workerId: activeWorkers.keySet()) {
            terminateEC2instance(workerId);
        }
    }

    public static void launchEC2Instance() {
        try {
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(AMI_ID)
                                .withInstanceType("t2.micro")
                                .withMinCount(1)
                                .withMaxCount(1)
                                .withKeyName(KEY_NAME)
                                .withSecurityGroupIds(SEC_GROUP_ID);
            
            RunInstancesResult runInstancesResult = LoadBalancer.ec2.runInstances(runInstancesRequest);
            Instance instance = runInstancesResult.getReservation().getInstances().get(0);
            System.out.println("You have " + LoadBalancer.ec2.describeInstances().getReservations().size() + " Amazon EC2 instance(s) running.");

            activeWorkers.put(instance.getInstanceId(), instance);

        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    public static void terminateEC2instance(String instanceId) {
        Instance instance = activeWorkers.remove(instanceId);
        boolean terminated = false;
        try {
            while (!terminated) {
                TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
                termInstanceReq.withInstanceIds(instanceId);
                TerminateInstancesResult res = LoadBalancer.ec2.terminateInstances(termInstanceReq);
                List<InstanceStateChange> stateChanges = res.getTerminatingInstances();
                for (InstanceStateChange stChange: stateChanges) {
                    System.out.println("The ID of the {status: " + stChange.getCurrentState().getName() + 
                        "} instance is " + stChange.getInstanceId());
                    if (stChange.getCurrentState().getName().equals(InstanceStateName.Terminated.toString())) {
                        terminated = true;
                    }
                }
            }

        } catch (AmazonServiceException ase) {
            activeWorkers.put(instanceId, instance);
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }


    public static List<Instance> fetchEC2instances() {
        List<Instance> activeInstances = new ArrayList<Instance>();
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        DescribeInstancesResult response = LoadBalancer.ec2.describeInstances(request);
        boolean done = false;

        while (!done) {
            for (Reservation reservation: response.getReservations()) {
                for (Instance instance: reservation.getInstances()) {
                    if (InstanceStateName.Running.toString().equalsIgnoreCase(instance.getState().getName())) {
                        activeInstances.add(instance);
                    }
                }
            }
            request.setNextToken(response.getNextToken());
            if (response.getNextToken() == null) {
                done = true;
            }
        }
        return activeInstances;
    }
}
