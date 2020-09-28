package cz.xtf.builder.builders;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingFluent;

/**
 * Definition of RoleBinding. Example:
 *
 * <pre>
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
 * </pre>
 */
public class RoleBindingBuilder extends AbstractBuilder<RoleBinding, RoleBindingBuilder> {
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
    @Deprecated
    public RoleBindingBuilder roleRefNamespace(String roleRefNamespace) {
        this.roleRefNamespace = roleRefNamespace;
        return this;
    }

    @Override
    public RoleBinding build() {
        RoleBindingFluent.SubjectsNested<io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder> subject = new io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder()
                .withNewMetadata()
                .withName(this.getName())
                .endMetadata()
                .addNewSubject()
                .withKind(subjectKind)
                .withName(subjectName);

        if (subjectNamespace != null && !subjectNamespace.isEmpty())
            subject.withNamespace(subjectNamespace);

        RoleBindingFluent.RoleRefNested<io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder> roleRef = subject
                .endSubject()
                .withNewRoleRef()
                .withKind(roleRefKind)
                .withName(roleRefName);

        return roleRef
                .endRoleRef()
                .build();
    }

    @Override
    protected RoleBindingBuilder getThis() {
        return this;
    }
}
