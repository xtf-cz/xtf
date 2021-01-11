# XTF
XTF is a framework designed to ease up aspects of testing in OpenShift environment.


## Modules
### Core
Core concepts of XTF framework used by other modules.

#### Configuration
While the framework itself doesn't require any configuration, it can ease up some repetitive settings in tests. Setup of XTF can be done in 4 ways with priority from top to down:

* System properties 
* Environment variables
* `test.properties` file in root of the project designed to contain user specific setup. You can use `-Dxtf.test_properties.path` property to specify the location for the desired user specific setup.
* `global-test.properties` file in root of the project designed to contain a shared setup. You can use `-Dxtf.global_test_properties.path` property to specify the location for the desired user specific setup.

The mapping between system properties and environment variables is done by lower casing environment variable, replacing `_` with `.` and adding `xtf.` before the result.

Example: `OPENSHIFT_MASTER_URL` is mapped to `xtf.openshift.master.url`.

#### OpenShift
[OpenShift](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/openshift/OpenShift.java) class is entry point for communicating with OpenShift. It extends `OpenShiftNamespaceClient` from Fabric8 client as is meant to be used within one namespace where tests are executed.

`OpenShift` class extends upstream version with several shortcuts. Eg. using deploymentconfig name only for retrieving any `Pod` or its log. This is useful in test cases where we know that we have only one pod created by dc or we don't care which one will we get. The class itself also provides access to OpenShift specific `Waiters`.

##### Configuration:
Take a look at [OpenShiftConfig](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/config/OpenShiftConfig.java) class to see possible configurations. Enabling some them will allow you to instantiate as `OpenShift openShift = OpenShifts.master()`.

##### Pull Secrets
There's a convenient method `OpenShift::setupPullSecret()` to setup pull secrets as recommended by OpenShift [documentation](https://docs.openshift.com/container-platform/4.2/openshift_images/managing-images/using-image-pull-secrets.html).
Property `xtf.openshift.pullsecret` is checked in `ProjectCreator` listener and `BuildManager` to populate projects with pull secret if provided. The pull secret is expected to be provided in Json format.

Single registry
```json
{"auths":{"registry.redhat.io":{"auth":"<TOKEN>"}}}
```

Multiple registries
```json
{"auths":{"registry.redhat.io":{"auth":"<TOKEN>"},"quay.io":{"auth":"<TOKEN>"}}}
```


#### Waiters
[Waiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/Waiter.java) is a concept for conditional waiting. It retrieves an object or state in specified `interval` and checks for the specified success and failure conditions. When one of them is met, the waiter will quit. If neither is met in `timeout`, then exception is thrown.

XTF provides two different implementations ([SimpleWaiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) and [SupplierWaiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SupplierWaiter.java)) and several preconfigured instances. All the default parameters of preconfigured Waiters are overrideable.

`OpenShifts.master().waiters().isDcReady("my-deployment").waitFor();`

`Https.doesUrlReturnsOK("http://example.com").timeOut(TimeUnit.MINUTES, 10).waitFor();`

#### BuildManager
[BuildManager](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/bm/BuildManager.java) caches test builds in one namespace so they can be reused. After first time specified ManagedBuild succeeds only the reference is returned but build is already present.

```
BuildManager bm = new BuildManagers.get();
ManagedBuild mb = new BinaryBuild("my-builder-image", Paths.resolve("/resources/apps/my-test-app"));
ManagedBuildReference = bm.deploy(mb);

bm.hasBuildCompleted().waitFor();
```

#### Image
Wrapper class for url specified images. Its purpose is to parse them or turn them into ImageStream objects.

##### Specifying images
Every image that is set in `global-test.properties` using xtf.{foo}.image can be accessed by using `Images.get(foo)`.

#### Products
Allows to hold some basic and custom properties related to tested product image in properties file. 
Example considering maintenance of one image version:
```
xtf.foo.image=image.url/user/repo:tag
xtf.foo.version=1.0.3
```

XTF also considers possibility of maintenance of several versions. In this case add subId to your properties and specify `xtf.foo.subid` to activate particular properties (in your 'pom.xml' profile for example). Most of the properties can be shared for product. While `image` properties will override version properties.

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
JUnit5 module provides number of extensions and listeners designed to easy up OpenShift images test management. See [JUnit5](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) for more information. 
