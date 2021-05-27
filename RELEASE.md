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
1. Clone the upstream XTF repo, not a personal fork
2. The Maven Release plugin is pre-configured, a release repository is defined in the distribution management section, 
and GitHub repository is defined in the SCM section to push tags:

```mvn release:clean release:prepare```

in a nutshell it will create the release tag, version and move to next devel version. This is an interactive process 
and requires a few details to be provided, see the following example:
```
What is the release version for "XTF"? (cz.xtf:utilities) 0.5: 0.5
What is SCM release tag or label for "XTF"? (cz.xtf:utilities) utilities-0.5: : 0.5
What is the new development version for "XTF"? (cz.xtf:utilities) 0.6-SNAPSHOT: 0.6-SNAPSHOT
```

the finally tagged version is then pushed to GitHub repository. 
GitHub Actions are configured to perform `mvn deploy` for new tags and push to maven repository. 
After a few minutes, the new version should appear in the online Maven repository.

## Snapshot release

### Automatic deploy to XTF Snapshots repository when pushing branch
The XTF project is using GitHub _actions_ to deploy XTF snapshots to the JBoss Snapshots repository: 

https://repository.jboss.org/nexus/content/repositories/snapshots

This works automatically when a branch is pushed to the "upstream" repo.

If you want this to work when pushing to your personal fork then you need to configure a number of GitHub _secrets_, 
i.e.:
```text
JBOSS_REPO_PASSWORD=<jboss.org username>
JBOSS_REPO_USER=<jboss.org password>
```

In order to set up those secrets, go to your XTF fork, click "Settings" in the top panel -> click "Secrets" in the 
left menu -> click "New repository secret" in the top right corner and add both the above secrets (JBOSS_REPO_USER, 
JBOSS_REPO_PASSWORD) so it will look like what in: https://github.com/xtf-cz/xtf/settings/secrets/actions

Note that you don't need to set the GPG_PASSPHRASE and GPG_PRIVATE_KEY secrets if you do not plan to release a new 
version of XTF.

### Manual deploy to the JBoss Snapshots repository
In case you want to deploy your XTF bits to jboss-snapshots-repository you will need to provide the required 
authentication credentials, hence you might want to update your Maven `settings.xml` file with your Red Hat account
credentials, e.g.:

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

**Important** 
Consider changing the XTF version when deploying your custom snapshot, since anyone could redeploy it by providing 
their different artifact with the same GAV and this is not desirable.

You could use the following command to change the default _SNAPSHOT_ version (e.g.: `0.22-SNAPSHOT`) to your custom 
version:

```shell
grep -lr 0.22-SNAPSHOT * | xargs sed -i s,0.22-SNAPSHOT,0.22-pretest-SNAPSHOT,g
```

This will replace the version in each of the project _pom.xml_ files, i.e. `0.22-pretest-SNAPSHOT`
