package pt.ulisboa.tecnico.cnv.loadbalancer;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;

import pt.ulisboa.tecnico.cnv.webserver.Worker;

import java.util.Collections;
import java.util.List;

public class CloudWatchMetrics {
    private static String AWS_REGION = System.getenv("AWS_DEFAULT_REGION");

    public static void updateWorkersAvgCPUUtilization() {
        // TODO: nsei s basta o default, testo amanha xd
        AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build();

        System.out.println();
        for (Worker worker: WorkersOracle.getWorkers().values()) {
            double avgCPU = fetchAvgCPUUtilization(cw, worker.getId());
            System.out.println("Avg CPU: " + avgCPU);
            worker.setAvgCPUUtilization(avgCPU);
        }
        System.out.println();

        cw.shutdown();
    }

    public static double fetchAvgCPUUtilization(String instanceId) {
        return fetchAvgCPUUtilization(AmazonCloudWatchClientBuilder.defaultClient(), instanceId);
    }
    
    public static double fetchAvgCPUUtilization(AmazonCloudWatch cw, String instanceId) {

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
            .withStatistics(Statistic.Average)
            .withUnit(StandardUnit.Percent);

        System.out.println("Request: " + request);
        GetMetricStatisticsResult res = cw.getMetricStatistics(request);
        System.out.println("GetMetricStatisticsResult: " + res);

        double totalCPUUtilization = 0.0;
        List<Datapoint> datapoints = res.getDatapoints();
        if (datapoints.size() > 0) {
            System.out.println(String.format("Datapoints for instance %s : %s", instanceId, datapoints));
            for (Datapoint dp : datapoints) {
                totalCPUUtilization += dp.getAverage() / 100; // Divide by 100 to get fraction from 0 to 1
            }
            totalCPUUtilization = totalCPUUtilization / datapoints.size();
        }

        return totalCPUUtilization;
    }
}
