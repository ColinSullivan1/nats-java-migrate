// Copyright 2020 Colin Sullivan
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.java.examples;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Consumer;
import io.nats.client.Dispatcher;
import io.nats.client.ErrorListener;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;

public class NatsLossSubscriber implements ErrorListener, ConnectionListener {

    static final String usageString = "\nUsage: java NatsLossSubscriber [server] <subject>\n"
            + "\nUse tls:// or opentls:// to require tls, via the Default SSLContext\n"
            + "\nSet the environment variable NATS_NKEY to use challenge response authentication by setting a file containing your private key.\n"
            + "\nSet the environment variable NATS_CREDS to use JWT/NKey authentication by setting a file containing your user creds.\n"
            + "\nUse the URL for user/pass/token authentication.\n";

    static final private int NANOSPERSEC = 1000000000;
    static final private String qgroup = "loss-subs";

    private String server;
    private String subject;
    private Connection conn;
    private Object connLock = new Object();
    private int pubCount = 0;
    AtomicInteger count = new AtomicInteger();

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch stopLatch = new CountDownLatch(1);

    public NatsLossSubscriber(String server, String subject) {
        this.server = server;
        this.subject = subject;
    }

    @Override
    public void errorOccurred(Connection conn, String error) {
        System.err.println("NATS Error: " + error);
    }

    @Override
    public void exceptionOccurred(Connection conn, Exception exp) {
        System.err.println("NATS Exception: " + exp.getMessage());
    }

    @Override
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        System.out.println("NATS Slow consumer");
    }

    @Override
    public void connectionEvent(Connection conn, Events type) {
        if (type == Events.CONNECTED || type == Events.DISCOVERED_SERVERS || type == Events.RESUBSCRIBED)
            return;

        System.out.println("NATS Connection Event: " + type);
    }

    MessageHandler msgHandler = new MessageHandler() {
        boolean started = false;

        @Override
        public void onMessage(Message msg) throws InterruptedException {
            if (!started) {
                pubCount = Integer.parseInt(new String(msg.getData(), StandardCharsets.UTF_8));
                System.out.printf("Received start message from publisher, expecting %d messages.\n", pubCount);
                startLatch.countDown();
                started = true;
                return;
            }

            if (msg.getData().length > 0) {
                count.incrementAndGet();
            } else {
                stopLatch.countDown();
            }
        }
    };

    ControlPlane.MigrationHandler lmh = new ControlPlane.MigrationHandler() {

         @Override
        public Connection migrate(String url) throws Exception {
            Connection oldConn;

            // Create a connection the the new server
            System.out.println("Connecting to server at:  " + url == null ? "locahost:4222" : url);
            Connection newConn = Nats.connect(getOptions(url));
            synchronized (connLock) {
                oldConn = conn;
            }

            // create an additional queue subscriber to start load 
            // balancing on the new server.
            System.out.println("Load balancing subscriber.");
            Dispatcher d = newConn.createDispatcher(msgHandler);
            d.subscribe(subject, qgroup);
            newConn.flush(Duration.ofSeconds(5));

            // Sleep to ensure interest is propagated from the new NATS 
            // server, so we won't lose messages.  Ideally, this would
            // be a message retried on a unique subject until received,
            // but here we'll cheat a bit.
            Thread.sleep(1000);

            // reassign the conn for use later in the program.  We synchronize
            // this to prevent race conditions.
            synchronized (connLock) {
                conn = newConn;
            }
            
            // drain the old connection, which will unsubscribe the 
            // old subscriber and then close it.  When the old subscriber 
            // unsubscribes, all new data will go to the new subscriber
            // created above.
            System.out.println("Draining the connection.");
            try  {
                oldConn.drain(Duration.ofSeconds(5));
            } catch (Exception e) {
                e.printStackTrace();
                // NOOP.
            }

            System.out.println("Done with migration.");

            return newConn;
        }
        
        @Override
        public void errorHandler(Exception e) {
            System.out.println("Migration exception: " + e.getMessage());
            e.printStackTrace();
        }
    };

    public Options getOptions(String url) {
        return new Options.Builder().
            server(url).
            connectionName("LossSubscriber").
            connectionTimeout(Duration.ofSeconds(5)).
            pingInterval(Duration.ofSeconds(10)).
            reconnectWait(Duration.ofMillis(20)). // We want subscribers to reconnect before publishers.
            maxReconnects(1024).
            errorListener(this).
            connectionListener(this).
            build();
    }

    public Connection connect(String url) throws Exception {
        Connection nc = Nats.connect(getOptions(server));
        Dispatcher d = nc.createDispatcher(msgHandler);
        d.subscribe(subject, qgroup);
        nc.flush(Duration.ofSeconds(5));
        return nc;
    }

    public void Run() throws Exception {
         
        System.out.println();
        System.out.printf("Trying to connect to %s and listen to %s for messages.\n", server, subject);
        System.out.println();

        conn = connect(server);
        new ControlPlane(conn, lmh, "subscriber");

        // wait for the first message
        startLatch.await();

        long startTime = System.nanoTime();

        // check every 10 seconds for a stall in case the
        // producer failed.
        Timer t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            int lastCount = 0;
            public void run() {
                int cur = count.get();
                if (lastCount == cur) {
                    stopLatch.countDown();
                }
                lastCount = count.get();
            }
        }, 10000, 10000);

        stopLatch.await();

        long elapsed = System.nanoTime() - startTime;

        int finalCount = count.get();
        System.out.printf("Done.  Received %d of %d messages.\n", finalCount, pubCount);
        System.out.printf("Message Rate: %.2f msgs/sec\n", (double)finalCount / ((double)elapsed / (double)NANOSPERSEC));
        System.out.printf("Loss Percentage: %f\n", 100.0*((double)pubCount - (double)finalCount) / (double)pubCount);

        synchronized (connLock) {
            conn.close();
        }
    }

    public static void main(String args[]) {
        String subject;
        String server;

        if (args.length == 2) {
            server = args[0];
            subject = args[1];
        } else if (args.length == 0) {
            server = Options.DEFAULT_URL;
            subject = "foo";
        } else {
            usage();
            return;
        }

        try {
            new NatsLossSubscriber(server, subject).Run();        
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }

    static void usage() {
        System.err.println(usageString);
        System.exit(-1);
    }
}