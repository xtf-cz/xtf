# XTF release process

The XTF repository is hosted on the [JBoss public repository](https://repository.jboss.org/). 

## Dismissed Bintray repository
The XTF repository moved to the _JBoss public repository_ just recently (early 2021) and was previously hosted on 
[Bintray](https://bintray.com/).
Please take care of this fact and update your projects accordingly in order to depend on and use the latest XTF versions.

XTF itself has already been updated to reflect the above changes, so just go through the following procedure when 
releasing a XTF version, either official or a `SNAPSHOT` for custom testing.

## Prerequisites to create a new XTF release
* You'll need to have _write_ access to XTF upstream repository (ask one of the admins to provide access: 
  https://github.com/orgs/xtf-cz/teams/admins)

## Production release

### Prerequisites
1. Clone the upstream XTF repo, not a personal fork
2. Ensure the following secrets/credentials are configured in GitHub for the upstream XTF repo:
   - GPG key for signing artifacts (GPG_PRIVATE_KEY and GPG_PASSPHRASE secrets in GitHub)
   - JBoss repository access (JBOSS_REPO_USER and JBOSS_REPO_PASSWORD secrets in GitHub)

### Creating a new tag and release

#### Step 1: Prepare the release using Maven Release Plugin

Run the Maven release preparation command:
```bash
mvn release:clean release:prepare
```

This interactive process will:
- Create a release tag
- Update the SNAPSHOT version in the `main` branch to the new version
- Push changes to GitHub

Example of the interactive prompts:
```
What is the release version for "XTF"? (cz.xtf:xtf-parent) 1.2-SNAPSHOT: : 1.2
What is SCM release tag or label for "XTF"? (cz.xtf:xtf-parent) xtf-parent-1.2: : 1.2
What is the new development version for "XTF"? (cz.xtf:xtf-parent) 1.3-SNAPSHOT: : 1.3-SNAPSHOT
```

#### Step 2: Automatic deployment via GitHub Actions
Once the tag is pushed to GitHub, the workflow `.github/workflows/xtf-maven-release.yml` automatically:
1. Builds the project with `mvn install`
2. Runs verification with `mvn clean verify`
3. Sets up Maven credentials for JBoss repositories
4. Imports GPG key for artifact signing
5. Deploys to Maven repository with `mvn deploy -Prelease`

The `release` profile (activated with `-Prelease`) includes:
- GPG signing of artifacts (`maven-gpg-plugin`)
- Generation of Javadoc JARs (`maven-javadoc-plugin`)
- Generation of source JARs (`maven-source-plugin`)

#### Step 3: Repository management
Tagged releases are deployed to: https://repository.jboss.org/nexus/content/groups/developer/cz/xtf/ and 
automatically synced to Maven Central repository.

#### Step 4: Verify the release
Check that the new version appears in the https://repository.jboss.org/nexus/content/groups/developer/cz/xtf/ repository 
and that the GitHub tag was created successfully.

## Snapshot release

### Automatic deploy to XTF Snapshots repository when pushing branch
The XTF project is using GitHub _actions_ to deploy XTF snapshots to the JBoss Snapshots repository: 

https://repository.jboss.org/nexus/repository/snapshots/

This works automatically when a branch is pushed to the "upstream" repo.

If you want this to work when pushing to your personal fork then you need to configure a number of GitHub _secrets_, 
i.e.:
```text
JBOSS_REPO_USER=<jboss.org username>
JBOSS_REPO_PASSWORD=<jboss.org password>
GPG_PASSPHRASE=<gpg password>
GPG_PRIVATE_KEY=<gpg key>
```

To set up these secrets, go to your XTF fork, click "Settings" in the top panel -> click "Secrets" in the 
left menu -> click "New repository secret" in the top right corner and add all the above secrets (JBOSS_REPO_USER, 
JBOSS_REPO_PASSWORD, GPG_PASSPHRASE, GPG_PRIVATE_KEY) as shown in: https://github.com/xtf-cz/xtf/settings/secrets/actions

### Manual deploy to the JBoss Snapshots repository
If you want to deploy your XTF artifacts to the jboss-snapshots-repository, you will need to provide the required 
authentication credentials. Update your Maven `settings.xml` file with your Red Hat account credentials, for example:

```xml
<settings>  
    ...  
        <servers>  
            <server>  
                <id>jboss-snapshots-repository</id>  
                <username>jboss.org username</username>  
                <password>jboss.org password</password>
            </server>  
        ...
        </servers>  
    ...  
</settings>
```

and then run:
```shell
mvn clean deploy
```

**Note**
When using the mvn command above to deploy a new snapshot version, you don't need GPG_PASSPHRASE and GPG_PRIVATE_KEY as 
the `-Prelease` profile is not used and thus there is no GPG signing of the artifacts.

**Important** 
Consider changing the XTF version when deploying your custom snapshot, since anyone could redeploy it by providing 
a different artifact with the same GAV coordinates, which is not desirable.

You could use the following command to change the default _SNAPSHOT_ version (e.g.: `1.1-SNAPSHOT`) to your custom 
version:

```shell
mvn versions:set -DnewVersion=1.1-super-feature-SNAPSHOT
```

This will replace the version in each of the project _pom.xml_ files, e.g., `1.1-super-feature-SNAPSHOT`
