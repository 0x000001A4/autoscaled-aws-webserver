package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;

import pt.ulisboa.tecnico.cnv.webserver.Worker;

public class CloudWatchMetrics {

    public static void updateWorkersAvgCPUUtilization() {
        for (Worker worker: WorkersOracle.getWorkers().values()) {
            worker.setAvgCPUUtilization(fetchAvgCPUUtilization(worker.getId()));
        }
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
            .withStartTime(new Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60)))
            .withEndTime(new Date())
            .withPeriod(10) 
            .withStatistics(Statistic.Average);

        GetMetricStatisticsResult res = cw.getMetricStatistics(request);
        
        double totalCPUUtilization = 0.0;
        for (Datapoint dp : res.getDatapoints()) {
            totalCPUUtilization += dp.getAverage();
        }
        return totalCPUUtilization / res.getDatapoints().size();
    }
}
