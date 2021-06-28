package com.rbkmoney.scheduledpayoutworker.serde.impl.kafka;


import com.rbkmoney.machinegun.eventsink.SinkEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.util.Map;

@Slf4j
public class SinkEventSerializer implements Serializer<SinkEvent> {

    ThreadLocal<TSerializer> serializerThreadLocal =
            ThreadLocal.withInitial(() -> new TSerializer(new TBinaryProtocol.Factory()));

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public byte[] serialize(String topic, SinkEvent data) {
        byte[] retVal = null;
        try {
            retVal = serializerThreadLocal.get().serialize(data);
        } catch (Exception e) {
            log.error("Error when serialize machine event data: {} ", data, e);
        }
        return retVal;
    }

    @Override
    public void close() {

    }

}
