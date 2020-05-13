package cz.xtf.core.openshift.imagestreams;

import io.fabric8.openshift.api.model.ImageStream;

/**
 * An image stream with its related subscriptions
 */
class ImageStreamHolder {
	private final ImageStream imageStream;
	private int subscriptions;
	
	private ImageStreamHolder(final ImageStream subscribedImageStream, final int initialSubscriptions) {
		this.imageStream = subscribedImageStream;
		this.subscriptions = initialSubscriptions;
	}
	
	ImageStreamHolder subscribe() {
		this.subscriptions += 1;
		return this;
	}
	
	ImageStreamHolder unsubscribe() {
		this.subscriptions -= 1;
		return this;
	}
	
	ImageStream get() {
		return imageStream;
	}
	
	int count() {
		return subscriptions;
	}
	
	static final class Builder {
		private ImageStream imageStream;

		Builder() {}

		Builder withImageStream(final ImageStream imageStream) {
			this.imageStream = imageStream;
			return this;
		}

		ImageStreamHolder build() {
			return new ImageStreamHolder(imageStream, 1);
		}
	}
}
