package uk.gov.pay.connector.matcher;

import org.apache.commons.lang3.ObjectUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.time.ZonedDateTime;
import java.util.Map;

import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;

public class TransactionEventMatcher extends TypeSafeMatcher<Map<String, Object>> {

    static public class State {
        private final String status;
        private final String finished;
        private final String code;
        private final String message;

        public State(String status, String finished, String code, String message) {
            this.status = status;
            this.finished = finished;
            this.code = code;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getFinished() {
            return finished;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

    }

    private String type;
    private State state;
    private String amount;
    private String updated;
    private String refundReference;
    private String refundSubmittedBy;

    public TransactionEventMatcher(String type, State state, String amount, ZonedDateTime updated, String refundReference, String submittedBy) {
        this.type = type;
        this.state = state;
        this.amount = amount;
        this.updated = ISO_INSTANT_MILLISECOND_PRECISION.format(updated);
        this.refundReference = refundReference;
        this.refundSubmittedBy = submittedBy;
    }

    public TransactionEventMatcher(String type, State state, String amount, ZonedDateTime updated) {
        this(type, state, amount, updated, null, null);
    }

    static public State withState(String status, String finished) {
        return new State(status, finished, null, null);
    }

    static public State withState(String status, String finished, String code, String message) {
        return new State(status, finished, code, message);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("{amount=").appendValue(amount).appendText(", ");
        description.appendText("refund_reference=").appendValue(refundReference).appendText(", ");
        description.appendText("submitted_by=").appendValue(refundSubmittedBy).appendText(", ");
        description.appendText("state={");
        description.appendText("finished=").appendValue(state.getFinished()).appendText(", ");
        description.appendText("status=").appendValue(state.getStatus()).appendText(", ");
        description.appendText("code=").appendValue(state.getCode()).appendText(", ");
        description.appendText("message=").appendValue(state.getMessage()).appendText("}, ");
        description.appendText("type=").appendValue(type).appendText(", ");
        description.appendText("updated=").appendValue(updated).appendText("}");
    }

    @Override
    protected boolean matchesSafely(Map<String, Object> record) {
        boolean stateMatches;

        if (record.get("state") == null) {
            stateMatches = (state == null);
        } else {
            stateMatches = ObjectUtils.equals(((Map)record.get("state")).get("status"), state.getStatus()) &&
                    ObjectUtils.equals(((Map)record.get("state")).get("finished").toString(), state.getFinished()) &&
                    ObjectUtils.equals(((Map)record.get("state")).get("code"), state.getCode()) &&
                    ObjectUtils.equals(((Map)record.get("state")).get("message"), state.getMessage());
        }

        return stateMatches &&
                ObjectUtils.equals(record.get("refund_reference"), refundReference) &&
                ObjectUtils.equals(record.get("type"), type) &&
                ObjectUtils.equals(record.get("amount"), Integer.valueOf(amount)) &&
                ObjectUtils.equals(record.get("updated"), updated) &&
                ObjectUtils.equals(record.get("submitted_by"), refundSubmittedBy);
    }
}
