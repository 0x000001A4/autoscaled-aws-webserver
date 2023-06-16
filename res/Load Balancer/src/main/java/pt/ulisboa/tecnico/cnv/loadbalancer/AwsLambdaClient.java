package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.amazonaws.services.lambda.model.AWSLambdaException;


import java.util.Map;

public class AwsLambdaClient {

    private static AWSLambda awsLambdaClient;

    public static void init() {
        awsLambdaClient = AWSLambdaClient.builder()
                            .withCredentials(new EnvironmentVariableCredentialsProvider())
                            .withRegion(System.getenv("AWS_DEFAULT_REGION"))
                            .build();
    }

    public static String getImageCompressionJson(Map<String, String> args) {
        return String.format("{ \"image\": %s, \"target-format\": %s, \"compression-factor\": %s }",
            args.get("image"), args.get("target-format"), args.get("compression-factor")
        );
    }

    public static String getFoxesAndRabbitsJson(Map<String, String> args) {
        return String.format("{ \"world\": %s, \"scenario\", %s, \"generations\": %s }",
            args.get("world"), args.get("scenario"), args.get("generations")
        );
    }

    public static String getInsectWarsJson(Map<String, String> args) {
        return String.format("{ \"max\": %s, \"army1\": %s, \"army2\": %s }",
            args.get("max"), args.get("army1"), args.get("army2")
        );
    }

    public static String getJSONFromArgs(String lambdaName, Map<String, String> args) {
        switch (lambdaName) {
            case "compressimage-lambda":
                return getImageCompressionJson(args);
            case "foxrabbit-lambda":
                return getFoxesAndRabbitsJson(args);
            case "insectwar-lambda":
                return getInsectWarsJson(args);
            default:
                System.out.println("Incorrect lambda name");
                return "";
        }
    }

	public static byte[] invokeLambda(String lambdaName, Map<String, String> reqArgs) {
       try {
           String json = getJSONFromArgs(lambdaName, reqArgs);
           ByteBuffer payload = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)) ;

           InvokeRequest request = new InvokeRequest()
                                    .withFunctionName(lambdaName)
                                    .withPayload(payload);

           return awsLambdaClient.invoke(request)
                    .getPayload()
                    .array();

       } catch(AWSLambdaException e) {
           System.err.println(e.getMessage());
           System.exit(1);
           return null;
       }
   }
}
