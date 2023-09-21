package uk.gov.pay.connector.gateway.stripe.json;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class StripeExpandableFieldTest {

    public static final String ID = "an-id";

    @Test
    void shouldReturnFalseIfNotExpanded() {
        StripeExpandableField<TestStripeObject> expandableField = new StripeExpandableField<>(ID, null);
        assertThat(expandableField.isExpanded(), is(false));
    }

    @Test
    void shouldReturnEmptyOptionalIfNotExpanded() {
        StripeExpandableField<TestStripeObject> expandableField = new StripeExpandableField<>(ID, null);
        assertThat(expandableField.getExpanded(), is(Optional.empty()));
    }

    @Test
    void shouldReturnIdIfNotExpanded() {
        StripeExpandableField<TestStripeObject> expandableField = new StripeExpandableField<>(ID, null);
        assertThat(expandableField.getId(), is(ID));
    }

    @Test
    void shouldReturnTrueIfExpanded() {
        TestStripeObject object = new TestStripeObject(ID);
        StripeExpandableField<TestStripeObject> expandableField = new StripeExpandableField<>(ID, object);
        assertThat(expandableField.isExpanded(), is(true));
    }

    @Test
    void shouldReturnExpandedObject() {
        TestStripeObject object = new TestStripeObject(ID);
        StripeExpandableField<TestStripeObject> expandableField = new StripeExpandableField<>(ID, object);
        assertThat(expandableField.getExpanded().isPresent(), is(true));
        assertThat(expandableField.getExpanded().get().getId(), is(ID));
    }

    @Test
    void shouldReturnIdIfExpanded() {
        TestStripeObject object = new TestStripeObject(ID);
        StripeExpandableField<TestStripeObject> expandableField = new StripeExpandableField<>(ID, object);
        assertThat(expandableField.getId(), is(ID));
    }

    private static class TestStripeObject implements StripeObjectWithId {

        private final String id;
        
        public TestStripeObject(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
