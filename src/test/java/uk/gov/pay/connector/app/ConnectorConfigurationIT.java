package uk.gov.pay.connector.app;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.dropwizard.util.Duration;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.BaseUrls;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

public class ConnectorConfigurationIT {

    @Rule
    public final DropwizardAppRule<ConnectorConfiguration> RULE =
            new DropwizardAppRule<>(ConnectorApp.class, ResourceHelpers.resourceFilePath("config/test-config.yaml"));

    @Test
    public void shouldParseConfiguration() {
        JerseyClientOverrides jerseyClientOverrides = RULE.getConfiguration().getWorldpayConfig().getJerseyClientOverrides().get();

        Duration authReadTimeout = jerseyClientOverrides.getAuth().getReadTimeout();
        assertThat(authReadTimeout, is(Duration.milliseconds(222)));

        CaptureProcessConfig captureProcessConfig = RULE.getConfiguration().getCaptureProcessConfig();
        assertThat(captureProcessConfig.getChargesConsideredOverdueForCaptureAfter(), is(60));
        assertThat(captureProcessConfig.getMaximumRetries(), is(26));

        assertThat(RULE.getConfiguration().getLedgerBaseUrl(), is(not(emptyString())));
        assertThat(RULE.getConfiguration().getRestClientConfig().isDisabledSecureConnection(), is(true));
        assertThat(RULE.getConfiguration().getEmittedEventSweepConfig().getNotEmittedEventMaxAgeInSeconds(), is(1800));
    }
    
    @Test
    public void shouldParseAdyenConfiguration () {
        AdyenGatewayConfig adyenGatewayConfig = RULE.getConfiguration().getAdyenGatewayConfig();
        BaseUrls baseUrls = adyenGatewayConfig.getBaseUrls();

        assertThat(baseUrls.checkout().test(), is("https://checkout-test.adyen.com/someVersion"));
        assertThat(baseUrls.checkout().live(), is("https://checkout-live.adyen.com/someVersion"));
        
        assertThat(adyenGatewayConfig.getMerchantAccountIds().live(), is("adyen-live-merchant-account-id"));
        assertThat(adyenGatewayConfig.getMerchantAccountIds().test(), is("adyen-test-merchant-account-id"));
        assertThat(adyenGatewayConfig.getBalancePlatformIds().live(), is("adyen-live-balance-platform-id"));
        assertThat(adyenGatewayConfig.getBalancePlatformIds().test(), is("adyen-test-balance-platform-id"));
        
        assertThat(adyenGatewayConfig.getApiKeys().companyAccount().live(), is("adyen-live-company-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().companyAccount().test(), is("adyen-test-company-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().balancePlatform().live(), is("adyen-live-balance-platform-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().balancePlatform().test(), is("adyen-test-balance-platform-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().legalEntityManagement().live(), is("adyen-live-legal-entity-management-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().legalEntityManagement().test(), is("adyen-test-legal-entity-management-api-key"));

        assertThat(adyenGatewayConfig.getNotificationDomain(), is(".adyen.com"));
        
        assertThat(adyenGatewayConfig.getHmacKeys().payments().test().getPrimary().get(), is("adyen-test-payments-hmac-primary"));
        assertThat(adyenGatewayConfig.getHmacKeys().payments().live().getPrimary().get(), is("adyen-live-payments-hmac-primary"));
        assertThat(adyenGatewayConfig.getHmacKeys().tokens().test().getPrimary().get(), is("adyen-test-tokens-hmac-primary"));
        assertThat(adyenGatewayConfig.getHmacKeys().tokens().live().getPrimary().get(), is("adyen-live-tokens-hmac-primary"));
    }

}
