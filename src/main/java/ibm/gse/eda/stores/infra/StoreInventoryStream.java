package ibm.gse.eda.stores.infra;

import java.util.Optional;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.eclipse.microprofile.config.ConfigProvider;

import ibm.gse.eda.stores.domain.StoreInventory;

/**
 * Item inventory stream represents the stream of output message 
 * to share stock value for an item
 */
public class StoreInventoryStream {

    public String storeInventoryOutputStreamName= "store.inventory";

    
    public StreamsBuilder builder;

    public StoreInventoryStream() {
        builder = new StreamsBuilder();
        Optional<String> v =ConfigProvider.getConfig().getOptionalValue("app.store.inventory.topic", String.class);
        if (v.isPresent()) {
            this.storeInventoryOutputStreamName = v.get();
        }
    }



    public void produceStoreInventoryToInventoryOutputStream(KTable<String, StoreInventory> storeInventory) {
        KStream<String, StoreInventory> inventories = storeInventory.toStream();
        inventories.print(Printed.toSysOut());
        inventories.to(storeInventoryOutputStreamName, Produced.with(Serdes.String(), StoreInventory.storeInventorySerde));
    }

}
