package uk.gov.pay.connector.usernotification.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;


@Entity
@Table(name = "notification_credentials")
@SequenceGenerator(name = "notification_credentials_id_seq",
        sequenceName = "notification_credentials_id_seq", allocationSize = 1)
public class NotificationCredentials extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_credentials_id_seq")
    @JsonIgnore
    private Long id;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    @JsonIgnore
    private GatewayAccountEntity accountEntity;

    @Column(name = "username", nullable = false)
    private String userName;

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String password;

    public NotificationCredentials() {
    }

    public NotificationCredentials(GatewayAccountEntity accountEntity) {
        this.accountEntity = accountEntity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GatewayAccountEntity getAccountEntity() {
        return accountEntity;
    }

    public void setAccountEntity(GatewayAccountEntity accountEntity) {
        this.accountEntity = accountEntity;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
