package pt.ulisboa.tecnico.cnv.webserver;

import com.amazonaws.services.ec2.model.Instance;

public class Worker implements Comparable<Worker> {
    
    private String id;
    private Double load;
    private Double avgCPUUtilization;
    private Instance ec2Instance;

    public Worker(String id, Instance ec2Instance) {
        this.id = id;
        this.ec2Instance = ec2Instance;
        this.avgCPUUtilization = 0.0;
        this.load = 0.0;
    }

    @Override
    public int compareTo(Worker other) {
        return Double.compare(other.getLoad(), this.getLoad());
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

    synchronized public void loadWork(Double instrs) {
        load += instrs;
    }

    synchronized public void unloadWork(Double instrs) {
        load -= instrs;
    }


    public void setAvgCPUUtilization(Double avg) {
        avgCPUUtilization = avg;
    }

    public Double getLoad() {
        return load;
    }

    public Double getAvgCPUUtilization() {
        return avgCPUUtilization;
    }
}
