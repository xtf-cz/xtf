package cz.xtf.junit5.listeners;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import cz.xtf.core.bm.BuildManagers;
import cz.xtf.junit5.annotations.UsesBuild;
import cz.xtf.junit5.interfaces.BuildDefinition;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManagedBuildPrebuilder implements TestExecutionListener {

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		List<BuildDefinition> buildsToBeBuilt = new LinkedList<>();
		Set<BuildDefinition> buildsSeen = new HashSet<>();

		for (TestIdentifier root : testPlan.getRoots()) {
			process(buildsToBeBuilt, buildsSeen, root);

			for (TestIdentifier descendant : testPlan.getDescendants(root)) {
				process(buildsToBeBuilt, buildsSeen, descendant);
			}
		}

		for (BuildDefinition buildDefinition : buildsToBeBuilt) {
			log.debug("Building {}", buildDefinition);
			BuildManagers.get().deploy(buildDefinition.getManagedBuild());
		}
	}

	private void addBuildDefinition(List<BuildDefinition> buildsToBeBuilt, Set<BuildDefinition> buildsSeen, BuildDefinition buildDefinition) {
		if (!buildsSeen.contains(buildDefinition)) {
			buildsSeen.add(buildDefinition);
			buildsToBeBuilt.add(buildDefinition);
		}
	}

	private void process(List<BuildDefinition> buildsToBeBuilt, Set<BuildDefinition> buildsSeen, TestIdentifier identifier) {
		if (identifier.getSource().isPresent()) {
			TestSource testSource = identifier.getSource().get();
			if (testSource instanceof ClassSource) {
				ClassSource classSource = (ClassSource) testSource;
				Class klass = classSource.getJavaClass();

				log.debug("Processing {}", klass);

				Arrays.stream(klass.getAnnotations()).filter(a -> a.annotationType().getAnnotation(UsesBuild.class) != null).forEach(a -> {
					try {
						Object result = a.annotationType().getMethod("value").invoke(a);
						if (result instanceof BuildDefinition) {
							addBuildDefinition(buildsToBeBuilt, buildsSeen, (BuildDefinition) result);
						} else if (result instanceof BuildDefinition[]) {
							Stream.of((BuildDefinition[]) result).forEach(x -> addBuildDefinition(buildsToBeBuilt, buildsSeen, x));
						} else {
							log.error("Value present in {} is not instance of {}, not able to get ManagedBuild to be built", result, BuildDefinition.class);
						}
					} catch (Exception e) {
						log.error("Failed to invoke value() on annotation " + a.annotationType().getName(), e);
					}
				});
			}
		}
	}
}
