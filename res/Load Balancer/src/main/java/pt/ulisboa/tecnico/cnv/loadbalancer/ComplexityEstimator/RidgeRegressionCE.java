package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import smile.regression.RidgeRegression;

public class RidgeRegressionCE extends RegressionCE {

    protected RidgeRegression regModel;
    private final double lambda = 0.1;

    public RidgeRegressionCE(String _serviceName) {
        super.serviceName = _serviceName;
    }

    public void clearModelData() {
        accComplexities.clear();
        accFeatures.clear();
    }

    public void addDataToModel(Double complexity, List<Double> features) {
        accComplexities.add(complexity);
        accFeatures.add(features);
    }

    public void addDataToModel(List<Double> complexity, List<List<Double>> features) {
        accComplexities.addAll(complexity);
        accFeatures.addAll(features);
    }


    public void updateModelParameters(Map<List<Double>, Double> featuresComplexities) {
        List<List<Double>> features = new ArrayList<>();
        List<Double> complexities = new ArrayList<>();

        featuresComplexities.forEach((feature, complexity) -> {
            features.add(feature);
            complexities.add(complexity);
        });

        updateModelParameters(complexities, features);
    }

    public void updateModelParameters(List<Double> complexities, List<List<Double>> features) {
        if (complexities.size() == 0 || features.size() == 0) return;
        double[][] x = features
            .stream()
            .map(feature -> feature.stream().mapToDouble(Double::doubleValue).toArray())
            .toArray(double[][]::new);

        double[] y = complexities.stream().mapToDouble(Double::doubleValue).toArray();

        System.out.println(String.format("*\nUpdating parameters in service: %s  and listing parameters:", super.serviceName));
        System.out.println(String.format("%s model Features: %s", super.serviceName, features.toString()));
        System.out.println(String.format("%s model Complexities: %s", super.serviceName, complexities.toString()));
        System.out.println("*");

        regModel = new RidgeRegression(x, y, lambda);
    }

    public void updateParameters() {

        if (accComplexities.size() <= 1 || accFeatures.size() <= 1) {
            System.out.println(String.format("Error updating parameters in service: %s", super.serviceName));
            System.out.println("Not enough data points for Ridge Regression");
            return;
        }

        // Check that the number of features doesn't exceed 2.
        if (accFeatures.stream().anyMatch(feature -> feature.size() > 2)) {
            System.out.println(String.format("Error updating parameters in service: %s", super.serviceName));
            System.out.println("Too many features for Ridge Regression");
            return;
        }

        // Check that the number of data points matches the number of targets.
        if (accComplexities.size() != accFeatures.size()) {
            System.out.println(String.format("Error updating parameters in service: %s", super.serviceName));
            System.out.println("Mismatch between number of data points and targets for Ridge Regression");
            return;
        }

        System.out.println(String.format("*\nUpdating parameters in service: %s  and listing parameters:", super.serviceName));
        System.out.println(String.format("%s model Features: %s", super.serviceName, accFeatures.toString()));
        System.out.println(String.format("%s model Complexities: %s", super.serviceName, accComplexities.toString()));
        System.out.println("*");


        double[][] x = accFeatures
            .stream()
            .map(feature -> feature.stream().mapToDouble(Double::doubleValue).toArray())
            .toArray(double[][]::new);

        double[] y = accComplexities.stream().mapToDouble(Double::doubleValue).toArray();

        /*
        System.out.println();
        for (double[] arr_x: x) {
            for (double el: arr_x) {
                System.out.print(el+" ");
            }
            System.out.println();
        }

        for (double el: y) {
            System.out.println(el);
        }
        System.out.println();
        */

        regModel = new RidgeRegression(x, y, lambda);
    }

    public Double estimateComplexity(double[] reqArgs) {
        try {
            System.out.println(String.format("Estimating Complexity for %s with args: ", super.serviceName, reqArgs.toString()));
            return regModel.predict(reqArgs);
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }
}
