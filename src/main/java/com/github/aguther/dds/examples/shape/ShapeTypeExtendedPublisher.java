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

package com.github.aguther.dds.examples.shape;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.infrastructure.Cookie_t;
import com.rti.dds.infrastructure.InstanceHandle_t;
import com.rti.dds.infrastructure.Locator_t;
import com.rti.dds.infrastructure.RETCODE_ERROR;
import com.rti.dds.infrastructure.StatusKind;
import com.rti.dds.publication.AcknowledgmentInfo;
import com.rti.dds.publication.DataWriter;
import com.rti.dds.publication.DataWriterListener;
import com.rti.dds.publication.LivelinessLostStatus;
import com.rti.dds.publication.OfferedDeadlineMissedStatus;
import com.rti.dds.publication.OfferedIncompatibleQosStatus;
import com.rti.dds.publication.PublicationMatchedStatus;
import com.rti.dds.publication.ReliableReaderActivityChangedStatus;
import com.rti.dds.publication.ReliableWriterCacheChangedStatus;
import com.rti.dds.publication.ServiceRequestAcceptedStatus;
import idl.ShapeTypeExtended;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShapeTypeExtendedPublisher implements Runnable, DataWriterListener {

  private static final Logger log = LoggerFactory.getLogger(ShapeTypeExtendedPublisher.class);

  private final DataWriter dataWriter;
  private final ShapeAttributes shapeAttributes;
  private final int sleepTime;

  private boolean shouldTerminate;
  private int xPosition;
  private int xSpeed;
  private int xPositionMin;
  private int xPositionMax;
  private int yPosition;
  private int ySpeed;
  private int yPositionMin;
  private int yPositionMax;

  ShapeTypeExtendedPublisher(
      final ShapeAttributes shapeAttributes,
      final DomainParticipant domainParticipant,
      final String dataWriterName,
      final int sleepTime
  ) {
    this(
        shapeAttributes,
        domainParticipant.lookup_datawriter_by_name(dataWriterName),
        sleepTime
    );
  }

  ShapeTypeExtendedPublisher(
      final ShapeAttributes shapeAttributes,
      final DataWriter dataWriter,
      final int sleepTime
  ) {
    checkNotNull(shapeAttributes, "Shape attributes must not be null");
    checkNotNull(dataWriter, "DataWriter must not be null");
    checkArgument(sleepTime >= 0, "Sleep time expected to be 0 or greater");

    this.shapeAttributes = shapeAttributes;
    this.dataWriter = dataWriter;
    this.sleepTime = sleepTime;

    this.xPositionMin = shapeAttributes.getSize() / 2;
    this.xPositionMax = 235 - shapeAttributes.getSize() / 2;
    this.xPosition = shapeAttributes.getSize() / 2;
    this.xSpeed = 1;

    this.yPositionMin = shapeAttributes.getSize() / 2;
    this.yPositionMax = 265 - shapeAttributes.getSize() / 2;
    this.yPosition = yPositionMin;
    this.ySpeed = 2;
  }

  void stop() {
    shouldTerminate = true;
  }

  @Override
  public void run() {
    log.info("Start sending ...");

    dataWriter.set_listener(this, StatusKind.STATUS_MASK_ALL);

    while (!shouldTerminate) {
      try {
        // create sample
        ShapeTypeExtended sample = generateSample();

        // log number of sample
        log.info(
            "Writing sample (x='{}', y='{}', color='{}', size='{}', fill='{}', angle='{}')",
            sample.x,
            sample.y,
            sample.color,
            sample.shapesize,
            sample.fillKind,
            sample.angle
        );

        // write sample
        dataWriter.write_untyped(sample, InstanceHandle_t.HANDLE_NIL);

        // wait some time
        if (sleepTime >= 0) {
          Thread.sleep(sleepTime);
        }

      } catch (RETCODE_ERROR e) {
        // log the problem and sTerminate the application
        log.error("Failed to write sample.", e);
      } catch (InterruptedException e) {
        log.error("Failed to wait.", e);
        Thread.currentThread().interrupt();
      }
    }

    dataWriter.set_listener(null, StatusKind.STATUS_MASK_NONE);

    log.info("... done.");
  }

  private ShapeTypeExtended generateSample() {
    // create sample
    ShapeTypeExtended sample = new ShapeTypeExtended();

    // calculate position
    xPosition = xPosition + xSpeed;
    if (xPosition >= xPositionMax) {
      xPosition = xPositionMax;
      xSpeed = xSpeed * -1;
    }
    if (xPosition <= xPositionMin) {
      xPosition = xPositionMin;
      xSpeed = xSpeed * -1;
    }
    yPosition = yPosition + ySpeed;
    if (yPosition >= yPositionMax) {
      yPosition = yPositionMax;
      ySpeed = ySpeed * -1;
    }
    if (yPosition <= yPositionMin) {
      yPosition = yPositionMin;
      ySpeed = ySpeed * -1;
    }

    // set properties
    sample.color = shapeAttributes.getColor();
    sample.shapesize = shapeAttributes.getSize();
    sample.x = xPosition;
    sample.y = yPosition;

    // set extended properties
    sample.fillKind = shapeAttributes.getFillKind();
    sample.angle = shapeAttributes.getAngle();

    // print sample
    if (log.isDebugEnabled()) {
      log.debug("Created sample: '{}'", sample.toString().replace("\n", " "));
    }

    // return the result
    return sample;
  }

  @Override
  public void on_offered_deadline_missed(
      final DataWriter dataWriter,
      final OfferedDeadlineMissedStatus offeredDeadlineMissedStatus
  ) {
    if (log.isWarnEnabled()) {
      log.warn("{}", offeredDeadlineMissedStatus.toString());
    }
  }

  @Override
  public void on_offered_incompatible_qos(
      final DataWriter dataWriter,
      final OfferedIncompatibleQosStatus offeredIncompatibleQosStatus
  ) {
    if (log.isWarnEnabled()) {
      log.warn("{}", offeredIncompatibleQosStatus.toString());
    }
  }

  @Override
  public void on_liveliness_lost(
      final DataWriter dataWriter,
      final LivelinessLostStatus livelinessLostStatus
  ) {
    if (log.isDebugEnabled()) {
      log.debug("{}", livelinessLostStatus.toString());
    }
  }

  @Override
  public void on_publication_matched(
      final DataWriter dataWriter,
      final PublicationMatchedStatus publicationMatchedStatus
  ) {
    if (log.isDebugEnabled()) {
      log.debug("{}", publicationMatchedStatus.toString());
    }
  }

  @Override
  public void on_reliable_writer_cache_changed(
      final DataWriter dataWriter,
      final ReliableWriterCacheChangedStatus reliableWriterCacheChangedStatus
  ) {
    if (log.isDebugEnabled()) {
      log.debug("{}", reliableWriterCacheChangedStatus.toString());
    }
  }

  @Override
  public void on_reliable_reader_activity_changed(
      final DataWriter dataWriter,
      final ReliableReaderActivityChangedStatus reliableReaderActivityChangedStatus
  ) {
    if (log.isDebugEnabled()) {
      log.debug("{}", reliableReaderActivityChangedStatus.toString());
    }
  }

  @Override
  public void on_destination_unreachable(
      final DataWriter dataWriter,
      final InstanceHandle_t instanceHandle,
      final Locator_t locator
  ) {
    if (log.isInfoEnabled()) {
      log.info("{}; {}", instanceHandle.toString(), locator.toString());
    }
  }

  @Override
  public Object on_data_request(
      final DataWriter dataWriter,
      final Cookie_t cookie
  ) {
    return null;
  }

  @Override
  public void on_data_return(
      final DataWriter dataWriter,
      final Object o,
      final Cookie_t cookie
  ) {
    if (log.isDebugEnabled()) {
      log.debug("{} {}", o.toString(), cookie.toString());
    }
  }

  @Override
  public void on_sample_removed(
      final DataWriter dataWriter,
      final Cookie_t cookie
  ) {
    if (log.isWarnEnabled()) {
      log.warn("{}", cookie.toString());
    }
  }

  @Override
  public void on_instance_replaced(
      final DataWriter dataWriter,
      final InstanceHandle_t instanceHandle
  ) {
    if (log.isWarnEnabled()) {
      log.warn("{}", instanceHandle.toString());
    }
  }

  @Override
  public void on_application_acknowledgment(
      final DataWriter dataWriter,
      final AcknowledgmentInfo acknowledgmentInfo
  ) {
    if (log.isInfoEnabled()) {
      log.info("{}", acknowledgmentInfo.toString());
    }
  }

  @Override
  public void on_service_request_accepted(
      final DataWriter dataWriter,
      final ServiceRequestAcceptedStatus serviceRequestAcceptedStatus
  ) {
    if (log.isInfoEnabled()) {
      log.info("{}", serviceRequestAcceptedStatus.toString());
    }
  }
}
