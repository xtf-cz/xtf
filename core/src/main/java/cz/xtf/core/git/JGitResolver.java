package cz.xtf.core.git;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Try to resolve repository remote URL and branch from .git directory
 * <p>
 * This method tries to match HEAD commit to remote references.
 * If there is a match, the remote URL and branch are set.
 * For attached HEAD this method could be simplified with jgit methods, but
 * "universal" approach was chosen, as for example Jenkins git plugin creates a detached state.
 * In case of multiple matches, upstream or origin (in this order) is preferred.
 * </p>
 */
@Slf4j
class JGitResolver implements GitResolver {
    private static final String URL_TEMPLATE = "https://%s/%s/%s";
    private static String reference;
    private static String url;

    /**
     * Try to set repository ref and URL from HEAD commit
     */
    public JGitResolver() {
        try {
            resolveRepoFromHEAD();
        } catch (IOException | URISyntaxException e) {
            log.error("Failed to resolve repository from HEAD", e);
            throw new RuntimeException("Failed to resolve repository from HEAD with error: " + e.getMessage());
        }
    }

    /**
     * Try to set repository ref and URL from HEAD commit
     */
    private static void resolveRepoFromHEAD() throws IOException, URISyntaxException {
        //look for a git repository recursively till system root folder
        Repository repository = new FileRepositoryBuilder().findGitDir().build();

        if (repository == null) {
            log.error("Failed to find a git repository");
            return;
        }

        //get current commit hash
        ObjectId commitId = repository.resolve("HEAD");

        //get all remote references
        List<Ref> refs = repository.getRefDatabase().getRefs().stream()
                .filter(reference -> reference.getName().startsWith("refs/remotes/")).collect(Collectors.toList());

        List<String> matches = new ArrayList<>();
        // Walk through all the refs to see if any point to this commit
        for (Ref ref : refs) {
            if (ref.getObjectId().equals(commitId)) {
                matches.add(ref.getName());
            }
        }

        if (matches.isEmpty()) {
            log.error("No remote references found for the current commit");
            return;
        }

        //In case there are multiple matches, we prefer upstream or origin (in this order)
        List<String> preferredMatches = matches.stream()
                .filter(reference -> reference.contains("upstream") || reference.contains("origin"))
                .sorted(Comparator.reverseOrder()) // 1) upstream 2) origin
                .collect(Collectors.toList());

        if (matches.size() > 1 && !preferredMatches.isEmpty()) {
            matches = preferredMatches;
        }

        //branch is string behind the last /
        reference = matches.stream().findFirst().map(ref -> ref.substring(ref.lastIndexOf('/') + 1)).orElse(null);

        log.info("xtf.git.repository.ref got automatically resolved as {}", reference);

        String remote = repository.getRemoteName(matches.get(0));
        url = getRemoteUrl(repository, remote);

        if (url != null) {
            log.info("xtf.git.repository.url got automatically resolved as {}", url);
        }

    }

    /**
     * given a remote reference, get it's remote URL
     *
     * @param repository git repository
     * @param remoteReference reference in format "refs/remotes/remote/branch"
     * @return URL in HTTPS format
     */
    private static String getRemoteUrl(Repository repository, String remoteReference) throws URISyntaxException {
        RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), remoteReference);
        if (remoteConfig.getURIs() == null || remoteConfig.getURIs().isEmpty()) {
            log.info("Missing URI in git remote ref '{}'", remoteReference);
            return null;
        }
        // we expect a single URI
        String[] pathTokens = remoteConfig.getURIs().get(0).getPath().split("/");
        if (pathTokens.length != 2) {
            log.info("Unexpected path '{}' in URI '{}' of git remote ref '{}'", remoteConfig.getURIs().get(0).getPath(),
                    remoteConfig.getURIs().get(0), remoteReference);
            return null;
        }
        // the URI must be in HTTPS format
        return getRepositoryUrl(remoteConfig.getURIs().get(0).getHost(), pathTokens[0], pathTokens[1]);
    }

    /**
     * We require HTTPS format, for unauthorized access to the repository, let's convert it
     */
    private static String getRepositoryUrl(String host, String remote, String repository) {
        return String.format(URL_TEMPLATE, host, remote, repository);
    }

    public String resolveRepoUrl() {
        return url;
    }

    public String resolveRepoRef() {
        return reference;
    }
}
