package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.List;

public class FoxRabbitCE {

    private static Map<String, RidgeRegressionCE> regEstimators = new ConcurrentHashMap<String, RidgeRegressionCE>();

    public static void updateRegParameters(List<Double> complexities, List<Entry<String, Double>> features) {
        System.out.println(complexities.toString());
        System.out.println(features.toString());
        
        for (RidgeRegressionCE regEstimator: regEstimators.values()) {
            regEstimator.clearModelData();
        }

        for (int i = 0; i < features.size(); i++) {
            String key = features.get(i).getKey();
            regEstimators.putIfAbsent(key, new RidgeRegressionCE());
            regEstimators.get(key).addDataToModel(
                complexities.get(i),
                Arrays.asList(features.get(i).getValue())
            );
        }
        for (RidgeRegressionCE regEstimator: regEstimators.values()) {
            regEstimator.updateParameters();
        }
    }

    public static double estimateComplexity(Map<String, String> reqFeatures) {
        RidgeRegressionCE regEstimator = regEstimators.get(reqFeatures.get("world") + reqFeatures.get("scenario"));
        return regEstimator.estimateComplexity(new double[]
            { Double.parseDouble(reqFeatures.get("generations")) }
        );
    }
}
