#!/bin/sh

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

command="$1"

if [ "$command" = "start" ]; then

  echo && echo "================== DOCKER COMPOSING UP ==================" && echo

  docker-compose -f "$SCRIPT_DIR"/docker/docker-compose.yml up -d

elif [ "$command" = "stop" ]; then

  echo && echo "================== DOCKER COMPOSING DOWN =================" && echo

  docker-compose -f "$SCRIPT_DIR"/docker/docker-compose.yml down -v

else

  echo "sh project-runner.sh <start | stop>"
  echo "<start | stop> start or stop all docker container"

fi