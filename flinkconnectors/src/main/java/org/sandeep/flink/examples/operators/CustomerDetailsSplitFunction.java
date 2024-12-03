package org.sandeep.flink.examples.operators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.sandeep.flink.examples.contracts.CustomerDetails;
import org.sandeep.flink.examples.utils.OutputTagUtils;

@Slf4j
public class CustomerDetailsSplitFunction extends RichFlatMapFunction<CustomerDetails, Tuple2<Boolean, String>> {

    private static final Gson GSON = new GsonBuilder().create();

    private transient Counter numEvenCounter;
    private transient Counter numOddCounter;

    private static final OutputTag<String> outputTag = OutputTagUtils.getSideOutputTag();

    private Counter counterInitializer(String counterName) {
        return getRuntimeContext()
                .getMetricGroup()
                .addGroup("CustomerDetailsProcessor")
                .counter(counterName);
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        numEvenCounter = counterInitializer("numEvenCounter");
        numOddCounter = counterInitializer("numOddCounter");
    }

    private boolean isEven(CustomerDetails customerDetails) {
        int age = customerDetails.getAge();
        return age % 2 == 0;
    }

    @Override
    public void flatMap(CustomerDetails value, Collector<Tuple2<Boolean, String>> out) throws Exception {
        if(isEven(value)) {
            log.debug("Even age");
            numEvenCounter.inc();
            out.collect(new Tuple2<>(true,GSON.toJson(value)));
        } else {
            log.debug("Odd age");
            numOddCounter.inc();
            out.collect(new Tuple2<>(false,GSON.toJson(value)));
        }
    }
}
