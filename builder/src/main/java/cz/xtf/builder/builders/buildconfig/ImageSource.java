package cz.xtf.builder.builders.buildconfig;

import java.util.ArrayList;
import java.util.List;

public abstract class ImageSource {
    protected final String name;
    protected final String namespace;
    protected String kind;

    List<ImageSourcePaths> paths = new ArrayList<>();

    public ImageSource(String name) {
        this(name, null);
    }

    public ImageSource(String name, String namespace) {
        this.name = name;
        this.namespace = namespace;
    }

    public ImageSource addPath(String destinationDir, String sourcePath) {
        paths.add(new ImageSourcePaths(sourcePath, destinationDir));
        return this;
    }

    public String getKind() {
        return kind;
    }

    public List<ImageSourcePaths> getPaths() {
        return paths;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }
}
