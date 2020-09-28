package cz.xtf.junit5.interfaces;

import cz.xtf.core.bm.ManagedBuild;

/**
 * Interface for getting build definition from collection class.
 * <p>
 * Meant for enums so it can be easily used with annotations.
 */
public interface BuildDefinition<T extends ManagedBuild> {
    T getManagedBuild();
}
