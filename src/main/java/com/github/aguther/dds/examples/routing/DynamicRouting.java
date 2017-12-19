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

package com.github.aguther.dds.examples.routing;

import com.github.aguther.dds.examples.discovery.observer.PublicationObserver;
import com.github.aguther.dds.examples.discovery.observer.SubscriptionObserver;
import com.github.aguther.dds.examples.routing.dynamic.RoutingServiceEntitiesFilter;
import com.github.aguther.dds.examples.routing.dynamic.RoutingServiceGroupEntitiesFilter;
import com.github.aguther.dds.examples.routing.dynamic.RtiTopicFilter;
import com.github.aguther.dds.examples.routing.dynamic.WildcardPartitionFilter;
import com.github.aguther.dds.examples.routing.dynamic.command.RoutingServiceCommander;
import com.github.aguther.dds.examples.routing.dynamic.observer.DynamicPartitionObserver;
import com.github.aguther.dds.util.AutoEnableCreatedEntitiesHelper;
import com.github.aguther.dds.util.Slf4jDdsLogger;
import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.domain.DomainParticipantQos;
import com.rti.dds.infrastructure.Duration_t;
import com.rti.dds.infrastructure.ServiceQosPolicyKind;
import com.rti.dds.infrastructure.StatusKind;
import com.rti.routingservice.RoutingService;
import com.rti.routingservice.RoutingServiceProperty;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicRouting {

  private static final String ROUTING_SERVICE_NAME;

  private static final Logger log;

  private static boolean shouldTerminate;

  static {
    ROUTING_SERVICE_NAME = "dds-examples-routing-dynamic";

    log = LoggerFactory.getLogger(DynamicRouting.class);
  }

  public static void main(String[] args) throws InterruptedException {

    // register shutdown hook
    registerShutdownHook();

    // register logger DDS messages
    try {
      Slf4jDdsLogger.createRegisterLogger();
    } catch (IOException e) {
      log.error("Failed to create and register DDS logging device.", e);
      return;
    }

    log.info("Starting routing service");

    // setup routing service properties
    final RoutingServiceProperty routingServiceProperty = new RoutingServiceProperty();
    routingServiceProperty.cfgFile = "routing.xml";
    routingServiceProperty.serviceName = ROUTING_SERVICE_NAME;
    routingServiceProperty.applicationName = routingServiceProperty.serviceName;
    routingServiceProperty.serviceVerbosity = 3;

    // create routing service instance
    try (RoutingService routingService = new RoutingService(routingServiceProperty)) {

      // start routing service
      routingService.start();
      log.info("Routing service was started");

      // start dynamic routing
      startupDynamicRouting();

      while (!shouldTerminate) {
        Thread.sleep(1000);
      }

      // stop routing service
      routingService.stop();
    }
  }

  private static void registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown signal received...");
      shouldTerminate = true;
    }));
  }

  private static void startupDynamicRouting() {
    log.info("Starting dynamic routing");

    // create domain participant for administration interface
    DomainParticipant domainParticipantAdministration = createRemoteAdministrationDomainParticipant(0);

    // create routing service administration
    RoutingServiceCommander routingServiceCommander = new RoutingServiceCommander(domainParticipantAdministration);

    // wait for routing service to be discovered
    log.info("Waiting for remote administration interface of routing service to be discovered");
    if (routingServiceCommander.waitForRoutingService(ROUTING_SERVICE_NAME, new Duration_t(30, 0))) {
      log.info("Remote administration interface of routing service was discovered");
    } else {
      log.error("Remote administration interface of routing service could not be discovered within time out");
    }

    // disable auto enable of entities
    AutoEnableCreatedEntitiesHelper.disable();

    // create domain participant for discovery
    DomainParticipant domainParticipantDiscovery = createDiscoveryDomainParticipant(0);

    // create dynamic partition observer
    DynamicPartitionObserver dynamicPartitionObserver = new DynamicPartitionObserver(domainParticipantDiscovery);
    // add filters to dynamic partition observer
    dynamicPartitionObserver.addFilter(new RtiTopicFilter());
    dynamicPartitionObserver.addFilter(new RoutingServiceEntitiesFilter());
    dynamicPartitionObserver.addFilter(new RoutingServiceGroupEntitiesFilter(ROUTING_SERVICE_NAME));
    dynamicPartitionObserver.addFilter(new WildcardPartitionFilter());
    // add listener to dynamic partition observer
    dynamicPartitionObserver.addListener(new DynamicRoutingCommander(routingServiceCommander, ROUTING_SERVICE_NAME));

    // create new publication observer
    PublicationObserver publicationObserver = new PublicationObserver(domainParticipantDiscovery);
    publicationObserver.addListener(dynamicPartitionObserver);

    // create new subscription observer
    SubscriptionObserver subscriptionObserver = new SubscriptionObserver(domainParticipantDiscovery);
    subscriptionObserver.addListener(dynamicPartitionObserver);

    // enable domain participant
    domainParticipantDiscovery.enable();

    // auto enable entities
    AutoEnableCreatedEntitiesHelper.enable();

    log.info("Dynamic routing was started");
  }

  private static DomainParticipant createRemoteAdministrationDomainParticipant(
      int domainId
  ) {
    return createDomainParticipant(domainId, "RTI Routing Service: remote administration");
  }

  private static DomainParticipant createDiscoveryDomainParticipant(
      int domainId
  ) {
    return createDomainParticipant(domainId, "RTI Routing Service: discovery");
  }

  private static DomainParticipant createDomainParticipant(
      int domainId,
      String participantName
  ) {
    // create default participant qos marked as routing service entity
    DomainParticipantQos domainParticipantQos = new DomainParticipantQos();
    DomainParticipantFactory.get_instance().get_default_participant_qos(domainParticipantQos);
    domainParticipantQos.service.kind = ServiceQosPolicyKind.ROUTING_SERVICE_QOS;
    domainParticipantQos.participant_name.name = participantName;

    // create domain participant for administration interface
    return DomainParticipantFactory.get_instance().create_participant(
        domainId,
        domainParticipantQos,
        null,
        StatusKind.STATUS_MASK_NONE
    );
  }
}