package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;

import pt.ulisboa.tecnico.cnv.webserver.Worker;

import java.util.List;

public class CloudWatchMetrics {

    public static void updateWorkersAvgCPUUtilization() {
        System.out.println();
        for (Worker worker: WorkersOracle.getWorkers().values()) {
            double avgCPU = fetchAvgCPUUtilization(worker.getId());
            System.out.println("Avg CPU: " + avgCPU);
            worker.setAvgCPUUtilization(avgCPU);
        }
        System.out.println();
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
            .withStartTime(new Date(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(120)))
            .withEndTime(new Date())
            .withPeriod(60)
            .withStatistics(Statistic.Average);

        GetMetricStatisticsResult res = cw.getMetricStatistics(request);
        System.out.println("GetMetricStatisticsResult: " + res.toString());

        double totalCPUUtilization = 0.0;
        List<Datapoint> datapoints = res.getDatapoints();
        if (datapoints.size() > 0) {
            System.out.println(String.format("Datapoints for instance %s : %s", instanceId, datapoints.toString()));
            for (Datapoint dp : datapoints) {
                totalCPUUtilization += dp.getAverage();
            }
            totalCPUUtilization = totalCPUUtilization / datapoints.size();
        } 
        return totalCPUUtilization;
    }
}
