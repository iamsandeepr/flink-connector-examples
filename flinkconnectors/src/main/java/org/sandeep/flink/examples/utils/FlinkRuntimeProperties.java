package org.sandeep.flink.examples.utils;

public class FlinkRuntimeProperties {

    private FlinkRuntimeProperties() {}

    public static final String CHECKPOINT_INTERVAL = "checkpoint.interval.seconds";

    public static final String SOURCE_TOPIC = "kafka.source.topic";
    public static final String KAFKA_SOURCE_BOOTSTRAP_SERVERS = "kafka.source.bootstrap.servers";
    public static final String KAFKA_SOURCE_GROUP_ID = "kafka.source.group.id";

    public static final String EVEN_AGE_SINK_TOPIC = "even.age.sink.topic";
    public static final String KAFKA_EVEN_BOOTSTRAP_SERVERS = "kafka.even.bootstrap.servers";

    public static final String ODD_AGE_SINK_TOPIC = "odd.age.sink.topic";
    public static final String KAFKA_ODD_BOOTSTRAP_SERVERS = "kafka.odd.bootstrap.servers";

    public static final String OPEN_SEARCH_SINK_INDEX = "opensearch.sink.index";
    public static final String OPEN_SEARCH_HOST_ADDRESS = "opensearch.host.address";
    public static final String OPEN_SEARCH_USERNAME= "opensearch.username";
    public static final String OPEN_SEARCH_PASSWORD = "opensearch.password";
    public static final String OPEN_SEARCH_ALLOW_INSECURE = "opensearch.allow.insecure";
    public static final String OPEN_SEARCH_BULK_FLUSH_ACTIONS = "opensearch.bulk.flush.actions";
    public static final String OPEN_SEARCH_BULK_FLUSH_INTERVAL_MS = "opensearch.bulk.flush.interval.ms";
    public static final String OPEN_SEARCH_BULK_FLUSH_SIZE_MB= "opensearch.bulk.flush.size.mb";
    public static final String OPEN_SEARCH_RETRIES = "opensearch.retries";
    public static final String OPEN_SEARCH_RETRY_DELAY_MS = "opensearch.retry.delay.ms";

}
