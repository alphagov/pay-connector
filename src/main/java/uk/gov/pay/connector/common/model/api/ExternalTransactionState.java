package uk.gov.pay.connector.common.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExternalTransactionState {

    private final String value;
    private final boolean finished;
    private final String code;
    private final String message;
    private final Boolean canRetry;

    public ExternalTransactionState(String value, boolean finished) {
        this.value = value;
        this.finished = finished;
        this.code = null;
        this.message = null;
        this.canRetry = null;
    }

    public ExternalTransactionState(String value, boolean finished, String code, String message) {
        this.value = value;
        this.finished = finished;
        this.code = code;
        this.message = message;
        this.canRetry = null;
    }

    public ExternalTransactionState(String value, boolean finished, String code, String message, boolean canRetry) {
        this.value = value;
        this.finished = finished;
        this.code = code;
        this.message = message;
        this.canRetry = canRetry;
    }

    @Schema(example = "success")
    public String getStatus() {
        return value;
    }

    @Schema(example = "true")
    public boolean isFinished() {
        return finished;
    }

    @Schema(example = "P0010", description = "Error code for failed payments")
    public String getCode() {
        return code;
    }

    @Schema(example = "Payment method rejected", description = "Message describing error code if payment failed")
    public String getMessage() {
        return message;
    }

    @Schema(example = "true", description = "If a failed payment, whether it may be possible to retry it")
    public Boolean getCanRetry() {
        return canRetry;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof ExternalTransactionState) {
            var that = (ExternalTransactionState) other;
            return finished == that.finished
                    && Objects.equals(code, that.code)
                    && Objects.equals(message, that.message)
                    && Objects.equals(canRetry, that.canRetry);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, finished, code, message, canRetry);
    }

    @Override
    public String toString() {
        return "ExternalTransactionState{" +
                "value='" + value + '\'' +
                ", finished=" + finished +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", can_retry='" + canRetry+ '\'' +
                '}';
    }
}
