package org.sandeep.flink.examples.operators;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.util.Collector;
import org.sandeep.flink.examples.contracts.CustomerDetails;

import java.util.Objects;

@Slf4j
public class CustomerDetailsMapperFunction extends RichFlatMapFunction<String, CustomerDetails> {

    private static final Gson GSON = new GsonBuilder().create();

    private transient Counter numInputEventCounter;
    private transient Counter numErrorEventCounter;

    private Counter counterInitializer(String counterName) {
        return getRuntimeContext()
                .getMetricGroup()
                .addGroup("CustomerDetailsMapper")
                .counter(counterName);
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        numInputEventCounter = counterInitializer("numSuccessEvents");
        numErrorEventCounter = counterInitializer("numErrorEvents");
    }

    @Override
    public void flatMap(String value, Collector<CustomerDetails> out) throws Exception {

        numInputEventCounter.inc();
        try {
            CustomerDetails customerDetails = fromJson(value);
            if(Objects.nonNull(customerDetails) && Objects.nonNull(customerDetails.getAge())) {
                out.collect(customerDetails);
            } else {
                numErrorEventCounter.inc();
                log.error("Invalid CustomerDetails: {}", value);
            }

        } catch (JsonSyntaxException e) {
            numErrorEventCounter.inc();
            log.error("Error while deserializing event: {}", value, e);
        }
    }

    private CustomerDetails fromJson(String value) {
        return GSON.fromJson(String.valueOf(value), CustomerDetails.class);
    }
}
