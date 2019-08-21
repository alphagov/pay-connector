package uk.gov.pay.connector.charge.model.telephone;

public class State {

    private String status;

    private Boolean finished;

    private String message;

    private String code;

    public State() {
        // For Jackson serialization
    }
    
    public State(String status, Boolean finished, String message) {
        this.status = status;
        this.finished = finished;
        this.message = message;
    }

    public State(String status, Boolean finished, String message, String code) {
        this.status = status;
        this.finished = finished;
        this.message = message;
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getFinished() {
        return finished;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
