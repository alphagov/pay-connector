package uk.gov.pay.connector.common.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class ExternalTransactionState {

    private final String value;
    private final boolean finished;
    private final String code;
    private final String message;

    public ExternalTransactionState(String value, boolean finished) {
        this.value = value;
        this.finished = finished;
        this.code = null;
        this.message = null;
    }

    public ExternalTransactionState(String value, boolean finished, String code, String message) {
        this.value = value;
        this.finished = finished;
        this.code = code;
        this.message = message;
    }

    public String getStatus() {
        return value;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExternalTransactionState)) return false;

        ExternalTransactionState that = (ExternalTransactionState) o;

        if (finished != that.finished) return false;
        if (!value.equals(that.value)) return false;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        return message != null ? message.equals(that.message) : that.message == null;
    }

    @Override
    public int hashCode() {
        int result = value.hashCode();
        result = 31 * result + (finished ? 1 : 0);
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ExternalTransactionState{" +
                "value='" + value + '\'' +
                ", finished=" + finished +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
