# XTF
XTF is framework designed to easy up aspects of testing in OpenShift environment.

## Modules
### Core
Core concepts of XTF framework used by other modules.

#### Configuration
While framework itself doesn't require any configuration it can easy up some repetative settings in tests. Setup of XTF can be done in 4 ways with priority from top to down:

* System properties 
* Environment variables
* test.properties file in root of the project designed to contain user specific setup
* global-test.properties file in root of the project designed to contain shared setup

The mapping between system properties and environment variables is done by lower casing environment variable, replacing `_` with `.` and adding `xtf.` before the result.

Example: `OPENSHIFT_MASTER_URL` is mapped to `xtf.openshift.master.url`.

#### OpenShift
[OpenShift](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/openshift/OpenShift.java) class is entry point for communicating with OpenShift. It extends `OpenShiftNamespaceClient` from Fabric8 client as is meant to be used within one namespace where tests are executed.

`OpenShift` class extends upstream version with several shortcuts. Eg. using deploymentconfig name only for retrieving any `Pod` or its log. This is usefull in test cases where we know that we have only one pod created by dc or we don't care which one will we get. The class itself also provides access to OpenShift specific `Waiters`.

##### Configuration:
Take a look at [OpenShiftConfig](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/config/OpenShiftConfig.java) class to see possible configurations. Enabling some them will allow you to instantiate as `OpenShift openShift = OpenShifts.master()`.

#### Waiters
[Waiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/Waiter.java) is a concept for conditional waiting. It retrieves an object or state in specified `interval` and checks for the specified success and failure conditions. When one of them is met, the waiter will quit. If neither is met in `timeout`, then exception is thrown. 

XTF provides two different implementations ([SimpleWaiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) and [SupplierWaiter](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SupplierWaiter.java)) and several preconfigured instances. All the default parameters of preconfigured Waiters are overrideable.

`OpenShifts.master().waiters().isDcReady("my-deployment").waitFor();`

`Https.doesUrlReturnsOK("http://example.com").timeOut(TimeUnit.MINUTES, 10).waitFor();`

#### BuildManager
[BuildManager](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/bm/BuildManager.java) cachces test builds in one namespace so they can be reused. After first time specified ManagedBuild succeds only the reference is returned but build is already present.

```
BuildManager bm = new BuildManagers.get();
ManagedBuild mb = new BinaryBuild("my-builder-image", Paths.resolve("/resources/apps/my-test-app"));
ManagedBuildReference = bm.deploy(mb);

bm.hasBuildCompleted().waitFor();
```

#### Image
Wrapper class for url specified images. It's puprose is to parse them or turn them into ImageStream objects.

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
JUnit5 module provides number of extensions and listeners designed to easy up OpenShift images test management. See [JUnit5](https://github.com/xtf-cz/xtf/blob/master/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) for more informations. 

### Utilities
Utilities contain former XTF version and is meant to be deprecated. Classes within this module are now being refactored and moved in other modules. Yet won't be touched and deleted from utilities module itself.
