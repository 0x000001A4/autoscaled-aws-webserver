package pt.ulisboa.tecnico.cnv.dynamoclient;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;

import pt.ulisboa.tecnico.cnv.javassist.tools.PrintMetrics;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;

import java.util.ArrayList;
import java.util.Arrays;

public class DynamoClient {

    private static String AWS_REGION = System.getenv("AWS_DEFAULT_REGION");
    private static AmazonDynamoDB dynamoDB;

    public static void init(AmazonDynamoDB _dynamoDB) {
        dynamoDB = _dynamoDB;
    }

    public static void initServiceTables(List<String> serviceNames) {
        try {
            for (String serviceName: serviceNames) {
                CreateTableRequest createTableRequest = new CreateTableRequest()
                    .withTableName(serviceName)
                    .withKeySchema(new KeySchemaElement()
                        .withAttributeName("id")
                        .withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition()
                        .withAttributeName("id")
                        .withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput()
                        .withReadCapacityUnits(1L)
                        .withWriteCapacityUnits(1L));

                // Create table if it does not exist yet
                TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
                // wait for the table to move into ACTIVE state
                TableUtils.waitUntilActive(dynamoDB, serviceName);

                DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(serviceName);
                TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
                System.out.println("Table Description: " + tableDescription);
            }
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateDBWithInstrumentationMetrics() {
        System.out.println("updateDB");
        List<PrintMetrics.Metric> metrics = PrintMetrics.returnMetrics();
        for (PrintMetrics.Metric metric: metrics) {
            writeToDynamoDB(metric.serviceName, buildRecord(metric));
        }
    }

    public static Map<String, AttributeValue> buildRecord(PrintMetrics.Metric metric) {
        Map<String, AttributeValue> record = new HashMap<String, AttributeValue>();
        record.put("id", new AttributeValue(UUID.randomUUID().toString()));
        switch (metric.serviceName) {
            case "compression":
                record.put("image-size", new AttributeValue().withN(Integer.toString( ((byte[])metric.args[0]).length )));
                record.put("format", new AttributeValue((String)metric.args[1]));
                record.put("compression-factor", new AttributeValue()
                    .withN(String.format(Locale.US, "%.5f", (float)metric.args[2])));
                break;
            case "foxrabbit":
                record.put("world-size", new AttributeValue().withN(Integer.toString((int)metric.args[0])));
                record.put("scenario", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                record.put("generations", new AttributeValue().withN(Integer.toString((int)metric.args[2])));
                break;
            case "insectwar":
                record.put("max-rounds", new AttributeValue().withN(Integer.toString((int)metric.args[0])));
                record.put("army1-size", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                record.put("army2-size", new AttributeValue().withN(Integer.toString((int)metric.args[2])));
                break;
        }
        record.put("nblocks", new AttributeValue().withN(Long.toString((long)metric.nblocks)));
        record.put("nmethods", new AttributeValue().withN(Long.toString((long)metric.nmethods)));
        record.put("ninsts", new AttributeValue().withN(Long.toString((long)metric.ninsts)));
        return record;
    }

    public static void writeToDynamoDB(String tableName, Map<String, AttributeValue> newRecord) {
        System.out.println("Writing " + tableName + " " + newRecord);
        dynamoDB.putItem(new PutItemRequest(tableName, newRecord));
    }

    public static void queryDynamoDB() {

    }


    public static void start_dynamo_thread(String[] args, ExecutorService threadPool) {
        boolean noDynamo = false;
        for (String arg : args) {
            if (arg.toLowerCase().contains("nodynamo")) {
                noDynamo = true;
                break;
            }
        }

        if (!noDynamo) {
            DynamoClient.init(AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build()
            );
            DynamoClient.initServiceTables(new ArrayList<String>(
                Arrays.asList("compression", "foxrabbit", "insectwar")
            ));
            Runnable task = DynamoClient::updateDBWithInstrumentationMetrics;
            threadPool.execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    task.run();
                    try {
                        TimeUnit.MINUTES.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }
}
