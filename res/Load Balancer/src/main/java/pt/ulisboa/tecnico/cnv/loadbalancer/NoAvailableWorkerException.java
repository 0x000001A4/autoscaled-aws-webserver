package pt.ulisboa.tecnico.cnv.loadbalancer;

public class NoAvailableWorkerException extends Exception {
    
    private String message;
    
    public NoAvailableWorkerException(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
