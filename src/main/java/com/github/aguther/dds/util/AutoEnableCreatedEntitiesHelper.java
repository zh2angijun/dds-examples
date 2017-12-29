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

package com.github.aguther.dds.util;

import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.domain.DomainParticipantFactoryQos;

public class AutoEnableCreatedEntitiesHelper {

  private AutoEnableCreatedEntitiesHelper() {
  }

  public static void enable() {
    switchValueTo(true);
  }

  public static void disable() {
    switchValueTo(false);
  }

  private static void switchValueTo(
      final boolean value
  ) {
    // create new QoS object
    DomainParticipantFactoryQos domainParticipantFactoryQos = new DomainParticipantFactoryQos();

    // get current QoS from domain participant factory
    DomainParticipantFactory.get_instance().get_qos(domainParticipantFactoryQos);

    // update needed?
    if (domainParticipantFactoryQos.entity_factory.autoenable_created_entities != value) {
      // update value
      domainParticipantFactoryQos.entity_factory.autoenable_created_entities = value;

      // update QoS on domain participant factory
      DomainParticipantFactory.get_instance().set_qos(domainParticipantFactoryQos);
    }
  }
}
