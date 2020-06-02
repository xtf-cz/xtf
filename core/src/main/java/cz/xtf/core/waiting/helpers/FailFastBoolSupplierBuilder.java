package cz.xtf.core.waiting.helpers;


import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.event.EventList;
import cz.xtf.core.event.EventListFilter;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The class provides support to create {@link BooleanSupplier} that checks if a waiter should fail due to an error state
 * of cluster. The function is supposed to be called by a {@link cz.xtf.core.waiting.Waiter}.
 * If it returns true, the waiter should fail.
 *
 * Example of use:
 * <pre>
 *     FailFastBoolSupplierBuilder
 *     		.ofOpenshiftAndBmNamespace()
 *     		.events()
 *     		.after(ZonedDateTime.now())
 *     		.ofNames("my-app.*")
 *     		.ofMessages("Failed.*")
 *     		.atLeasOneExists()
 *     		.build()
 * </pre>
 *
 * You can create more checks for events and every one of them will be checked. If one of them returns true, the whole
 * fail fast function returns true.
 */
public class FailFastBoolSupplierBuilder {

	private final List<OpenShift> openShifts = new ArrayList<>();
	private final List<BooleanSupplier> suppliers = new ArrayList<>();
	private long intervalMillis;

	private FailFastBoolSupplierBuilder(String... namespaces) {
		intervalMillis = 1_000L;
		if (namespaces.length == 0) {
			openShifts.add(OpenShifts.master());
		} else {
			for (String namespace : namespaces) {
				openShifts.add(OpenShifts.master(namespace));
			}
		}
	}

	public static FailFastBoolSupplierBuilder ofOpenshiftAndBmNamespace() {
		return ofNamespaces(OpenShiftConfig.namespace(), BuildManagerConfig.namespace());
	}

	public static FailFastBoolSupplierBuilder ofNamespaces(String... namespaces) {
		return new FailFastBoolSupplierBuilder(namespaces);
	}

	public EventFailFastBoolSupplierBuilder events() {
		return new EventFailFastBoolSupplierBuilder(this);
	}

	private void addBoolSupplier(BooleanSupplier supplier) {
		this.suppliers.add(supplier);
	}

	public FailFastBoolSupplierBuilder interval(long intervalMillis) {
		this.intervalMillis = intervalMillis;
		return this;
	}

	public BooleanSupplier build() {
		return () -> {
			for (BooleanSupplier supplier : suppliers) {
				if (supplier.getAsBoolean()) {
					return true;
				}
			}
			return false;
		};
	}

	public static class EventFailFastBoolSupplierBuilder {

		private final FailFastBoolSupplierBuilder failFastBoolSupplierBuilder;
		private String[] names = null;
		private ZonedDateTime after;
		private String[] reasons;
		private String[] messages;
		private String[] types;
		private String[] kinds;

		private EventFailFastBoolSupplierBuilder(FailFastBoolSupplierBuilder failFastBoolSupplierBuilder) {
			this.failFastBoolSupplierBuilder = failFastBoolSupplierBuilder;
		}

		/**
		 * Regexes to match event involved object name.
		 */
		public EventFailFastBoolSupplierBuilder ofNames(String... name) {
			this.names = name;
			return this;
		}

		/**
		 * Array of demanded reasons of events (case in-sensitive). One of them must be equal.
		 */
		public EventFailFastBoolSupplierBuilder ofReasons(String... reasons) {
			this.reasons = reasons;
			return this;
		}


		/**
		 * Array of demanded types of events (case in-sensitive). One of them must be equal.
		 * For example: {@code Warning}, {@code Normal}, ...
		 */
		public EventFailFastBoolSupplierBuilder ofTypes(String... types) {
			this.types = types;
			return this;
		}


		/**
		 * Array of demanded object kinds of events (case in-sensitive). One of them must be equal.
		 * For example: {@code persistentvolume}, {@code pod}, ...
		 */
		public EventFailFastBoolSupplierBuilder ofKinds(String... kinds) {
			this.kinds = kinds;
			return this;
		}

		/**
		 * Regexes for demanded messages. One of them must match.
		 * @param messages
		 * @return
		 */
		public EventFailFastBoolSupplierBuilder ofMessages(String... messages) {
			this.messages = messages;
			return this;
		}

		/**
		 * If at least one event exist (after filtration), final function returns true.
		 */
		public FailFastBoolSupplierBuilder atLeastOneExists() {
			// function is invoked every time...everytime we get events and filter them
			failFastBoolSupplierBuilder.addBoolSupplier(() -> getFilterEventList().count() >= 1);
			return failFastBoolSupplierBuilder;
		}

		/**
		 * Consider event after certain time.
		 */
		public EventFailFastBoolSupplierBuilder after(ZonedDateTime after) {
			this.after = after;
			return this;
		}

		private EventListFilter getFilterEventList() {
			EventListFilter filter = getEventsForAllNamespaces().filter();
			if (names != null) {
				filter.ofObjNames(names);
			}
			if (after != null) {
				filter.inOneOfTimeWindows(after, ZonedDateTime.now());
			}
			if (reasons != null) {
				filter.ofReasons(reasons);
			}
			if (messages != null) {
				filter.ofMessages(messages);
			}
			if (types != null) {
				filter.ofEventTypes(types);
			}
			if (kinds != null) {
				filter.ofObjKinds(kinds);
			}
			return filter;
		}

		private EventList getEventsForAllNamespaces() {
			EventList events = null;

			for (OpenShift openShift : this.failFastBoolSupplierBuilder.openShifts) {
				if (events == null) {
					events = openShift.getEventList();
				} else {
					events.addAll(openShift.getEventList());
				}
			}
			return events;
		}
	}
}
