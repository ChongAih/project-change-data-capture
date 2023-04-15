debezium setup:
https://debezium.io/documentation/reference/stable/tutorial.html

sh entrypoint.sh start
sh entrypoint.sh stop

read binlog
    docker container exec -it mysql bash
    mysqlbinlog /var/lib/mysql/mysql-bin.000002

    mysql -uroot -ppassword
        show databases;
        show variables like '%bin%';
        select user from mysql.user;

curl -H "Accept:application/json" localhost:8083/
curl -H "Accept:application/json" localhost:8083/connectors/

grant access and allow public key retrieval
https://medium.com/tech-learn-share/docker-mysql-access-denied-for-user-172-17-0-1-using-password-yes-c5eadad582d3
https://rmoff.net/2019/10/23/debezium-mysql-v8-public-key-retrieval-is-not-allowed/
register connector - note the user and password
    curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d @/Users/chongaih.hau/Desktop/change-data-capture/src/main/resources/auth_connector.json
    docker container logs -f connect
    curl -H "Accept:application/json" localhost:8083/connectors/


## Check Debezium Kafka Content
Create debezium connector by running main script

check kafka topic content after the data update - {topic.prefix}.{db}.{table}
    docker container exec -it kafka bash -c "kafka-console-consumer --bootstrap-server kafka:9092 --topic dbserver1.auth.users"

check using local kafka
    bin/kafka-console-consumer.sh  --bootstrap-server localhost:29092 --topic dbserver1.auth.users

docker container exec -it mysql bash -c "mysql -uroot -ppassword -e 'use auth; select * from users'"

change database;
    use auth;

Values for the MySQL connector are c for create (or insert), u for update, d for delete, and r for read (in the case of a snapshot, need to process also).

insert data into mysql
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; INSERT INTO users (username, password, enabled, country) VALUES ('user3', 'password3', true, 'SG');"
    cmd:
        INSERT INTO users (username, password, enabled)
        VALUES
        ('user3', 'password3', true);
    kafka output:
        {"schema":{"type":"struct","fields":[{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"before"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"after"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"version"},{"type":"string","optional":false,"field":"connector"},{"type":"string","optional":false,"field":"name"},{"type":"int64","optional":false,"field":"ts_ms"},{"type":"string","optional":true,"name":"io.debezium.data.Enum","version":1,"parameters":{"allowed":"true,last,false,incremental"},"default":"false","field":"snapshot"},{"type":"string","optional":false,"field":"db"},{"type":"string","optional":true,"field":"sequence"},{"type":"string","optional":true,"field":"table"},{"type":"int64","optional":false,"field":"server_id"},{"type":"string","optional":true,"field":"gtid"},{"type":"string","optional":false,"field":"file"},{"type":"int64","optional":false,"field":"pos"},{"type":"int32","optional":false,"field":"row"},{"type":"int64","optional":true,"field":"thread"},{"type":"string","optional":true,"field":"query"}],"optional":false,"name":"io.debezium.connector.mysql.Source","field":"source"},{"type":"string","optional":false,"field":"op"},{"type":"int64","optional":true,"field":"ts_ms"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"id"},{"type":"int64","optional":false,"field":"total_order"},{"type":"int64","optional":false,"field":"data_collection_order"}],"optional":true,"name":"event.block","version":1,"field":"transaction"}],"optional":false,"name":"dbserver1.auth.users.Envelope","version":1},"payload":{"before":null,"after":{"username":"user3","password":"password3","enabled":1},"source":{"version":"2.1.3.Final","connector":"mysql","name":"dbserver1","ts_ms":1680662234000,"snapshot":"false","db":"auth","sequence":null,"table":"users","server_id":1,"gtid":null,"file":"mysql-bin.000003","pos":1837,"row":0,"thread":124,"query":null},"op":"c","ts_ms":1680662234282,"transaction":null}}

