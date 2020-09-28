package cz.xtf.builder.builders.buildconfig;

public class ImageStreamTagImageSource extends ImageSource {

    public ImageStreamTagImageSource(String name) {
        this(name, null);
    }

    public ImageStreamTagImageSource(String name, String namespace) {
        super(name, namespace);
        kind = "ImageStreamTag";
    }
}
