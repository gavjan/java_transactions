/*
 * University of Warsaw
 * Concurrent Programming Course 2020/2021
 * Java Assignment
 * 
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp1.solution;

import java.util.Collection;

import cp1.base.TransactionManager;
import cp1.base.LocalTimeProvider;
import cp1.base.Resource;

/**
 * A factory for instantiating transaction managers.
 * 
 * @author Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
public final class TransactionManagerFactory {

	/**
	 * Returns a new transaction manager that takes control over a given collection of resources.
	 * @param resources The collection of resources.
	 * @param timeProvider A local time provider.
	 * @return A new transaction manager for controlling the resources.
	 */
	public static TransactionManager newTM(Collection<Resource> resources, LocalTimeProvider timeProvider) {
		return new cp1.solution.TransactionManager(resources, timeProvider);
	}
	
}