update data
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; update users set password='password4' where username='user3';"
    cmd:
        update users set password='password4' where username='user3';
    kafka output:
        {"schema":{"type":"struct","fields":[{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"before"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"after"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"version"},{"type":"string","optional":false,"field":"connector"},{"type":"string","optional":false,"field":"name"},{"type":"int64","optional":false,"field":"ts_ms"},{"type":"string","optional":true,"name":"io.debezium.data.Enum","version":1,"parameters":{"allowed":"true,last,false,incremental"},"default":"false","field":"snapshot"},{"type":"string","optional":false,"field":"db"},{"type":"string","optional":true,"field":"sequence"},{"type":"string","optional":true,"field":"table"},{"type":"int64","optional":false,"field":"server_id"},{"type":"string","optional":true,"field":"gtid"},{"type":"string","optional":false,"field":"file"},{"type":"int64","optional":false,"field":"pos"},{"type":"int32","optional":false,"field":"row"},{"type":"int64","optional":true,"field":"thread"},{"type":"string","optional":true,"field":"query"}],"optional":false,"name":"io.debezium.connector.mysql.Source","field":"source"},{"type":"string","optional":false,"field":"op"},{"type":"int64","optional":true,"field":"ts_ms"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"id"},{"type":"int64","optional":false,"field":"total_order"},{"type":"int64","optional":false,"field":"data_collection_order"}],"optional":true,"name":"event.block","version":1,"field":"transaction"}],"optional":false,"name":"dbserver1.auth.users.Envelope","version":1},"payload":{"before":{"username":"user3","password":"password3","enabled":1},"after":{"username":"user3","password":"password4","enabled":1},"source":{"version":"2.1.3.Final","connector":"mysql","name":"dbserver1","ts_ms":1680662367000,"snapshot":"false","db":"auth","sequence":null,"table":"users","server_id":1,"gtid":null,"file":"mysql-bin.000003","pos":2149,"row":0,"thread":124,"query":null},"op":"u","ts_ms":1680662367299,"transaction":null}}

delete data
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; delete from users where username='user3';"
    cmd:
        delete from users where username='user3';
    kafka output:
        {"schema":{"type":"struct","fields":[{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"before"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"username"},{"type":"string","optional":false,"field":"password"},{"type":"int16","optional":false,"field":"enabled"}],"optional":true,"name":"dbserver1.auth.users.Value","field":"after"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"version"},{"type":"string","optional":false,"field":"connector"},{"type":"string","optional":false,"field":"name"},{"type":"int64","optional":false,"field":"ts_ms"},{"type":"string","optional":true,"name":"io.debezium.data.Enum","version":1,"parameters":{"allowed":"true,last,false,incremental"},"default":"false","field":"snapshot"},{"type":"string","optional":false,"field":"db"},{"type":"string","optional":true,"field":"sequence"},{"type":"string","optional":true,"field":"table"},{"type":"int64","optional":false,"field":"server_id"},{"type":"string","optional":true,"field":"gtid"},{"type":"string","optional":false,"field":"file"},{"type":"int64","optional":false,"field":"pos"},{"type":"int32","optional":false,"field":"row"},{"type":"int64","optional":true,"field":"thread"},{"type":"string","optional":true,"field":"query"}],"optional":false,"name":"io.debezium.connector.mysql.Source","field":"source"},{"type":"string","optional":false,"field":"op"},{"type":"int64","optional":true,"field":"ts_ms"},{"type":"struct","fields":[{"type":"string","optional":false,"field":"id"},{"type":"int64","optional":false,"field":"total_order"},{"type":"int64","optional":false,"field":"data_collection_order"}],"optional":true,"name":"event.block","version":1,"field":"transaction"}],"optional":false,"name":"dbserver1.auth.users.Envelope","version":1},"payload":{"before":{"username":"user3","password":"password4","enabled":1},"after":null,"source":{"version":"2.1.3.Final","connector":"mysql","name":"dbserver1","ts_ms":1680662476000,"snapshot":"false","db":"auth","sequence":null,"table":"users","server_id":1,"gtid":null,"file":"mysql-bin.000003","pos":2472,"row":0,"thread":124,"query":null},"op":"d","ts_ms":1680662476552,"transaction":null}}

update schema
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; alter table users add number INTEGER(255);"

insert data after schema update
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; INSERT INTO users (username, password, enabled, country, number) VALUES ('user3', 'password3', true, 'SG', 50);"


## Restart Debezium Kafka Connect to Check if it restarts from the last offset
docker container kill connect
sh entrypoint.sh start
curl -H "Accept:application/json" localhost:8083/connectors/
docker container exec -it kafka bash -c "kafka-console-consumer --bootstrap-server kafka:9092 --topic dbserver1.auth.users" --> it should not print anything

## Hive metastore
Check password in hive metastore at /opt/hive/conf/hive hive-default.xml.template - https://repost.aws/knowledge-center/postgresql-hive-metastore-emr

