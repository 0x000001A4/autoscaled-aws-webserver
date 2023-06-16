package pt.ulisboa.tecnico.cnv.loadbalancer.Exceptions;

public class NoAvailableWorkerException extends Exception {
    
    private String message;

    public NoAvailableWorkerException() {
        
    }
    
    public NoAvailableWorkerException(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
