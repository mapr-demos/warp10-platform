# Running on MapR

## Overview

This document explains how to build and run Warp 10 on MapR 6.0.1 Cluster. The Warp 10 Platform is designed to collect, 
store and manipulate sensor data. Original distributed version of the platform uses Apache HBase as a scalable storage 
layer. Another infrastructure component of Warp 10 is Apache Kafka, which acts as the data hub of the platform and a 
great shock absorber which makes all components loosely coupled and enables them to scale and be updated without 
interrupting the overall service. Branch `1.2.18-mapr` contains ported version of the platform, which uses MapR-DB 
Binary and MapR Streams instead of HBase and Kafka.

## Build

* Clone repository

```
$ git clone https://github.com/mapr-demos/warp10-platform
```

* Build

Build Warp10 according to [building](/BUILDING.md) document. Note that you can build project even without specifying 
Bintray credentials. In such case Gradle build will result in `warp10/archive/` directory, although the build will be 
marked as failed.

Or execute the following commands to create warp10 archive without Bintray publication:
```
./gradlew warp10:generateThrift
./gradlew token:generateThrift
./gradlew crypto:clean crypto:install   
./gradlew token:clean token:install
./gradlew warp10:clean warp10:createTarArchive
```


## Install

Copy contents of `warp10/archive/` directory into one of the MapR Cluster nodes:
```
$ scp -r warp10/archive/warp10-1.2.18-*/ mapr@yournode:/home/mapr
```

## Configure

`templates/conf-distributed.template` configuration file is ready to be used on MapR Cluster. Now we must create 
MapR Stream with topics and MapR-DB Binary table:

* Create MapR Stream with topics 

Run the following command on MapR Cluster node:
```
[mapr@yournode ~]$ maprcli stream create -path /apps/warp 
[mapr@yournode ~]$ maprcli stream topic create -path /apps/warp -topic data
[mapr@yournode ~]$ maprcli stream topic create -path /apps/warp -topic metadata
[mapr@yournode ~]$ maprcli stream topic create -path /apps/warp -topic webcall
[mapr@yournode ~]$ maprcli stream topic create -path /apps/warp -topic throttling
[mapr@yournode ~]$ maprcli stream topic create -path /apps/warp -topic plasmafe1
[mapr@yournode ~]$ maprcli stream topic create -path /apps/warp -topic runner
```

* Create storage table using `hbase shell` tool

```
[mapr@yournode ~]$ hbase shell

hbase(main):001:0> create '/continuum', {NAME => 'm', DATA_BLOCK_ENCODING => 'FAST_DIFF', BLOOMFILTER => 'NONE', REPLICATION_SCOPE => '0', VERSIONS => '1', COMPRESSION => 'NONE', MIN_VERSIONS => '0', TTL => '2147483647', KEEP_DELETED_CELLS => 'false', BLOCKSIZE => '65536', IN_MEMORY => 'false', BLOCKCACHE => 'true'}, {NAME => 'v', DATA_BLOCK_ENCODING => 'FAST_DIFF', BLOOMFILTER => 'NONE', REPLICATION_SCOPE => '0', VERSIONS=> '1', COMPRESSION => 'LZ4', MIN_VERSIONS => '0', TTL => '2147483647', KEEP_DELETED_CELLS => 'false', BLOCKSIZE => '65536', IN_MEMORY =>'false', BLOCKCACHE => 'true'}, {MAX_FILESIZE => '10737418240', REGION_REPLICATION => 1}
```


## Run

### Generate read & write tokens

The Warp 10 platform is built with a robust security model that allows you to have a tight control of who has the right 
to write and/or read data. The model is structured around the concepts of data producer, data owner and application, 
and `WRITE` and `READ` tokens.

You can generate your own write and read tokens using `worf` tool:

