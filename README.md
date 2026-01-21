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
  <url>https://repository.jboss.org/nexus/repository/snapshots/</url>
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
[OpenShift](https://github.com/xtf-cz/xtf/blob/main/core/src/main/java/cz/xtf/core/openshift/OpenShift.java) class is 
the entry point for communicating with OpenShift. It extends `OpenShiftNamespaceClient` from Fabric8 client as it is 
meant to be used within one namespace, where tests are executed.

The `OpenShift` class extends the upstream version with several shortcuts, e.g. using `DeploymentConfig` name only for 
retrieving any `Pod` or its log. This is useful in test cases where we know that we have only one pod created by 
`DeploymentConfig`s or that we don't care which one will we get. The class itself also provides access to OpenShift 
specific `Waiters`.

##### Configuration:
Take a look at the
[OpenShiftConfig](https://github.com/xtf-cz/xtf/blob/main/core/src/main/java/cz/xtf/core/config/OpenShiftConfig.java) 
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
[Waiter](https://github.com/xtf-cz/xtf/blob/main/core/src/main/java/cz/xtf/core/waiting/Waiter.java) is a concept for 
conditional waiting. It retrieves an object or state in the specified `interval` and checks for the specified success 
and failure conditions. When one of them is met, the waiter will quit. If neither is met within the `timeout`, then 
an exception is thrown.

XTF provides two different implementations, 
([SimpleWaiter](https://github.com/xtf-cz/xtf/blob/main/core/src/main/java/cz/xtf/core/waiting/SimpleWaiter.java) and 
[SupplierWaiter](https://github.com/xtf-cz/xtf/blob/main/core/src/main/java/cz/xtf/core/waiting/SupplierWaiter.java)) 
and several preconfigured instances. All the default parameters of preconfigured `Waiter`s are overrideable.

`OpenShifts.master().waiters().isDcReady("my-deployment").waitFor();`

`Https.doesUrlReturnsOK("http://example.com").timeOut(TimeUnit.MINUTES, 10).waitFor();`

#### BuildManager
[BuildManager](https://github.com/xtf-cz/xtf/blob/main/core/src/main/java/cz/xtf/core/bm/BuildManager.java) caches 
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

#### Using `TestCaseContext` to get name of currently running test case

If `junit.jupiter.extensions.autodetection.enabled=true` then JUnit 5 extension `cz.xtf.core.context.TestCaseContextExtension` is
automatically registered. It
sets name of currently running test case into `TestCaseContext` before `@BeforeAll` of test case is called.

Following code then can be used to retrieve the name of currently running test case in:
```
String testCase = TestCaseContext.getRunningTestCaseName()
```

#### Automatic creation of namespace(s)

XTF allows to automatically manage creation of testing namespace which is defined by `xtf.openshift.namespace` property. This 
namespace is created before any test case is started. 

This feature requires to have XTF JUnit5 `cz.xtf.junit5.listeners.ProjectCreator` extension enabled. This can be done by adding
`cz.xtf.junit5.listeners.ProjectCreator` line into files:
```
src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension
src/test/resources/META-INF/services/org.junit.platform.launcher.PostDiscoveryFilter
src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener
```

#### Run test cases in separate namespaces using `xtf.openshift.namespace.per.testcase` property 

You can enable running each test case in separate namespace by setting `xtf.openshift.namespace.per.testcase=true`. 

Namespace names follow pattern: "`${xtf.openshift.namespace}`-TestCaseName". 
For example for `xtf.openshift.namespace=testnamespace` and test case `org.test.SmokeTest` it will be `testnamespace-SmokeTest`.

You can limit the length of created namespace by `xtf.openshift.namespace.per.testcase.length.limit` property. By default it's `25` chars. If limit is breached then
test case name in namespace name is hashed to hold the limit. So namespace name would like `testnamespace-s623jd6332`

**Warning - Limitations**

When enabling this feature in your project, **you may need to replace [OpenShiftConfig.getNamespace()](core/src/main/java/cz/xtf/core/config/OpenShiftConfig.java) 
with [NamespaceManager.getNamespace()](core/src/main/java/cz/xtf/core/namespace/NamespaceManager.java). Check method's javadoc to understand difference.**

In case that you're using this feature, consuming test suite must follow those rules to avoid unexpected behaviour when using `cz.xtf.core.openshift.OpenShift` instances:

* **Do not create static `cz.xtf.core.openshift.OpenShift` variable** like: `public static final OpenShift openshift = Openshifts.master()` on class level. 
The reason is that during initialization of static instances the test case and corresponsing namespace is not known. To avoid unexpected behaviour `RuntimeException` is thrown, when programmer breaks this rule.
* Similarly as above do not create `cz.xtf.core.openshift.OpenShift` variables in static blocks or do not initialize other static variables which creates `cz.xtf.core.openshift.OpenShift` instance.

#### Service Logs Streaming (SLS)
This feature allows for you to stream the services output while the test is running; this way you can see immediately 
what is happening inside the cluster.
This is of great help when debugging provisioning, specifically on Cloud environments, which instead would require for
you to access your Pods.

##### Kubernetes/OpenShift implementation
The SLS OpenShift platform implementation relies upon the following fabric8 Kubernetes Client API features:

- Watching Kubernetes events (see
  [PodWatchEquivalent.java](https://github.com/fabric8io/kubernetes-client/blob/main/kubernetes-examples/src/main/java/io/fabric8/kubernetes/examples/kubectl/equivalents/PodWatchEquivalent.java))
- Watching Pod logs (see
  [PodLogExample.java](https://github.com/fabric8io/kubernetes-client/blob/main/kubernetes-examples/src/main/java/io/fabric8/kubernetes/examples/PodLogExample.java))

The expected behavior is to stream the output of all the containers that are started or terminated in the selected 
namespaces.

##### Usage
The SLS feature can be configured and enabled either via annotations or via properties.
This behavior is provided by 
[the ServiceLogsStreamingRunner JUnit 5 extension](./junit5/src/main/java/cz/xtf/junit5/extensions/ServiceLogsStreamingRunner.java).
There are two different ways for enabling the SLS functionality, which are summarized in the following sections, please 
refer to the [JUnit 5 submodule documentation](./junit5/README.md) in order to read about the extension implementation 
details.

###### The `@ServiceLogsStreaming` annotation (Developer perspective)
Usage is as simple as annotating your test with `@ServiceLogsStreaming` e.g.:

```java
@ServiceLogsStreaming
@Slf4j
public class HelloWorldTest {
  // ...
}
```

###### The `xtf.log.streaming.enabled` and `xtf.log.streaming.config` property (Developer/Automation perspective)
You can enable the SLS feature by setting the `xtf.log.streaming.enabled` property so that it would apply to 
all the test classes being executed. 

Conversely, if the above property is not set, you can set the `xtf.log.streaming.config` 
property in order to provide multiple SLS configurations which could map to different test classes. 

The `xtf.log.streaming.config` property value is expected to be a _comma_ (`,`) separated list of _configuration items_,
each one formatted as a _semi-colon_ (`;`) separated list of _name_ and _value_ pairs for the above mentioned
attributes, where the name/value separator is expected to be the _equals_ char (`=`). 
A single _configuration item_ represents a valid source of configuration for a single SLS activation and exposes the 
following information:

* _**target**_: a regular expression which allows for the testing engine to check whether the current context test class
    name matches the Service Logs Streaming configuration - **REQUIRED**

* _**filter**_: a string representing a _regex_ to filter out the resources which the Service Logs Streaming activation
    should be monitoring - **OPTIONAL**

* _**output**_: the base path where the log stream files - one for each executed test class - will be created. 
  **OPTIONAL**, if not assigned, logs will be streamed to `System.out`. When assigned, XTF will attempt to create the 
  path in case it doesn't exist and default to `System.out` should any error occur. 


###### Usage examples 
Given what above, enabling SLS for all test classes is possible by executing the following command: 

```shell
mvn clean install -Dxtf.log.streaming.enabled=true
```

Similarly, in order to enable the feature for all test classes whose name is ending with "Test" should
be as simple as executing something similar to the following command:

```shell
mvn clean install -Dxtf.log.streaming.config="target=.*Test"
```

which would differ in case the logs should be streamed to an output file:

```shell
mvn clean install -Dxtf.log.streaming.config="target=.*Test;output=/home/myuser/sls-logs"
```

or in case you'd want to provide multiple configuration items to map different test classes, e.g.:

```shell
mvn clean install -Dxtf.log.streaming.config="target=TestClassA,target=TestClassB.*;output=/home/myuser/sls-logs;filter=.*my-app.*"
```

### JUnit5
JUnit5 module provides a number of extensions and listeners designed to easy up OpenShift images test management. 
See [JUnit5](https://github.com/xtf-cz/xtf/blob/main/junit5/README.md) for
more information.

### Helm

You can use `HelmBinary.execute()` method to run Helm against your cluster. Following Helm properties are introduced:

| Property name | Type | Description | Default value |
----------------|------|-------------|---------------|
| `xtf.helm.clients.url` | `String` | URL from which version specified by `xtf.helm.client.version` | `https://mirror.openshift.com/pub/openshift-v4/clients/helm` |
| `xtf.helm.client.version` | `String` | Version of the Helm client to be downloaded (from `http://[xtf.clients.url]/[xtf.client.version`) | `latest` |
| `xtf.helm.binary.path` | `String` | Path to existing Helm client binary. If absent, it will be downloaded using combination of `xtf.helm.clients.url` and `xtf.helm.client.version` parameters | |

## Releasing XTF
Have a look to the [release documentation](RELEASE.md) to learn about the process that defines how to release XTF to 
the community.

