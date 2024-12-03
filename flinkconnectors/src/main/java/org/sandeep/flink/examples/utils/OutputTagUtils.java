package org.sandeep.flink.examples.utils;

import org.apache.flink.util.OutputTag;

public class OutputTagUtils {

    private OutputTagUtils() {}

    public static OutputTag<String> getSideOutputTag() {
        return new OutputTag<>("odd-age") {};
    }
}
