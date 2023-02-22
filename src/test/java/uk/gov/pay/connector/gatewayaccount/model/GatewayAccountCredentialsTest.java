package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

class GatewayAccountCredentialsTest {

    @Test
    void shouldRemovePasswordsFromProvidedCredentials_noNestedCredentials() {
        Map<String, Object> credentials = Map.of(
                "some-property", "foo",
                "password", "bar");
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        GatewayAccountCredentials gatewayAccountCredentials = new GatewayAccountCredentials(credentialsEntity);
        assertThat(gatewayAccountCredentials.getCredentials(), hasEntry("some-property", "foo"));
        assertThat(gatewayAccountCredentials.getCredentials(), not(hasKey("password")));
    }
    
    @Test
    void shouldRemovePasswordsFromProvidedCredentials_whenNestedCredentials() {
        var credentials = Map.of(
                "some-property", "foo",
                "password", "bar",
                "nested", Map.of(
                        "nested-property", "foo1",
                        "password", "bar1"
                ));
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        GatewayAccountCredentials gatewayAccountCredentials = new GatewayAccountCredentials(credentialsEntity);
        assertThat(gatewayAccountCredentials.getCredentials(), hasEntry("some-property", "foo"));
        assertThat(gatewayAccountCredentials.getCredentials(), not(hasKey("password")));
        
        assertThat(gatewayAccountCredentials.getCredentials(), hasKey("nested"));
        Map<String, Object> nestedMap = (Map<String, Object>) gatewayAccountCredentials.getCredentials().get("nested");
        assertThat(nestedMap, hasEntry("nested-property", "foo1"));
        assertThat(nestedMap, not(hasKey("password")));
    }
}
