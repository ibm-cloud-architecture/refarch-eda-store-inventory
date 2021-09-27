package ut;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ibm.gse.eda.stores.domain.ItemProcessingAgent;
import ibm.gse.eda.stores.domain.ItemTransaction;
import ibm.gse.eda.stores.domain.StoreInventory;
import ibm.gse.eda.stores.infra.ItemStream;
import ibm.gse.eda.stores.infra.StoreInventoryStream;
import io.quarkus.kafka.client.serialization.JsonbSerde;

/**
 * Use TestDriver to test the Kafka streams topology without kafka brokers
 */
public class TestItemStreamTopology {
     
    private static TopologyTestDriver testDriver;

    private TestInputTopic<String, ItemTransaction> inputTopic;
    private TestOutputTopic<String, StoreInventory> storeInventoryOutputTopic;
 
    private Serde<String> stringSerde = new Serdes.StringSerde();
    private JsonbSerde<ItemTransaction> itemSerde = new JsonbSerde<>(ItemTransaction.class);
    private JsonbSerde<StoreInventory> inventorySerde = new JsonbSerde<>(StoreInventory.class);
  
    private ItemProcessingAgent agent = new ItemProcessingAgent();
   
    
    public  Properties getStreamsConfig() {
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stock-aggregator");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummmy:1234");
        return props;
    }


    /**
     * From items streams which includes sell or restock events from a store
     * aggregate per store and keep item, quamntity
     */
    @BeforeEach
    public void setup() {
        // as no CDI is used set the topic names
        agent.inItemsAsStream = new ItemStream();
        agent.inItemsAsStream.itemSoldInputStreamName="itemSold";
        // output will go to the inventory
        agent.storeInventoryAsStream = new StoreInventoryStream();
        agent.storeInventoryAsStream.storeInventoryOutputStreamName = "inventory";
        
        Topology topology = agent.processItemTransaction();
        testDriver = new TopologyTestDriver(topology, getStreamsConfig());
        inputTopic = testDriver.createInputTopic(agent.inItemsAsStream.itemSoldInputStreamName, 
                                stringSerde.serializer(),
                                itemSerde.serializer());
        storeInventoryOutputTopic = testDriver.createOutputTopic(agent.storeInventoryAsStream.storeInventoryOutputStreamName, 
                                stringSerde.deserializer(), 
                                inventorySerde.deserializer());
    }

    @AfterEach
    public void tearDown() {
        try {
            testDriver.close();
        } catch (final Exception e) {
             System.out.println("Ignoring exception, test failing due this exception:" + e.getLocalizedMessage());
        } 
    }

    @Test
    public void shouldGetAStoreInventoryWithTwoItems() {
        // given two items are stocked in the same store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        item = new ItemTransaction("Store-1","Item-2",ItemTransaction.RESTOCK,10,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // the inventory keeps the store stock per items
        ReadOnlyKeyValueStore<String,StoreInventory> inventory = testDriver.getKeyValueStore(ItemProcessingAgent.STORE_INVENTORY_KAFKA_STORE_NAME);
        StoreInventory aStoreStock = (StoreInventory)inventory.get("Store-1");
        Assertions.assertEquals(5L,  aStoreStock.stock.get("Item-1"));
        Assertions.assertEquals(10L,  aStoreStock.stock.get("Item-2"));
    }



    @Test
    public void shouldGetInventoryUpdatedQuantity(){
        //given an item is stocked in a store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // and then sold        
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // verify an store inventory aggregate events are created with good quantity
        Assertions.assertFalse(storeInventoryOutputTopic.isEmpty()); 
        Assertions.assertEquals(5, storeInventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
        Assertions.assertEquals(3, storeInventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
    }
    
    @Test
    public void shouldGetRestockQuantity(){
        // given an item is stocked in a store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,20);
        inputTopic.pipeInput(item.storeName, item);        
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,2,20);
        inputTopic.pipeInput(item.storeName, item);

        Assertions.assertFalse(storeInventoryOutputTopic.isEmpty()); 
        // can validate at the <Key,Value> Store
        ReadOnlyKeyValueStore<String,StoreInventory> storage = testDriver.getKeyValueStore(ItemProcessingAgent.STORE_INVENTORY_KAFKA_STORE_NAME);
        StoreInventory i = (StoreInventory)storage.get("Store-1");
        // the store keeps the last inventory
        Assertions.assertEquals(7L,  i.stock.get("Item-1"));
        // the output streams gots all the events
        Assertions.assertEquals(5, storeInventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
        Assertions.assertEquals(7, storeInventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
     
     }
 
}