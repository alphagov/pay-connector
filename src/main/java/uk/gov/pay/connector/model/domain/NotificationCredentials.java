package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import uk.gov.pay.connector.auth.BasicAuthUser;

import javax.persistence.*;

@Entity
@Table(name = "notification_credentials")
@SequenceGenerator(name = "notification_credentials_id_seq", sequenceName = "notification_credentials_id_seq", allocationSize = 1)
public class NotificationCredentials extends AbstractEntity {

    @OneToOne
    @JsonIgnore
    @JoinColumn(name = "account_id", nullable = false)
    @JsonManagedReference
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

    public BasicAuthUser toBasicAuthUser() {
        return new BasicAuthUser(getUserName());
    }
}
