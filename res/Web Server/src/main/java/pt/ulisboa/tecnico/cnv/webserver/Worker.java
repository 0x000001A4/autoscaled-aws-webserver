package pt.ulisboa.tecnico.cnv.webserver;

import com.amazonaws.services.ec2.model.Instance;

public class Worker implements Comparable<Worker> {

    private String id;
    private Long load;
    private Double avgCPUUtilization;
    private Instance ec2Instance;

    public Worker(String id, Instance ec2Instance) {
        this.id = id;
        this.ec2Instance = ec2Instance;
        this.avgCPUUtilization = 0.0;
        this.load = 0l;
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

    synchronized public void loadWork(Double work) {
        load += work.longValue();
    }

    synchronized public void unloadWork(Double work) {
        load -= work.longValue();
    }


    public void setAvgCPUUtilization(Double avg) {
        avgCPUUtilization = avg;
    }

    public Long getLoad() {
        return load;
    }

    public Double getAvgCPUUtilization() {
        return avgCPUUtilization;
    }

    @Override
    public String toString() {
        return String.format("Worker: %s  has Load: %s  and  AverageCPUUtilization: %s",
            this.id, this.load, this.avgCPUUtilization);
    }
}
