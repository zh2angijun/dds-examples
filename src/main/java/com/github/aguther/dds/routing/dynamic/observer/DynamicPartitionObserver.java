/*
 * MIT License
 *
 * Copyright (c) 2017 Andreas Guther
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

package com.github.aguther.dds.routing.dynamic.observer;

import com.github.aguther.dds.discovery.observer.PublicationObserverListener;
import com.github.aguther.dds.discovery.observer.SubscriptionObserverListener;
import com.github.aguther.dds.routing.dynamic.observer.TopicRoute.Direction;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.infrastructure.InstanceHandle_t;
import com.rti.dds.infrastructure.StringSeq;
import com.rti.dds.publication.builtin.PublicationBuiltinTopicData;
import com.rti.dds.subscription.builtin.SubscriptionBuiltinTopicData;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicPartitionObserver implements Closeable, PublicationObserverListener, SubscriptionObserverListener {

  private static final Logger log;

  static {
    log = LoggerFactory.getLogger(DynamicPartitionObserver.class);
  }

  private final DomainParticipant domainParticipant;

  private final Map<Session, Multimap<TopicRoute, InstanceHandle_t>> mapping;
  private final List<DynamicPartitionObserverFilter> filterList;
  private final List<DynamicPartitionObserverListener> listenerList;
  private final ExecutorService listenerExecutor;

  public DynamicPartitionObserver(
      DomainParticipant domainParticipant
  ) {
    this.domainParticipant = domainParticipant;

    mapping = Collections.synchronizedMap(new HashMap<>());
    filterList = Collections.synchronizedList(new ArrayList<>());
    listenerList = Collections.synchronizedList(new ArrayList<>());
    listenerExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void close() {
    listenerList.clear();
    listenerExecutor.shutdownNow();
  }

  public void addListener(
      DynamicPartitionObserverListener listener
  ) {
    synchronized (listenerList) {
      if (!listenerList.contains(listener)) {
        listenerList.add(listener);
      }
    }
  }

  public void removeListener(
      DynamicPartitionObserverListener listener
  ) {
    listenerList.remove(listener);
  }

  public void addFilter(
      DynamicPartitionObserverFilter filter
  ) {
    synchronized (filterList) {
      if (!filterList.contains(filter)) {
        filterList.add(filter);
      }
    }
  }

  public void removeFilter(
      DynamicPartitionObserverFilter filter
  ) {
    filterList.remove(filter);
  }

  @Override
  public void publicationDiscovered(
      InstanceHandle_t instanceHandle,
      PublicationBuiltinTopicData data
  ) {
    // ignore the publication?
    if (ignorePublication(instanceHandle, data)) {
      return;
    }

    // handle discovered entity
    handleDiscovered(
        instanceHandle,
        Direction.OUT,
        data.topic_name,
        data.type_name,
        data.partition.name
    );
  }

  @Override
  public void publicationLost(
      InstanceHandle_t instanceHandle,
      PublicationBuiltinTopicData data
  ) {
    // ignore the publication?
    if (ignorePublication(instanceHandle, data)) {
      return;
    }

    // handle lost entity
    handleLost(
        instanceHandle,
        Direction.OUT,
        data.topic_name,
        data.type_name,
        data.partition.name
    );
  }

  @Override
  public void subscriptionDiscovered(
      InstanceHandle_t instanceHandle,
      SubscriptionBuiltinTopicData data
  ) {
    // ignore the publication?
    if (ignoreSubscription(instanceHandle, data)) {
      return;
    }

    // handle discovered entity
    handleDiscovered(
        instanceHandle,
        Direction.IN,
        data.topic_name,
        data.type_name,
        data.partition.name
    );
  }

  @Override
  public void subscriptionLost(
      InstanceHandle_t instanceHandle,
      SubscriptionBuiltinTopicData data
  ) {
    // ignore the publication?
    if (ignoreSubscription(instanceHandle, data)) {
      return;
    }

    // handle lost entity
    handleLost(
        instanceHandle,
        Direction.IN,
        data.topic_name,
        data.type_name,
        data.partition.name
    );
  }

  private void handleDiscovered(
      InstanceHandle_t instanceHandle,
      Direction direction,
      String topicName,
      String typeName,
      StringSeq partitions
  ) {
    synchronized (mapping) {
      // create routes for all partitions we discovered
      if (partitions.isEmpty()) {
        // ignore partition?
        if (ignorePartition(topicName, "")) {
          return;
        }
        // add instance handle to map
        addInstanceHandleToMap(
            instanceHandle,
            new Session(topicName, ""),
            new TopicRoute(direction, topicName, typeName)
        );
      } else {
        for (Object partition : partitions) {
          // ignore partition?
          if (ignorePartition(topicName, partition.toString())) {
            continue;
          }
          // add instance handle to map
          addInstanceHandleToMap(
              instanceHandle,
              new Session(topicName, partition.toString()),
              new TopicRoute(direction, topicName, typeName)
          );
        }
      }
    }
  }

  private void handleLost(
      InstanceHandle_t instanceHandle,
      Direction direction,
      String topicName,
      String typeName,
      StringSeq partitions
  ) {
    synchronized (mapping) {
      // delete routes for all partitions we lost
      if (partitions.isEmpty()) {
        // ignore partition?
        if (ignorePartition(topicName, "")) {
          return;
        }
        // remove instance handle from map
        removeInstanceHandleFromMap(
            instanceHandle,
            new Session(topicName, ""),
            new TopicRoute(direction, topicName, typeName)
        );
      } else {
        for (Object partition : partitions) {
          // ignore partition?
          if (ignorePartition(topicName, partition.toString())) {
            return;
          }
          // remove instance handle from map
          removeInstanceHandleFromMap(
              instanceHandle,
              new Session(topicName, partition.toString()),
              new TopicRoute(direction, topicName, typeName)
          );
        }
      }
    }
  }

  private boolean ignorePublication(
      InstanceHandle_t instanceHandle,
      PublicationBuiltinTopicData data
  ) {
    synchronized (filterList) {
      for (DynamicPartitionObserverFilter filter : filterList) {
        if (filter.ignorePublication(domainParticipant, instanceHandle, data)) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Publication topic='{}', type='{}', instance='{}' ignored",
                data.topic_name,
                data.type_name,
                instanceHandle);
          }
          return true;
        }
      }
    }
    return false;
  }

  private boolean ignoreSubscription(
      InstanceHandle_t instanceHandle,
      SubscriptionBuiltinTopicData data
  ) {
    synchronized (filterList) {
      for (DynamicPartitionObserverFilter filter : filterList) {
        if (filter.ignoreSubscription(domainParticipant, instanceHandle, data)) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Subscription topic='{}', type='{}', instance='{}' ignored",
                data.topic_name,
                data.type_name,
                instanceHandle);
          }
          return true;
        }
      }
    }
    return false;
  }

  private boolean ignorePartition(
      String topicName,
      String partition
  ) {
    synchronized (filterList) {
      for (DynamicPartitionObserverFilter filter : filterList) {
        if (filter.ignorePartition(partition)) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Partition topic='{}', name='{}' ignored",
                topicName,
                partition);
          }
          return true;
        }
      }
    }
    return false;
  }

  private void addInstanceHandleToMap(
      InstanceHandle_t instanceHandle,
      Session session,
      TopicRoute topicRoute
  ) {
    // create topic session if first item discovered
    if (!mapping.containsKey(session)) {
      mapping.put(session, ArrayListMultimap.create());
      createSession(session);
    }

    // check if topic route is about to be created
    if (!mapping.get(session).containsKey(topicRoute)) {
      createTopicRoute(session, topicRoute);
    }

    // add instance handle to topic route
    mapping.get(session).put(topicRoute, instanceHandle);
  }

  private void removeInstanceHandleFromMap(
      InstanceHandle_t instanceHandle,
      Session session,
      TopicRoute topicRoute
  ) {
    // remove instance handle from topic route
    mapping.get(session).remove(topicRoute, instanceHandle);

    // check if route was deleted
    if (!mapping.get(session).containsKey(topicRoute)) {
      deleteTopicRoute(session, topicRoute);
    }

    // delete topic session if last items was removed
    if (mapping.get(session).isEmpty()) {
      mapping.remove(session);
      deleteSession(session);
    }
  }

  private void createSession(
      Session session
  ) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Calling 'createSession' on listeners with topic='{}', partition='{}'",
          session.getTopic(),
          session.getPartition()
      );
    }
    // invoke listener
    listenerExecutor.submit(() -> {
      synchronized (listenerList) {
        for (DynamicPartitionObserverListener listener : listenerList) {
          listener.createSession(session);
        }
      }
    });
  }

  private void deleteSession(
      Session session
  ) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Calling 'deleteSession' on listeners with topic='{}', partition='{}'",
          session.getTopic(),
          session.getPartition()
      );
    }
    // invoke listener
    listenerExecutor.submit(() -> {
      synchronized (listenerList) {
        for (DynamicPartitionObserverListener listener : listenerList) {
          listener.deleteSession(session);
        }
      }
    });
  }

  private void createTopicRoute(
      Session session,
      TopicRoute topicRoute
  ) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Calling 'createTopicRoute' on listeners with topic='{}', type='{}', partition='{}', direction='{}'",
          session.getTopic(),
          topicRoute.getType(),
          session.getPartition(),
          topicRoute.getDirection()
      );
    }
    // invoke listener
    listenerExecutor.submit(() -> {
      synchronized (listenerList) {
        for (DynamicPartitionObserverListener listener : listenerList) {
          listener.createTopicRoute(session, topicRoute);
        }
      }
    });
  }

  private void deleteTopicRoute(
      Session session,
      TopicRoute topicRoute
  ) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Calling 'deleteTopicRoute' on listeners with topic='{}', type='{}', partition='{}', direction='{}'",
          session.getTopic(),
          topicRoute.getType(),
          session.getPartition(),
          topicRoute.getDirection()
      );
    }
    // invoke listener
    listenerExecutor.submit(() -> {
      synchronized (listenerList) {
        for (DynamicPartitionObserverListener listener : listenerList) {
          listener.deleteTopicRoute(session, topicRoute);
        }
      }
    });
  }
}
