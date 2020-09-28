package cz.xtf.core.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;

import io.fabric8.kubernetes.api.model.Event;

/**
 * List of events implementing {@link List} interface to easy up the work with events
 */
public class EventList implements List<Event> {

    private final ArrayList<Event> eventList;

    public EventList(List<Event> eventList) {
        this.eventList = new ArrayList<>(eventList);
    }

    public EventListFilter filter() {
        return new EventListFilter(this);
    }

    public boolean containsAll(Collection<?> c) {
        return eventList.containsAll(c);
    }

    public boolean addAll(Collection<? extends Event> c) {
        return eventList.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends Event> c) {
        return eventList.addAll(index, c);
    }

    public boolean equals(Object o) {
        return eventList.equals(o);
    }

    public int hashCode() {
        return eventList.hashCode();
    }

    public int size() {
        return eventList.size();
    }

    public boolean isEmpty() {
        return eventList.isEmpty();
    }

    public boolean contains(Object o) {
        return eventList.contains(o);
    }

    public int indexOf(Object o) {
        return eventList.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return eventList.lastIndexOf(o);
    }

    public Object[] toArray() {
        return eventList.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return eventList.toArray(a);
    }

    public boolean add(Event event) {
        return eventList.add(event);
    }

    public void add(int index, Event element) {
        eventList.add(index, element);
    }

    public Event get(int index) {
        return eventList.get(index);
    }

    public Event set(int index, Event element) {
        return eventList.set(index, element);
    }

    public Event remove(int index) {
        return eventList.remove(index);
    }

    public boolean remove(Object o) {
        return eventList.remove(o);
    }

    public void clear() {
        eventList.clear();
    }

    public boolean removeAll(Collection<?> c) {
        return eventList.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return eventList.retainAll(c);
    }

    public ListIterator<Event> listIterator(int index) {
        return eventList.listIterator(index);
    }

    public ListIterator<Event> listIterator() {
        return eventList.listIterator();
    }

    public Iterator<Event> iterator() {
        return eventList.iterator();
    }

    public List<Event> subList(int fromIndex, int toIndex) {
        return eventList.subList(fromIndex, toIndex);
    }

    public Spliterator<Event> spliterator() {
        return eventList.spliterator();
    }
}
