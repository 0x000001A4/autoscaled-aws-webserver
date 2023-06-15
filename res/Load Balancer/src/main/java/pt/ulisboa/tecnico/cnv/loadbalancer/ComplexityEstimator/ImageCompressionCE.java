package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.List;
import java.util.Map;

public class ImageCompressionCE {

    private static RegressionCE regEstimator = new RidgeRegressionCE("compression");

    public static void updateRegParameters(Map<List<Double>, Double> featuresComplexities) {
        regEstimator.updateModelParameters(featuresComplexities);
    }

    public static void updateRegParameters(List<Double> complexities, List<List<Double>> features) {
        regEstimator.updateModelParameters(complexities, features);
    }

    public static Double getTargetFormatInDouble(String targetFormat) {
        return 1.0; // TODO
    }

    public static double estimateComplexity(Map<String, String> reqFeatures) {
        return regEstimator.estimateComplexity(reqFeatures
                .keySet()
                .stream()
                .mapToDouble(arg -> Double.parseDouble(reqFeatures.get(arg)))
                .toArray()
        );
    }
}
