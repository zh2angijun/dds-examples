package com.github.aguther.dds.examples.monitoring.prometheus;

import com.github.aguther.dds.util.BuiltinTopicHelper;
import com.rti.dds.infrastructure.InstanceHandle_t;
import com.rti.dds.subscription.InstanceStateKind;
import com.rti.dds.subscription.SampleInfo;
import idl.rti.dds.monitoring.DataWriterDescription;
import io.prometheus.client.Gauge;
import java.util.HashMap;

class DataWriterDescriptionMetricProcessor {

  private final HashMap<InstanceHandle_t, String[]> instanceHandleHashMap;

  private final Gauge serializedSampleMaxSize;
  private final Gauge serializedSampleMinSize;
  private final Gauge serializedKeyMaxSize;

  DataWriterDescriptionMetricProcessor() {
    instanceHandleHashMap = new HashMap<>();

    serializedSampleMaxSize = Gauge.build()
        .name("datawriter_description_serialized_sample_max_size")
        .labelNames(getLabelNames())
        .help("datawriter_description_serialized_sample_max_size")
        .register();

    serializedSampleMinSize = Gauge.build()
        .name("datawriter_description_serialized_sample_min_size")
        .labelNames(getLabelNames())
        .help("datawriter_description_serialized_sample_min_size")
        .register();

    serializedKeyMaxSize = Gauge.build()
        .name("datawriter_description_serialized_key_max_size")
        .labelNames(getLabelNames())
        .help("datawriter_description_serialized_key_max_size")
        .register();
  }

  void process(
      DataWriterDescription sample,
      SampleInfo info
  ) {
    // put instance handle to hash map if not present
    instanceHandleHashMap.putIfAbsent(info.instance_handle, getLabelValues(sample));
    // get label values once to improve performance
    final String[] labelValues = instanceHandleHashMap.get(info.instance_handle);

    // check if sample is alive and contains valid data
    if (info.instance_state != InstanceStateKind.ALIVE_INSTANCE_STATE || !info.valid_data) {
      // remove labels
      serializedSampleMaxSize.remove(labelValues);
      serializedSampleMinSize.remove(labelValues);
      serializedKeyMaxSize.remove(labelValues);
      // remove instance from hash map
      instanceHandleHashMap.remove(info.instance_handle);
      return;
    }

    // update gauges
    serializedSampleMaxSize.labels(labelValues).set(sample.serialized_sample_max_size);
    serializedSampleMinSize.labels(labelValues).set(sample.serialized_sample_min_size);
    serializedKeyMaxSize.labels(labelValues).set(sample.serialized_key_max_size);
  }

  private String[] getLabelNames() {
    return new String[]{
        "entity_key",
        "publisher_entity_key",
        "topic_entity_key",
        "domain_id",
        "host_id",
        "process_id",
        "type_name",
        "topic_name",
        "publication_name",
        "publication_role_name"
    };
  }

  private String[] getLabelValues(
      DataWriterDescription sample
  ) {
    return new String[]{
        BuiltinTopicHelper.toString(sample.entity_key.value),
        BuiltinTopicHelper.toString(sample.publisher_entity_key.value),
        BuiltinTopicHelper.toString(sample.topic_entity_key.value),
        Integer.toUnsignedString(sample.domain_id),
        Integer.toUnsignedString(sample.host_id),
        Integer.toUnsignedString(sample.process_id),
        sample.type_name,
        sample.topic_name,
        sample.qos.publication_name.name,
        sample.qos.publication_name.role_name
    };
  }
}
