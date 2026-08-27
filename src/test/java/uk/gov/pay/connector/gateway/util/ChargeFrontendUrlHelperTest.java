package uk.gov.pay.connector.gateway.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChargeFrontendUrlHelperTest {
    
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private LinksConfig mockLinksConfig;
    
    private static final String CHARGE_EXTERNAL_ID = "chargeExternalId";
    
    @Test
    void shouldBuildChargeFrontendUrl() {
        given(mockConnectorConfiguration.getLinks()).willReturn(mockLinksConfig);
        given(mockLinksConfig.getFrontendUrl()).willReturn("http://frontend.test");

        ChargeFrontendUrlHelper helper = new ChargeFrontendUrlHelper(mockConnectorConfiguration);

        String actual = helper.getFrontendUrlForCharge(CHARGE_EXTERNAL_ID);
        String expected = "http://frontend.test/card_details/" + CHARGE_EXTERNAL_ID;
        
        assertThat(actual, is(expected));

    }

    @Test
    void shouldBuildAdyen3dsRequiredInChargeFrontendUrl() {
        given(mockConnectorConfiguration.getLinks()).willReturn(mockLinksConfig);
        given(mockLinksConfig.getFrontendUrl()).willReturn("http://frontend.test");

        ChargeFrontendUrlHelper helper = new ChargeFrontendUrlHelper(mockConnectorConfiguration);

        String actual = helper.getAdyen3dsRequiredInFrontendUrlForCharge(CHARGE_EXTERNAL_ID);
        String expected = "http://frontend.test/card_details/" + CHARGE_EXTERNAL_ID + "/3ds_required_in/adyen";

        assertThat(actual, is(expected));

    }

}
