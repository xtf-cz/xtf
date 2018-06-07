package cz.xtf.build;

public class GitBuildProcess extends BuildProcess {
	private final String gitRepo;
	private final String branch;
	private final String contextDir;

	public GitBuildProcess(GitBuildDefinition definition) {
		super(definition);

		this.gitRepo = definition.getGitRepo();
		this.branch = definition.getBranch();
		this.contextDir = definition.getContextDir();
	}

	@Override
	public void deployBuild() {
		deployBuildFromGit(gitRepo, branch, contextDir);
	}

	@Override
	public void deleteBuild() {
		deleteOpenshiftResources();
	}
	
	@Override
	public BuildStatus getBuildStatus() {
		return getCommonStatus(getBuildConfig());
	}

	@Override
	public void updateBuild() {
		throw new UnsupportedOperationException("Yet not implemented");
	}
}
