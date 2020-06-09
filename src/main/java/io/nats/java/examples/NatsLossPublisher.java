// Copyright 2020 The NATS Authors
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

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Consumer;

public class NatsLossPublisher {

    private String subject;
    private int messageSize;
    private String server;
    private int count;
    private int rate;
    private long delay;
    private long startTime;
    private Connection conn;

    private synchronized void setConnection(Connection c) {
        conn = c;
    }

    private synchronized Connection getConnection() {
        return conn;
    }

    static final private int NANOSPERSEC = 1000000000;
    static final private int NANOSPERMS = NANOSPERSEC / 1000;

    static final String usageString = "\nUsage: java NatsLossPublisher <server> <count> <rate (msgs/sec)> <subject> <msgsize>\n"
            + "\nUse tls:// or opentls:// to require tls, via the Default SSLContext\n"
            + "\nSet the environment variable NATS_NKEY to use challenge response authentication by setting a file containing your private key.\n"
            + "\nSet the environment variable NATS_CREDS to use JWT/NKey authentication by setting a file containing your user creds.\n"
            + "\nUse the URL for user/pass/token authentication.\n";

    public void adjustAndSleep(int currentCount) throws InterruptedException {

        long elapsed = System.nanoTime() - startTime;
        double r = (double) currentCount / ((double) elapsed / (double) NANOSPERSEC);
        long adj = delay / 20; // 5%
        if (adj == 0) {
            adj = 1; // 1ns min
        }
        if (r < rate) {
            delay -= adj;
        } else if (r > rate) {
            delay += adj;
        }
        if (delay < 0) {
            delay = 0;
        }

        int nanos;
        long millis = 0;

        if (delay < NANOSPERMS) {
            nanos = (int) delay;
        } else {
            millis = delay / (NANOSPERMS);
            nanos = (int) (delay - (NANOSPERMS * millis));
        }
        Thread.sleep(millis, nanos);
    }

    public NatsLossPublisher(String server, int count, int rate, String subject, int size) {
        this.server = server;
        this.count = count;
        this.rate = rate;
        this.subject = subject;
        this.messageSize = size;
        delay = NANOSPERSEC / count;
    }

    private class Listeners implements ErrorListener, ConnectionListener {

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
    }

    ControlPlane.MigrationHandler lmh = new ControlPlane.MigrationHandler() {

        @Override
        public Connection migrate(String url) throws Exception {
           
            // Create a connection the the new server
            Connection newConn = Nats.connect(getOptions(url));
            Connection oldConn = getConnection();

            // setting the connection to null will skip publishing
            setConnection(null);

            // reassign the conn for use later in the program and to resume
            // publishing
            setConnection(newConn);
           
            // close the old connection
            try {
                oldConn.drain(Duration.ofSeconds(5));
            }
            catch (InterruptedException e) {
                // NOOP
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
        Listeners l = new Listeners();
        return new Options.Builder().
            server(url).
            connectionName("LossPublisher").
            connectionTimeout(Duration.ofSeconds(5)).
            pingInterval(Duration.ofSeconds(10)).
            reconnectWait(Duration.ofSeconds(5)).
            reconnectBufferSize(messageSize * rate * 15). // tolerate 15s of publishing outage during reconnect.
            maxReconnects(1024).
            errorListener(l).
            connectionListener(l).
            build();
    }

    public void Run() {
        try {
            Connection nc = Nats.connect(getOptions(server));
            setConnection(nc);
            new ControlPlane(nc, lmh, "publisher");

            System.out.println();
            System.out.printf("Sending %s messages of %d bytes on %s, server is %s\n", count, messageSize, subject, server);
            System.out.println();

            // send the expected count to the subscriber
            nc.publish(subject, Integer.toString(count).getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[messageSize];

            startTime = System.nanoTime();

            for (int i = 0; i < count; i++) {
                try {
                    nc = getConnection();
                    if (nc != null) {
                        nc.publish(subject, payload);
                        adjustAndSleep(i+1);
                    } else {
                        i--;
                        Thread.sleep(250);
                        System.out.println("publishing paused...");
                    }
                } catch (final Exception e) {
                    System.out.println("Publish: Exception: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            long endTime = System.nanoTime();

            // publish null message as EOS (end of stream)
            nc.publish(subject, null);
            nc.flush(Duration.ofSeconds(2));
            nc.close();

            System.out.println("Finished.");

            double seconds = (double)(endTime - startTime) / (double)NANOSPERSEC;
            System.out.printf("Publish rate: %d msgs/sec.\n", (int)(count / seconds));

        } catch (final Exception exp) {
            exp.printStackTrace();
        }
    }

    public static void main(final String args[]) {
        String subject;
        int messageSize;
        String server;
        int count;
        int rate;

        if (args.length == 5) {
            server = args[0];
            count = Integer.parseInt(args[1]);
            rate = Integer.parseInt(args[2]);
            subject = args[3];
            messageSize = Integer.parseInt(args[4]);
        } else if (args.length == 0) {
            server = "nats://localhost:4222";
            count = 100000;
            rate = 200;
            subject = "foo";
            messageSize = 128;
        } else {
            usage();
            return;
        }

        if (messageSize < 1) {
            System.err.println("Error:  message size must be greater than 0");
            System.exit(1);
        }

        new NatsLossPublisher(server, count, rate, subject, messageSize).Run();
    }

    static void usage() {
        System.err.println(usageString);
        System.exit(-1);
    }
}