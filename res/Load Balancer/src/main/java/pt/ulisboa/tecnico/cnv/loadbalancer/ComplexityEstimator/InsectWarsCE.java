package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import smile.regression.RidgeRegression;

public class InsectWarsCE {
    
    private static RidgeRegressionCE regEstimator = new RidgeRegressionCE();
    
    public static void updateRegParameters(List<Double> complexities, List<List<Double>> features) {
        regEstimator.updateModelParameters(complexities, features);
    }

    public static double estimateComplexity(Map<String, String> reqFeatures) {
        return regEstimator.estimateComplexity(new double[] { 
                Double.parseDouble(reqFeatures.get("max")), 
                Math.abs(
                    Double.parseDouble(reqFeatures.get("army1")) - Double.parseDouble(reqFeatures.get("army2"))
                ) 
            }
        );
    }
}
