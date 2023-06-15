package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FoxRabbitCE {

    private static Map<String, RegressionCE> regEstimators = new ConcurrentHashMap<>();

    public static void updateRegParameters(Map<Entry<String, Double>, Double> featuresComplexities) {
        List<Entry<String, Double>> features = new ArrayList<>();
        List<Double> complexities = new ArrayList<>();

        featuresComplexities.forEach((feature, complexity) -> {
            features.add(feature);
            complexities.add(complexity);
        });

        updateRegParameters(complexities, features);
    }

    public static void updateRegParameters(List<Double> complexities, List<Entry<String, Double>> features) {

        for (RegressionCE regEstimator: regEstimators.values()) {
            regEstimator.clearModelData();
        }

        for (int i = 0; i < features.size(); i++) {
            String key = String.format("foxrabbit-%s", features.get(i).getKey());
            System.out.println(String.format("Updating/Creating %s", key));
            regEstimators.putIfAbsent(key, new RidgeRegressionCE(key));
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
        try {
            String key = String.format("foxrabbit-%s", reqFeatures.get("world")+reqFeatures.get("scenario"));
            String generations = reqFeatures.get("generations");
            RegressionCE regEstimator = regEstimators.get(key);
            System.out.println(String.format("Estimating complexity for %s with generations: %s", key, generations));
            return regEstimator.estimateComplexity(new double[] { Double.parseDouble(generations) });
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }
}
