# nats-java-migrate

Examples for migrating java applications across NATS servers in a controlled manner via control plane.

## Dependencies

- Java 1.8 or higher
- Gradle
- The golang nats-req binary, found in the examples of the [nats.go]() repository
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

The publisher will simply create a new connection to the server it's migrating to, 
swap the connection it's publishing on in a threadsafe manner and close the old one.

## Test Flow Overview

1) Start the servers (cluster of 2)
2) Start the subscriber, connect to server #1
3) Start the publisher, connect to server #1
4) Migrate the subscriber to server #1 (`nats-req control.migrate.subscriber "nats://<host>:port"`)
5) Migrate the publisher (`nats-req control.migrate.publisher "nats://<host>:port"`)
6) bounce server 1, simulating and upgrade.

Repeat as necessary for testing.


## Running the test

Various scripts are provided to assist testing these applications.

- migrate_test.sh - a script that starts local servers, publisher, subscriber,
and migrates between servers over the course of a minute.
- publoss.sh - starts the publisher.
- subloss.sh - starts the subscriber.

### Example Test Run

```text
$ ./scripts/migrate_test.sh 
Starting Servers
[2916] 2020/06/09 10:56:31.543195 [INF] Starting nats-server version 2.2.0-beta.11
[2916] 2020/06/09 10:56:31.543261 [INF] Git commit [not set]
[2916] 2020/06/09 10:56:31.543608 [INF] Starting http monitor on 0.0.0.0:8222
[2916] 2020/06/09 10:56:31.543648 [INF] Listening for client connections on 0.0.0.0:4222
[2916] 2020/06/09 10:56:31.543652 [INF] Server id is NBUJV3FPBDSNPUTOHPJDFQNUE74N4WDWVT4YFQIILMPGY3RPFQLLGOF3
[2916] 2020/06/09 10:56:31.543653 [INF] Server is ready
[2916] 2020/06/09 10:56:31.543860 [INF] Listening for route connections on 127.0.0.1:6222
[2916] 2020/06/09 10:56:31.544150 [ERR] Error trying to connect to route (attempt 1): dial tcp 127.0.0.1:6333: connect: connection refused
[2918] 2020/06/09 10:56:32.545067 [INF] Starting nats-server version 2.2.0-beta.11
[2918] 2020/06/09 10:56:32.545133 [INF] Git commit [not set]
[2918] 2020/06/09 10:56:32.545435 [INF] Starting http monitor on 0.0.0.0:8333
[2918] 2020/06/09 10:56:32.545481 [INF] Listening for client connections on 0.0.0.0:4333
[2918] 2020/06/09 10:56:32.545484 [INF] Server id is NDNEDUG3SUOKL25BAZAJVERZ4D5HRSU7WQ2V2D7YSLU6ERT7Z4P3WHWN
[2918] 2020/06/09 10:56:32.545485 [INF] Server is ready
[2918] 2020/06/09 10:56:32.545677 [INF] Listening for route connections on 127.0.0.1:6333
[2918] 2020/06/09 10:56:32.546082 [INF] 127.0.0.1:6222 - rid:1 - Route connection created
[2916] 2020/06/09 10:56:32.546101 [INF] 127.0.0.1:51135 - rid:1 - Route connection created
[2916] 2020/06/09 10:56:32.549213 [INF] 127.0.0.1:6333 - rid:2 - Route connection created
[2918] 2020/06/09 10:56:32.549222 [INF] 127.0.0.1:51136 - rid:2 - Route connection created
[2916] 2020/06/09 10:56:32.549330 [INF] 127.0.0.1:6333 - rid:2 - Router connection closed
[2918] 2020/06/09 10:56:32.549355 [INF] 127.0.0.1:51136 - rid:2 - Router connection closed
Starting Test Apps

Trying to connect to nats://localhost:4222 and listen to foo for messages.


Sending 600000 messages of 128 bytes on foo, server is nats://localhost:4222

Received start message from publisher, expecting 600000 messages.
Migrating applications
Migrating applications to server 2
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.4QxaDKz0U9AsLMcu7hxiot.14lBS335] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.BWsIPTc9jZNcM0GzECvAYJ.ADnGcMEw] : '+OK'
Bouncing server 1
NATS Connection Event: nats: connection closed
[2918] 2020/06/09 10:56:41.597298 [INF] 127.0.0.1:6222 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2916 Killed: 9               nats-server -p 4222 -m 8222 --cluster "nats://127.0.0.1:6222" --routes "nats://127.0.0.1:6333" --pid s1.pid
[2933] 2020/06/09 10:56:42.606816 [INF] Starting nats-server version 2.2.0-beta.11
[2933] 2020/06/09 10:56:42.606885 [INF] Git commit [not set]
[2933] 2020/06/09 10:56:42.607222 [INF] Starting http monitor on 0.0.0.0:8222
[2933] 2020/06/09 10:56:42.607260 [INF] Listening for client connections on 0.0.0.0:4222
[2933] 2020/06/09 10:56:42.607263 [INF] Server id is NB55SJP4MFDLMN4VXTFVRKSM2G3B64NOFMI4T6LXBNO7H2R5MVUBXLRI
[2933] 2020/06/09 10:56:42.607264 [INF] Server is ready
[2933] 2020/06/09 10:56:42.607473 [INF] Listening for route connections on 127.0.0.1:6222
[2918] 2020/06/09 10:56:42.607844 [INF] 127.0.0.1:51150 - rid:5 - Route connection created
[2933] 2020/06/09 10:56:42.607879 [INF] 127.0.0.1:6333 - rid:1 - Route connection created
[2918] 2020/06/09 10:56:42.696301 [INF] 127.0.0.1:6222 - rid:6 - Route connection created
[2933] 2020/06/09 10:56:42.696353 [INF] 127.0.0.1:51151 - rid:2 - Route connection created
[2933] 2020/06/09 10:56:42.696501 [INF] 127.0.0.1:51151 - rid:2 - Router connection closed
[2918] 2020/06/09 10:56:42.696565 [INF] 127.0.0.1:6222 - rid:6 - Router connection closed
Migrating applications to server 1
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.Ch8WIqSC6T1ETslKtjZgVl.fAoZTmKG] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.T1a633ZxS7biw8EQtdHW7J.yeUxpX3H] : '+OK'
Bouncing server 2
NATS Connection Event: nats: connection closed
[2933] 2020/06/09 10:56:46.638821 [INF] 127.0.0.1:6333 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2918 Killed: 9               nats-server -p 4333 -m 8333 --cluster "nats://127.0.0.1:6333" --routes "nats://127.0.0.1:6222" --pid s2.pid
[2933] 2020/06/09 10:56:47.646065 [ERR] Error trying to connect to route (attempt 1): dial tcp 127.0.0.1:6333: connect: connection refused
[2941] 2020/06/09 10:56:47.648517 [INF] Starting nats-server version 2.2.0-beta.11
[2941] 2020/06/09 10:56:47.648583 [INF] Git commit [not set]
[2941] 2020/06/09 10:56:47.648918 [INF] Starting http monitor on 0.0.0.0:8333
[2941] 2020/06/09 10:56:47.648960 [INF] Listening for client connections on 0.0.0.0:4333
[2941] 2020/06/09 10:56:47.648963 [INF] Server id is NADABOQACIJY6AJFCEFJPOWFN2KQ5N4L4IOGCBU3FCVIDQ3J6ODRPFXM
[2941] 2020/06/09 10:56:47.648965 [INF] Server is ready
[2941] 2020/06/09 10:56:47.649186 [INF] Listening for route connections on 127.0.0.1:6333
[2933] 2020/06/09 10:56:47.649526 [INF] 127.0.0.1:51163 - rid:7 - Route connection created
[2941] 2020/06/09 10:56:47.649592 [INF] 127.0.0.1:6222 - rid:1 - Route connection created
[2933] 2020/06/09 10:56:48.646429 [INF] 127.0.0.1:6333 - rid:8 - Route connection created
[2941] 2020/06/09 10:56:48.646481 [INF] 127.0.0.1:51164 - rid:2 - Route connection created
Migrating applications to server 2
[2933] 2020/06/09 10:56:48.646591 [INF] 127.0.0.1:6333 - rid:8 - Router connection closed
[2941] 2020/06/09 10:56:48.646634 [INF] 127.0.0.1:51164 - rid:2 - Router connection closed
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.HsqQosI0EqH0ziPaMSQPxV.Nlkm9kXO] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.P0ER6IBPg1KmwIE34ROAb2.wyyY7cLI] : '+OK'
NATS Connection Event: nats: connection closed
Bouncing server 1
[2941] 2020/06/09 10:56:51.691606 [INF] 127.0.0.1:6222 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2933 Killed: 9               nats-server -p 4222 -m 8222 --cluster "nats://127.0.0.1:6222" --routes "nats://127.0.0.1:6333" --pid s1.pid
[2949] 2020/06/09 10:56:52.703439 [INF] Starting nats-server version 2.2.0-beta.11
[2949] 2020/06/09 10:56:52.703507 [INF] Git commit [not set]
[2949] 2020/06/09 10:56:52.703885 [INF] Starting http monitor on 0.0.0.0:8222
[2949] 2020/06/09 10:56:52.703945 [INF] Listening for client connections on 0.0.0.0:4222
[2949] 2020/06/09 10:56:52.703948 [INF] Server id is NDN2UUFWY3AYUDK4MZTOUMAVQRAOIIHHIRLEVSD5R4MEXUQRS5EJJOXX
[2949] 2020/06/09 10:56:52.703957 [INF] Server is ready
[2949] 2020/06/09 10:56:52.704199 [INF] Listening for route connections on 127.0.0.1:6222
[2941] 2020/06/09 10:56:52.704549 [INF] 127.0.0.1:51175 - rid:5 - Route connection created
[2949] 2020/06/09 10:56:52.704615 [INF] 127.0.0.1:6333 - rid:1 - Route connection created
[2941] 2020/06/09 10:56:52.733548 [INF] 127.0.0.1:6222 - rid:6 - Route connection created
[2949] 2020/06/09 10:56:52.733611 [INF] 127.0.0.1:51176 - rid:2 - Route connection created
[2941] 2020/06/09 10:56:52.733718 [INF] 127.0.0.1:6222 - rid:6 - Router connection closed
[2949] 2020/06/09 10:56:52.733789 [INF] 127.0.0.1:51176 - rid:2 - Router connection closed
Migrating applications to server 1
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.RYu3zU3MsFHyy2LUGFT1W7.LSiRzYjq] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.MzWMtSYhhr7HlaHJrxZYlG.ctrT3I4d] : '+OK'
Bouncing server 2
NATS Connection Event: nats: connection closed
[2949] 2020/06/09 10:56:56.738864 [INF] 127.0.0.1:6333 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2941 Killed: 9               nats-server -p 4333 -m 8333 --cluster "nats://127.0.0.1:6333" --routes "nats://127.0.0.1:6222" --pid s2.pid
[2957] 2020/06/09 10:56:57.748997 [INF] Starting nats-server version 2.2.0-beta.11
[2957] 2020/06/09 10:56:57.749062 [INF] Git commit [not set]
[2957] 2020/06/09 10:56:57.749366 [INF] Starting http monitor on 0.0.0.0:8333
[2957] 2020/06/09 10:56:57.749406 [INF] Listening for client connections on 0.0.0.0:4333
[2957] 2020/06/09 10:56:57.749409 [INF] Server id is NB3TWJBFVKMJOAJ3C2DUG3OHDZKB7V7UWWAOT3JR3BRE6ISVGL3QVJV6
[2957] 2020/06/09 10:56:57.749411 [INF] Server is ready
[2957] 2020/06/09 10:56:57.749620 [INF] Listening for route connections on 127.0.0.1:6333
[2949] 2020/06/09 10:56:57.750007 [INF] 127.0.0.1:51187 - rid:7 - Route connection created
[2957] 2020/06/09 10:56:57.750056 [INF] 127.0.0.1:6222 - rid:1 - Route connection created
[2949] 2020/06/09 10:56:57.806462 [INF] 127.0.0.1:6333 - rid:8 - Route connection created
[2957] 2020/06/09 10:56:57.806477 [INF] 127.0.0.1:51188 - rid:2 - Route connection created
[2949] 2020/06/09 10:56:57.806603 [INF] 127.0.0.1:6333 - rid:8 - Router connection closed
[2957] 2020/06/09 10:56:57.806668 [INF] 127.0.0.1:51188 - rid:2 - Router connection closed
Migrating applications to server 2
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.wrBPnpj1LfQZkzCcfIb8VP.1FFyX5Un] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.73CgW1WXrbYAkpCALa019X.dnxKVnDf] : '+OK'
Bouncing server 1
NATS Connection Event: nats: connection closed
[2957] 2020/06/09 10:57:01.788682 [INF] 127.0.0.1:6222 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2949 Killed: 9               nats-server -p 4222 -m 8222 --cluster "nats://127.0.0.1:6222" --routes "nats://127.0.0.1:6333" --pid s1.pid
[2965] 2020/06/09 10:57:02.799414 [INF] Starting nats-server version 2.2.0-beta.11
[2965] 2020/06/09 10:57:02.799480 [INF] Git commit [not set]
[2965] 2020/06/09 10:57:02.799830 [INF] Starting http monitor on 0.0.0.0:8222
[2965] 2020/06/09 10:57:02.799883 [INF] Listening for client connections on 0.0.0.0:4222
[2965] 2020/06/09 10:57:02.799887 [INF] Server id is NBSKXCZSNI4PVSZNACIYFUITSLCTL4DDN2P2MXFLYPGWC2LYA5MSHR7N
[2965] 2020/06/09 10:57:02.799890 [INF] Server is ready
[2965] 2020/06/09 10:57:02.800144 [INF] Listening for route connections on 127.0.0.1:6222
[2957] 2020/06/09 10:57:02.800499 [INF] 127.0.0.1:51199 - rid:5 - Route connection created
[2965] 2020/06/09 10:57:02.800545 [INF] 127.0.0.1:6333 - rid:1 - Route connection created
[2957] 2020/06/09 10:57:02.872832 [INF] 127.0.0.1:6222 - rid:6 - Route connection created
[2965] 2020/06/09 10:57:02.872908 [INF] 127.0.0.1:51200 - rid:2 - Route connection created
[2957] 2020/06/09 10:57:02.873064 [INF] 127.0.0.1:6222 - rid:6 - Router connection closed
[2965] 2020/06/09 10:57:02.873128 [INF] 127.0.0.1:51200 - rid:2 - Router connection closed
Migrating applications to server 1
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.HjlmPD98XCHUc5NktciTxX.cuX0qwRs] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.bczU7Lxq1U4aXAtYLpLrok.gCNPQHQc] : '+OK'
Bouncing server 2
NATS Connection Event: nats: connection closed
[2965] 2020/06/09 10:57:06.836722 [INF] 127.0.0.1:6333 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2957 Killed: 9               nats-server -p 4333 -m 8333 --cluster "nats://127.0.0.1:6333" --routes "nats://127.0.0.1:6222" --pid s2.pid
[2965] 2020/06/09 10:57:07.845064 [ERR] Error trying to connect to route (attempt 1): dial tcp 127.0.0.1:6333: connect: connection refused
[2973] 2020/06/09 10:57:07.846558 [INF] Starting nats-server version 2.2.0-beta.11
[2973] 2020/06/09 10:57:07.846623 [INF] Git commit [not set]
[2973] 2020/06/09 10:57:07.846954 [INF] Starting http monitor on 0.0.0.0:8333
[2973] 2020/06/09 10:57:07.847005 [INF] Listening for client connections on 0.0.0.0:4333
[2973] 2020/06/09 10:57:07.847009 [INF] Server id is NAJCFMVE74QB66L4MU5KPFBR73GC6UIUZHZUVZ2ZKIFDMOMBF6JRX4XH
[2973] 2020/06/09 10:57:07.847012 [INF] Server is ready
[2973] 2020/06/09 10:57:07.847225 [INF] Listening for route connections on 127.0.0.1:6333
[2965] 2020/06/09 10:57:07.847570 [INF] 127.0.0.1:51212 - rid:7 - Route connection created
[2973] 2020/06/09 10:57:07.847664 [INF] 127.0.0.1:6222 - rid:1 - Route connection created
Migrating applications to server 2
[2973] 2020/06/09 10:57:08.845385 [INF] 127.0.0.1:51213 - rid:2 - Route connection created
[2965] 2020/06/09 10:57:08.845471 [INF] 127.0.0.1:6333 - rid:8 - Route connection created
[2965] 2020/06/09 10:57:08.845737 [INF] 127.0.0.1:6333 - rid:8 - Router connection closed
[2973] 2020/06/09 10:57:08.845821 [INF] 127.0.0.1:51213 - rid:2 - Router connection closed
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.DnPiElfTACMhGofdIVdSO3.UwYuMc3B] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.ntTHE0Uk8Ml5FPZIzUCaw4.6CKXpzN8] : '+OK'
Bouncing server 1
NATS Connection Event: nats: connection closed
[2973] 2020/06/09 10:57:11.880686 [INF] 127.0.0.1:6222 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2965 Killed: 9               nats-server -p 4222 -m 8222 --cluster "nats://127.0.0.1:6222" --routes "nats://127.0.0.1:6333" --pid s1.pid
[2981] 2020/06/09 10:57:12.893745 [INF] Starting nats-server version 2.2.0-beta.11
[2981] 2020/06/09 10:57:12.893812 [INF] Git commit [not set]
[2981] 2020/06/09 10:57:12.894190 [INF] Starting http monitor on 0.0.0.0:8222
[2981] 2020/06/09 10:57:12.894244 [INF] Listening for client connections on 0.0.0.0:4222
[2981] 2020/06/09 10:57:12.894248 [INF] Server id is NBELMAGP7XTQ75QPMPHYLE3BHX6KSGTSR225UDSPCFRYQMRMNO5M7XZQ
[2981] 2020/06/09 10:57:12.894249 [INF] Server is ready
[2981] 2020/06/09 10:57:12.894461 [INF] Listening for route connections on 127.0.0.1:6222
[2973] 2020/06/09 10:57:12.894840 [INF] 127.0.0.1:51224 - rid:5 - Route connection created
[2981] 2020/06/09 10:57:12.894923 [INF] 127.0.0.1:6333 - rid:1 - Route connection created
[2973] 2020/06/09 10:57:12.951848 [INF] 127.0.0.1:6222 - rid:6 - Route connection created
[2981] 2020/06/09 10:57:12.951867 [INF] 127.0.0.1:51225 - rid:2 - Route connection created
[2973] 2020/06/09 10:57:12.951969 [INF] 127.0.0.1:6222 - rid:6 - Router connection closed
[2981] 2020/06/09 10:57:12.952029 [INF] 127.0.0.1:51225 - rid:2 - Router connection closed
Migrating applications to server 1
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.MYgtXnqkNagBSHxw1r3t0O.SyQBlxHb] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.kOS1jWME5frXHoCiourZh9.R5cBeVUC] : '+OK'
Bouncing server 2
NATS Connection Event: nats: connection closed
[2981] 2020/06/09 10:57:16.937458 [INF] 127.0.0.1:6333 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2973 Killed: 9               nats-server -p 4333 -m 8333 --cluster "nats://127.0.0.1:6333" --routes "nats://127.0.0.1:6222" --pid s2.pid
[2989] 2020/06/09 10:57:17.946127 [INF] Starting nats-server version 2.2.0-beta.11
[2989] 2020/06/09 10:57:17.946195 [INF] Git commit [not set]
[2989] 2020/06/09 10:57:17.946520 [INF] Starting http monitor on 0.0.0.0:8333
[2989] 2020/06/09 10:57:17.946586 [INF] Listening for client connections on 0.0.0.0:4333
[2989] 2020/06/09 10:57:17.946594 [INF] Server id is NCY6HPZ3QLHZZ5XG3DCVRR6MK7EBWDBCVQCKIDABMRRJV5XKYUSH65CI
[2989] 2020/06/09 10:57:17.946597 [INF] Server is ready
[2989] 2020/06/09 10:57:17.946821 [INF] Listening for route connections on 127.0.0.1:6333
[2981] 2020/06/09 10:57:17.947192 [INF] 127.0.0.1:51237 - rid:7 - Route connection created
[2989] 2020/06/09 10:57:17.947248 [INF] 127.0.0.1:6222 - rid:1 - Route connection created
[2981] 2020/06/09 10:57:18.029329 [INF] 127.0.0.1:6333 - rid:8 - Route connection created
[2989] 2020/06/09 10:57:18.029365 [INF] 127.0.0.1:51238 - rid:2 - Route connection created
[2981] 2020/06/09 10:57:18.029481 [INF] 127.0.0.1:6333 - rid:8 - Router connection closed
[2989] 2020/06/09 10:57:18.029556 [INF] 127.0.0.1:51238 - rid:2 - Router connection closed
Migrating applications to server 2
nats://127.0.0.1:4333
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4333'
Received  [_INBOX.N41597iuc8o9wuzd8pXnLg.ccyeTFVz] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4333'
Received  [_INBOX.mffhBhLJUjkHRwlqSHpdsN.adCfkCg5] : '+OK'
Bouncing server 1
NATS Connection Event: nats: connection closed
[2989] 2020/06/09 10:57:21.989870 [INF] 127.0.0.1:6222 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2981 Killed: 9               nats-server -p 4222 -m 8222 --cluster "nats://127.0.0.1:6222" --routes "nats://127.0.0.1:6333" --pid s1.pid
[2997] 2020/06/09 10:57:23.002208 [INF] Starting nats-server version 2.2.0-beta.11
[2997] 2020/06/09 10:57:23.002276 [INF] Git commit [not set]
[2997] 2020/06/09 10:57:23.002579 [INF] Starting http monitor on 0.0.0.0:8222
[2997] 2020/06/09 10:57:23.002616 [INF] Listening for client connections on 0.0.0.0:4222
[2997] 2020/06/09 10:57:23.002619 [INF] Server id is NDR3OCLFQ7WSQFHT5MI5ZIOGUU27RGKEFOSRBZJIVZKMJ3Y37Y3DY5LQ
[2997] 2020/06/09 10:57:23.002621 [INF] Server is ready
[2997] 2020/06/09 10:57:23.002826 [INF] Listening for route connections on 127.0.0.1:6222
[2989] 2020/06/09 10:57:23.003183 [INF] 127.0.0.1:51250 - rid:5 - Route connection created
[2997] 2020/06/09 10:57:23.003213 [INF] 127.0.0.1:6333 - rid:1 - Route connection created
[2989] 2020/06/09 10:57:23.045955 [INF] 127.0.0.1:6222 - rid:6 - Route connection created
[2997] 2020/06/09 10:57:23.045994 [INF] 127.0.0.1:51251 - rid:2 - Route connection created
[2989] 2020/06/09 10:57:23.046102 [INF] 127.0.0.1:6222 - rid:6 - Router connection closed
[2997] 2020/06/09 10:57:23.046211 [INF] 127.0.0.1:51251 - rid:2 - Router connection closed
Migrating applications to server 1
nats://127.0.0.1:4222
Load balancing subscriber.
Draining the connection.
Done with migration.
Published [control.migrate.subscriber] : 'nats://127.0.0.1:4222'
Received  [_INBOX.NrepkBO9OCu5iqQPbPmzrB.cxmJdoOR] : '+OK'
NATS Connection Event: nats: connection closed
Done with migration.
Published [control.migrate.publisher] : 'nats://127.0.0.1:4222'
Received  [_INBOX.mnxa6t2cQOr5lWjWU7frap.VYVJb63E] : '+OK'
Bouncing server 2
NATS Connection Event: nats: connection closed
[2997] 2020/06/09 10:57:27.039388 [INF] 127.0.0.1:6333 - rid:1 - Router connection closed
./scripts/migrate_test.sh: line 65:  2989 Killed: 9               nats-server -p 4333 -m 8333 --cluster "nats://127.0.0.1:6333" --routes "nats://127.0.0.1:6222" --pid s2.pid
[3005] 2020/06/09 10:57:28.053014 [INF] Starting nats-server version 2.2.0-beta.11
[3005] 2020/06/09 10:57:28.053099 [INF] Git commit [not set]
[3005] 2020/06/09 10:57:28.053476 [INF] Starting http monitor on 0.0.0.0:8333
[3005] 2020/06/09 10:57:28.053516 [INF] Listening for client connections on 0.0.0.0:4333
[3005] 2020/06/09 10:57:28.053520 [INF] Server id is NBZE7A265EBD2G4EOAZ4MQPYCHUUGERI6V6CYTBCMEKJW2S54UXECLJN
[3005] 2020/06/09 10:57:28.053522 [INF] Server is ready
[3005] 2020/06/09 10:57:28.053736 [INF] Listening for route connections on 127.0.0.1:6333
[2997] 2020/06/09 10:57:28.054095 [INF] 127.0.0.1:51262 - rid:7 - Route connection created
[3005] 2020/06/09 10:57:28.054151 [INF] 127.0.0.1:6222 - rid:1 - Route connection created
[2997] 2020/06/09 10:57:28.094229 [INF] 127.0.0.1:6333 - rid:8 - Route connection created
[3005] 2020/06/09 10:57:28.094259 [INF] 127.0.0.1:51263 - rid:2 - Route connection created
[2997] 2020/06/09 10:57:28.094368 [INF] 127.0.0.1:6333 - rid:8 - Router connection closed
[3005] 2020/06/09 10:57:28.094479 [INF] 127.0.0.1:51263 - rid:2 - Router connection closed
NATS @ ~/Dropbox/go/src/github.com/ColinSullivan1/nats-java-migrate (master) $
Done.  Received 600000 of 600000 messages.
Message Rate: 9999.93 msgs/sec
Loss Percentage: 0.000000
NATS Connection Event: nats: connection closed
Finished.
Publish rate: 9999 msgs/sec.
NATS Connection Event: nats: connection closed
```