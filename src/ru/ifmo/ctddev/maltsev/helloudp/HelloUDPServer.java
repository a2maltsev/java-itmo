package ru.ifmo.ctddev.maltsev.helloudp;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPServer implements HelloServer {
    private ExecutorService service;
    private DatagramSocket receiver;

    public static void main(String[] args) {
        HelloUDPServer server = new HelloUDPServer();
        if (args != null && args[0] != null && args[1] != null) {
            server.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        } else {
            System.err.println("Wrong arguments");
        }
    }

    @Override
    public void start(int port, int threads) {
        service = Executors.newFixedThreadPool(threads);
        try {
            receiver = new DatagramSocket(port);
            for (int i = 0; i < threads; i++) {
                service.submit(new ServerRunnable());
            }
        } catch (SocketException ignored) {

        }
    }

    @Override
    public void close() {
        service.shutdown();
        try {
            service.awaitTermination(5, TimeUnit.SECONDS);
            service.shutdownNow();
        } catch (InterruptedException ignored) {
        }
        if (receiver != null && !receiver.isClosed()) {
            receiver.close();
        }
    }

    private class ServerRunnable implements Runnable {

        @Override
        public void run() {
            try (DatagramSocket sender = new DatagramSocket()) {
                DatagramPacket receivedPacket = new DatagramPacket(
                        new byte[sender.getReceiveBufferSize()], sender.getReceiveBufferSize());
                while (!Thread.interrupted() && !receiver.isClosed()) {
                    receiver.receive(receivedPacket);
                    String received = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
                    byte[] arr = String.format("Hello, %s", received).getBytes("UTF-8");
                    sender.send(new DatagramPacket(arr, arr.length, receivedPacket.getAddress(),
                            receivedPacket.getPort()));
                }
            } catch (IOException ignored) {

            }
        }
    }


}
