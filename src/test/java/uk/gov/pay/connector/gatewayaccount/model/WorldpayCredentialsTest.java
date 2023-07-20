package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WorldpayCredentialsTest {

    @Test
    void shouldReturnDoesNotHaveCredentialsWhenNoCredentialsSet() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        assertThat(worldpayCredentials.hasCredentials(), is(false));
    }

    @Test
    void shouldReturnHasCredentialsWhenLegacyCredentialsSet() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setLegacyOneOffCustomerInitiatedMerchantCode("foo");
        assertThat(worldpayCredentials.hasCredentials(), is(true));
    }

    @Test
    void shouldReturnHasCredentialsWhenOneOffCustomerInitiatedCredentialsSet() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("merchant-code", "username", "password"));
        assertThat(worldpayCredentials.hasCredentials(), is(true));
    }

    @Test
    void shouldReturnDoesNotHaveCredentialsWhenOnlyRecurringCustomerInitiatedSet() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("merchant-code", "username", "password"));
        assertThat(worldpayCredentials.hasCredentials(), is(false));
    }

    @Test
    void shouldReturnDoesNotHaveCredentialsWhenOnlyRecurringMerchantInitiatedSet() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials("merchant-code", "username", "password"));
        assertThat(worldpayCredentials.hasCredentials(), is(false));
    }

    @Test
    void shouldReturnHasCredentialsWhenAllRecurringCredentialsSet() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setRecurringCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials("merchant-code", "username", "password"));
        worldpayCredentials.setRecurringMerchantInitiatedCredentials(new WorldpayMerchantCodeCredentials("merchant-code", "username", "password"));
        assertThat(worldpayCredentials.hasCredentials(), is(true));
    }

    @Test
    void shouldReturnOneOffCredentialsPopulatedFromLegacyCredentials() {
        WorldpayCredentials worldpayCredentials = new WorldpayCredentials();
        worldpayCredentials.setLegacyOneOffCustomerInitiatedMerchantCode("a-merchant-code");
        worldpayCredentials.setLegacyOneOffCustomerInitiatedUsername("a-username");
        worldpayCredentials.setLegacyOneOffCustomerInitiatedPassword("a-password");
        
        assertThat(worldpayCredentials.getOneOffCustomerInitiatedCredentials().isPresent(), is(true));
        WorldpayMerchantCodeCredentials oneOffCredentials = worldpayCredentials.getOneOffCustomerInitiatedCredentials().get();
        assertThat(oneOffCredentials.getMerchantCode(), is("a-merchant-code"));
        assertThat(oneOffCredentials.getUsername(), is("a-username"));
        assertThat(oneOffCredentials.getPassword(), is("a-password"));
    }
}
