package ibm.gse.eda.stores.domain;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.eclipse.microprofile.config.ConfigProvider;

import ibm.gse.eda.stores.infra.ItemStream;
import ibm.gse.eda.stores.infra.StoreInventoryStream;

/**
 * The agent processes item sold events from the items topic using Kafka streams
 * topology. 
 * The goal is to compute the store inventory, which mean the number of items per item id per store
 */
@ApplicationScoped
public class ItemProcessingAgent {
    // Kafka store construct to keep item stock per store-id
    public static String STORE_INVENTORY_KAFKA_STORE_NAME = "StoreInventoryStock";
    // input streams
    public ItemStream inItemsAsStream;
    // two output streams
    public StoreInventoryStream storeInventoryAsStream;
   
    public ItemProcessingAgent() {
        this.inItemsAsStream = new ItemStream();
        this.storeInventoryAsStream = new StoreInventoryStream();
    }

    /**
     * The topology processes the items stream into two different paths: one
     * to compute the sum of items sold per item-id, the other to compute
     * the inventory per store. An app can have one topology.
     **/  
    @Produces
    public Topology processItemTransaction(){
        KStream<String,ItemTransaction> items = inItemsAsStream.getItemStreams();     
        // process items and aggregate at the store level 
        KTable<String,StoreInventory> storeItemInventory = items
            // use store name as key, which is what the item event is also using
            .groupByKey(ItemStream.buildGroupDefinitionType())
            // update the current stock for this <store,item> pair
            // change the value type
            .aggregate(
                () ->  new StoreInventory(), // initializer when there was no store in the table
                (store , newItem, existingStoreInventory) 
                    -> existingStoreInventory.updateStockQuantity(store,newItem), 
                    materializeAsStoreInventoryKafkaStore());       
        produceStoreInventoryToInventoryOutputStream(storeItemInventory);
        return inItemsAsStream.run();
    }

    private static Materialized<String, StoreInventory, KeyValueStore<Bytes, byte[]>> materializeAsStoreInventoryKafkaStore() {
        return Materialized.<String, StoreInventory, KeyValueStore<Bytes, byte[]>>as(STORE_INVENTORY_KAFKA_STORE_NAME)
                .withKeySerde(Serdes.String()).withValueSerde(StoreInventory.storeInventorySerde);
    }

    public void produceStoreInventoryToInventoryOutputStream(KTable<String, StoreInventory> storeInventory) {
        KStream<String, StoreInventory> inventories = storeInventory.toStream();
        inventories.print(Printed.toSysOut());
        inventories.to(storeInventoryAsStream.storeInventoryOutputStreamName, Produced.with(Serdes.String(), StoreInventory.storeInventorySerde));
    }

}