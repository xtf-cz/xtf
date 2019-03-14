package cz.xtf.openshift.builder;

import io.fabric8.openshift.api.model.OpenshiftRoleBinding;
import io.fabric8.openshift.api.model.OpenshiftRoleBindingBuilder;
import io.fabric8.openshift.api.model.OpenshiftRoleBindingFluent;

/**
 * Definition of RoleBinding. Example:
 *
 *<pre>
 * apiVersion: v1
 * kind: RoleBinding
 * metadata:
 *   name: pods-listing-binding
 *   annotations:
 *     description: "Default service account"
 * subjects:
 * - kind: ServiceAccount
 *   name: default
 *   namespace: myproject
 * roleRef:
 *   kind: Role
 *   name: pods-listing
 *   namespace: myproject
 *</pre>
 */
public class RoleBindingBuilder extends AbstractBuilder<OpenshiftRoleBinding, RoleBindingBuilder> {
    private String subjectKind;
    private String subjectName;
    private String subjectNamespace;
    private String roleRefKind = "Role";
    private String roleRefName;
    private String roleRefNamespace;

    public RoleBindingBuilder(String roleName) {
        this(null, roleName);
    }

    RoleBindingBuilder(ApplicationBuilder applicationBuilder, String roleName) {
        super(applicationBuilder, roleName);
    }

    /**
     * What <code>kind</code> gains the role defined at roleRef.
     * For example: <code>ServiceAccount</code> 
     */
    public RoleBindingBuilder subjectKind(String subjectKind) {
    	this.subjectKind = subjectKind;
    	return this;
    }
    /**
     * What is name of the <code>kind</code> that gains the <code>role</code> defined at roleRef.
     * For example account <code>default</code>.
     */
    public RoleBindingBuilder subjectName(String subjectName) {
    	this.subjectName = subjectName;
    	return this;
    }
    /**
     * What is namespace of the defined <code>kind</code>.
     * For example namespace <code>myproject</code>.
     */
    public RoleBindingBuilder subjectNamespace(String subjectNamespace) {
    	this.subjectNamespace = subjectNamespace;
    	return this;
    }
    /**
     * What is <code>kind</code> we want to reference to the subject.
     * For example kind <code>Role</code>.
     */
    public RoleBindingBuilder roleRefKind(String roleRefKind) {
    	this.roleRefKind = roleRefKind;
    	return this;
    }
    /**
     * What is name of the role reference.
     */
    public RoleBindingBuilder roleRefName(String roleRefName) {
    	this.roleRefName = roleRefName;
    	return this;
    }
    /**
     * What is namespace of the role reference.
     */
    public RoleBindingBuilder roleRefNamespace(String roleRefNamespace) {
    	this.roleRefNamespace = roleRefNamespace;
    	return this;
    }

	@Override
	public OpenshiftRoleBinding build() {
		OpenshiftRoleBindingFluent.SubjectsNested<OpenshiftRoleBindingBuilder> subject = new OpenshiftRoleBindingBuilder()
			.withNewMetadata()
				.withName(this.getName())
			.endMetadata()
			.addNewSubject()
				.withKind(subjectKind)
				.withName(subjectName);

		if(subjectNamespace != null && !subjectNamespace.isEmpty())
			subject.withNamespace(subjectNamespace);

		OpenshiftRoleBindingFluent.RoleRefNested<OpenshiftRoleBindingBuilder> roleRef = subject
			.endSubject()
			.withNewRoleRef()
				.withKind(roleRefKind)
				.withName(roleRefName);

		if(roleRefNamespace != null && !roleRefNamespace.isEmpty())
			roleRef.withNamespace(roleRefNamespace);

		return roleRef
			.endRoleRef()
			.build();
    }

    @Override
    protected RoleBindingBuilder getThis() {
        return this;
    }
}
