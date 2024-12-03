package org.sandeep.flink.examples.job;

import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.opensearch.sink.FlushBackoffType;
import org.apache.flink.connector.opensearch.sink.Opensearch2Sink;
import org.apache.flink.connector.opensearch.sink.Opensearch2SinkBuilder;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.http.HttpHost;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.sandeep.flink.examples.contracts.CustomerDetails;
import org.sandeep.flink.examples.operators.CustomerDetailsMapperFunction;
import org.sandeep.flink.examples.operators.CustomerDetailsSplitFunction;
import org.sandeep.flink.examples.operators.KafkaOpenSearchEmitter;
import org.sandeep.flink.examples.utils.FlinkRuntimeProperties;

import java.util.Optional;
import java.util.Properties;

@Slf4j
public class FlinkConnectorExample {

    private static final Integer MAX_RETRIES = 3;
    private static final Integer RETRY_DELAY_MS = 1000;

    private static final CustomerDetailsMapperFunction flatmapFUnction = new CustomerDetailsMapperFunction();

    private static final CustomerDetailsSplitFunction customerDetailsSplitFunction = new CustomerDetailsSplitFunction();

    public static void main(String[] args) {

        try {

            final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            final ParameterTool parameterTool = ParameterTool.fromArgs(args);
            env.getConfig().setGlobalJobParameters(parameterTool);

            int checkpointInterval = parameterTool.getInt(FlinkRuntimeProperties.CHECKPOINT_INTERVAL);
            env.enableCheckpointing(checkpointInterval, CheckpointingMode.EXACTLY_ONCE);

            KafkaSource<String> kafkaSource = getKafkaSource(parameterTool);

            DataStreamSource<String> sourceDataStream = env.fromSource(kafkaSource,
                    WatermarkStrategy.noWatermarks(),
                    "Kafka Source");

            Opensearch2Sink<CustomerDetails> opensearch2Sink = getOpenSearchSink(parameterTool);
            SingleOutputStreamOperator<CustomerDetails> singleOutputStreamOperator = sourceDataStream
                    .flatMap(flatmapFUnction)
                    .name("customer-details-mapper")
                    .uid("customer-details-mapper");

            // Sinking all events to opensearch
            KafkaSink<String> evenKafkaSink = getEvenKafkaSink(parameterTool);
            singleOutputStreamOperator
                    .sinkTo(opensearch2Sink)
                    .name("opensearch-sink")
                    .uid("opensearch-sink");

            // Split the stream and sink to even
            SingleOutputStreamOperator<Tuple2<Boolean, String>> splitStream = singleOutputStreamOperator
                    .flatMap(customerDetailsSplitFunction)
                    .name("customer-details-split")
                    .uid("customer-details-split");

            splitStream
                    .filter(v -> v.f0)
                    .map(f -> f.f1)
                    .sinkTo(evenKafkaSink)
                    .uid("even-topic-sink")
                    .name("even-topic-sink");

            KafkaSink<String> oddKafkaSink = getOddKafkaSink(parameterTool);
            splitStream
                    .filter(v -> !v.f0)
                    .map(f -> f.f1)
                    .sinkTo(oddKafkaSink)
                    .name("odd-topic-sink")
                    .uid("odd-topic-sink");

            env.execute();

        } catch (Exception e) {
            log.error("Exception while starting flink application", e);
        }
    }

