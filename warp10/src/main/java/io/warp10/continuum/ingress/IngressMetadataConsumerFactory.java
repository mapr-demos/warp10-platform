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

package io.warp10.continuum.ingress;

import io.warp10.continuum.Configuration;
import io.warp10.continuum.KafkaOffsetCounters;
import io.warp10.continuum.KafkaSynchronizedConsumerPool;
import io.warp10.continuum.KafkaSynchronizedConsumerPool.ConsumerFactory;
import io.warp10.continuum.sensision.SensisionConstants;
import io.warp10.continuum.store.thrift.data.Metadata;
import io.warp10.crypto.CryptoUtils;
import io.warp10.sensision.Sensision;

import java.math.BigInteger;
import java.util.Arrays;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.protocol.TCompactProtocol;

public class IngressMetadataConsumerFactory implements ConsumerFactory {
  
  private final Ingress ingress;
  
  public IngressMetadataConsumerFactory(Ingress ingress) {
    this.ingress = ingress;
  }
  
  @Override
  public Runnable getConsumer(final KafkaSynchronizedConsumerPool pool, final KafkaConsumer<byte[], byte[]> consumer) {

    return new Runnable() {          
      @Override
      public void run() {

        // Iterate on the messages
        TDeserializer deserializer = new TDeserializer(new TCompactProtocol.Factory());

        KafkaOffsetCounters counters = pool.getCounters();
        
        try {
          while (true) {
            ConsumerRecords<byte[], byte[]> records =  consumer.poll(500L);
            for (ConsumerRecord<byte[], byte[]> record : records) {
              counters.count(record.partition(), record.offset());
              
              byte[] data = record.value();

              Sensision.update(SensisionConstants.SENSISION_CLASS_WARP_INGRESS_KAFKA_META_IN_MESSAGES, Sensision.EMPTY_LABELS, 1);
              Sensision.update(SensisionConstants.SENSISION_CLASS_WARP_INGRESS_KAFKA_META_IN_BYTES, Sensision.EMPTY_LABELS, data.length);
              
              if (null != ingress.SIPHASH_KAFKA_META) {
                data = CryptoUtils.removeMAC(ingress.SIPHASH_KAFKA_META, data);
              }
              
              // Skip data whose MAC was not verified successfully
              if (null == data) {
                Sensision.update(SensisionConstants.SENSISION_CLASS_WARP_INGRESS_KAFKA_META_IN_INVALIDMACS, Sensision.EMPTY_LABELS, 1);
                continue;
              }
              
              // Unwrap data if need be
              if (null != ingress.AES_KAFKA_META) {
                data = CryptoUtils.unwrap(ingress.AES_KAFKA_META, data);
              }
              
              // Skip data that was not unwrapped successfuly
              if (null == data) {
                Sensision.update(SensisionConstants.SENSISION_CLASS_WARP_INGRESS_KAFKA_META_IN_INVALIDCIPHERS, Sensision.EMPTY_LABELS, 1);
                continue;
              }
              
              //
              // Extract Metadata
              //
              
              //
              // TODO(hbs): We could check that metadata class/labels Id match those of the key, but
              // since it was wrapped/authenticated, we suppose it's ok.
              //
                          
              byte[] clslblsBytes = Arrays.copyOf(data, 16);
              BigInteger clslblsId = new BigInteger(clslblsBytes);
              
              byte[] metadataBytes = Arrays.copyOfRange(data, 16, data.length);

              Metadata metadata = new Metadata();
              deserializer.deserialize(metadata, metadataBytes);
              
              //
              // Only handle DELETE and METADATA sources
              // We treat those two types of updates the same way, by removing the cache entry
              // for the corresponding Metadata. By doing so we simplify handling
              //
              // TODO(hbs): update metadata cache when receiving Metadata from '/meta'?
              //
              
              if (Configuration.INGRESS_METADATA_DELETE_SOURCE.equals(metadata.getSource())) {
                //
                // Remove entry from Metadata cache
                //
                
                synchronized(ingress.metadataCache) {
                  ingress.metadataCache.remove(clslblsId);
                }
                continue;
              } else if (Configuration.INGRESS_METADATA_UPDATE_ENDPOINT.equals(metadata.getSource())) {
                //
                // //Update cache with new metadata
                // Remove entry from Metadata cache
                //
                
                //ingress.metadataCache.put(clslblsId, metadata);
                synchronized(ingress.metadataCache) {
                  ingress.metadataCache.remove(clslblsId);
                }
                continue;
              } else {
                continue;
              }
            }
          }        
        } catch (Throwable t) {
          t.printStackTrace(System.err);
        } finally {
          // Set abort to true in case we exit the 'run' method
          pool.getAbort().set(true);
        }
      }
    };
  }
}
