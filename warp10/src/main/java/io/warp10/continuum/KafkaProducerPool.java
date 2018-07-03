//
//   Copyright 2016  Cityzen Data
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.continuum;

import io.warp10.sensision.Sensision;

import java.util.Properties;
import java.util.concurrent.locks.LockSupport;

import kafka.producer.ProducerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;

public class KafkaProducerPool {
  
  /**
   * Pool of producers
   */
  private final KafkaProducer<byte[], byte[]>[] producers;
  private final String GET_METRIC_CLASS;
  private final String TIME_METRIC_CLASS;
  
  private int currentPoolSize = 0;

  public KafkaProducerPool(ProducerConfig config, int size, String GET_METRIC, String TIME_METRIC) {
    this.producers = new KafkaProducer[size];
    this.GET_METRIC_CLASS = GET_METRIC;
    this.TIME_METRIC_CLASS = TIME_METRIC;
    
    for (int i = 0; i < this.producers.length; i++) {

      Properties properties = new Properties(config.props().props());
      properties.put("key.serializer","org.apache.kafka.common.serialization.ByteArraySerializer");
      properties.put("value.serializer","org.apache.kafka.common.serialization.ByteArraySerializer");

      this.producers[i] = new KafkaProducer<byte[], byte[]>(properties);
    }
    
    this.currentPoolSize = this.producers.length;
  }
  
  public KafkaProducer<byte[],byte[]> getProducer() {

    //
    // We will count how long we wait for a producer
    //
    
    long nano = System.nanoTime();
    
    Sensision.update(this.GET_METRIC_CLASS, Sensision.EMPTY_LABELS, 1);
    
    while(true) {
      synchronized (this.producers) {
        if (this.currentPoolSize > 0) {
          //
          // hand out the producer at index 0
          //
          
          KafkaProducer<byte[],byte[]> producer = this.producers[0];

          //
          // Decrement current pool size
          //
          
          this.currentPoolSize--;
          
          //
          // Move the last element of the array at index 0
          //
          
          this.producers[0] = this.producers[this.currentPoolSize];
          this.producers[this.currentPoolSize] = null;

          //
          // Log waiting time
          //
          
          nano = System.nanoTime() - nano;          
          Sensision.update(this.TIME_METRIC_CLASS, Sensision.EMPTY_LABELS, nano);

          return producer;
        }
      }
      
      LockSupport.parkNanos(500000L);
    }    
  }
  
  public void recycleProducer(KafkaProducer<byte[],byte[]> producer) {

    if (this.currentPoolSize == this.producers.length) {
      throw new RuntimeException("Invalid call to recycleProducer, pool already full!");
    }
    
    synchronized (this.producers) {
      //
      // Add the recycled producer at the end of the pool
      //

      this.producers[this.currentPoolSize++] = producer;
    }
  }

}
