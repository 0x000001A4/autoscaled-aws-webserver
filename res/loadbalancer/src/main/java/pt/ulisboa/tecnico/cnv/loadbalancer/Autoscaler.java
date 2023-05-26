package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class Autoscaler {

    private static String AMI_ID;
    private static String KEY_NAME;
    private static String SEC_GROUP_ID;
    private static Map<String, Instance> _activeInstances = new ConcurrentHashMap<String, Instance>();
    
    public static void getActiveInstances() {
        return _activeInstances;
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

            _activeInstances.put(instance.getInstanceId(), instance);

        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId())
        }
    }

    public static void terminateEC2instance(String instanceId) {
        Instance instance = _activeInstances.remove(instanceId);
        try {
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(instanceId);
            LoadBalancer.ec2.terminateInstances(termInstanceReq);

        } catch (AmazonServiceException ase) {
            _activeInstances.put(instanceId, instance);
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId())
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
                    if (InstanceStatusName.Running.toString().equalsIgnoreCase(instance.getState().getName())) {
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