#!/bin/sh

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Read Docker config and export for docker-compose usage
. "$SCRIPT_DIR"/docker/docker.env

export KAFKA_INTERNAL_SERVER
export KAFKA_BOOTSTRAP_SERVER
export KAFKA_DATA_LOCAL_PATH
export DEBEZIUM_CONFIG_LOCAL_PATH

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

command="$1"

if [ "$command" = "start" ]; then

  echo && echo "================== DOCKER COMPOSING UP ==================" && echo

  docker-compose -f "$SCRIPT_DIR"/docker/docker-compose.yml up -d

elif [ "$command" = "stop" ]; then

  echo && echo "================== DOCKER COMPOSING DOWN =================" && echo

  docker-compose -f "$SCRIPT_DIR"/docker/docker-compose.yml down -v

  echo && echo "========================= CLEANING =======================" && echo

  # Clear volume bin mount directory
  if [ -d "$KAFKA_DATA_LOCAL_PATH" ]; then rm -Rf "$KAFKA_DATA_LOCAL_PATH"; fi
  if [ -d "$DEBEZIUM_CONFIG_LOCAL_PATH" ]; then rm -Rf "$DEBEZIUM_CONFIG_LOCAL_PATH"; fi
  docker volume prune -f

else

  echo "sh project-runner.sh <start | stop>"
  echo "<start | stop> start or stop all docker container"

fi