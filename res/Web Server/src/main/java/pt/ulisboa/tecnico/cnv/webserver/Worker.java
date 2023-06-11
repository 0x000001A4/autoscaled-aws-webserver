package pt.ulisboa.tecnico.cnv.webserver;

import com.amazonaws.services.ec2.model.Instance;

public class Worker {
    
    private String id;
    private Double instructionsToComplete;
    private Double avgCPUUtilization;
    private Instance ec2Instance;

    public Worker(String id, Instance ec2Instance) {
        this.id = id;
        this.ec2Instance = ec2Instance;
        this.avgCPUUtilization = 0.0;
        this.instructionsToComplete = 0.0;
    }

    public Instance getEC2Instance() {
        return ec2Instance;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setInstructionsToComplete(Double instrToEnd) {
        instructionsToComplete = instrToEnd;
    }

    public void setAvgCPUUtilization(Double avg) {
        avgCPUUtilization = avg;
    }

    public Double getInstructionsToComplete() {
        return instructionsToComplete;
    }

    public Double getAvgCPUUtilization() {
        return avgCPUUtilization;
    }
}
