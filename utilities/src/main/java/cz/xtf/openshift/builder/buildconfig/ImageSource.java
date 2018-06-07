package cz.xtf.openshift.builder.buildconfig;

import java.util.ArrayList;
import java.util.List;

import cz.xtf.tuple.Tuple;

public abstract class ImageSource {
	protected final String name;
	protected final String namespace;
	protected String kind;
	
	List<Tuple.Pair<String, String>> paths = new ArrayList<>();

	public ImageSource(final String name, final String namespace) {
		this.name = name;
		this.namespace = namespace;
	}

	public ImageSource(final String name) {
		this(name,  null);
	}

	public ImageSource addPath(final String destinationDir, final String sourcePath) {
		paths.add(Tuple.pair(destinationDir, sourcePath));
		return this;
	}

	public String getKind() {
		return kind;
	}

	public List<Tuple.Pair<String, String>> getPaths() {
		return paths;
	}

	public String getName() {
		return name;
	}

	public String getNamespace() {
		return namespace;
	}
}
