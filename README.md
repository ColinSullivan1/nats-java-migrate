# nats-java-migrate

Examples for migrating java applications across NATS servers in a controlled manner via control plane.

## Dependencies

- Java 1.8 or higher
- Gradle
- The golang nats-req binary, found in the examples of the [nats.go]() repository (or a substitute that sends a request message).
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
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.0JoqVgdyGji3r8NUBBj9vt.LG1jopUR] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.XB0EuMomEbdSJzOfO9kWLp.3O4vqDgv] : '+OK'
Stopping server 1.
NATS Connection Event: nats: connection closed
Starting server 1.
Migrating applications to server 1.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.b7YnZ1mxlCQkyh9bWSp95s.hjQT8zy9] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.gpbgSh9XT8vCEKHkUYUZCQ.U2sDjYo6] : '+OK'
Stopping server 2.
NATS Connection Event: nats: connection closed
Starting server 2.
Migrating applications to server 2.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.gvwRX4UxEwN2pz6FRCg5v6.tM8aQnTz] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.RNxaAlP9wLR9TokPRbM6Pn.p6OqDZft] : '+OK'
Stopping server 1.
NATS Connection Event: nats: connection closed
Starting server 1.
Migrating applications to server 1.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.llTRoPevTsucJQD5tsHhHB.PcBadiXz] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.YV3yOglIMz2rZ8WoR0EwiB.LrIVamEY] : '+OK'
NATS Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Migrating applications to server 2.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.OshSg2i5ohklP7cZ8xDG22.ae0dueEJ] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.pkQlt2ouPsLFyKXaNAHAXI.IqcAIipk] : '+OK'
Stopping server 1.
NATS Connection Event: nats: connection closed
Starting server 1.
Migrating applications to server 1.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.hadWMPo7WBukbG1CiMQ2AM.k5QU1hdz] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.0WaSRJmecc2pbslQZa0Pld.FER6wYzk] : '+OK'
NATS Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Migrating applications to server 2.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.v4JcHibnHo9JPUfNQL95AS.6v3BVjP3] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.pz4uYRm3e1mgEIVXQqqNSb.Thf5tJWw] : '+OK'
NATS Connection Event: nats: connection closed
Stopping server 1.
Starting server 1.
Migrating applications to server 1.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.1M1UdbwWM5T9WthWL79dF8.qZl8QiJJ] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.aeb0K3DGhzIBbPLKxmEtBj.O6XiLu76] : '+OK'
NATS Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Migrating applications to server 2.
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.9s1ZGTkxjcT8HN20A8rw9T.rMj2iqyt] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.mTb1tKb9FQMJXziGYoTXvG.TC8EqcZo] : '+OK'
Stopping server 1.
NATS Connection Event: nats: connection closed
Starting server 1.
Migrating applications to server 1.
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.izqrTx0wldMHNWFypTCooS.TgPm7OjD] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.BPpMrxkwq1x9q8Ep19RPf1.byuTFrgz] : '+OK'
NATS Connection Event: nats: connection closed
Stopping server 2.
Starting server 2.
Wait for the NATS Loss Subscriber to finish.
Done.  Received 600000 of 600000 messages.
Message Rate: 9999.88 msgs/sec
Loss Percentage: 0.000000
NATS Connection Event: nats: connection closed
NATS Connection Event: nats: connection closed

Finished.
Publish rate: 9999 msgs/sec.
Stopping server 1.
Stopping server 2.
Test complete.
```
