package cz.xtf.core.openshift.imagestreams;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.openshift.api.model.ImageStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a map based registry for keeping track of {@link ImageStream} 
 * instances.
 * 
 * When an image stream passed in to be cached via the 
 * {@link MapBasedImageStreamRegistry#register(io.fabric8.openshift.api.model.ImageStream, java.util.function.UnaryOperator)} method
 * the registry checks whether an instance with the same key has been cached 
 * in order to avoid errors due to the attempt of creating two identical
 * image streams for the same namespace.
 */
@Slf4j
public class MapBasedImageStreamRegistry implements ImageStreamRegistry {

	private static MapBasedImageStreamRegistry instance;
	private final OpenShift openShift;
	private final ConcurrentHashMap<String, ImageStreamHolder> internalCache = new ConcurrentHashMap<>();

	private MapBasedImageStreamRegistry(OpenShift openShift) {
		this.openShift = openShift;
	}

	private String forgeKey(ImageStream imageStream) {
		return forgeKey(imageStream.getMetadata().getName());
	}

	private String forgeKey(String imageStreamName) {
		return String.format("namespace=%s;name=%s", openShift.getNamespace(), imageStreamName);
	}

	//	singleton here
	public static MapBasedImageStreamRegistry get() {
		if (instance == null) {
			synchronized (MapBasedImageStreamRegistry.class) {
				if (instance == null) {
					instance = new MapBasedImageStreamRegistry(OpenShifts.master());
					//  let's initialize the cache with survivors
					log.debug("About to initialize image streams cache with survivors");
					instance.openShift.imageStreams().list().getItems().stream().forEach(is -> 
						instance.internalCache.put(
							instance.forgeKey(is), 
							new ImageStreamHolder.Builder().withImageStream(is).build()
						)
					);					
					log.debug(String.format("Chache was filled in with survivors and is now storing %d items", 
						instance.internalCache.size())
					);
				}
			}
		}
		return instance;
	}

	@Override
	public ImageStream register(ImageStream imageStream, UnaryOperator<ImageStream> imageStreamCreationFunc) {
		String key = forgeKey(imageStream);
		log.debug(String.format("About to evaluate caching %s image stream with key: %s. Chache is currently storing %d items", 
			imageStream.getMetadata().getName(), 
			key, 
			internalCache.size())
		);
		ImageStream item;
		synchronized(MapBasedImageStreamRegistry.class) {
			ImageStreamHolder existing = internalCache.get(key);
			if (existing != null) {
				existing.subscribe();
			} else {
				existing = new ImageStreamHolder.Builder().withImageStream(imageStreamCreationFunc.apply(imageStream)).build();
				existing = internalCache.put(key, existing);
			}
			item = existing.get();
		}
		log.debug(String.format("Compute completed. Chache is now storing %d items", 
			internalCache.size())
		);
		return item;
	}

	@Override
	public ImageStream unregister(String name, Consumer<ImageStream> imageStreamDeletion) {
		String key = forgeKey(name);
		log.debug(String.format("About to remove %s image stream with key: %s. Chache is currently storing %d items", 
			name, 
			key, 
			internalCache.size())
		);
		ImageStream item;
		synchronized(MapBasedImageStreamRegistry.class) {
			ImageStreamHolder existing = internalCache.get(key);
			item = existing.get();
			if (existing.count() == 1) {
				imageStreamDeletion.accept(item);
				internalCache.remove(key);
			} else {
				existing.unsubscribe();
			}			
		}
		log.debug(String.format("Removal completed. Chache is now storing %d items", 
			internalCache.size())
		);
		return item;
	}
}
