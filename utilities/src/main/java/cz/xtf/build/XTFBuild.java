package cz.xtf.build;

/**
 * Interface for getting build definition from collection class.
 * <p>
 * Meant for enums so it can be easily used with annotations.
 */
public interface XTFBuild {
	public BuildDefinition getBuildDefinition();
}
