package uk.gov.pay.connector.model;

import uk.gov.pay.connector.resources.ChargesApiResource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.model.PatchRequest.PatchField.*;

public class ChargePatchRequest extends PatchRequest {
    private static final List<String> patchableFields = Arrays.asList(ChargesApiResource.EMAIL_KEY);
    private static final  List<String> validOps = Arrays.asList("replace");

    public static ChargePatchRequest fromMap(Map<String, String> patchRequestMap) throws IllegalArgumentException {
        return new ChargePatchRequest(
                patchRequestMap.get(OP.toString()),
                patchRequestMap.get(PATH.toString()),
                patchRequestMap.get(VALUE.toString())
        );
    }

    private ChargePatchRequest(String op, String path, String value) throws IllegalArgumentException{
        super(op, path, value);
    }

    @Override
    protected boolean isValidOp(String op) {
        return validOps.contains(op);
    }

    @Override
    protected boolean isValidPath(String path) {
        return patchableFields.contains(path);
    }
}
