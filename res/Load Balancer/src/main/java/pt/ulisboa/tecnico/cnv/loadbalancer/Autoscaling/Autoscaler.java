package pt.ulisboa.tecnico.cnv.loadbalancer.Autoscaling;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;

import java.util.List;
import java.util.concurrent.ExecutorService;

import com.sun.net.httpserver.HttpServer;

import java.util.ArrayList;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;

import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer;
import pt.ulisboa.tecnico.cnv.loadbalancer.WorkersOracle;
import pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer.LoadBalancerStatus;
import pt.ulisboa.tecnico.cnv.webserver.Worker;

public class Autoscaler {

    private static AmazonEC2 ec2;
    private static Thread autoscalingThread = null;
    private static String AMI_ID = GET_AMI_ID();
    private static String KEY_NAME = System.getenv("AWS_KEYPAIR_NAME");
    private static String SEC_GROUP_ID = System.getenv("AWS_SG_ID");

    // Policies
    private static double MIN_AVG_CPU_UTILIZATION = 25.0;
    private static double MAX_AVG_CPU_UTILIZATION = 75.0;

    public static String GET_AMI_ID() {
        try {
            return Files.readString(Paths.get("/home/ec2-user/image.id"), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void init(AmazonEC2 ec2Client, HttpServer server) {
        ec2 = ec2Client;
        launchEC2Instance();
        server.createContext("/cputracker", new AutoscalerServiceImpl());
        autoscalingThread = new Thread(() -> {
            Integer cnt = 0;
            while (LoadBalancer.getStatus().equals(LoadBalancerStatus.STATUS_ON) &&
                    !Thread.currentThread().isInterrupted()) {
                try {
                    updateActiveWorkers();
                    cnt = (cnt + 1) % 6;
                    if (cnt == 0) printActiveInstances();
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                }  catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        autoscalingThread.start();
    }
    
    synchronized public static void updateActiveWorkers() {
        Double avgCPUUtilization = WorkersOracle.computeAvgWorkersAvgCPUUtilization();
        if (avgCPUUtilization > MAX_AVG_CPU_UTILIZATION) {
            launchEC2Instance();

        } else if (WorkersOracle.getWorkers().size() > 1 && avgCPUUtilization < MIN_AVG_CPU_UTILIZATION) {
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
        List<Thread> threads = new ArrayList<>();
        for (String workerId: WorkersOracle.getWorkers().keySet()) {
            Thread t = new Thread(() -> terminateEC2instance(workerId));
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    public static void printActiveInstances() {
        List<Reservation> reservations = ec2.describeInstances(new DescribeInstancesRequest()).getReservations();
        List<String> activeInstances = new ArrayList<>();

        for (Reservation reservation: reservations) {
            for (Instance instance: reservation.getInstances()) {
                if (instance.getState().getName().equalsIgnoreCase("running")) 
                    activeInstances.add(instance.getInstanceId());
            }
        }
        System.out.println();
        System.out.println(String.format("In AWS Console I have: %s instances. Listing instances:", activeInstances.size()));
        System.out.println(activeInstances.toString());

        System.out.println(String.format("In WorkersOracle I have: %s workers. Listing workers", WorkersOracle.getWorkers().size()));
        System.out.println(WorkersOracle.getWorkers().toString());
        System.out.println();
    }

    private static void completeWorkerRegistration(String workerId, Instance instance) {
        Worker worker = new Worker(workerId, instance);
        System.out.println(String.format(
            "Completing Worker Registration by sending him workerId: %s", workerId));

        String query = String.format("?id=%s", workerId);
        String url = String.format("http://" + worker.getEC2Instance().getPrivateIpAddress() +
             ":" + 8000 + "/register" + query);

        URI newURI;
        try {
            newURI = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        // Build and Forward request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(newURI)
                .build();
                
        while (true) {
            try {
                System.out.println("Sending instance-id to worker in request with url: " + url);
                HttpResponse<byte[]> res = HttpClient.newHttpClient().send(httpRequest, BodyHandlers.ofByteArray());
                System.out.println(String.format("Got response from Worker Registration of worker: %s with res: %s",
                    workerId, res.toString()));
                if (res.statusCode() == 200) {
                    WorkersOracle.addWorker(worker);
                    break;
                }
                Thread.sleep(5000);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        printActiveInstances();
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
                                .withSecurityGroupIds(SEC_GROUP_ID)
                                .withMonitoring(true);

            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            Instance instance = runInstancesResult.getReservation().getInstances().get(0);
            String workerId = instance.getInstanceId();

            printActiveInstances();
            waitForInstancesReady(workerId);
            new Thread(() -> completeWorkerRegistration(workerId, instance)).start();

        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    public static void terminateEC2instance(String instanceId) {
        Worker worker = WorkersOracle.removeWorker(instanceId);
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
            WorkersOracle.addWorker(worker);
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }


    public static void waitForInstancesReady(String... instanceId) {
        DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest().withInstanceIds(instanceId);
        DescribeInstanceStatusResult response;
        do {
            try {
                System.out.println("Waiting for launched instance to be running");
                Thread.sleep(8000);
            }
            catch (InterruptedException e) {
                break;  
            }
            response = ec2.describeInstanceStatus(request);
            System.out.println("After waiting 8 seconds the status of the instance is: " + response.toString());

        } while (!response.getInstanceStatuses().stream().allMatch(status -> status.getInstanceState().getCode() == 16)); // 16 -> Running code
    }
}
