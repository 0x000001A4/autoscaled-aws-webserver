package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.List;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

public class RegressionComplexityEstimator {

    public static double[] updateRegParameters(
        OLSMultipleLinearRegression regModel, List<Double> complexities, List<List<Double>> features
    ) {
        regModel.newSampleData(
            complexities
                .stream()
                .mapToDouble(Double::doubleValue)
                .toArray(), 
            features
                .stream()
                .map(feature -> feature.stream().mapToDouble(Double::doubleValue).toArray())
                .toArray(double[][]::new)
        );
        return regModel.estimateRegressionParameters();
    }

    public static Double estimateComplexity(double[] regParameters, double[] reqFeatures) {
        double estimate = regParameters[0];
        for (int i = 1; i < regParameters.length; i++) {
            estimate += regParameters[i]*reqFeatures[i-1];
        }
        return estimate;
    }
}
