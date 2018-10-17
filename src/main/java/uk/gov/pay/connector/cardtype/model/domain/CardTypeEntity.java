package uk.gov.pay.connector.cardtype.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.common.model.domain.UuidAbstractEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "card_types")
public class CardTypeEntity extends UuidAbstractEntity {

    /**
     * Internal entity used to drive the frontend UI based on the
     * strings stored in the table {@code card_types}. This represents
     * which card types are supported by the service, not what card types
     * are used for payment by the paying user
     * <p>
     * This should be used only for driving the UI
     */
    public enum SupportedType {
        CREDIT,
        DEBIT
    }

    @Column
    @JsonProperty
    private String brand;

    @Column
    @JsonProperty
    private String label;

    @Column
    @Enumerated(EnumType.STRING)
    private SupportedType type;

    @Column(name = "requires_3ds")
    @JsonProperty
    private boolean requires3ds;

    @Override
    @JsonIgnore
    public Long getVersion() {
        return super.getVersion();
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public SupportedType getType() {
        return type;
    }

    public void setType(SupportedType supportedType) {
        this.type = supportedType;
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public void setRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardTypeEntity that = (CardTypeEntity) o;
        return requires3ds == that.requires3ds &&
                Objects.equals(brand, that.brand) &&
                Objects.equals(label, that.label) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(brand, label, type, requires3ds);
    }
}
