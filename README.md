# nats-java-migrate

Examples for migrating java applications across NATS servers in a controlled manner via control plane.

## Dependencies

- Java 1.8 or higher
- Gradle
- NATS server in your path

## Setup

Gradle will find the right NATS jars, but we run via command line directly
referencing the NATS client jars via classpath.  To install the jars from
maven, in the top level repo directory run:

`./scripts/download_jars.sh`

## Building

To build, run:

`./gradlew build`

## The Applications

The applications share a ControlPlane class, that subscribe to
"control.migrate.publisher" for publishers, and "control.migrate.subscriber"
for subscribers.

Requests are in the form of a body with a string url, indicating which NATS
server to migrate to.  Using the `nats-req` utility (which simply sends a request)
with a string body.  For example, to migrate publishers to a server running locally on
port 4333, you would run:

`nats-req control.migrate.publisher "nats://127.0.0.1:4333"`

When this control plane receives a request with a body containing a string of a url,
it will trigger a migration handler in the application.

### Subscriber Migration

In its control message handler, the subscriber will create another queue subscriber
to the server it's migrating to, then drain it's current connection.  The applications
connection will be replaced.

### Publisher Migration

The publisher will simply create a new connection to the server it is migrating to, 
swap the connection it's publishing on in a threadsafe manner and close the old one.

## Test Flow Overview

1) Start the servers (cluster of 2)
2) Start the subscriber, connect to server #1
3) Start the publisher, connect to server #1
4) Migrate the subscriber to server #1 (`nats-req control.migrate.subscriber "nats://<host>:port"`)
5) Migrate the publisher (`nats-req control.migrate.publisher "nats://<host>:port"`)
6) bounce server 1, simulating an upgrade.

Repeat as necessary for testing.

## Running the test

Various scripts are provided to assist testing.

- migrate_test.sh - a script that starts local servers, publisher, subscriber,
and migrates between servers over the course of a minute.
- publoss.sh - starts the publisher.
- subloss.sh - starts the subscriber.
- request.sh - uses the java requestor example to make a migration request.

### Example Test Run

```text
 $ ./scripts/migrate_test.sh 
Starting server 1.
Starting server 2.
Starting Test Apps.

Trying to connect to nats://localhost:4222 and listen to foo for messages.


Sending 600000 messages of 128 bytes on foo, server is nats://localhost:4222

Received start message from publisher, expecting 600000 messages.
Migrating applications to server 2.
Migrate control message: subscriber migrating to nats://127.0.0.1:4333.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
NATS Loss Subscriber Connection Event: nats: connection closed
Migrate control message: publisher migrating to nats://127.0.0.1:4333.
Done with migration.
NATS Loss Publisher Connection Event: nats: connection closed
Stopping server 1.
Starting server 1.
Migrating applications to server 1.
Migrate control message: subscriber migrating to nats://127.0.0.1:4222.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
NATS Loss Subscriber Connection Event: nats: connection closed
Migrate control message: publisher migrating to nats://127.0.0.1:4222.
Done with migration.
NATS Loss Publisher Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Migrating applications to server 2.
Migrate control message: subscriber migrating to nats://127.0.0.1:4333.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
NATS Loss Subscriber Connection Event: nats: connection closed
Migrate control message: publisher migrating to nats://127.0.0.1:4333.
Done with migration.
NATS Loss Publisher Connection Event: nats: connection closed
Stopping server 1.
Starting server 1.
Migrating applications to server 1.
Migrate control message: subscriber migrating to nats://127.0.0.1:4222.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
NATS Loss Subscriber Connection Event: nats: connection closed
Migrate control message: publisher migrating to nats://127.0.0.1:4222.
Done with migration.
NATS Loss Publisher Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Migrating applications to server 2.
Migrate control message: subscriber migrating to nats://127.0.0.1:4333.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
NATS Loss Subscriber Connection Event: nats: connection closed
Migrate control message: publisher migrating to nats://127.0.0.1:4333.
Done with migration.
NATS Loss Publisher Connection Event: nats: connection closed
Stopping server 1.
Starting server 1.
Migrating applications to server 1.
Migrate control message: subscriber migrating to nats://127.0.0.1:4222.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
NATS Loss Subscriber Connection Event: nats: connection closed
Migrate control message: publisher migrating to nats://127.0.0.1:4222.
Done with migration.
NATS Loss Publisher Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Wait for the NATS Loss Subscriber to finish.
Done.  Received 600000 of 600000 messages.
Message Rate: 9999.92 msgs/sec
Loss Percentage: 0.000000
NATS Loss Publisher Connection Event: nats: connection closed
NATS Loss Subscriber Connection Event: nats: connection closed
Finished.
Publish rate: 9999 msgs/sec.
```
