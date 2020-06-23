#!/bin/sh

java -cp jars/jnats-2.6.8.jar:jars/jnats-2.6.8-examples.jar:$CLASSPATH io/nats/examples/NatsReq $1 $2 $3

