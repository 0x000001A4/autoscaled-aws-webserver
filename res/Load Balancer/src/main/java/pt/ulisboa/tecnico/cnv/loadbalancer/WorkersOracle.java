package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.FoxRabbitCE;
import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.ImageCompressionCE;
import pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator.InsectWarsCE;
import pt.ulisboa.tecnico.cnv.webserver.Worker;
import pt.ulisboa.tecnico.cnv.dynamoclient.DynamoClient;

public class WorkersOracle {

    private static Map<String, Worker> workers = new ConcurrentHashMap<String, Worker>();
    public static String[] workerServiceNames = {"compression", "foxrabbit", "insectwar"};
    private static Runnable queryDBTask = WorkersOracle::updateLBWithInstrumentationMetrics;
    private static PriorityQueue<Worker> workersQueue = new PriorityQueue<Worker>();

    public static Map<String, Worker> getWorkers() {
        return workers;
    }

    public static void addWorker(Worker newWorker) {
        synchronized (LoadBalancer.queueLock) {
            workers.put(newWorker.getId(), newWorker);
            workersQueue.add(newWorker);
        }
    }

    public static Worker removeWorker(String workerId) {
        synchronized (LoadBalancer.queueLock) {
            Worker worker = workers.get(workerId);
            workersQueue.remove(worker);
            workers.remove(workerId);
            return worker;
        }
    }

    public static void init(ExecutorService threadPool) {
        threadPool.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(40);
                    if (LoadBalancer.getStatus().equals(LoadBalancer.LoadBalancerStatus.STATUS_ON)) {
                        queryDBTask.run();
                    }
                    else Thread.currentThread().interrupt();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static Double getComplexity(Map<String, AttributeValue> record) {
        Double nblocks = Double.parseDouble(record.get("nblocks").getN());
        Double ninsts = Double.parseDouble(record.get("ninsts").getN());
        Double nmethods = Double.parseDouble(record.get("nmethods").getN());

        return 0.7 * ninsts + 0.3 * nblocks + 0.0 * nmethods;
    }

    public static void updateLBWithInstrumentationMetrics() {
        System.out.println("Entering method to update LB with Instrumentation metrics");
        for (String serviceName: workerServiceNames) {
            List<Map<String, AttributeValue>> metrics = DynamoClient.queryDynamoDB(serviceName);
            List<Double> complexities = new ArrayList<>();

            switch (serviceName) {
                case "compression": {
                    Map<List<Double>, Double> featuresComplexities = new HashMap<>();

                    for (Map<String, AttributeValue> record: metrics) {
                        featuresComplexities.put(Arrays.asList(
                            Double.parseDouble(record.get("image-size").getN()),
                            /* Double.parseDouble(record.get("format").getS()) TODO */
                            Double.parseDouble(record.get("compression-factor").getN())
                        ), getComplexity(record));
                    }

                    ImageCompressionCE.updateRegParameters(featuresComplexities);

                    break;
                }

                case "foxrabbit": {
                    Map<Entry<String, Double>, Double> featuresComplexities = new HashMap<>();

                    for (Map<String, AttributeValue> record: metrics) {
                        featuresComplexities.put(new SimpleEntry<String, Double>(
                            record.get("world").getN()+record.get("scenario").getN(),
                            Double.parseDouble(record.get("generations").getN())
                        ), getComplexity(record));
                    }

                    FoxRabbitCE.updateRegParameters(featuresComplexities);

                    break;
                }

                case "insectwar": {
                    Map<List<Double>, Double> featuresComplexities = new HashMap<>();

                    for (Map<String, AttributeValue> record: metrics) {
                        featuresComplexities.put(Arrays.asList(
                            Double.parseDouble(record.get("max").getN()),
                            Math.abs(
                                Double.parseDouble(record.get("army1").getN()) - Double.parseDouble(record.get("army2").getN())
                            )
                        ), getComplexity(record));
                    }

                    InsectWarsCE.updateRegParameters(featuresComplexities);

                    break;
                }
            }
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

    public static Worker getTopWorker() {
        synchronized (LoadBalancer.queueLock) {
            if (workersQueue.isEmpty()) {
                System.out.println("ERROR: Empty Queue");
                System.out.println(workers.keySet().toString());
                System.out.println(workersQueue.toString());
            }
            return workersQueue.peek();
        }
    }

    public static Worker roundRobin(Worker prevWorker) {
        List<Worker> _workers = new ArrayList<>(workers.values());
        if (prevWorker != null) {
            return _workers.get((_workers.indexOf(prevWorker)+1) % _workers.size());
        } else {
            return _workers.get(0);
        }
    }
}
