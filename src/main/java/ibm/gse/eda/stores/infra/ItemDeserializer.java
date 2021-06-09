package ibm.gse.eda.stores.infra;

import ibm.gse.eda.stores.domain.ItemTransaction;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ItemDeserializer extends JsonbDeserializer<ItemTransaction> {
    public ItemDeserializer(){
        // pass the class to the parent.
        super(ItemTransaction.class);
    }
}