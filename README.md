# Change Data Capture (CDC) with Debezium & Hudi

A project that leverages Debezium CDC tools and Spark Structured Streaming with Hudi to write the MySQL data to a path
continuously.

## 1. Set up Debezium & Associated Components in Docker
Debezium is an open source distributed platform for change data capture in MySQL, PostgreSQL and etc. It basically reads
and write the binlog of the database to the Kafka topic which can later be read by the downstream ingestion tool.

The debezium is set up according to the [Debezium tutorial](https://debezium.io/documentation/reference/stable/tutorial.html)

For ease of setup, the Docker is used and the following command can be used to run all the required containers and check
the running container.
```
sh entrypoint.sh start

docker container ls
```
![Container](image/container.png?)

By default, a database 'auth' is created with two tables 'users' and 'authorities'

To view the binlog in the data, the following command can be used:
```
docker container exec -it mysql bash

// Inside the container
mysqlbinlog /var/lib/mysql/mysql-bin.000002
```

To check if the binlog is activated:
```
// To go into mysql
mysql -uroot -ppassword

// To check
show databases;
show variables like '%bin%';
select user from mysql.user;
```

To check if Debezium is working, run the following command and the output should be similar to the below:
```
curl -H "Accept:application/json" localhost:8083/
```
```
{"version":"3.3.1","commit":"e23c59d00e687ff5","kafka_cluster_id":"eH6tCtKKSA2INxvyBTWxpA"}
```

It is to note that in the Docker Debezium container, the OFFSET_STORAGE_TOPIC configuration is used to specify the
name of the Kafka topic where the connector stores its offsets, allowing it to resume from where it left off if it is
restarted. However, if you are using the Debezium Connect image in a Docker container, this configuration may not work
as expected. This is because the OFFSET_STORAGE_TOPIC configuration is typically set in the Debezium Connect
configuration file, which is mounted as a volume in the container. When the container is restarted, the configuration
file is not reloaded, so the new value for OFFSET_STORAGE_TOPIC is not picked up. Thus, we need to bind mount the
volume to store the config.

## 2. Create Debezium Connector and Ingestion Job

Run the class `CreateMain` directly from IDE with the arguments updated to create debezium connector and ingestion job:
```
// For COW
run(Array(
  "--config-path", "config/auth/users.conf",
  "--kafka-start-time", "-2",
  "--kafka-end-time", "-1",
  "--local", "--write-to-hudi"
))

// For MOR
run(Array(
  "--config-path", "config/auth/users_mor.conf",
  "--kafka-start-time", "-2",
  "--kafka-end-time", "-1",
  "--local", "--write-to-hudi"
))
```

The job will set up a Debezium connector based on the template ('template/connector.json') and the given config. It does
the same thing as the curl command:
```
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d @/Users/chongaih.hau/Desktop/change-data-capture/src/main/resources/auth_connector.json
```
It is important to note that the 'connector.class' should be set to be the class compatible to the database. Also, public key
retrieval needs to be permitted to allow Debezium to access database (
[reference1](https://medium.com/tech-learn-share/docker-mysql-access-denied-for-user-172-17-0-1-using-password-yes-c5eadad582d3),
[reference2](https://rmoff.net/2019/10/23/debezium-mysql-v8-public-key-retrieval-is-not-allowed/))

To check if connector is running, run the following command and the output should be similar to the below:
```
curl -H "Accept:application/json" localhost:8083/connectors/
```
```
// Empty list if no connector submitted before
["auth-connector"]
```

On top of the connector, the job will set up a Spark Structured Streaming job which consumes from the
binlog Kafka topic with naming convention ({topic.prefix}.{db}.{table}). When there is no checkpoint, the
job will run from the set Kafka offset, if not, it will restore from the checkpoint. Users should find the
difference in log:
```
// restart from checkpoint
Resuming at batch 4 with committed offsets ...

// new checkpoint
Starting checkpoint trigger. Current epoch ...
```

There are few Hudi configuration being set ([all Hudi configurations](https://hudi.apache.org/docs/configurations/)):
* hoodie.combine.before.upsert
* hoodie.insert.shuffle.parallelism
* hoodie.upsert.shuffle.parallelism
* hoodie.table.name - For using Spark directly, if using HoodieDeltaStreamer, hoodie.datasource.write.table.name is used
* hoodie.datasource.write.table.type - COPY_ON_WRITE (create a new columnar file each time write) OR MERGE_ON_READ available
  (new record/ updated record will be written to the row based file - faster in write and only merged to columnar file
  upon read, the row based file will be merged during automatic compaction (hoodie.clear.policy)/
  manual compaction (call compact method in Hudi API)).
* precombine.field - precombine record with the same record key before actual write
* hoodie.datasource.write.recordkey.field - simialr to primary key
* hoodie.datasource.write.partitionpath.field - column used to partition data
* path - hudi parquet path to write to
* hoodie.keep.max.commit & hoodie.keep.min.commit - controls how many commits can exist in the table at the same time
* hoodie.cleaner.policy - policy used to clean old commit during compaction
* hoodie.cleaner.commits.retained - a minimum number of commits are always retained, even if they are older than hoodie.keep.max.commits

It should also be noted that a column '_hoodie_is_deleted' is added to indicate if the data should be removed during
compaction.

For COW, each commit creates a new parquet file. If the number committed file is larger than "hoodie.keep.max.commits",
a cleaning process will be performed and keep the latest committed up to "hoodie.keep.min.commits".

For MOR, commit of insert will create a new parquet file while any other changes will create a log under the same HUDI
path which will be merged into the parquet file when issue incremental or snapshot query.

## 3. Check Debezium and Hudi Operation
* Check MySQL data
    ```
    docker container exec -it mysql bash -c "mysql -uroot -ppassword -e 'use auth; select * from users'"
    ```

* Check kafka topic content after the data update, Kafka topic format ({topic.prefix}.{db}.{table})
    ```
    docker container exec -it kafka bash -c "kafka-console-consumer --bootstrap-server kafka:9092 --topic dbserver1.auth.users"
    ```
  Check using local Kafka, note the bootstrap server
    ```
    bin/kafka-console-consumer.sh  --bootstrap-server localhost:29092 --topic dbserver1.auth.users
    ````

* Check data insertion
    ```
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; INSERT INTO users (username, password, enabled, country) VALUES ('user3', 'password3', true, 'SG');"
    ```
  Kafka output
    ```
    {"schema":{"type":"struct","fields":[{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"before"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"after"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"version"},{"type":"string","optional":false,"field":"connector"},{"type":"string","optional":false,"field":"name"},{"type":"int64","optional":false,"field":"ts_ms"},{"type":"string","optional":true,"name":"io.debezium.data.Enum","version":1,"parameters":{"allowed":"true,last,false,incremental"},"default":"false","field":"snapshot"},{"type":"string","optional":false,"field":"db"},{"type":"string","optional":true,"field":"sequence"},{"type":"string","optional":true,"field":"table"},{"type":"int64","optional":false,"field":"server_id"},{"type":"string","optional":true,"field":"gtid"},{"type":"string","optional":false,"field":"file"},{"type":"int64","optional":false,"field":"pos"},{"type":"int32","optional":false,"field":"row"},{"type":"int64","optional":true,"field":"thread"},{"type":"string","optional":true,"field":"query"}],"optional":false,"name":"io.debezium.connector.mysql.Source","field":"source"},{"type":"string","optional":false,"field":"op"},{"type":"int64","optional":true,"field":"ts_ms"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"id"},{"type":"int64","optional":false,"field":"total_order"},{"type":"int64","optional":false,"field":"data_collection_order"}],"optional":true,"name":"event.block","version":1,"field":"transaction"}],"optional":false,"name":"dbserver1.auth.users.Envelope","version":1},"payload":{"before":null,"after":{"username":"user3","password":"password3","enabled":1},"source":{"version":"2.1.3.Final","connector":"mysql","name":"dbserver1","ts_ms":1680662234000,"snapshot":"false","db":"auth","sequence":null,"table":"users","server_id":1,"gtid":null,"file":"mysql-bin.000003","pos":1837,"row":0,"thread":124,"query":null},"op":"c","ts_ms":1680662234282,"transaction":null}}
    ```

* Check data update
    ```
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; update users set password='password4' where username='user3';"
    ```
  Kafka output
    ```
    {"schema":{"type":"struct","fields":[{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"before"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"after"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"version"},{"type":"string","optional":false,"field":"connector"},{"type":"string","optional":false,"field":"name"},{"type":"int64","optional":false,"field":"ts_ms"},{"type":"string","optional":true,"name":"io.debezium.data.Enum","version":1,"parameters":{"allowed":"true,last,false,incremental"},"default":"false","field":"snapshot"},{"type":"string","optional":false,"field":"db"},{"type":"string","optional":true,"field":"sequence"},{"type":"string","optional":true,"field":"table"},{"type":"int64","optional":false,"field":"server_id"},{"type":"string","optional":true,"field":"gtid"},{"type":"string","optional":false,"field":"file"},{"type":"int64","optional":false,"field":"pos"},{"type":"int32","optional":false,"field":"row"},{"type":"int64","optional":true,"field":"thread"},{"type":"string","optional":true,"field":"query"}],"optional":false,"name":"io.debezium.connector.mysql.Source","field":"source"},{"type":"string","optional":false,"field":"op"},{"type":"int64","optional":true,"field":"ts_ms"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"id"},{"type":"int64","optional":false,"field":"total_order"},{"type":"int64","optional":false,"field":"data_collection_order"}],"optional":true,"name":"event.block","version":1,"field":"transaction"}],"optional":false,"name":"dbserver1.auth.users.Envelope","version":1},"payload":{"before":{"username":"user3","password":"password3","enabled":1},"after":{"username":"user3","password":"password4","enabled":1},"source":{"version":"2.1.3.Final","connector":"mysql","name":"dbserver1","ts_ms":1680662367000,"snapshot":"false","db":"auth","sequence":null,"table":"users","server_id":1,"gtid":null,"file":"mysql-bin.000003","pos":2149,"row":0,"thread":124,"query":null},"op":"u","ts_ms":1680662367299,"transaction":null}}
    ```

* Check data delete
    ```
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; delete from users where username='user3';"
    ```
  Kafka output
    ```
    {"schema":{"type":"struct","fields":[{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"before"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"after"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"version"},{"type":"string","optional":false,"field":"connector"},{"type":"string","optional":false,"field":"name"},{"type":"int64","optional":false,"field":"ts_ms"},{"type":"string","optional":true,"name":"io.debezium.data.Enum","version":1,"parameters":{"allowed":"true,last,false,incremental"},"default":"false","field":"snapshot"},{"type":"string","optional":false,"field":"db"},{"type":"string","optional":true,"field":"sequence"},{"type":"string","optional":true,"field":"table"},{"type":"int64","optional":false,"field":"server_id"},{"type":"string","optional":true,"field":"gtid"},{"type":"string","optional":false,"field":"file"},{"type":"int64","optional":false,"field":"pos"},{"type":"int32","optional":false,"field":"row"},{"type":"int64","optional":true,"field":"thread"},{"type":"string","optional":true,"field":"query"}],"optional":false,"name":"io.debezium.connector.mysql.Source","field":"source"},{"type":"string","optional":false,"field":"op"},{"type":"int64","optional":true,"field":"ts_ms"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"id"},{"type":"int64","optional":false,"field":"total_order"},{"type":"int64","optional":false,"field":"data_collection_order"}],"optional":true,"name":"event.block","version":1,"field":"transaction"}],"optional":false,"name":"dbserver1.auth.users.Envelope","version":1},"payload":{"before":{"username":"user3","password":"password4","enabled":1},"after":null,"source":{"version":"2.1.3.Final","connector":"mysql","name":"dbserver1","ts_ms":1680662476000,"snapshot":"false","db":"auth","sequence":null,"table":"users","server_id":1,"gtid":null,"file":"mysql-bin.000003","pos":2472,"row":0,"thread":124,"query":null},"op":"d","ts_ms":1680662476552,"transaction":null}}
    ```

Check the Hudi evolution of schem
```
// update schema
docker container exec -it mysql mysql -uroot -ppassword -e "use auth; alter table users add number INTEGER(255);"

