package cz.xtf.junit.filter;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

import cz.xtf.junit.annotation.release.SinceVersion;
import cz.xtf.junit.annotation.release.SkipFor;
import cz.xtf.openshift.imagestream.ImageRegistry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SinceVersionFilter implements ExclusionTestClassFilter {

	public static SinceVersionFilter instance() {
		return FilterHolder.FILTER;
	}

	/**
	 * Method to get the current image as an image property used in @SinceVersion(image='eap'... or @SkipFor(image='$getImage')
	 * @param image either "foo", for an ImageRegistry method name, or "$foo", for the current test class static method name "foo"
	 * @return
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 */
	private String getRegistryOrClassImage(String image) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		if (image.startsWith("$")) {
			// special case, we get the image by a method call
			return (String)this.getClass().getMethod(image.replace("$", "")).invoke(null);
		}
		else {
			return (String) ImageRegistry.class.getDeclaredMethod(image).invoke(ImageRegistry.get());
		}
	}

	@Override
	public boolean exclude(Class<?> testClass) {
		final SinceVersion[] sinceVersions = testClass.getAnnotationsByType(SinceVersion.class);
		for (final SinceVersion sinceVersion : sinceVersions) {
			try {
				final String image = getRegistryOrClassImage(sinceVersion.image());

				if (image.contains(sinceVersion.name())) {
					final String sinceVersionVersion = sinceVersion.since().split("-")[0];
					if (!ImageRegistry.isVersionAtLeast(new BigDecimal(sinceVersionVersion), image)) {
						if (!sinceVersion.jira().isEmpty()) {
							log.info("Excluding test {} for {}, image {} ({}) stream containing {} must be of version at least {}",
									testClass.toString(),
									sinceVersion.jira(),
									image,
									sinceVersion.image(),
									sinceVersion.name(),
									sinceVersionVersion);
						}
						else {
							log.debug("Excluding test {}, image {} ({}) stream containing {} must be of version at least {}",
									testClass.toString(),
									image,
									sinceVersion.image(),
									sinceVersion.name(),
									sinceVersionVersion);
						}
						return true;
					}
				}
			} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				log.error("Error invoking during @SinceVersion processing", e);
			}
		}

		final SkipFor[] skipFors = testClass.getAnnotationsByType(SkipFor.class);
		for (final SkipFor skipFor : skipFors) {
			try {
				final String image = getRegistryOrClassImage(skipFor.image());

				if (image.contains(skipFor.name())) {

					if (!skipFor.reason().isEmpty()) {
						log.info("Excluding test {}: {} - image {} ({}) stream containing {}",
								testClass.toString(),
								skipFor.reason(),
								image,
								skipFor.image(),
								skipFor.name());

					}
					else {
						log.debug("Excluding test {} - image {} ({}) stream containing {}",
								testClass.toString(),
								image,
								skipFor.image(),
								skipFor.name());
					}

					return true;
				}
			} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				log.error("Error invoking during @SkipFor processing", e);
			}
		}

		return false;
	}

	private static class FilterHolder {
		public static final SinceVersionFilter FILTER = new SinceVersionFilter();
	}
}
