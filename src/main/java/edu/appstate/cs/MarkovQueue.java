package edu.appstate.cs;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.Collectors;


/**
 * Implements an ordered table of strings, with deep hashing and off-the-edge pushing to the end of the queue. 
 * @author Alek Ratzloff <alekratz@gmail.com>
 *
 */
public class MarkovQueue extends ArrayDeque<String> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7678929707313035652L;
	
	private int order;
	
	/**
	 * Creates a queue for a Markov chain that will have a limited number of items in it. The order is the limit.
	 * @param order
	 */
	public MarkovQueue(int order) {
		this.order = order;
	}

    /**
     * Creates a queue for a Markov chain that will infer its order from the length of the items
     * @param items
     */
    public MarkovQueue(String[] items) {
        this.order = items.length;
        Arrays.stream(items).forEach(s -> push(s));
    }

	@Override
	public void addFirst(String e) {
		while(this.size() >= order) {
			this.removeLast();
		}
		super.addFirst(e);
	}
	
	@Override
	public void addLast(String e) {
		while(this.size() >= order) {
			this.removeFirst();
		}
		super.addLast(e);
	}
	
	@Override
	public int hashCode() {
		int h = 1;
		for(String s : this) {
			h += 31 * h + (s == null ? 0 : s.hashCode());
		}
		return h;
	}
	
	@Override
	public boolean equals(Object queue) {
		return ((MarkovQueue)queue).hashCode() == hashCode();
	}
	
	@Override
	public MarkovQueue clone() {
		MarkovQueue c = new MarkovQueue(order);
		for(String s : this)
			c.add(s);
		return c;
	}
	
	@Override
	public String toString() {
		return this.stream().collect(Collectors.joining(" "));
	}
	
	public int getOrder() {
		return order;
	}
	
}