```
[mapr@yournode ~]$ cd warp10-1.2.18-*
[mapr@yournode ~]$ java -cp bin/warp10-1.2.18-*.jar io.warp10.worf.Worf  -i templates/conf-distributed.template

# GENERATE 'READ' TOKEN:
warp10>encodeToken
encodeToken/token type (read|write)>read
encodeToken/application name>test
encodeToken/application names - optional (app1,app2)>test
encodeToken/data producer UUID (you can create a new one by typing 'uuidgen') >uuidgen
uuid generated=4ee54ebb-80f9-4532-8cc0-8452211c40c9
encodeToken/data producers - optional (UUID1,UUID2)>
encodeToken/data owners UUID>
encodeToken/token time to live (TTL) in ms >9999999999
encodeToken/OPTIONAL fixed labels (key1=value1,key2=value2) >
encodeToken(generate | cancel)>generate
token=SjdL_kHZwYSoqa4.a8RJ9eHXqhKgeey6H4IpJJw7bgnTEPyvqLTcfD5t263e0j94i21R93iFvR4Tns3iWoJu7AKQ6ykEMjHXaNFevzgy2rBstZu3yT.zs.
tokenIdent=05f67d7073c20340
application name=test
producer=4ee54ebb-80f9-4532-8cc0-8452211c40c9
authorizations (applications)=[test]
authorizations (owners)=[4ee54ebb-80f9-4532-8cc0-8452211c40c9]
authorizations (producers)=[]
ttl=9999999999

# GENERATE 'WRITE' TOKEN:
warp10>encodeToken
encodeToken/token type (read|write)>write
encodeToken/application name>test
encodeToken/data producer UUID (you can create a new one by typing 'uuidgen') >4ee54ebb-80f9-4532-8cc0-8452211c40c9
uuid generated=7f71642c-4fb1-44e5-bfa4-a595c19e4cec
encodeToken/data owner UUID (you can create a new one by typing 'uuidgen') >4ee54ebb-80f9-4532-8cc0-8452211c40c9
encodeToken/token time to live (TTL) in ms >9999999999
encodeToken/OPTIONAL fixed labels (key1=value1,key2=value2) >
encodeToken(generate | cancel)>generate
token=t9QiOaL8FJ5oYt4ZefJVn6tNkxNEdLz3GdFT.8iTn0zPhYnMeP6b4VlQ24Eebn05IXxiLiGGH5J7b1xc5KQpIeP6.wA2ad8AB39dGD8pE9J
tokenIdent=020720cd1289c51d
application name=test
producer=7f71642c-4fb1-44e5-bfa4-a595c19e4cec
owner=c7da21ef-277a-4e62-bf96-09e27153cf67
ttl=9999999999

```


### Start Warp10 platform

By default `` configuration file enables `ingress,directory,store,egress` required components. Now, you can run Warp10 
using the following command:
```
[mapr@yournode ~]$ cd warp10-1.2.18-*/
[mapr@yournode ~]$ java -Djava.security.auth.login.config=/opt/mapr/conf/mapr.login.conf -Dzookeeper.sasl.clientconfig=Client_simple -Dzookeeper.saslprovider=com.mapr.security.simplesasl.SimpleSaslProvider -Dlog4j.configuration=file:./etc/log4j.xml -cp bin/warp10-1.2.18-*.jar io.warp10.WarpDist ./templates/conf-distributed.template
```

### Verification

You can push test data to `ingress` using Warp WRITE token:
```
curl -H 'X-Warp10-Token: t9QiOaL8FJ5oYt4ZefJVn6tNkxNEdLz3GdFT.8iTn0zPhYnMeP6b4VlQ24Eebn05IXxiLiGGH5J7b1xc5KQpIeP6.wA2ad8AB39dGD8pE9J' --data-binary '// test{} T' http://localhost:8882/api/v0/update
```

If you check MapR Stream `data` topic, you should see that the message you identified above has been consumed:
```
[mapr@yournode ~]$ /opt/mapr/kafka/kafka-1.0.1/bin/kafka-console-consumer.sh --new-consumer --bootstrap-server willbeignored:9092 --topic /apps/warp:data --from-beginning
```

You can also scan `/continuum` table in HBase, it should have an entry:
```
[mapr@yournode ~]$ hbase shell
hbase(main):001:0> scan '/continuum'
```

You can check that your `egress` instance is working by running the following command using Warp READ token:
```
curl --data-binary "[ 'SjdL_kHZwYSoqa4.a8RJ9eHXqhKgeey6H4IpJJw7bgnTEPyvqLTcfD5t263e0j94i21R93iFvR4Tns3iWoJu7AKQ6ykEMjHXaNFevzgy2rBstZu3yT.zs.' 'test' {} NOW -1 ] FETCH" http://localhost:8881/api/v0/exec
```
It should return a Geo Time Series object with a single datapoint.

Congratulations, you now have a working Warp 10 distributed platform!
