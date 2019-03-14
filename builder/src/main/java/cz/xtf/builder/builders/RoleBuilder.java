package cz.xtf.builder.builders;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import io.fabric8.openshift.api.model.OpenshiftRole;
import io.fabric8.openshift.api.model.OpenshiftRoleBuilder;

/**
 * Definition of Role. Example:
 *
 *<pre>
 * apiVersion: v1
 * kind: Role
 * metadata:
 *   name: pods-listing
 * rules:
 *   resources: ["pods", "pods/log"]
 *   verbs: ["list", "get"]
 *</pre>
 */
public class RoleBuilder extends AbstractBuilder<OpenshiftRole, RoleBuilder> {
	private Collection<String> resources;
	private Collection<String> verbs;

	public RoleBuilder(String roleName) {
		this(null, roleName);
	}

	RoleBuilder(ApplicationBuilder applicationBuilder, String roleName) {
		super(applicationBuilder, roleName);
	}

	public RoleBuilder resources(String... resources) {
		this.resources = new ArrayList<String>(Arrays.asList(resources));
		return this;
	}

	public RoleBuilder verbs(String... verbs) {
		this.verbs = new ArrayList<String>(Arrays.asList(verbs));
		return this;
	}

	@Override
	public OpenshiftRole build() {
		return new OpenshiftRoleBuilder()
				.withNewMetadata()
					.withName(this.getName())
				.endMetadata()
				.addNewRule()
					.addToVerbs(verbs.toArray(new String[0]))
					.addToResources(resources.toArray(new String[0]))
				.endRule()
				.build();
	}

	@Override
	protected RoleBuilder getThis() {
		return this;
	}
}
