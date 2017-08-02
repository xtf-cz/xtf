package cz.xtf.junit.filter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Composite test filter container with collection of unique test filters.
 */
@Slf4j
@NoArgsConstructor
public abstract class CompositeFilterContainer<T> {

	@Getter protected final Set<T> filters = new HashSet<>();

	/**
	 * Adds all the exclusion filters to this collection.
	 *
	 * @param filters the filters to be added
	 */
	public void addFilters(Collection<T> filters) {
		filters.forEach(this::addFilter);
	}

	/**
	 * Adds an exclusion filter to this collection.
	 *
	 * @param filter the filter to be added
	 */
	public void addFilter(T filter) {
		if (filter == null) { // ignore null filters
			return;
		}
		if (filter instanceof CompositeFilterContainer) {
			addFilters(((CompositeFilterContainer) filter).getFilters());
		} else {
			filters.add(filter);
		}
	}
}
