package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.AWSLambdaException;


public class AwsLambdaClient {

    private static AWSLambda awsLambdaClient;

    public static void init() {
        awsLambdaClient = AWSLambdaClient.builder()
                            .withCredentials(new EnvironmentVariableCredentialsProvider())
                            .withRegion(System.getenv("AWS_DEFAULT_REGION"))
                            .build();
    }

    public static String getImageCompressionJson(Object[] args) {
        return String.format("{ image-size: %s, target-format: %s, compression-factor: %s }",
            ((Integer)args[0]).toString(), (String)args[1], ((Float)args[2]).toString()
        );
    }

    public static String getFoxesAndRabbitsJson(Object[] args) {
        return String.format("{ world: %s, scenario, %s, generations: %s }",
            ((Integer)args[0]).toString(), ((Integer)args[1]).toString(), ((Integer)args[2]).toString()
        );
    }

    public static String getInsectWarsJson(Object[] args) {
        return String.format("{ max: %s, army1: %s, army2: %s }",
            ((Integer)args[0]).toString(), ((Integer)args[1]).toString(), ((Integer)args[2]).toString()
        );
    }

    public static String getJSONFromArgs(String lambdaName, Object[] args) {
        switch (lambdaName) {
            case "image-compression-lambda":
                return getImageCompressionJson(args);
            case "foxes-and-rabbits-lambda":
                return getFoxesAndRabbitsJson(args);
            case "insect-wars-lambda":
                return getInsectWarsJson(args);
            default:
                System.out.println("Incorrect lambda name");
                return "";
        }
    }

	public static void invokeLambda(String lambdaName, Object[] args) {
       try {
           String json = getJSONFromArgs(lambdaName, args);
           ByteBuffer payload = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)) ;

           InvokeRequest request = new InvokeRequest()
                                    .withFunctionName(lambdaName)
                                    .withPayload(payload);

           InvokeResult res = awsLambdaClient.invoke(request);
           String value = StandardCharsets.UTF_8.decode(res.getPayload()).toString();
           System.out.println(value);

       } catch(AWSLambdaException e) {
           System.err.println(e.getMessage());
           System.exit(1);
       }
   }
}
