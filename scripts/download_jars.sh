#!/bin/sh

mkdir -p jars
curl -H "Accept: application/zip" https://repo1.maven.org/maven2/io/nats/jnats/2.6.8/jnats-2.6.8.jar -o jars/jnats-2.6.8.jar

