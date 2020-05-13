package cz.xtf.core.openshift.client.dsl;

import cz.xtf.core.openshift.imagestreams.MapBasedImageStreamRegistry;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.OperationContext;
import io.fabric8.openshift.api.model.DoneableImageStream;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamList;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.dsl.internal.OpenShiftOperation;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a concrete implementation of {@link OpenShiftOperation} to handle 
 * operations on {@link ImageStream} instances.
 * 
 * This class replaces {@link ImageStreamOperationsImpl} to add the logic 
 * needed to leverage the {@link MapBasedImageStreamRegistry} in order to 
 * keep track of image streams creation.
 */
@Slf4j
public class TracedImageStreamOperations 
	extends OpenShiftOperation<ImageStream, ImageStreamList, DoneableImageStream, Resource<ImageStream, DoneableImageStream>> {

	public TracedImageStreamOperations(OkHttpClient client, OpenShiftConfig config) {
		this(new OperationContext().withOkhttpClient(client).withConfig(config));
	}

	public TracedImageStreamOperations(OperationContext context) {
		super(context.withApiGroupName("image.openshift.io")
				.withApiGroupVersion("v1")
				.withPlural("imagestreams"));
		this.type = ImageStream.class;
		this.listType = ImageStreamList.class;
		this.doneableType = DoneableImageStream.class;
	}

	@Override
	public TracedImageStreamOperations newInstance(OperationContext context) {
		TracedImageStreamOperations result = new TracedImageStreamOperations(context);
		return result;
	}

	@Override
	protected ImageStream handleCreate(ImageStream resource) throws ExecutionException, InterruptedException, KubernetesClientException, IOException {
		log.debug(String.format("Handling creation of image stream resource %s in namespace %s.",
			resource.getMetadata().getName(),
			this.getNamespace())
		);
		return MapBasedImageStreamRegistry.get().register(resource, i -> {
			try {
				return super.handleCreate(resource);
			} catch (ExecutionException | InterruptedException | IOException e) {
				throw KubernetesClientException.launderThrowable(forOperationType("create"), e);
			}
		});
	}

	@Override
	protected void handleDelete(URL requestUrl, long gracePeriodSeconds, String propagationPolicy, boolean cascading) throws ExecutionException, InterruptedException, KubernetesClientException, IOException {
		log.debug(String.format("Handling deletion of image stream resource at %s.",
			requestUrl)
		);
		MapBasedImageStreamRegistry.get().unregister(this.getName(), i -> {
			try {
				super.handleDelete(requestUrl, gracePeriodSeconds, propagationPolicy, cascading);
			} catch (ExecutionException | InterruptedException | IOException e) {
				throw KubernetesClientException.launderThrowable(forOperationType("delete"), e);
			}
		});
		
	}
}
