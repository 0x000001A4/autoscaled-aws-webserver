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

    public static Map<String, List<Datapoint>> avgCPUUtilization = new HashMap<String, List<Datapoint>>();

    public static void updateWorkerMetrics() {
        avgCPUUtilization.clear();
        Autoscaler.getActiveInstances().keySet()
            .stream()
            .map(instanceId -> avgCPUUtilization.put(instanceId, fetchAvgCPUUtilization(instanceId)));
    }

    public static List<Datapoint> fetchAvgCPUUtilization(String instanceId) {
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

        GetMetricStatisticsResult response = cw.getMetricStatistics(request);
        return response.getDatapoints();
    }
}
