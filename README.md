# XTF
XTF is a framework designed to ease up aspects of testing in the OpenShift environment.

XTF is an open source project and managed in best-effort mode by anyone who is interested in or is using this project. 
There is no dedicated maintainer and there is not set time in which any given XTF issues will be fixed.

## XTF Maven repository
The XTF repository moved to the _JBoss public repository_ just recently (early 2021) and was previously hosted on
[Bintray](https://bintray.com/).
Please take care of this and update your projects accordingly in order to depend on and use the latest XTF versions,
i.e. adjust your XTF repository `pom.xml` configuration by adding (if not there already) the following snippet:

```xml
...
<repository>
  <id>jboss-releases-repository</id>
  <name>JBoss Releases Repository</name>
  <url>https://repository.jboss.org/nexus/content/groups/public/</url>
  <snapshots>
     <enabled>false</enabled>
  </snapshots>
  <releases>
     <enabled>true</enabled>
  </releases>
</repository>

<repository>
  <id>jboss-snapshots-repository</id>
  <name>JBoss Snapshots Repository</name>
  <url>https://repository.jboss.org/nexus/content/repositories/snapshots</url>
  <snapshots>
     <enabled>true</enabled>
  </snapshots>
  <releases>
     <enabled>false</enabled>
  </releases>
</repository>
...
```

## Modules
### Core
Core concepts of XTF framework used by other modules.

#### Configuration
While the framework itself doesn't require any configuration, it can ease up some repetitive settings in tests. Setup 
of XTF can be done in 4 ways with priority from top to down:

* System properties 
* Environment variables
* `test.properties` file in root of the project designed to contain user specific setup. You can use 
  `-Dxtf.test_properties.path` property to specify the location for the desired user specific setup.
* `global-test.properties` file in root of the project designed to contain a shared setup. You can use 
  `-Dxtf.global_test_properties.path` property to specify the location for the desired user specific setup.

The mapping between system properties and environment variables is done by lower casing environment variable, 
replacing `_` with `.` and adding `xtf.` before the result.

Example: `OPENSHIFT_MASTER_URL` is mapped to `xtf.openshift.master.url`.

#### OpenShift
[OpenShift](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/openshift/OpenShift.java) class is 
the entry point for communicating with OpenShift. It extends `OpenShiftNamespaceClient` from Fabric8 client as it is 
meant to be used within one namespace, where tests are executed.

The `OpenShift` class extends the upstream version with several shortcuts, e.g. using `DeploymentConfig` name only for 
retrieving any `Pod` or its log. This is useful in test cases where we know that we have only one pod created by 
`DeploymentConfig`s or that we don't care which one will we get. The class itself also provides access to OpenShift 
specific `Waiters`.

##### Configuration:
Take a look at the
[OpenShiftConfig](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/config/OpenShiftConfig.java) 
class to see possible configurations. Enabling some of them will allow you to instantiate as 
`OpenShift openShift = OpenShifts.master()`.

##### Pull Secrets
There's a convenient method `OpenShift::setupPullSecret()` to set up pull secrets as recommended by OpenShift 
[documentation](https://docs.openshift.com/container-platform/4.2/openshift_images/managing-images/using-image-pull-secrets.html).
The property `xtf.openshift.pullsecret` is checked in the `ProjectCreator` listener and in `BuildManager` to populate 
projects with pull secret if provided. The pull secret is expected to be provided in Json format.

Single registry
```json
{"auths":{"registry.redhat.io":{"auth":"<TOKEN>"}}}
```

Multiple registries
```json
{"auths":{"registry.redhat.io":{"auth":"<TOKEN>"},"quay.io":{"auth":"<TOKEN>"}}}
```


#### Waiters
[Waiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/Waiter.java) is a concept for 
conditional waiting. It retrieves an object or state in the specified `interval` and checks for the specified success 
and failure conditions. When one of them is met, the waiter will quit. If neither is met within the `timeout`, then 
an exception is thrown.

XTF provides two different implementations, 
([SimpleWaiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) and 
[SupplierWaiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SupplierWaiter.java)) 
and several preconfigured instances. All the default parameters of preconfigured `Waiter`s are overrideable.

`OpenShifts.master().waiters().isDcReady("my-deployment").waitFor();`

`Https.doesUrlReturnsOK("http://example.com").timeOut(TimeUnit.MINUTES, 10).waitFor();`

#### BuildManager
[BuildManager](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/bm/BuildManager.java) caches 
test builds in one namespace so that they can be reused. After the first time a specified `ManagedBuild` succeeds,  
only the reference is returned, but the build will be already present.

```
BuildManager bm = new BuildManagers.get();
ManagedBuild mb = new BinaryBuild("my-builder-image", Paths.resolve("/resources/apps/my-test-app"));
ManagedBuildReference = bm.deploy(mb);

bm.hasBuildCompleted().waitFor();
```

#### Image
Wrapper class for URL specified images. Its purpose is to parse them or turn them into ImageStream objects.

##### Specifying Maven
In some images Maven needs to be activated, for example on RHEL7 via script `/opt/rh/rh-maven35/enable`. 
This can be controlled by properties.

 * `xtf.maven.activation_script` - path to Maven activation script. Defaults to `/opt/rh/rh-maven35/enable` if not set.

Not setting these options might result in faulty results from `ImageContent#mavenVersion()`.

##### Specifying images
Every image that is set in `global-test.properties` using xtf.{foo}.image can be accessed by using `Images.get(foo)`.

#### Products
Allows to hold some basic and custom properties related to tested product image in properties file. 
Example considering maintenance of one image version:
```
xtf.foo.image=image.url/user/repo:tag
xtf.foo.version=1.0.3
```

XTF also considers the possibility of maintenance of several versions. In this case add a "subId" to your properties and 
specify `xtf.foo.subid` to activate particular properties (in your 'pom.xml' profile for example). Most of the 
properties can be shared for a given product. While `image` properties will override version properties.

Example considering maintenance of two image versions:
```
xtf.foo.image                               // Will override versions image property
xtf.foo.templates.repo=git.repo.url         // Will be used as default if not specified in version property
xtf.foo.v1.image=image.url/user/repoV1:tag1
xtf.foo.v1.version=1.0.3
xtf.foo.v2.image=image.url/user/repoV2:tag2
xtf.foo.v2.version=1.0.3
```

Retrieving an instance with this metadata: `Produts.resolve("product");`

### JUnit5
JUnit5 module provides a number of extensions and listeners designed to easy up OpenShift images test management. 
See [JUnit5](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) for 
more information.

## Releasing XTF
Have a look to the [release documentation](RELEASE.md) to learn about the process that defines how to release XTF to 
the community.
