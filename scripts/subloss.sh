#!/bin/sh

java -cp jars/jnats-2.6.8.jar:build/libs/java-examples.jar:$CLASSPATH io.nats.java.examples.NatsLossSubscriber 

