package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import uk.gov.pay.connector.app.config.Authorisation3dsConfig;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.app.config.EmittedEventSweepConfig;
import uk.gov.pay.connector.app.config.EventEmitterConfig;
import uk.gov.pay.connector.app.config.ExpungeConfig;
import uk.gov.pay.connector.app.config.PayoutReconcileProcessConfig;
import uk.gov.pay.connector.app.config.RestClientConfig;
import uk.gov.pay.connector.app.config.TaskQueueConfig;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public class ConnectorConfiguration extends Configuration {

    @Valid
    @NotNull
    private WorldpayConfig worldpayConfig;

    @Valid
    @NotNull
    private ExecutorServiceConfig executorServiceConfig = new ExecutorServiceConfig();

    @Valid
    @NotNull
    private CaptureProcessConfig captureProcessConfig;

    @Valid
    @NotNull
    private PayoutReconcileProcessConfig payoutReconcileProcessConfig;

    @Valid
    @NotNull
    @JsonProperty("notifyConfig")
    private NotifyConfiguration notifyConfig;

    @Valid
    @NotNull
    private SandboxGatewayConfig sandboxConfig;

    @Valid
    @NotNull
    private StripeGatewayConfig stripeConfig;

    @Valid
    @NotNull
    private DataSourceFactory dataSourceFactory;

    @Valid
    @NotNull
    private JPAConfiguration jpaConfiguration;

    @Valid
    @NotNull
    private LinksConfig links = new LinksConfig();

    @Valid
    @NotNull
    @JsonProperty("jerseyClient")
    private JerseyClientConfiguration jerseyClientConfig;

    @Valid
    @NotNull
    @JsonProperty("customJerseyClient")
    private CustomJerseyClientConfiguration customJerseyClient;

    @Valid
    @NotNull
    @JsonProperty("chargesSweepConfig")
    private ChargeSweepConfig chargeSweepConfig;

    @NotNull
    private EmittedEventSweepConfig emittedEventSweepConfig;

    @NotNull
    private EventEmitterConfig eventEmitterConfig;

    @Valid
    @NotNull
    @JsonProperty("expungeConfig")
    private ExpungeConfig expungeConfig;

    @NotNull
    @JsonProperty("authorisation3dsConfig")
    private Authorisation3dsConfig authorisation3dsConfig;

    @NotNull
    @JsonProperty("authorisationConfig")
    private AuthorisationConfig authorisationConfig;

    @NotNull
    private String graphiteHost;

    @NotNull
    private String graphitePort;

    @NotNull
    private Boolean xrayEnabled;

    @NotNull
    private Boolean emitPaymentStateTransitionEvents;

    @NotNull
    private Boolean emitPayoutEvents;

    @Valid
    @NotNull
    @JsonProperty("sqsConfig")
    private SqsConfig sqsConfig;

    @Valid
    @NotNull
    @JsonProperty("eventQueue")
    private EventQueueConfig eventQueueConfig;

    @Valid
    @NotNull
    @JsonProperty("taskQueue")
    private TaskQueueConfig taskQueueConfig;

    @Valid
    @NotNull
    private RestClientConfig restClientConfig;

    @NotNull
    @JsonProperty("ledgerBaseURL")
    private String ledgerBaseUrl;

    @NotNull
    @JsonProperty("cardidBaseURL")
    private String cardidBaseUrl;
    
    @JsonProperty("ecsContainerMetadataUriV4")
    private URI ecsContainerMetadataUriV4;

    @NotNull
    private Long ledgerPostEventTimeoutInMillis;

    public String getLedgerBaseUrl() {
        return ledgerBaseUrl;
    }

    public String getCardidBaseUrl() {
        return cardidBaseUrl;
    }

    public Long getLedgerPostEventTimeoutInMillis() {
        return ledgerPostEventTimeoutInMillis;
    }

    public Duration getLedgerPostEventTimeout() {
        return Duration.ofMillis(ledgerPostEventTimeoutInMillis);
    }

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return dataSourceFactory;
    }

    public LinksConfig getLinks() {
        return links;
    }

    @JsonProperty("worldpay")
    public WorldpayConfig getWorldpayConfig() {
        return worldpayConfig;
    }

    @JsonProperty("sandbox")
    public SandboxGatewayConfig getSandboxConfig() {
        return sandboxConfig;
    }

    @JsonProperty("stripe")
    public StripeGatewayConfig getStripeConfig() {
        return stripeConfig;
    }

    @JsonProperty("rejectPaymentLinkPaymentsWithCardNumberInReference")
    public Boolean rejectPaymentLinkPaymentsWithCardNumberInReference;

    public GatewayConfig getGatewayConfigFor(PaymentGatewayName gateway) {
        switch (gateway) {
            case WORLDPAY:
                return getWorldpayConfig();
            case EPDQ:
            case SMARTPAY:
            default:
                throw new PaymentGatewayName.Unsupported();
        }
    }

    @JsonProperty("jpa")
    public JPAConfiguration getJpaConfiguration() {
        return jpaConfiguration;
    }

    public ExecutorServiceConfig getExecutorServiceConfig() {
        return executorServiceConfig;
    }

    public NotifyConfiguration getNotifyConfiguration() {
        return notifyConfig;
    }

    public JerseyClientConfiguration getClientConfiguration() {
        return jerseyClientConfig;
    }

    public CustomJerseyClientConfiguration getCustomJerseyClient() {
        return customJerseyClient;
    }

    public String getGraphiteHost() {
        return graphiteHost;
    }

    public String getGraphitePort() {
        return graphitePort;
    }

    public CaptureProcessConfig getCaptureProcessConfig() {
        return captureProcessConfig;
    }

    public Boolean isXrayEnabled() {
        return xrayEnabled;
    }

    public ChargeSweepConfig getChargeSweepConfig() {
        return chargeSweepConfig;
    }

    public SqsConfig getSqsConfig() {
        return sqsConfig;
    }

    public EventQueueConfig getEventQueueConfig() {
        return eventQueueConfig;
    }

    public Boolean getEmitPaymentStateTransitionEvents() {
        return emitPaymentStateTransitionEvents;
    }

    public Boolean getEmitPayoutEvents() {
        return emitPayoutEvents;
    }

    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }

    public EmittedEventSweepConfig getEmittedEventSweepConfig() {
        return emittedEventSweepConfig;
    }

    public EventEmitterConfig getEventEmitterConfig() {
        return eventEmitterConfig;
    }

    public ExpungeConfig getExpungeConfig() {
        return expungeConfig;
    }

    public Authorisation3dsConfig getAuthorisation3dsConfig() {
        return authorisation3dsConfig;
    }

    public AuthorisationConfig getAuthorisationConfig() {
        return authorisationConfig;
    }

    public PayoutReconcileProcessConfig getPayoutReconcileProcessConfig() {
        return payoutReconcileProcessConfig;
    }

    public TaskQueueConfig getTaskQueueConfig() {
        return taskQueueConfig;
    }

    public Boolean getRejectPaymentLinkPaymentsWithCardNumberInReference() {
        return rejectPaymentLinkPaymentsWithCardNumberInReference;
    }

    public Optional<URI> getEcsContainerMetadataUriV4() {
        return Optional.ofNullable(ecsContainerMetadataUriV4);
    }
}
