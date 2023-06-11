package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.ec2.model.Instance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.ImageCompressionCE;
import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.InsectWarsCE;
import pt.ulisboa.tecnico.cnv.webserver.Worker;

public class WorkersOracle {
    
    private static Map<String, Worker> workers = new ConcurrentHashMap<String, Worker>();
    private static String[] workerServiceNames = {"compression", "insectwar"};

    public static Map<String, Worker> getWorkers() {
        return workers;
    }


    public static void updateLBWithInstrumentationMetrics() {
        for (String serviceName: workerServiceNames) {
            List<Map<String, AttributeValue>> metrics = DynamoClient.queryDynamoDB(serviceName);
            List<Double> complexities = new ArrayList<Double>();
            List<List<Double>> features = new ArrayList<List<Double>>();
            switch (serviceName) {
                case "compression":
                    for (Map<String, AttributeValue> record: metrics) {
                        features.add(Arrays.asList(
                            Double.parseDouble(record.get("image-size").getN()),
                            1.0 /* Double.parseDouble(record.get("format").getS()) TODO */,
                            Double.parseDouble(record.get("compression-factor").getN())
                        ));
                    }
                    ImageCompressionCE.updateRegParameters(complexities, features);
                    break;
                
                case "insectwar":
                    for (Map<String, AttributeValue> record: metrics) {
                        features.add(Arrays.asList(
                            Double.parseDouble(record.get("max").getN()),
                            Double.parseDouble(record.get("army1").getN()),
                            Double.parseDouble(record.get("army2").getN())
                        ));   
                    }
                    InsectWarsCE.updateRegParameters(complexities, features);
                    break;
            }
        }
    }

    public static Double queryWorkerProgress(String instanceId) {
        return 1.0; // TODO
    }

    public static void updateWorkersProgress() {
        for (Worker worker: workers.values()) {
            worker.setInstructionsToComplete(queryWorkerProgress(worker.getId()));
        }
    }

    public static Double computeAvgWorkersAvgCPUUtilization() {
        return workers.values()
                .stream()
                .map(Worker::getAvgCPUUtilization)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    public static boolean CPUFilter(String instanceId, Double complexity) {
        return workers.get(instanceId).getAvgCPUUtilization() < 0.75;
    }

    public static Instance findBestInstanceToHandleRequest(Double complexity) {
        /* Filter instances for enough CPU to handle the request */
        List<String> instances = workers.keySet()
            .stream()
            .filter(id -> CPUFilter(id, complexity))
            .collect(Collectors.toList());
        
        

        return null;
    }
}
