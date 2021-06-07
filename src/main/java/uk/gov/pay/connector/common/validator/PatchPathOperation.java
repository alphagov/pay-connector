package uk.gov.pay.connector.common.validator;

import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;

import java.util.Objects;

public class PatchPathOperation {
    private final String path;
    private final JsonPatchOp operation;

    public PatchPathOperation(String path, JsonPatchOp operation) {
        this.path = path;
        this.operation = operation;
    }

    public String getPath() {
        return path;
    }

    public JsonPatchOp getOperation() {
        return operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatchPathOperation that = (PatchPathOperation) o;
        return Objects.equals(path, that.path) && Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, operation);
    }
}
