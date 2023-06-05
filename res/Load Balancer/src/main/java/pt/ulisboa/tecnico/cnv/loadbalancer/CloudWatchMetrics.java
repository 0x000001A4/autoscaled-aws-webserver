package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;


public class CloudWatchMetrics {

    public static Map<String, Double> avgCPUUtilization = new HashMap<String, Double>();

    public static void updateWorkerMetrics() {
        avgCPUUtilization.clear();
        Autoscaler.getActiveInstances().keySet()
            .stream()
            .map(instanceId -> avgCPUUtilization.put(instanceId, fetchAvgCPUUtilization(instanceId)));
    }

    public static double fetchAvgCPUUtilization(String instanceId) {
        AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();

        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
            .withNamespace("AWS/EC2")
            .withMetricName("CPUUtilization")
            .withDimensions(new Dimension()
                        .withName("InstanceId")
                        .withValue(instanceId)
                        )
            .withStartTime(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))) // setting start time to 1 hour ago
            .withEndTime(new Date()) // current time
            .withPeriod(3600) // period in seconds (3600 seconds = 1 hour)
            .withStatistics(Statistic.Average); // can also use MAX, MIN, SAMPLE_COUNT, SUM

        GetMetricStatisticsResult res = cw.getMetricStatistics(request);
        
        double totalCPUUtilization = 0.0;
        for (Datapoint dp : res.getDatapoints()) {
            totalCPUUtilization += dp.getAverage();
        }
        return totalCPUUtilization / res.getDatapoints().size();
    }
}
