package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

public class ImageCompressionCE {
    
    private static OLSMultipleLinearRegression regModel = new OLSMultipleLinearRegression();
    private static double[] regParameters;

    public static void updateRegParameters(List<Double> complexities, List<List<Double>> features) {
        regParameters = RegressionComplexityEstimator.updateRegParameters(regModel, complexities, features);
    }

    public static Double getTargetFormatInDouble(String targetFormat) {
        return 1.0; // TODO
    }

    public static double estimateComplexity(Map<String, String> reqFeatures) {
        return RegressionComplexityEstimator.estimateComplexity(
            regParameters, 
            reqFeatures.keySet().stream()
                .mapToDouble(arg -> arg.equals("target-format") ? getTargetFormatInDouble(arg) : Double.parseDouble(arg))
                .toArray()
        );
    }
}
