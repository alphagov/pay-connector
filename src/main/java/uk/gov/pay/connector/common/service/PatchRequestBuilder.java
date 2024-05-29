package uk.gov.pay.connector.common.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PatchRequestBuilder {
    private final Map<String, String> patchRequestMap;
    private List<String> validOps;
    private Set<String> validPaths;

    private enum PatchField {
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

    public static PatchRequestBuilder aPatchRequestBuilder(Map<String, String> patchRequestMap) {
        return new PatchRequestBuilder(patchRequestMap);
    }

    private PatchRequestBuilder(Map<String, String> patchRequestMap) {
        this.patchRequestMap = patchRequestMap;
    }

    public PatchRequestBuilder withValidOps(List<String> validOps) {
        this.validOps = validOps;
        return this;
    }

    public PatchRequestBuilder withValidPaths(Set<String> validPaths) {
        this.validPaths = validPaths;
        return this;
    }

    public PatchRequest build() {
        return new PatchRequest(
                patchRequestMap.get(PatchField.OP.toString()),
                patchRequestMap.get(PatchField.PATH.toString()),
                patchRequestMap.get(PatchField.VALUE.toString()),
                validOps,
                validPaths
        );
    }

    public class PatchRequest {
        private String op;
        private String path;
        private String value;
        private List<String> validOps;
        private Set<String> validPaths;

        private PatchRequest(String op, String path, String value, List<String> validOps, Set<String> validPaths) throws IllegalArgumentException{
            this.validOps = validOps;
            this.validPaths = validPaths;
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

        private boolean isValidOp(String op) {
            return Optional.of(validOps)
                    .map(validOps -> validOps.contains(op))
                    .orElse(true);
        }

        private boolean isValidPath(String path) {
            return Optional.of(validPaths)
                    .map(validOps -> validOps.contains(path))
                    .orElse(true);
        }
    }
}
