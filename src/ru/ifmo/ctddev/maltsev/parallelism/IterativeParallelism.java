package ru.ifmo.ctddev.maltsev.parallelism;

import info.kgeorgiy.java.advanced.concurrent.ScalarIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class IterativeParallelism implements ScalarIP {
    private ParallelMapper mapper;

    public IterativeParallelism() {

    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private <T, K> List<K> run(int n, List<? extends T> list, Function<List<? extends T>, K> function) throws InterruptedException {
        List<ParallelJob<List<? extends T>, K>> data = new ArrayList<>();
        List<List<? extends T>> mapperList = new ArrayList<>();
        n = Math.min(n, list.size());
        int x = list.size() / n;
        int k = n + n * x - list.size();
        int pos = 0;
        for (int i = 0; i < n; ++i) {
            int operationsForThisJob = i >= k ? x + 1 : x;
            int right = pos + operationsForThisJob;
            if (mapper != null) {
                mapperList.add(list.subList(pos, right));
            } else {
                data.add(i, new ParallelJob<>(list.subList(pos, right), function));
            }
            pos += operationsForThisJob;
        }
        if (mapper != null) {
            return mapper.map(function, mapperList);
        } else {
            List<Thread> threads = new ArrayList<>();
            data.stream().map(Thread::new).forEach(threads::add);
            threads.forEach(Thread::start);
            for (Thread thread : threads) {
                thread.join();
            }
            return data.stream().map(ParallelJob::getResult).collect(Collectors.toList());
        }

    }

    private <T> T comparatorOperation(int i, List<? extends T> list, Function<List<? extends T>, T> function) throws InterruptedException {
        List<T> result = run(i, list, function);
        return function.apply(result);
    }

    @Override
    public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return comparatorOperation(i, list, x -> x.stream().max(comparator).get());
    }

    @Override
    public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return comparatorOperation(i, list, x -> x.stream().min(comparator).get());
    }

    @Override
    public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        Function<List<? extends T>, Boolean> all = data -> data.stream().allMatch(predicate);
        List<Boolean> result = run(i, list, all);
        return result.stream().allMatch(Predicate.isEqual(true));
    }

    @Override
    public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        Function<List<? extends T>, Boolean> all = data -> data.stream().anyMatch(predicate);
        List<Boolean> result = run(i, list, all);
        return result.stream().anyMatch(Predicate.isEqual(true));
    }

    private static class ParallelJob<T, K> implements Runnable {

        private final T data;
        private final Function<T, K> function;
        private K result;

        private ParallelJob(T data, Function<T, K> function) {
            this.data = data;
            this.function = function;
        }

        @Override
        public void run() {
            result = function.apply(data);
        }

        K getResult() {
            return result;
        }
    }


}
