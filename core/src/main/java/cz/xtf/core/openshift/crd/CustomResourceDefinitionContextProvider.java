package cz.xtf.core.openshift.crd;

import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

public interface CustomResourceDefinitionContextProvider {

    CustomResourceDefinitionContext getContext();
}
