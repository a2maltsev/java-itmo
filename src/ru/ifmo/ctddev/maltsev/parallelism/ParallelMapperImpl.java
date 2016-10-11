package ru.ifmo.ctddev.maltsev.parallelism;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final List<Thread> threadList;
    private final JobQueue jobQueue;

    public ParallelMapperImpl(int threads) {
        threadList = new ArrayList<>();
        jobQueue = new JobQueue();
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(new JobProcessor(jobQueue));
            threadList.add(thread);
            thread.start();
        }
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        List<ParallelJob<? super T, ? extends R>> jobs = new ArrayList<>();
        for (T arg : list) {
            ParallelJob<? super T, ? extends R> job = new ParallelJob<>(arg, function);
            jobQueue.add(job);
            jobs.add(job);
        }
        List<R> result = new ArrayList<>();
        for (ParallelJob<? super T, ? extends R> job : jobs) {
            result.add(job.getResult());
        }
        return result;
    }

    @Override
    public void close() throws InterruptedException {
        threadList.forEach(Thread::interrupt);
    }

    private static class JobProcessor implements Runnable {

        final JobQueue jobQueue;

        JobProcessor(JobQueue jobQueue) {
            this.jobQueue = jobQueue;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    jobQueue.next().process();
                }
            } catch (InterruptedException ignored) {

            }
        }
    }

    private static class JobQueue {

        final Queue<ParallelJob<?, ?>> queue;

        JobQueue() {
            queue = new LinkedList<>();
        }

        synchronized void add(ParallelJob<?, ?> job) {
            queue.add(job);
            this.notifyAll();
        }

        synchronized ParallelJob<?, ?> next() throws InterruptedException {
            while (queue.isEmpty()) {
                this.wait();
            }
            return queue.poll();
        }

    }

    private class ParallelJob<T, R> {

        private final T argument;
        private final Function<? super T, ? extends R> function;
        private R result;

        ParallelJob(T arg, Function<? super T, ? extends R> function) {
            this.argument = arg;
            this.function = function;
        }

        synchronized void process() {
            result = function.apply(argument);
            this.notifyAll();
        }

        synchronized R getResult() throws InterruptedException {
            while (result == null) {
                this.wait();
            }
            return result;
        }

    }
}
