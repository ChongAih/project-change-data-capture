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
check kafka topic content after the data update - {topic.prefix}.{db}.{table}
    docker container exec -it kafka bash -c "kafka-console-consumer --bootstrap-server kafka:9092 --topic dbserver1.auth.users"

docker container exec -it mysql bash -c "mysql -uroot -ppassword -e 'use auth; select * from users'"

change database;
    use auth;

insert data into mysql
    docker container exec -it mysql mysql -uroot -ppassword -e "use auth; INSERT INTO users (username, password, enabled) VALUES ('user3', 'password3', true);"
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


# Restart Debezium Kafka Connect to Check if it restarts from the last offset
docker container kill connect
sh entrypoint.sh start
curl -H "Accept:application/json" localhost:8083/connectors/
docker container exec -it kafka bash -c "kafka-console-consumer --bootstrap-server kafka:9092 --topic dbserver1.auth.users" --> it should not print anything


