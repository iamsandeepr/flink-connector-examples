package org.sandeep.flink.examples.operators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.connector.opensearch.sink.OpensearchEmitter;
import org.apache.flink.connector.opensearch.sink.RequestIndexer;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.Requests;
import org.sandeep.flink.examples.contracts.CustomerDetails;

import java.util.Map;

public class KafkaOpenSearchEmitter implements OpensearchEmitter<CustomerDetails> {

    public static final Gson GSON = new GsonBuilder().create();

    private final String index;

    public KafkaOpenSearchEmitter(String index) {
        this.index = index;
    }

    public static <T> Map<String, Object> toMap(T element) {
        String jsonString = GSON.toJson(element);
        return GSON.fromJson(jsonString, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    private IndexRequest createIndexRequest(CustomerDetails element) {
        // Let the id be auto generated
        return Requests.indexRequest()
                .index(index)
                .source(toMap(element));
    }

    @Override
    public void emit(CustomerDetails element, SinkWriter.Context context, RequestIndexer indexer) {
        indexer.add(createIndexRequest(element));
    }
}
