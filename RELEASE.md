# XTF release process

XTF repository is hosted on [Bintray](https://bintray.com/)

## Prerequisites to create new XTF release
* Write access to XTF upstream repository (ask one of the admins to provide access: https://github.com/orgs/xtf-cz/teams/admins)

## Production release
1. Clone upstream XTF repo, not a personal fork
2. Maven Release plugin is pre-configured, release repository is defined in distribution management section, 
GitHub repository in SCM section to push tags:

```mvn release:clean release:prepare```

in a nutshell it will create release tag, version and move to next devel version. This is an interactive process and requires 
a few details to be provided, see the example:
```
What is the release version for "XTF"? (cz.xtf:utilities) 0.5: 0.5
What is SCM release tag or label for "XTF"? (cz.xtf:utilities) utilities-0.5: : 0.5
What is the new development version for "XTF"? (cz.xtf:utilities) 0.6-SNAPSHOT: 0.6-SNAPSHOT
```

the finally tagged version is pushed to GitHub repository. GH Actions are configured to perform `mvn deploy` for new tags
and push to maven repository after few minutes new version should appear in the Maven repository.

## Snapshot release

Every push of branch to upstream repo of XTF repository triggers also a snapshot release that is hosted in the OSS JFrog repository:

https://oss.jfrog.org/artifactory/oss-snapshot-local/cz/xtf/utilities/ 

so you can have any custom XTF artifacts deployed in maven repository for further testing.

