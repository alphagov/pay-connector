package uk.gov.pay.connector.gateway.worldpay;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;

public class EntityMatcher implements org.mockito.ArgumentMatcher<Entity> {
    
    private Entity<Form> expectedEntity;

    public EntityMatcher(Entity<Form> expectedEntity) {
        this.expectedEntity = expectedEntity;
    }

    @Override
    public boolean matches(Entity entity) {
        Form form = (Form) entity.getEntity();
        return form.asMap().equals(expectedEntity.getEntity().asMap()) && 
                entity.getMediaType().equals(expectedEntity.getMediaType());
    }
}
