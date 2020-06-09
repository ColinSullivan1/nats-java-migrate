#!/bin/sh

function start_server_1 {
    nats-server -p 4222 -m 8222 --cluster "nats://127.0.0.1:6222" --routes "nats://127.0.0.1:6333" --pid s1.pid &
    sleep 1
}

function stop_server_1 {
   nats-server -sl stop=s1.pid  
}

function start_server_2 {
   nats-server -p 4333 -m 8333 --cluster "nats://127.0.0.1:6333" --routes "nats://127.0.0.1:6222" --pid s2.pid &
   sleep 1
}

function stop_server_2 {
    nats-server -sl stop=s2.pid 
}

function migrate_applications {
    nats-req control.migrate.subscriber "nats://127.0.0.1:$1"
    sleep 1
    nats-req control.migrate.publisher "nats://127.0.0.1:$1"
    sleep 1
}

echo "Starting Servers"
start_server_1
start_server_2

sleep 2

echo "Starting Test Apps"
./scripts/subloss.sh &
sleep 1
./scripts/publoss.sh &

# give some time for the apps to connect and publish messages
sleep 2

echo "Migrating applications"

for (( i=1; i<=5; i++ ))
do
    # migrate to server #2
    echo "Migrating applications to server 2"
    migrate_applications 4333

    # simulate server 1 being upgraded
    echo "Bouncing server 1"
    stop_server_1
    sleep 1
    start_server_1

    # migrate applications back to server 2
    echo "Migrating applications to server 1"
    migrate_applications 4222

    # simulate server 2 upgrade
    echo "Bouncing server 2"
    stop_server_2
    sleep 1
    start_server_2
done
