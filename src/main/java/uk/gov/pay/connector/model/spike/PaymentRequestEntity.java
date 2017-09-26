package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.AbstractEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

public class PaymentRequestEntity extends AbstractEntity {
    @Id
    @JsonIgnore
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "reference")
    private String reference;

    @Column(name = "description")
    private String description;

    @Column(name = "return_url")
    private String returnUrl;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;
}
