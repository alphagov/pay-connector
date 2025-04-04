package uk.gov.pay.connector.usernotification.model.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;


@Entity
@Table(name = "email_notifications")
@SequenceGenerator(name = "email_notifications_id_seq",
        sequenceName = "email_notifications_id_seq", allocationSize = 1)
public class EmailNotificationEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "email_notifications_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "template_body")
    @JsonProperty("template_body")
    @Schema(example = "", description = "Custom paragraph for the email template")
    private String templateBody;

    @Schema(example = "true", description = "Indicates whether emails are enabled for notifications type")
    private boolean enabled;

    @Column(name = "type", insertable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "Email notification type - PAYMENT_CONFIRMED or REFUND_ISSUED ")
    private EmailNotificationType type;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    @JsonBackReference
    @Schema(hidden = true)
    private GatewayAccountEntity accountEntity;

    public EmailNotificationEntity() {
        // for JPA
    }

    public EmailNotificationEntity(GatewayAccountEntity gatewayAccount, String templateBody, boolean enabled) {
        this.accountEntity = gatewayAccount;
        this.templateBody = templateBody;
        this.enabled = enabled;
    }

    public EmailNotificationEntity(GatewayAccountEntity gatewayAccount) {
        this(gatewayAccount, null, true);
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTemplateBody() {
        return templateBody;
    }

    public void setTemplateBody(String templateBody) {
        this.templateBody = templateBody;
    }

    public GatewayAccountEntity getAccountEntity() {
        return accountEntity;
    }


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
