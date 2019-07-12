/*
 * MIT License
 *
 * Copyright (c) 2019 Andreas Guther
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.aguther.dds.examples.prometheus.monitoring;

import com.github.aguther.dds.util.BuiltinTopicHelper;
import com.rti.dds.infrastructure.InstanceHandle_t;
import com.rti.dds.subscription.InstanceStateKind;
import com.rti.dds.subscription.SampleInfo;
import idl.rti.dds.monitoring.DataReaderDescription;
import io.prometheus.client.Gauge;
import java.util.HashMap;

public class DataReaderDescriptionMetricProcessor {

  private final HashMap<InstanceHandle_t, String[]> instanceHandleHashMap;

  private final Gauge serializedSampleMaxSize;
  private final Gauge serializedSampleMinSize;
  private final Gauge serializedKeyMaxSize;

  public DataReaderDescriptionMetricProcessor() {
    instanceHandleHashMap = new HashMap<>();

    serializedSampleMaxSize = Gauge.build()
      .name("datareader_description_serialized_sample_max_size")
      .labelNames(getLabelNames())
      .help("datareader_description_serialized_sample_max_size")
      .register();

    serializedSampleMinSize = Gauge.build()
      .name("datareader_description_serialized_sample_min_size")
      .labelNames(getLabelNames())
      .help("datareader_description_serialized_sample_min_size")
      .register();

    serializedKeyMaxSize = Gauge.build()
      .name("datareader_description_serialized_key_max_size")
      .labelNames(getLabelNames())
      .help("datareader_description_serialized_key_max_size")
      .register();
  }

  public void process(
    DataReaderDescription sample,
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
      "datareader_key",
      "subscriber_key",
      "topic_key",
      "domain_id",
      "host_id",
      "process_id",
      "type_name",
      "topic_name",
      "subscription_name",
      "subscription_role_name"
    };
  }

  private String[] getLabelValues(
    DataReaderDescription sample
  ) {
    return new String[]{
      BuiltinTopicHelper.toString(sample.entity_key.value),
      BuiltinTopicHelper.toString(sample.subscriber_entity_key.value),
      BuiltinTopicHelper.toString(sample.topic_entity_key.value),
      Integer.toUnsignedString(sample.domain_id),
      Integer.toUnsignedString(sample.host_id),
      Integer.toUnsignedString(sample.process_id),
      sample.type_name,
      sample.topic_name,
      sample.qos.subscription_name.name,
      sample.qos.subscription_name.role_name
    };
  }
}