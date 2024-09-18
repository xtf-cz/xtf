package cz.xtf.core.git;

import cz.xtf.core.config.XTFConfig;

class SystemPropertyGitResolver implements GitResolver {
    static final String GIT_URL = "xtf.git.repository.url";
    static final String GIT_BRANCH = "xtf.git.repository.ref";

    public String resolveRepoUrl() {
        return XTFConfig.get(GIT_URL);
    }

    public String resolveRepoRef() {
        return XTFConfig.get(GIT_BRANCH);
    }
}
