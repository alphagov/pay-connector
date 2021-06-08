package uk.gov.pay.connector.common.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchOp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PatchRequestValidatorTest {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static class InvalidValuesTestProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
            return Stream.of(
                    Arguments.of("Field [path] is required", buildPatchRequest(Map.of("operation", "add", "value", true))),
                    Arguments.of("Field [path] is required", buildPatchRequest(Map.of("operation", "add", "path", "", "value", true))),
                    Arguments.of("Field [path] is required", buildPatchRequest(new HashMap<>() {{
                        put("operation", "add");
                        put("path", null);
                        put("value", true);
                    }})),
                    Arguments.of("Field [path] must be a string", buildPatchRequest(Map.of("operation", "add", "path", 1234, "value", true))),

                    Arguments.of("Field [op] is required", buildPatchRequest(Map.of("path", "foo", "value", true))),
                    Arguments.of("Field [op] is required", buildPatchRequest(Map.of("operation", "", "path", "foo", "value", true))),
                    Arguments.of("Field [op] is required", buildPatchRequest(new HashMap<>() {{
                        put("operation", null);
                        put("path", "foo");
                        put("value", true);
                    }})),
                    Arguments.of("Field [op] must be a string", buildPatchRequest(Map.of("operation", 1234, "path", "foo", "value", true))),
                    Arguments.of("Field [op] must be one of [add, remove, replace]", buildPatchRequest(Map.of("operation", "cook", "path", "foo", "value", true))),

                    Arguments.of("Field [value] is required", buildPatchRequest(Map.of("operation", "add", "path", "foo"))),
                    Arguments.of("Field [value] is required", buildPatchRequest(Map.of("operation", "add", "path", "foo", "value", ""))),
                    Arguments.of("Field [value] is required", buildPatchRequest(new HashMap<>() {{
                        put("operation", "add");
                        put("path", "foo");
                        put("value", null);
                    }})),

                    Arguments.of("Field [path] must be one of [bar, foo]", buildPatchRequest(Map.of("operation", "add", "path", "baz", "value", true))),
                    Arguments.of("Operation [replace] not supported for path [foo]", buildPatchRequest(Map.of("operation", "replace", "path", "foo", "value", true)))
            );
        }
    }
    
    @ParameterizedTest
    @ArgumentsSource(InvalidValuesTestProvider.class)
    void shouldThrowExceptionWhenValidationFails(String expectedErrorMessage, JsonNode request) {
        Map<PatchPathOperation, Consumer<JsonNode>> operationValidators = Map.of(
                new PatchPathOperation("foo", JsonPatchOp.ADD), (node) -> {},
                new PatchPathOperation("bar", JsonPatchOp.REPLACE), (node) -> {}
        );
        
        var patchRequestValidator = new PatchRequestValidator(operationValidators);
        ValidationException validationException = assertThrows(ValidationException.class,
                () -> patchRequestValidator.validate(request));

        assertThat(validationException, hasProperty("errors", contains(expectedErrorMessage)));
    }

    @Test
    void shouldSucceedForValidRequestAndCallOperationValidator() {
        JsonNode fooOperation = objectMapper.valueToTree(Map.of("path", "foo",
                "op", "add",
                "value", 1));
        JsonNode barOperation = objectMapper.valueToTree(Map.of("path", "bar",
                "op", "replace",
                "value", 1));
        JsonNode request = objectMapper.valueToTree(List.of(fooOperation, barOperation));
        
        Consumer<JsonNode> addFooValidator = mock(Consumer.class);
        doAnswer((node) -> null).when(addFooValidator).accept(eq(fooOperation));

        Consumer<JsonNode> replaceBarValidator = mock(Consumer.class);
        doAnswer((node) -> null).when(replaceBarValidator).accept(eq(barOperation));
        
        Map<PatchPathOperation, Consumer<JsonNode>> operationValidators = Map.of(
                new PatchPathOperation("foo", JsonPatchOp.ADD), addFooValidator,
                new PatchPathOperation("bar", JsonPatchOp.REPLACE), replaceBarValidator
        );
        
        var patchRequestValidator = new PatchRequestValidator(operationValidators);

        patchRequestValidator.validate(request);
        verify(addFooValidator).accept(eq(fooOperation));
        verify(replaceBarValidator).accept(eq(barOperation));
    }

    private static JsonNode buildPatchRequest(Map<Object, Object> data) {
        Map<Object, Object> params = new HashMap<>();
        if (data.containsKey("operation")) {
            params.put("op", data.get("operation"));
        }
        if (data.containsKey("path")) {
            params.put("path", data.get("path"));
        }
        if (data.containsKey("value")) {
            params.put("value", data.get("value"));
        }

        return objectMapper.valueToTree(Collections.singletonList(params));
    }
}
