package pt.ulisboa.tecnico.cnv.loadbalancer.ComplexityEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class RegressionCE {

    protected List<Double> accComplexities = new ArrayList<>();
    protected List<List<Double>> accFeatures = new ArrayList<>();
    protected String serviceName;

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void clearModelData() {
        accComplexities.clear();
        accFeatures.clear();
    }

    public abstract void addDataToModel(Double complexity, List<Double> features);

    public abstract void addDataToModel(List<Double> complexity, List<List<Double>> features);

    public abstract void updateModelParameters(Map<List<Double>, Double> featuresComplexities);

    public abstract void updateModelParameters(List<Double> complexities, List<List<Double>> features);

    public abstract void updateParameters();

    public abstract Double estimateComplexity(double[] reqArgs);

}