// insert data after schema update
docker container exec -it mysql mysql -uroot -ppassword -e "use auth; INSERT INTO users (username, password, enabled, country, number) VALUES ('user3', 'password3', true, 'SG', 50);"
```

Check the data Hudi parquet path
```
// Install tools
pip3 install parquet-tools

// Show Hudi parquet
parquet-tools show <path to hudi parquet file>.parquet
```

## 4. Query
Run the class `QueryMain` directly from IDE with the arguments updated:
```
// COW
// Snapshot query
run(Array(
  "--config-path", "config/auth/users.conf",
  "--local"
))

// Incremental query
run(Array(
  "--config-path", "config/auth/users.conf",
  "--local",
  "--incremental",
  "--begin-instant-time", "20230731113000"
))

// MOR
// Snapshot query
run(Array(
  "--config-path", "config/auth/users_mor.conf",
  "--local"
))

// Incremental query
run(Array(
  "--config-path", "config/auth/users_mor.conf",
  "--local",
  "--incremental",
  "--begin-instant-time", "20230731133820"
))
```

The job can run three queries:
* Incremental - contains only part of the updated data from the begin-instant-time (committed time of the files)
  ```
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+---------+-------+----------+-------+------------------+
  |_hoodie_commit_time|_hoodie_commit_seqno |_hoodie_record_key|_hoodie_partition_path|_hoodie_file_name                                                         |username|password |enabled|ts        |country|_hoodie_is_deleted|
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+---------+-------+----------+-------+------------------+
  |20230802092010136  |20230802092010136_0_3|user3             |country=SG            |101bfefc-b624-4a94-9441-fedf8a8066b7-0_0-225-224_20230802092012474.parquet|user3   |password6|1      |1690938747|SG     |false             |
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+---------+-------+----------+-------+------------------+
  ```
* Snapshot - contains all the latest updated snapshot data
  ```
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+------------------------------------------------------------+-------+----------+-------+------------------+
  |20230802091152340  |20230802091152340_0_0|user1             |country=SG            |101bfefc-b624-4a94-9441-fedf8a8066b7-0_0-225-224_20230802092012474.parquet|user1   |$2a$10$W9jd1d6sVe6dNKxeYzTlZuuKovX5rHj36zHnrtkVOcyFI/jhc7ASW|1      |1690938612|SG     |false             |
  |20230802092010136  |20230802092010136_0_3|user3             |country=SG            |101bfefc-b624-4a94-9441-fedf8a8066b7-0_0-225-224_20230802092012474.parquet|user3   |password6                                                   |1      |1690938747|SG     |false             |
  |20230802091152340  |20230802091152340_1_0|user2             |country=ID            |b90b40fe-a4a6-48b0-97dd-fb71086d0735-0_1-14-20_20230802091152340.parquet  |user2   |$2a$10$OOHTa4Hm1fAnbprRTfi.teewvskNUO2jFpGEe7w.Xevhmi33OBG2K|1      |1690938612|ID     |false             |
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+------------------------------------------------------------+-------+----------+-------+------------------+
  ```
* Time travel - contains all the snapshot data up to a certain commit time (note the password difference of user3)
  ```
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+------------------------------------------------------------+-------+----------+-------+------------------+
  |_hoodie_commit_time|_hoodie_commit_seqno |_hoodie_record_key|_hoodie_partition_path|_hoodie_file_name                                                         |username|password                                                    |enabled|ts        |country|_hoodie_is_deleted|
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+------------------------------------------------------------+-------+----------+-------+------------------+
  |20230802091152340  |20230802091152340_0_0|user1             |country=SG            |101bfefc-b624-4a94-9441-fedf8a8066b7-0_0-123-129_20230802091307840.parquet|user1   |$2a$10$W9jd1d6sVe6dNKxeYzTlZuuKovX5rHj36zHnrtkVOcyFI/jhc7ASW|1      |1690938612|SG     |false             |
  |20230802091305165  |20230802091305165_0_1|user3             |country=SG            |101bfefc-b624-4a94-9441-fedf8a8066b7-0_0-123-129_20230802091307840.parquet|user3   |password4                                                   |1      |1690938747|SG     |false             |
  |20230802091152340  |20230802091152340_1_0|user2             |country=ID            |b90b40fe-a4a6-48b0-97dd-fb71086d0735-0_1-14-20_20230802091152340.parquet  |user2   |$2a$10$OOHTa4Hm1fAnbprRTfi.teewvskNUO2jFpGEe7w.Xevhmi33OBG2K|1      |1690938612|ID     |false             |
  +-------------------+---------------------+------------------+----------------------+--------------------------------------------------------------------------+--------+------------------------------------------------------------+-------+----------+-------+------------------+
  ```

## 5. Stop & Remove the Container
```
sh entrypoint.sh stop
```

## Others
* Restart Debezium Kafka Connect to check if it restarts from the last offset
    ```
    // Restart Debezium connect
    docker container kill connect
    sh entrypoint.sh start
    
    // Check connector - the connector should be there
    curl -H "Accept:application/json" localhost:8083/connectors/
    
    // Check if the Debezium reingest binlog 
    // Since the offset is stored in OFFSET_STORAGE_TOPIC and the config is bind mounted
    // any data that has been ingested should not be written to Kafka again
    docker container exec -it kafka bash -c "kafka-console-consumer --bootstrap-server kafka:9092 --topic dbserver1.auth.users" 
    ```

* Check Hive metastore
  Check password in hive metastore at '/opt/hive/conf/hive hive-default.xml.template' according to [reference](https://repost.aws/knowledge-center/postgresql-hive-metastore-emr)
    ```
    docker container exec -it hive-metastore-postgresql bash
    psql -U hive -d metastore
    \d - show table
    ```
* Hudi
  * If the partition value is updated, the data in the old partition won't be removed
  * hoodie.table.name - used when reading or writing to a Hoodie table through the Spark DataFrame or Dataset API
    hoodie.datasource.write.table.name is used when writing data to a Hoodie table using the HoodieDeltaStreamer or the HoodieSparkConnector
  * cannot issue delete if the hoodie table is empty (eg. delete already in the binlog when first runnig hudi pipeline)
    org.apache.hudi.exception.HoodieException: Deletes issued without any prior commits
  * hoodie support schema evolution - can update the mysql db and then spark code case class to take effect
  * to include both upsert and delete in 1 query, we need to add column _hoodie_is_deleted and set hoodie.compaction.strategy to be org.apache.hudi.compaction.strategy.ExcludeHoodieDeletesCompactionStrategy

## Reference
* [Debezium Tutorial](https://debezium.io/documentation/reference/stable/tutorial.html#starting-kafka-connect)
* [Structured streaming & ACL](https://spark.apache.org/docs/latest/structured-streaming-kafka-integration.html)
* Hudi
  * [Hive & HDFS](https://github.com/apache/hudi/blob/master/docker/compose/docker-compose_hadoop284_hive233_spark244.yml)
  * [Hudi Docker](https://hudi.apache.org/docs/docker_demo/)
  * [Spark](https://hudi.apache.org/docs/quick-start-guide)
    * If there is already a table, we cannot overwrite the setting - [reference](https://stackoverflow.com/questions/75293169/spark-streaming-hudi-hoodieexception-config-conflictkeycurrent-valueexisting)
  * [schema evolution](https://hudi.apache.org/docs/schema_evolution/)
  * snapshot vs incremental - Snapshot query is used to retrieve the latest view of data in the table as of a certain point in time.
    The snapshot query is performed on a read-optimized storage layer (for example, Apache Parquet), and it scans
    all data files and applies any updates to generate the latest view of data.
    On the other hand, incremental query is used to retrieve only the changes made to the data since the last query.
    It is performed on a write-optimized storage layer (for example, Apache Avro), and it scans only the data files
    that have been modified since the last query. This makes it much faster and more efficient than a snapshot query
    when dealing with large datasets that are frequently updated.
* Spark
  * Spark streaming cannot infer schema directly - [reference](https://stackoverflow.com/questions/48361177/spark-structured-streaming-kafka-convert-json-without-schema-infer-schema)
  * Connect spark to Hive - [reference](https://sparkbyexamples.com/spark/how-to-connect-spark-to-remote-hive/)
* [Typetag](https://stackoverflow.com/questions/12218641/what-is-a-typetag-and-how-do-i-use-it)
* Jackson JSON generic type reference - [reference](https://stackoverflow.com/questions/6846244/jackson-and-generic-type-reference)
* Hadoop
  * Read and write file - [reference](https://www.google.com/search?q=java+read+and+write+file+in+hdfs+examole&rlz=1C5GCEM_enSG1049SG1049&sxsrf=APwXEdfrRERmo2KAppUnl7EtQVV17jqG5Q%3A1681433793490&ei=waQ4ZIPPHY_C4-EPtrG-6As&ved=0ahUKEwjDs-X_lKj-AhUP4TgGHbaYD70Q4dUDCA8&uact=5&oq=java+read+and+write+file+in+hdfs+examole&gs_lcp=Cgxnd3Mtd2l6LXNlcnAQAzIHCCEQoAEQCjoKCAAQRxDWBBCwAzoKCAAQigUQsAMQQzoFCAAQgAQ6BggAEBYQHjoICAAQigUQhgM6BQghEKABOggIIRAWEB4QHToECCEQFUoECEEYAFDOAVibGGC4GWgBcAF4AIABYIgB0gmSAQIxNpgBAKABAcgBCcABAQ&sclient=gws-wiz-serp)
  * Load config on hdfs - [reference](https://stackoverflow.com/questions/38123764/how-to-load-typesafe-configfactory-from-file-on-hdfs)
  * Multi-node cluster - by default if datanode sends heartbeat to namenode, namenode will know which datanode exists - [reference](https://www.tutorialspoint.com/hadoop/hadoop_multi_node_cluster.htm)
  * Access hadoop container from macbook localhost - [reference](https://github.com/big-data-europe/docker-hadoop/issues/98)

## Future Work
1. Currently, macbook cannot access docker HDFS directly - https://github.com/big-data-europe/docker-hadoop/issues/98
2. Schema needs to be declared explicitly in structured streaming --> change to batch for schema inference
3. Register Hudi parquet path as Hive table
