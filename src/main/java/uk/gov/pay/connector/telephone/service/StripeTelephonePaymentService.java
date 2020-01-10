package uk.gov.pay.connector.telephone.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.inject.Inject;
import java.util.Optional;

public class StripeTelephonePaymentService {

    private final Logger logger = LoggerFactory.getLogger(StripeTelephonePaymentService.class);
    private ConnectorConfiguration connectorConfiguration;

    @Inject
    public StripeTelephonePaymentService(ConnectorConfiguration connectorConfiguration) {
        this.connectorConfiguration = connectorConfiguration;
        Stripe.apiKey = connectorConfiguration.getStripeTestApiKey();
    }

    public Optional<Charge> getStripePayment(String paymentId) {
        try {
            return Optional.ofNullable(Charge.retrieve("paymentId"));
        } catch (StripeException e) {
            logger.info("Error occurred when retrieving charge from stripe");
            return Optional.empty();
        }
    }
}
