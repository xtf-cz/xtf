package cz.xtf.openshift.builder.buildconfig;

public class ImageStreamTagImageSource extends ImageSource {

	public ImageStreamTagImageSource(String name, String namespace) {
		super(name, namespace);
		kind = "ImageStreamTag";
	}

	public ImageStreamTagImageSource(String name) {
		this(name, null);
	}
}
