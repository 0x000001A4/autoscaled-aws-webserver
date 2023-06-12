package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.List;

public class FoxRabbitCE {

    private static Map<String, RegressionCE> regEstimators = new ConcurrentHashMap<String, RegressionCE>();

    public static void updateRegParameters(List<Double> complexities, List<Entry<String, Double>> features) {
        for (RegressionCE regEstimator: regEstimators.values()) {
            regEstimator.clearModelData();
        }

        for (int i = 0; i < features.size(); i++) {
            String key = features.get(i).getKey();
            regEstimators.putIfAbsent(key, new RegressionCE());
            regEstimators.get(key).addDataToModel(
                complexities.get(i),
                Arrays.asList(features.get(i).getValue())
            );
        }
        for (RegressionCE regEstimator: regEstimators.values()) {
            regEstimator.updateParameters();
        }
    }

    public static double estimateComplexity(Map<String, String> reqFeatures) {
        RegressionCE regEstimator = regEstimators.get(reqFeatures.get("world") + reqFeatures.get("scenario"));
        return regEstimator.estimateComplexity(new double[]
            { Double.parseDouble(reqFeatures.get("generations")) }
        );
    }
}
