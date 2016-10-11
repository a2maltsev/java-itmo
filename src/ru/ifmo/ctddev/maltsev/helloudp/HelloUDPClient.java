package ru.ifmo.ctddev.maltsev.helloudp;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPClient implements HelloClient {


    public static void main(String[] args) {
        HelloUDPClient client = new HelloUDPClient();
        if (args != null && args.length == 5 && Arrays.stream(args).allMatch(x -> x != null)) {
            client.start(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        } else {
            System.err.println("Wrong arguments");
        }
    }


    @Override
    public void start(String ip, int port, String prefix, int requests, int threads) {
        ExecutorService service = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            service.submit(new ClientRunnable(ip, port, prefix, requests, i));
        }
        service.shutdown();
        try {
            service.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException ignored) {

        }
    }

    private class ClientRunnable implements Runnable {

        private final String host;
        private final int port;
        private final String prefix;
        private final int requests;
        private final int threadNumber;

        ClientRunnable(String host, int port, String prefix, int requests, int threadNumber) {
            this.host = host;
            this.port = port;
            this.prefix = prefix;
            this.requests = requests;
            this.threadNumber = threadNumber;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(500);
                byte[] receive = new byte[socket.getReceiveBufferSize()];
                DatagramPacket packetToReceive = new DatagramPacket(receive, receive.length);
                InetAddress inetAddress = InetAddress.getByName(host);
                String message;
                for (int sent = 0; sent < requests; sent++) {
                    message = String.format("%s%d_%d", prefix, threadNumber, sent);
                    byte[] toSend = message.getBytes("UTF-8");
                    DatagramPacket packetToSend = new DatagramPacket(toSend, toSend.length, inetAddress, port);
                    while (true) {
                        socket.send(packetToSend);
                        try {
                            socket.receive(packetToReceive);
                            String receivedString = new String(packetToReceive.getData(), packetToReceive.getOffset(),
                                    packetToReceive.getLength(), "UTF-8");
                            System.out.println(message);
                            System.out.println(receivedString);
                            String mustReceive = String.format("Hello, %s", message);
                            if (receivedString.equals(mustReceive)) {
                                break;
                            }
                        } catch (SocketTimeoutException ignored) {
                        }
                    }
                }
            } catch (IOException e) {
                System.err.printf("Exception %s%n", e.getMessage());
            }
        }
    }
}
