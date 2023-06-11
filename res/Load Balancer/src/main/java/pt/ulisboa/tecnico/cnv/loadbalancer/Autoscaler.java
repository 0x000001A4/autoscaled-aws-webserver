package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;

import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import pt.ulisboa.tecnico.cnv.webserver.Worker;

public class Autoscaler {

    private static AmazonEC2 ec2;
    private static String AMI_ID = GET_AMI_ID();
    private static String KEY_NAME = System.getenv("AWS_KEYPAIR_NAME");
    private static String SEC_GROUP_ID = System.getenv("AWS_SG_ID");

    // Policies
    private static double MIN_AVG_CPU_UTILIZATION = 0.25;
    private static double MAX_AVG_CPU_UTILIZATION = 0.75;
    
    public static String GET_AMI_ID() {
        try {
            return Files.readString(Paths.get("/home/ec2-user/image.id"), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void init(AmazonEC2 ec2Client) {
        ec2 = ec2Client;
        launchEC2Instance();
        autoscalingThread.start();
    }

    private static Thread autoscalingThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                /* Each 15 seconds update the avgCPUUtilization of each worker */
                CloudWatchMetrics.updateWorkersAvgCPUUtilization();
                updateActiveWorkers(WorkersOracle.computeAvgWorkersAvgCPUUtilization());
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });

    public static void updateActiveWorkers(double avgCPUUtilization) {
        if (avgCPUUtilization > MAX_AVG_CPU_UTILIZATION) {
            launchEC2Instance();

        } else if (WorkersOracle.getWorkers().size() > 0 && avgCPUUtilization < MIN_AVG_CPU_UTILIZATION) {
            double maxAvgCpuUtilization = 0;
            String id = null;
            for (Worker worker: WorkersOracle.getWorkers().values()) {
                double avg = worker.getAvgCPUUtilization();
                if (avg > maxAvgCpuUtilization) {
                    maxAvgCpuUtilization = avg;
                    id = worker.getId();
                }
            }
            if (id != null) terminateEC2instance(id);
        }
    }

    public static Thread getThread() {
        return autoscalingThread;
    }

    public static void terminateAllInstances() {
        for (String workerId: WorkersOracle.getWorkers().keySet()) {
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
            
            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            Instance instance = runInstancesResult.getReservation().getInstances().get(0);
            System.out.println("You have " + ec2.describeInstances().getReservations().size() + " Amazon EC2 instance(s) running.");

            String workerId = instance.getInstanceId();
            WorkersOracle.getWorkers().put(workerId, new Worker(workerId, instance));

        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    public static void terminateEC2instance(String instanceId) {
        Worker worker = WorkersOracle.getWorkers().remove(instanceId);
        boolean terminated = false;
        try {
            while (!terminated) {
                TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
                termInstanceReq.withInstanceIds(instanceId);
                TerminateInstancesResult res = ec2.terminateInstances(termInstanceReq);
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
            WorkersOracle.getWorkers().put(instanceId, worker);
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }


    public static List<Instance> fetchEC2instances() {
        List<Instance> activeInstances = new ArrayList<Instance>();
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        DescribeInstancesResult response = ec2.describeInstances(request);
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
