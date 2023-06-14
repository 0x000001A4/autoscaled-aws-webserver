package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.ArrayList;
import java.util.List;

import smile.regression.RidgeRegression;

public class RidgeRegressionCE {
    
    private RidgeRegression regModel;
    private List<Double> accComplexities = new ArrayList<>();
    private List<List<Double>> accFeatures = new ArrayList<>(); 
    private final double lambda = 0.1;

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
       

    public void updateModelParameters(List<Double> complexities, List<List<Double>> features) {
        if (complexities.size() == 0 || features.size() == 0) return;
        double[][] x = features
            .stream()
            .map(feature -> feature.stream().mapToDouble(Double::doubleValue).toArray())
            .toArray(double[][]::new);
            
        double[] y = complexities.stream().mapToDouble(Double::doubleValue).toArray();

        regModel = new RidgeRegression(x, y, lambda);
    }

    public void updateParameters() {
        
        if (accComplexities.size() <= 1 || accFeatures.size() <= 1) {
            System.out.println("Not enough data points for Ridge Regression");
            return;
        }
    
        // Check that the number of features doesn't exceed 2.
        if (accFeatures.stream().anyMatch(feature -> feature.size() > 2)) {
            System.out.println("Too many features for Ridge Regression");
            return;
        }

        // Check that the number of data points matches the number of targets.
        if (accComplexities.size() != accFeatures.size()) {
            System.out.println("Mismatch between number of data points and targets for Ridge Regression");
            return;
        }
            
        double[][] x = accFeatures
            .stream()
            .map(feature -> feature.stream().mapToDouble(Double::doubleValue).toArray())
            .toArray(double[][]::new);

        double[] y = accComplexities.stream().mapToDouble(Double::doubleValue).toArray();

        regModel = new RidgeRegression(x, y, lambda);
    }

    public Double estimateComplexity(double[] reqArgs) {
        return regModel.predict(reqArgs);
    }
}
