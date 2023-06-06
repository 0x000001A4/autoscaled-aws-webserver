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
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import pt.ulisboa.tecnico.cnv.javassist.tools.PrintMetrics;

public class DynamoClient {

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
        List<PrintMetrics.Metric> metrics = PrintMetrics.returnMetrics();
        for (PrintMetrics.Metric metric: metrics) {
            writeToDynamoDB(metric.serviceName, buildRecord(metric));
        }
    }

    public static Map<String, AttributeValue> buildRecord(PrintMetrics.Metric metric) {
        Map<String, AttributeValue> record = new HashMap<String, AttributeValue>();
        record.put("id", new AttributeValue(UUID.randomUUID().toString()));
        switch (metric.serviceName) {
            case "compress-image":
                record.put("image-size", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                record.put("format", new AttributeValue((String)metric.args[2]));
                record.put("compression-factor", new AttributeValue().withN(Double.toString((float)metric.args[3])));
                break;
            case "foxes-and-rabbits":
                record.put("world-size", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                record.put("scenario", new AttributeValue().withN(Integer.toString((int)metric.args[2])));
                record.put("generations", new AttributeValue().withN(Integer.toString((int)metric.args[3])));
                break;                        
            case "war-simulation":
                record.put("max-rounds", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                record.put("army1-size", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                record.put("army2-size", new AttributeValue().withN(Integer.toString((int)metric.args[1])));
                break;
        }
        record.put("nblocks", new AttributeValue().withN(Long.toString((long)metric.nblocks)));
        record.put("nmethods", new AttributeValue().withN(Long.toString((long)metric.nmethods)));
        record.put("ninsts", new AttributeValue().withN(Long.toString((long)metric.ninsts)));
        return record;
    }

    public static void writeToDynamoDB(String tableName, Map<String, AttributeValue> newRecord) {
        dynamoDB.putItem(new PutItemRequest(tableName, newRecord));
    }

    public static void queryDynamoDB() {

    }
}
