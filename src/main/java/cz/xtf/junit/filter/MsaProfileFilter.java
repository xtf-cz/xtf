package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.JavaS2IProfile;
import cz.xtf.junit.annotation.SpringBootProfile;
import cz.xtf.junit.annotation.VertxProfile;
import cz.xtf.junit.annotation.WildFlySwarmProfile;

import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * Class for MSA profile filters.
 */
public class MsaProfileFilter implements ExclusionTestClassFilter {
	public static final String MSA_PROVIDER_PROPERTY = "cz.xtf.msa.provider";
	protected final String msaProvider;

	/**
	 * Sets the {@code msaProvider} value from the {@link #MSA_PROVIDER_PROPERTY} system property.
	 * <p>
	 * If the system property is not provided, {@code 'none'} is used.
	 * </p>
	 */
	public MsaProfileFilter() {
		this.msaProvider = System.getProperty(MSA_PROVIDER_PROPERTY, "none");
	}

	@Override
	public boolean exclude(final Class<?> testClass) {
		for (final Annotation annotation : Arrays.asList(testClass.getAnnotations())) {
			if (annotation instanceof VertxProfile && "vertx".equals(this.msaProvider)) {
				return false;
			} else if (annotation instanceof SpringBootProfile && "springboot".equals(this.msaProvider)) {
				return false;
			} else if (annotation instanceof WildFlySwarmProfile && "wildfly-swarm".equals(this.msaProvider)) {
				return false;
			} else if (annotation instanceof JavaS2IProfile && "java-s2i".equals(this.msaProvider)) {
				return false;
			}
		}
		return true;
	}
}
