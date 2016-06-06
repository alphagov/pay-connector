package uk.gov.pay.connector.model;

import java.util.Arrays;
import java.util.List;

public class PatchRequest {
    private String op;
    private String path;
    private String value;

    public enum PatchField {
        OP("op"),
        PATH("path"),
        VALUE("value");

        private String value;

        PatchField(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }

    private static final List<String> validOps = Arrays.asList("add", "remove", "replace", "copy", "move", "test");

    public PatchRequest(String op, String path, String value) throws IllegalArgumentException {
        setOp(op);
        setPath(path);
        this.value = value;
    }

    private void setOp(String op) throws IllegalArgumentException {
        if (isValidOp(op)) {
            this.op = op;
        }else {
            throw new IllegalArgumentException("Invalid operation parameter in patch request:" + op);
        }
    }

    private void setPath(String path) throws IllegalArgumentException {
        if (isValidPath(path)) {
            this.path = path;
        } else {
            throw new IllegalArgumentException("Invalid path parameter in patch request:" + path);
        }
    }

    public String getPath() {
        return path;
    }

    public String getValue() {
        return value;
    }

    protected  boolean isValidOp(String op) {
        return validOps.contains(op);
    }

    protected  boolean isValidPath(String path) {
        return true;
    }
}
