package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

public class InsectWarsCE {
    
    private static OLSMultipleLinearRegression regModel = new OLSMultipleLinearRegression();
    private static double[] regParameters;

    public static void updateRegParameters(List<Double> complexities, List<List<Double>> features) {
        regParameters = RegressionComplexityEstimator.updateRegParameters(regModel, complexities, features);
    }

    public static double estimateComplexity(Map<String, String> reqFeatures) {
        return RegressionComplexityEstimator.estimateComplexity(
            regParameters, 
            new double[] { 
                Double.parseDouble(reqFeatures.get("max")), 
                Math.abs(
                    Double.parseDouble(reqFeatures.get("army1")) - Double.parseDouble(reqFeatures.get("army2"))
                ) 
            }
        );
    }
}
