package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import pt.ulisboa.tecnico.cnv.loadbalancer.Exceptions.InvalidArgumentException;

public class ComplexityEstimator {

    public static final Double MIN_COMPLEXITY_FOR_LAMBDA_INVOCATION = 10.0;

    public static Map<String,String> getReqFeatures(URI requestURI) {
        String query = requestURI.getQuery();
        Map<String, String> features = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            features.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return features;
    }

    public static Map<String, String> getInsectWarFeatures(URI requestURI) throws InvalidArgumentException {
        Map<String, String> features = getReqFeatures(requestURI);
        try {
            Integer max = Integer.parseInt(features.get("max"));
            Integer army1 = Integer.parseInt(features.get("army1"));
            Integer army2 = Integer.parseInt(features.get("army2"));
            if (features.size() == 3 && army1 >= 0 && army2 >= 0 && max >= 1)
                return features;
        } catch (Exception e) {}
        throw new InvalidArgumentException(String.format(
            "InvalidArgumentException in insect war. Features: %s", features.toString()
        ));
    }


    public static Map<String, String> getFoxRabbitFeatures(URI requestURI) throws InvalidArgumentException {
        Map<String, String> features = getReqFeatures(requestURI);
        try {
            Integer world = Integer.parseInt(features.get("world"));
            Integer scenario = Integer.parseInt(features.get("scenario"));
            Integer generations = Integer.parseInt(features.get("generations"));
            if (features.size() == 3 && 
                world >= 1 && world <= 4 && 
                scenario >= 1 && scenario <= 3 &&
                generations > 0)
                return features;
        } catch (Exception e) {}
        throw new InvalidArgumentException(String.format(
            "InvalidArgumentException in fox rabbit. Features: %s", features.toString()
        ));
    }

    public static Map<String, String> getImgCompressionFeatures(String body) throws InvalidArgumentException {
        Map<String, String> features = new HashMap<>();
        String[] entries = body.split(";");
        for (String entry: entries) {
            String[] kvPair = entry.split(":");
            if (kvPair.length == 2) {
                String key = kvPair[0].trim();
                String value = kvPair[1].trim();
                switch (key) {
                    case "targetFormat":
                        //features.put("target-format", value);
                        break;
                    case "compressionFactor":
                        features.put("compression-factor", value);
                        break;
                    case "data":
                        features.put("image-size", String.valueOf(value.length()));
                        features.put("image", value);
                        break;
                    default:
                        break;
                }
            } else {
                kvPair = entry.split(",");
                if (kvPair.length == 2) {
                    String key = kvPair[0].trim();
                    String value = kvPair[1].trim();
                    switch (key) {
                        case "base64":
                            features.put("image-size", String.valueOf(value.length()));
                            features.put("image", value);
                            break;
                        default:
                            break;
                    }
                } else {
                    System.out.println("kvPair not 2, printing:");
                    for (String kv : kvPair) {
                        System.out.println(kv);
                    }
                }
            }
        }
        try {
            Double cf = Double.parseDouble(features.get("compression-factor"));
            if (features.size() == 4 && features.containsKey("image-size") 
                && features.containsKey("image") && cf >= 0 && cf <= 1)
                return features;
        } catch (Exception e) {}
        throw new InvalidArgumentException(String.format(
            "InvalidArgumentException in image compression. Features: %s", features
        ));
    }

    public static Entry<Double,Map<String,String>> unfoldRequest(URI requestURI, String body) throws InvalidArgumentException {
        try {
            switch (requestURI.getPath()) {
                case "/compressimage": {
                    Map<String, String> requestFeatures = getImgCompressionFeatures(body);
                    System.out.println(requestFeatures);
                    return new SimpleEntry<Double, Map<String,String>>(
                        ImageCompressionCE.estimateComplexity(requestFeatures),
                        requestFeatures
                    );
                }
                case "/simulate": {
                    Map<String, String> requestFeatures = getFoxRabbitFeatures(requestURI);
                    System.out.println(requestFeatures);
                    return new SimpleEntry<Double, Map<String,String>>(
                        FoxRabbitCE.estimateComplexity(requestFeatures),
                        requestFeatures
                    );
                }
                case "/insectwar": {
                    Map<String, String> requestFeatures = getReqFeatures(requestURI);
                    System.out.println(requestFeatures);
                    return new SimpleEntry<Double, Map<String,String>>(
                        InsectWarsCE.estimateComplexity(requestFeatures),
                        requestFeatures
                    );
                }
                default:
                    System.out.println("Error: No service with this path");
                    return null;
            }
        } catch (Exception e) {
            System.out.println("Failed to estimate request complexity");
            e.printStackTrace();
            return null;
        }
    }

}
