package ru.ifmo.ctddev.maltsev.arrayset;

import java.util.*;

public class ArraySet<T extends Comparable<? super T>> extends AbstractSet<T> implements SortedSet<T> {

    private final List<T> list;
    private Comparator<? super T> comparator;
    private Comparator<T> equalsComparator = (x, y) -> (comparator.compare(x, y) >= 0) ? 1 : -1;
    private boolean isNatural = false;

    public ArraySet() {
        this(Collections.emptyList());
    }

    public ArraySet(Collection<? extends T> collection) {
        this(collection, Comparable::compareTo);
        isNatural = true;
    }

    public ArraySet(Collection<? extends T> collection, Comparator<T> comparator) {
        TreeSet<T> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(collection);
        list = Collections.unmodifiableList(new ArrayList<>(treeSet));
        this.comparator = comparator;
    }

    private ArraySet(List<T> list, Comparator<? super T> comparator, boolean isNatural) {
        this.list = list;
        this.comparator = comparator;
        this.isNatural = isNatural;
    }

    private int binSearch(T key, Comparator<? super T> comparator) {
        int index = Collections.binarySearch(list, key, comparator);
        return (index >= 0) ? index : (-index - 2);
    }


    @Override
    public Iterator<T> iterator() {
        return list.iterator();
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return headSet(toElement).tailSet(fromElement);
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        int toIndex = binSearch(toElement, comparator);
        if (toIndex != -1 && comparator.compare(toElement, list.get(toIndex)) == 0) {
            toIndex--;
        }
        return new ArraySet<>(list.subList(0, toIndex + 1), comparator, isNatural);
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        int fromIndex = binSearch(fromElement, equalsComparator) + 1;
        if (fromIndex == -1) {
            fromIndex++;
        }
        return new ArraySet<>(list.subList(fromIndex, list.size()), comparator, isNatural);
    }

    @Override
    public Comparator<? super T> comparator() {
        return isNatural ? null : comparator;
    }


    @Override
    public T first() {
        if (list.isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(0);
    }

    @Override
    public T last() {
        if (list.isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(list.size() - 1);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return Collections.binarySearch(list, (T) o, comparator) >= 0;
    }
}