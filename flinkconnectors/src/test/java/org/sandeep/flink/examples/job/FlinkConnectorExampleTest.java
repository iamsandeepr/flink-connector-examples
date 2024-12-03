package org.sandeep.flink.examples.job;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.salesforce.kafka.test.KafkaTestCluster;
import com.salesforce.kafka.test.KafkaTestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.*;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.sandeep.flink.examples.contracts.CustomerDetails;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
public class FlinkConnectorExampleTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DockerImageName OPEN_SEARCH_IMAGE = DockerImageName.parse("opensearchproject/opensearch:2.13.0");

    private static final OpensearchContainer<?> CONTAINER = new OpensearchContainer<>(OPEN_SEARCH_IMAGE);

    private static KafkaTestUtils utils;
    private static KafkaTestCluster cluster;

    private static final String INPUT_TOPIC = "input";
    private static final String EVEN_OUTPUT_TOPIC = "even";
    private static final String ODD_OUTPUT_TOPIC = "odd";
    private static final String INDEX_NAME = "hot-storage-index";

    @ClassRule
    public static MiniClusterWithClientResource flinkCluster =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    @BeforeClass
    public static void startKafkaCluster() throws Throwable {
        // Setup InMemory  Kafka Cluster
        cluster = new KafkaTestCluster(1);
        cluster.start();
        utils = new KafkaTestUtils(cluster);

        utils.createTopic(INPUT_TOPIC, 1, (short) 1);
        utils.createTopic(EVEN_OUTPUT_TOPIC, 1, (short) 1);
        utils.createTopic(ODD_OUTPUT_TOPIC, 1, (short) 1);
    }

    @BeforeClass
    public static void startOpenSearch() {
        // https://stackoverflow.com/questions/61108655/test-container-test-cases-are-failing-due-to-could-not-find-a-valid-docker-envi
        CONTAINER.start();
    }

    @AfterClass
    public static void stopKafkaCluster() throws Exception {
        cluster.close();
    }

    @AfterClass
    public static void closeOpenSearch() {
        log.info("Closing OpenSearch container");
        CONTAINER.stop();
    }

    private static Producer<String, String> getProducer() {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getKafkaConnectString());
        props.put("security.protocol", "PLAINTEXT");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 2);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 2);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");

        return new KafkaProducer<>(props);
    }

    private static List<String> getCustomers() {
        List<String> customers = new ArrayList<>();
        CustomerDetails oddCustomerDetails = CustomerDetails.builder()
                .age(21)
                .firstName("John")
                .lastName("Doe")
                .build();
        CustomerDetails evenCustomerDetails = CustomerDetails.builder()
                .age(22)
                .firstName("Dave")
                .lastName("Smith")
                .build();

        customers.add(GSON.toJson(oddCustomerDetails));
        customers.add(GSON.toJson(evenCustomerDetails));

        return customers;
    }

    private static String[] getSystemArgs() {
        return new String[]{

                "--checkpoint.interval.seconds", "5000",

                // KAFKA Telemetry PROPERTY

                "--kafka.source.bootstrap.servers", cluster.getKafkaConnectString(),
                "--kafka.source.topic", INPUT_TOPIC,
                "--kafka.source.group.id", "FlinkConnectorGroup",

                "--kafka.even.bootstrap.servers", cluster.getKafkaConnectString(),
                "--even.age.sink.topic", EVEN_OUTPUT_TOPIC,

                "--kafka.odd.bootstrap.servers", cluster.getKafkaConnectString(),
                "--odd.age.sink.topic", ODD_OUTPUT_TOPIC,

                // OpenSearch PROPERTY - ONLY USED FOR TEST CASES
                "--opensearch.sink.index", INDEX_NAME,
                "--opensearch.host.address", CONTAINER.getHttpHostAddress(),
                "--opensearch.username", CONTAINER.getUsername(),
                "--opensearch.password", CONTAINER.getPassword(),
        };
    }

    private int getNumberOfRecordsInOpenSearch() {

        String endpoint = String.format("/%s/_search", INDEX_NAME);
        log.info("OpenSearch endpoint: {}", endpoint);

        RestClient client = RestClient
                .builder(HttpHost.create(CONTAINER.getHttpHostAddress()))
                .build();

        try (client) {
            Request request = new Request("GET", endpoint);
            Response response =  client.performRequest(request);
            HttpEntity entity = response.getEntity();

            String responseString = EntityUtils.toString(entity);
            log.info("Repeatable: {}, Content Type: {}, Content: {}", entity.isRepeatable(), entity.getContentType(), responseString);

            JsonElement je = GSON.fromJson(responseString, JsonElement.class);
            JsonObject jo = je.getAsJsonObject();

            JsonObject totalHits = jo.getAsJsonObject("hits").getAsJsonObject("total");
            int numHits = totalHits.get("value").getAsInt();
            log.info("Number of records: {}", numHits);
            return numHits;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Long getEventsInTopic(String topic) {

        List<ConsumerRecord<String, String>> consumerRecords = utils.consumeAllRecordsFromTopic(
                topic,
                StringDeserializer.class,
                StringDeserializer.class
        );

        for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
            log.info("{}", consumerRecord);
        }
        int count = consumerRecords.size();
        log.info("Number of records in {}: {}", topic, count);

        return (long) count;
    }

    private static void produceData(List<String> customerDetails, String topic) {
        Producer<String, String> producer = getProducer();
        log.info("Producing events");
        customerDetails.forEach(v -> {
            ProducerRecord<String, String> data = new ProducerRecord<>(topic, v);
            producer.send(data);
        });

        producer.flush();
        producer.close();
    }

    @Test
    public void testFlinkConnectors() {
        List<String> customers = getCustomers();
        produceData(customers, INPUT_TOPIC);

        Awaitility.await()
                .timeout(Duration.ofSeconds(10))
                .pollDelay(Duration.ofSeconds(5))
                .untilAsserted(() -> Assert.assertTrue(true));

        try {

            Thread startFlinkJob = new Thread(() -> {
                try {
                    FlinkConnectorExample.main(getSystemArgs());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            startFlinkJob.start();
            // Interrupt the thread after 20 seconds
            startFlinkJob.join(Duration.ofSeconds(20).toMillis());

            int numOfRawEvHits = getNumberOfRecordsInOpenSearch();
            Assert.assertEquals(2, numOfRawEvHits);

            long evenEvents = getEventsInTopic(EVEN_OUTPUT_TOPIC);
            log.info("Number of even events: {}", evenEvents);
            Assert.assertEquals(1, evenEvents);

            long oddEvents = getEventsInTopic(ODD_OUTPUT_TOPIC);
            log.info("Number of odd events: {}", oddEvents);
            Assert.assertEquals(1, oddEvents);

        } catch (Exception e) {
            log.error("Exception occurred while launching Flink");
            throw new RuntimeException(e);
        }
    }

}
