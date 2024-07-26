package uk.gov.pay.connector.gatewayaccount.service;

import com.stripe.Stripe;
import com.stripe.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
 class StripeAccountServiceTest {

    private StripeAccountService stripeAccountService;

    private GatewayAccountEntity gatewayAccountEntity;
    
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    
    @Mock
    private StripeAuthTokens stripeAuthTokens;

    @BeforeEach
     void setUp() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeAuthTokens.getTest()).thenReturn("an-api-key");
        stripeAccountService = new StripeAccountService(stripeGatewayConfig);
    }
    
    /*
     * These tests are meant to be run manually to give confidence the Java API works. In order to run a valid
     * apiKey needs to be provided.
     */
    @Nested
    @Disabled
    class ManualStripeIntegrationTests {
        
        @BeforeEach
        void setUp() {
            Stripe.apiKey = System.getenv("STRIPE_API_KEY"); // pragma: allowlist secret
        }
        
        @Test
        void createTestAccount() {
            Account createdTestAccount = stripeAccountService.createTestAccount("fill-me-in");
            System.out.println("Created account " + createdTestAccount.getId());
        }

        @Test
        void associateDefaultPersonWithTestAccount() {
            stripeAccountService.createDefaultPersonForAccount("fill-the-created-account-id-from-above");
        }
    }

    @Test
     void buildStripeAccountResponseShouldReturnStripeAccountResponse() {
        String stripeAccountId = "acct_123example123";
        gatewayAccountEntity = aServiceAccount(Map.of("stripe_account_id", stripeAccountId));

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(gatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.get().getStripeAccountId(), is(stripeAccountId));
    }

    @Test
     void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsAreEmpty() {
        gatewayAccountEntity = aServiceAccount(Map.of());

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(gatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

    @Test
     void buildStripeAccountResponseShouldReturnEmptyOptionalWhenCredentialsPropertyIsNull() {
        gatewayAccountEntity = aServiceAccount(Collections.singletonMap("stripe_account_id", null));

        Optional<StripeAccountResponse> stripeAccountResponseOptional =
                stripeAccountService.buildStripeAccountResponse(gatewayAccountEntity);

        assertThat(stripeAccountResponseOptional.isPresent(), is(false));
    }

    private GatewayAccountEntity aServiceAccount(Map credentials) {
        var gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .build();
        var creds = aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();

        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));

        return gatewayAccountEntity;
    }
}