docker container exec -it hive-metastore-postgresql bash
psql -U hive -d metastore
\d - show table

docker container exec -it postgres_container psql -U chongaih -d auth -c 'SELECT * FROM users'

## Connect spark to Hive
https://sparkbyexamples.com/spark/how-to-connect-spark-to-remote-hive/

## Hudi
* If the partition value is updated, the data in the old partition won't be removed
* hoodie.table.name - used when reading or writing to a Hoodie table through the Spark DataFrame or Dataset API
  hoodie.datasource.write.table.name is used when writing data to a Hoodie table using the HoodieDeltaStreamer or the HoodieSparkConnector
* cannot issue delete if the hoodie table is empty (eg. delete already in the binlog when first runnig hudi pipeline)
  org.apache.hudi.exception.HoodieException: Deletes issued without any prior commits
* hoodie support schema evolution - can update the mysql db and then spark code case class to take effect
* to include both upsert and delete in 1 query, we need to add column _hoodie_is_deleted and set hoodie.compaction.strategy to be org.apache.hudi.compaction.strategy.ExcludeHoodieDeletesCompactionStrategy

## Reference
Debezium tutorial
https://debezium.io/documentation/reference/stable/tutorial.html#starting-kafka-connect
Structured streaming & ACL
https://spark.apache.org/docs/latest/structured-streaming-kafka-integration.html
Hudi 
    Hive & HDFS
        https://github.com/apache/hudi/blob/master/docker/compose/docker-compose_hadoop284_hive233_spark244.yml
        https://github.com/apache/hudi/blob/master/docker/compose/hadoop.env
    Docker - https://hudi.apache.org/docs/docker_demo/
    Spark - https://hudi.apache.org/docs/quick-start-guide
    If there is already a table, we cannot overwrite the setting
        https://stackoverflow.com/questions/75293169/spark-streaming-hudi-hoodieexception-config-conflictkeycurrent-valueexisting
    schema evolution
        https://hudi.apache.org/docs/schema_evolution/
Spark
    spark streaming cannot infer schema directly
        https://stackoverflow.com/questions/48361177/spark-structured-streaming-kafka-convert-json-without-schema-infer-schema
Typetag
    https://stackoverflow.com/questions/12218641/what-is-a-typetag-and-how-do-i-use-it
Jackson JSON
    https://stackoverflow.com/questions/6846244/jackson-and-generic-type-reference
Haddop
    read and write file
        https://www.google.com/search?q=java+read+and+write+file+in+hdfs+examole&rlz=1C5GCEM_enSG1049SG1049&sxsrf=APwXEdfrRERmo2KAppUnl7EtQVV17jqG5Q%3A1681433793490&ei=waQ4ZIPPHY_C4-EPtrG-6As&ved=0ahUKEwjDs-X_lKj-AhUP4TgGHbaYD70Q4dUDCA8&uact=5&oq=java+read+and+write+file+in+hdfs+examole&gs_lcp=Cgxnd3Mtd2l6LXNlcnAQAzIHCCEQoAEQCjoKCAAQRxDWBBCwAzoKCAAQigUQsAMQQzoFCAAQgAQ6BggAEBYQHjoICAAQigUQhgM6BQghEKABOggIIRAWEB4QHToECCEQFUoECEEYAFDOAVibGGC4GWgBcAF4AIABYIgB0gmSAQIxNpgBAKABAcgBCcABAQ&sclient=gws-wiz-serp
    load config on hdfs
        https://stackoverflow.com/questions/38123764/how-to-load-typesafe-configfactory-from-file-on-hdfs
    multi-node cluster - by default if datanode sends heartbeat to namenode, namenode will know which datanode exists
        https://www.tutorialspoint.com/hadoop/hadoop_multi_node_cluster.htm
    access hadoop container from macbook localhost
        https://github.com/big-data-europe/docker-hadoop/issues/98

## Cmd
--config-path config/auth_users.conf --kafka-start-time -2 --local --> print to console
--config-path config/auth_users.conf --kafka-start-time -2

* pip3 install parquet-tools --> parquet-tools show xx.parquet

## TODO
- query
- schema evolution
- can multiple write read update? -- done
- order - delete then upsert? -- done
- event_time

## Future Work
1. currently macbook cannot access docker HDFS directly - https://github.com/big-data-europe/docker-hadoop/issues/98