    private static KafkaSource<String> getKafkaSource(final ParameterTool parameterTool) {
        return KafkaSource.<String>builder()
                .setBootstrapServers(parameterTool.get(FlinkRuntimeProperties.KAFKA_SOURCE_BOOTSTRAP_SERVERS))
                .setProperty("partition.discovery.interval.ms", "10000") // discover new partitions per 10 seconds
                .setTopics(parameterTool.get(FlinkRuntimeProperties.SOURCE_TOPIC))
                .setGroupId(parameterTool.get(FlinkRuntimeProperties.KAFKA_SOURCE_GROUP_ID))
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST)) // Get offsets from state else start from the ea
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperties(getKafkaProperties(parameterTool, "source."))
                .build();
    }

    private static KafkaSink<String> getEvenKafkaSink(final ParameterTool parameterTool) {
        Properties additionalSinkProps = getKafkaProperties(parameterTool, "sink.");
        return KafkaSink
                .<String>builder()
                .setBootstrapServers(parameterTool.get(FlinkRuntimeProperties.KAFKA_EVEN_BOOTSTRAP_SERVERS))
                // Note - set sink.acks in the runtime configuration to all so that message is delivered to atleast one in-sync replica
                .setKafkaProducerConfig(additionalSinkProps)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(parameterTool.get(FlinkRuntimeProperties.EVEN_AGE_SINK_TOPIC))
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    private static KafkaSink<String> getOddKafkaSink(final ParameterTool parameterTool) {
        Properties additionalSinkProps = getKafkaProperties(parameterTool, "sink.");
        return KafkaSink
                .<String>builder()
                .setBootstrapServers(parameterTool.get(FlinkRuntimeProperties.KAFKA_ODD_BOOTSTRAP_SERVERS))
                // Note - set sink.acks in the runtime configuration to all so that message is delivered to atleast one in-sync replica
                .setKafkaProducerConfig(additionalSinkProps)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(parameterTool.get(FlinkRuntimeProperties.ODD_AGE_SINK_TOPIC))
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    private static Properties getKafkaProperties(final ParameterTool parameterTool, final String startsWith) {
        Properties properties = new Properties();
        parameterTool.getProperties().forEach((key, value) -> Optional.ofNullable(key).map(Object::toString)
                .filter(k -> k.startsWith(startsWith))
                .ifPresent(k -> properties.put(k.substring(startsWith.length()), value)));
        log.warn("{} Kafka Properties: {}", startsWith, properties);
        return properties;
    }

    private static Opensearch2Sink<CustomerDetails> getOpenSearchSink(final ParameterTool parameterTool) {
        final String index = parameterTool.get(FlinkRuntimeProperties.OPEN_SEARCH_SINK_INDEX);
        final KafkaOpenSearchEmitter opensearchEmitter = new KafkaOpenSearchEmitter(index);
        final String hostAddress = parameterTool.get(FlinkRuntimeProperties.OPEN_SEARCH_HOST_ADDRESS);
        final HttpHost host = HttpHost.create(hostAddress);
        final String username = parameterTool.get(FlinkRuntimeProperties.OPEN_SEARCH_USERNAME);
        final String password = parameterTool.get(FlinkRuntimeProperties.OPEN_SEARCH_PASSWORD);
        final int maxRetries = parameterTool.getInt(FlinkRuntimeProperties.OPEN_SEARCH_RETRIES, MAX_RETRIES);
        final int retryDelay = parameterTool.getInt(FlinkRuntimeProperties.OPEN_SEARCH_RETRY_DELAY_MS, RETRY_DELAY_MS);

        // Enabling insecure access for integration testing
        final boolean allowInsecure = parameterTool.getBoolean(FlinkRuntimeProperties.OPEN_SEARCH_ALLOW_INSECURE, true);

        // Setting bulk flush to 1 while integrated testing so that, each event will be sunk to opensearch container without queuing
        final int bulkFlushActions = parameterTool.getInt(FlinkRuntimeProperties.OPEN_SEARCH_BULK_FLUSH_ACTIONS, 1);
        final int bulkFlushInterval = parameterTool.getInt(FlinkRuntimeProperties.OPEN_SEARCH_BULK_FLUSH_INTERVAL_MS, -1);
        final int bulkFlushSize = parameterTool.getInt(FlinkRuntimeProperties.OPEN_SEARCH_BULK_FLUSH_SIZE_MB, -1);

        return new Opensearch2SinkBuilder<CustomerDetails>()
                .setHosts(host)
                .setEmitter(opensearchEmitter)
                .setConnectionUsername(username)
                .setConnectionPassword(password)
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .setBulkFlushBackoffStrategy(FlushBackoffType.EXPONENTIAL, maxRetries, retryDelay)
                .setAllowInsecure(allowInsecure)
                .setBulkFlushMaxSizeMb(bulkFlushSize)
                .setBulkFlushInterval(bulkFlushInterval)
                .setBulkFlushMaxActions(bulkFlushActions)
                .build();
    }
}
