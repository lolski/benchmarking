#!/bin/bash

LOG_PREFIX="external component supervision test - "

wait_until_process_stopped() {
  max_attempt=20
  i=0
  is_running=1
  while [ $is_running -eq 1 ] && [ $i -lt $max_attempt ]; do
    ps -p $1
    if [ $? -eq 1 ]; then
      is_running=0
    else
      i=$((i+1))
      echo "process $1 still alive after $i attempt. rechecking..."
      sleep 1
    fi
  done
  if [ $i -eq $max_attempt ] && [ $is_running -eq 1 ]; then
    echo "process $1 still alive after $i attempt!"
    exit 1
  fi
}

every_process_should_start_properly() {
  echo "========================"
  echo $LOG_PREFIX"running every_process_should_start_properly..."
  echo $LOG_PREFIX"starting engine..."
  grakn-package/bin/grakn-engine.sh start

  sleep 5

  echo $LOG_PREFIX"engine started. checking if all external components are properly started..."
  ENGINE_PID=`ps -ef | grep "ai.grakn.engine.Grakn" | grep -v grep | awk '{print $2}'`
  CASSANDRA_PID=`ps -ef | grep "org.apache.cassandra.service.CassandraDaemon" | grep -v grep | awk '{print $2}'`
  REDIS_PID=`ps -ef | grep "redis-server" | grep -v grep | awk '{print $2}'`

  if [ -z $ENGINE_PID ]; then
    echo $LOG_PREFIX"engine was not started properly!"
    exit 1
  fi

  if [ -z $REDIS_PID ]; then
    echo $LOG_PREFIX"redis was not started properly!"
    exit 1
  fi

  if [ -z $CASSANDRA_PID ]; then
    echo $LOG_PREFIX"cassandra was not started properly!"
    exit 1
  fi

  echo $LOG_PREFIX"every components started properly!"
  echo "========================"

  # cleanup
  grakn-package/bin/grakn-engine.sh stop
  wait_until_process_stopped $ENGINE_PID
  wait_until_process_stopped $REDIS_PID
  wait_until_process_stopped $CASSANDRA_PID
}

every_process_should_die_if_component_dies() {
  echo "========================"
  echo $LOG_PREFIX"running every_process_should_die_if_component_dies($1)..."
  echo $LOG_PREFIX"starting engine..."
  grakn-package/bin/grakn-engine.sh start
  echo $LOG_PREFIX"engine started."
  ENGINE_PID=`ps -ef | grep "ai.grakn.engine.Grakn" | grep -v grep | awk '{print $2}'`
  CASSANDRA_PID=`ps -ef | grep "org.apache.cassandra.service.CassandraDaemon" | grep -v grep | awk '{print $2}'`
  REDIS_PID=`ps -ef | grep "redis-server" | grep -v grep | awk '{print $2}'`

  echo "now deliberately attempting to kill "$1"... the expected behavior is that the other processes should also die along with it."

  if [ "$1" == "engine" ]; then
    STOP_PID=$ENGINE_PID
  elif [ "$1" == "redis" ]; then
    STOP_PID=$REDIS_PID
  elif [ "$1" == "cassandra" ]; then
    STOP_PID=$CASSANDRA_PID
  fi

  kill $STOP_PID

  wait_until_process_stopped $ENGINE_PID
  echo $LOG_PREFIX"checking if engine is still alive..."
  ps -p $ENGINE_PID
  if [ $? -eq 0 ]; then
    echo $LOG_PREFIX"error - engine process is still alive!"
    exit 1;
  fi

  echo $LOG_PREFIX"engine process was killed properly. continuing to check redis..."

  wait_until_process_stopped $REDIS_PID
  echo $LOG_PREFIX"checking if redis is still alive..."
  ps -p $REDIS_PID
  if [ $? -eq 0 ]; then
    echo $LOG_PREFIX"error - redis process is still alive!"
    exit 1;
  fi

  echo $LOG_PREFIX"redis process was killed properly. continuing to check cassandra..."

  wait_until_process_stopped $CASSANDRA_PID
  ps -p $CASSANDRA_PID
  if [ $? -eq 0 ]; then
    echo $LOG_PREFIX"error - cassandra process is still alive!"
    exit 1;
  fi

  echo $LOG_PREFIX"cassandra process was killed properly. every component was properly terminated."
  echo "========================"

  # cleanup
  grakn-package/bin/grakn-engine.sh stop
  wait_until_process_stopped $ENGINE_PID
  wait_until_process_stopped $REDIS_PID
  wait_until_process_stopped $CASSANDRA_PID
}

# every_process_should_start_properly
every_process_should_die_if_component_dies "engine"
every_process_should_die_if_component_dies "redis"
every_process_should_die_if_component_dies "cassandra"

exit 0
