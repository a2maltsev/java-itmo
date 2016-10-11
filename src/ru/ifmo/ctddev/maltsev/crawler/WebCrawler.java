package ru.ifmo.ctddev.maltsev.crawler;

import info.kgeorgiy.java.advanced.crawler.*;
import javafx.util.Pair;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


public class WebCrawler implements Crawler {
    private Map<String, Object> finished;
    private Map<String, Object> visited;
    private Map<String, IOException> errors;
    private Map<String, ConcurrentLinkedDeque<Pair<String, Integer>>> downloadQueue;
    private Semaphore semaphore;
    private Downloader downloader;
    private ExecutorService downloadingService;
    private ExecutorService extractingService;


    public WebCrawler(Downloader d, int downloaders, int extractors, int perHost) {
        this.downloader = d;
        this.downloadingService = Executors.newFixedThreadPool(downloaders);
        this.extractingService = Executors.newFixedThreadPool(extractors);
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0 || args.length > 4) {
            System.err.println("invalid arguments");
            return;
        }
        String url = args[0];
        int downloaders = (args.length > 1) ? Integer.parseInt(args[1]) : 10;
        int extractors = (args.length > 2) ? Integer.parseInt(args[2]) : 10;
        int perHost = (args.length > 3) ? Integer.parseInt(args[3]) : 10;
        try (WebCrawler crawler = new WebCrawler(new CachingDownloader(new File("./tmp2/")), downloaders, extractors, perHost)) {
            crawler.download(url, 1);
        } catch (IOException ignored) {

        }

    }

    private void extractLinks(int depth, Document document) {
        try {
            if (depth > 1) {
                List<String> links = document.extractLinks();
                links.forEach(url -> {
                    try {
                        String host = URLUtils.getHost(url);
                        downloadQueue.putIfAbsent(host, new ConcurrentLinkedDeque<>());
                        downloadQueue.get(host).addLast(new Pair<>(url, depth - 1));
                        semaphore.acquire();
                        downloadingService.submit(() -> this.downloadExtracted(host));
                    } catch (MalformedURLException | InterruptedException ignored) {

                    }
                });
            }
        } catch (IOException ignored) {

        } finally {
            semaphore.release();
        }
    }

    private void downloadExtracted(String host) {
        try {
            if (!downloadQueue.get(host).isEmpty()) {
                Pair<String, Integer> pair = downloadQueue.get(host).removeFirst();
                String url = pair.getKey();
                int depth = pair.getValue();
                if (visited.putIfAbsent(url, new Object()) == null) {
                    try {
                        Document document = downloader.download(url);
                        finished.put(url, new Object());
                        semaphore.acquire();
                        extractingService.submit(() -> extractLinks(depth, document));
                    } catch (IOException e) {
                        errors.put(url, e);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            semaphore.release();
        }
    }


    @Override
    public Result download(String url, int depth) {
        final int semSize = Integer.MAX_VALUE;
        semaphore = new Semaphore(semSize);
        finished = new ConcurrentHashMap<>();
        visited = new ConcurrentHashMap<>();
        errors = new ConcurrentHashMap<>();
        downloadQueue = new ConcurrentHashMap<>();
        try {
            String host = URLUtils.getHost(url);
            downloadQueue.putIfAbsent(host, new ConcurrentLinkedDeque<>());
            downloadQueue.get(host).addLast(new Pair<>(url, depth));
            semaphore.acquire();
            downloadingService.submit(() -> this.downloadExtracted(host));
        } catch (InterruptedException | MalformedURLException e) {
            return null;
        }
        try {
            semaphore.acquire(semSize);
        } catch (InterruptedException ignored) {

        }
        return new Result(new ArrayList<>(finished.keySet()), errors);
    }

    @Override
    public void close() {
        downloadingService.shutdown();
        extractingService.shutdown();
        downloadingService.shutdownNow();
        extractingService.shutdownNow();
    }
}










