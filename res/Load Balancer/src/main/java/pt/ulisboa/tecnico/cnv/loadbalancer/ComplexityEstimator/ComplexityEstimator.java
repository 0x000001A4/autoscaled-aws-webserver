package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ComplexityEstimator {

    public static Map<String, String> getReqFeatures(URI requestURI) {
        String query = requestURI.getQuery(); // "max=1&army1=10&army2=10"
        Map<String, String> features = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            features.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return features;
    }

    public static Map<String, String> getImgCompressionFeatures(String body) {
        Map<String, String> features = new HashMap<>();
        String[] entries = body.split(";");
        for (String entry: entries) {
            String[] kvPair = entry.split(":");
            if (kvPair.length == 2) {
                String key = kvPair[0].trim();
                String value = kvPair[1].trim();
                switch (key) {
                    case "targetFormat":
                        features.put("target-format", value);
                        break;
                    case "compressionFactor":
                        features.put("compression-factor", value);
                        break;
                    case "data":
                        features.put("image-size", String.valueOf(value.length()));
                        break;
                    default:
                        break;
                }
            }
        }
        return features;
    }

    public static Double estimateRequestComplexity(URI requestURI, String body) {
        switch (requestURI.getPath()) {
            case "/compression":
                return ImageCompressionCE.estimateComplexity(getImgCompressionFeatures(body));
            case "/foxrabbit":
                return FoxRabbitCE.estimateComplexity(getReqFeatures(requestURI));
            case "/insectwar":
                return InsectWarsCE.estimateComplexity(getReqFeatures(requestURI));
            default:
                System.out.println("Error: No service with this path");
                return 0.0;
        }
    }

}
