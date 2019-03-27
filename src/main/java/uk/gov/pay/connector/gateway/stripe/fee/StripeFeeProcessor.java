package uk.gov.pay.connector.gateway.stripe.fee;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.FeeProcessor;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.RecoupFeeRequest;
import uk.gov.pay.connector.gateway.model.response.RecoupFeeResponse;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.response.StripeRecoupFeeResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.inject.Inject;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

public class StripeFeeProcessor implements FeeProcessor {
    private StripeGatewayClient client;
    private final JsonObjectMapper jsonObjectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;

    @Inject
    public StripeFeeProcessor(GatewayClientFactory clientFactory,
                              ConnectorConfiguration configuration,
                              JsonObjectMapper jsonObjectMapper,
                              Environment environment
    ) {
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.client = clientFactory.createStripeGatewayClient(
                PaymentGatewayName.STRIPE,
                GatewayOperation.RECOUP_FEE,
                environment.metrics());
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @Override
    public Long calculateFee(Long chargeAmount, Long baseFee) {
        long percentageBasedFee = (long) Math.ceil(stripeGatewayConfig.getFeePercentage() * chargeAmount / 100);
        return percentageBasedFee + baseFee;
    }

    @Override
    public RecoupFeeResponse recoupFee(RecoupFeeRequest recoupFeeRequest) {
        GatewayAccountEntity gatewayAccount = recoupFeeRequest.getCharge().getGatewayAccount();
        try {
            String response = client.postRequest(
                    URI.create(stripeGatewayConfig.getUrl() + "/v1/transfers"), 
                    recoupFeePayload(recoupFeeRequest),
                    recoupFeeHeaders(recoupFeeRequest),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.authorise.create_token",
                            gatewayAccount.getGatewayName(),
                            gatewayAccount.getType())
            );

            StripeRecoupFeeResponse stripeRecoupFeeResponse = jsonObjectMapper.getObject(
                    response, 
                    StripeRecoupFeeResponse.class
            );

            return stripeRecoupFeeResponse.toRecoupFeeResponse();
        } catch (GatewayClientException | GatewayException | DownstreamException  e) {
            return RecoupFeeResponse.fromException(e);
        } 
    }
    
    private String recoupFeePayload(RecoupFeeRequest recoupFeeRequest) {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("amount", recoupFeeRequest.getFee().getAmountDue().toString()));
        params.add(new BasicNameValuePair("currency", "GBP"));
        //@TODO get this from config
        params.add(new BasicNameValuePair("destination", "StripePlatformAccountId"));
        
        return URLEncodedUtils.format(params, UTF_8);
    }
    
    private Map<String, String> recoupFeeHeaders(RecoupFeeRequest recoupFeeRequest) {
        Map<String, String> headers = new HashMap<>();
        headers.putAll(AuthUtil.getStripeAuthHeader(
                stripeGatewayConfig,
                recoupFeeRequest.getCharge().getGatewayAccount().isLive()
        ));
        headers.put("Stripe-Account", recoupFeeRequest.getCharge().getGatewayAccount().getCredentials().get("stripe_account_id"));
        
        return headers;
    }
}
