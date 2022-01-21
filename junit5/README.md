# JUnit5
JUnit5 module provides number of extensions and listeners designed to ease up OpenShift images test management.

This module assumes that user does have configured properties for OpenShift master url, namespace, username and password, alternatively token. It also assumes that user uses properties for specifying images.

## Listeners
Setting up test execution listeners allows user to react on test executions and its phases. List particular listeners in the following file to hook them up:

`projectPath/src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`

Content example:
```
cz.xtf.junit5.listeners.ConfigRecorder
cz.xtf.junit5.listeners.ProjectCreator
cz.xtf.junit5.listeners.TestExecutionLogger
cz.xtf.junit5.listeners.TestResultReporter
```

### ConfigRecorder
Records specified images url. Set `xtf.junit.used_image` property with images id split by ',' character. Particular image url will be then stored in `used-images.properties` file in project root.

### ProjectCreator
Creates project before test executions on OpenShift if doesn't exist. Use `xtf.junit.clean_openshift` property to delete after all test have been executed. It also checks property `xtf.openshift.pullsecret` to create pull secret in the new project.

### TestExecutionLogger
Logs individual test executions into the console.

### TestExecutionReporter
Reports test execution on the fly into one `log/junit-report.xml` file. 

## Extensions
Extensions enable better test management. 

### Usable directly on classes and methods through the related annotations.

#### @OpenShiftRecorder
Record OpenShift state when a test throws an exception or use `xtf.record.always` to record on success. Specify app names (which will be turned into regexes) to filter resources by name. When not specified, everything in test and build namespace will be recorded (regex - `.*`). Use `xtf.record.dir` to set the directory.

#### @CleanBeforeAll/@CleanBeforeEach
Cleans namespace specified by `xtf.openshift.namespace` property. Either before all tests or each test execution.

#### @SinceVersion
Marks that test for particular feature is available from specfied tag. Compares specified image version tag for particular image repo with one that is expected. Executed if tag is at least one that is expected.

`@SinceVersion(image = imageId, name = imageRepo, since = "1.5")`

#### @SkipFor
Skips test if image repo matches the name in annotation.

`@SkipFor(image = imageId, name = imageRepo)`

#### @KnownIssue
Marks test as skipped rather then failed in case that thrown exception's message contains failureIdentification String.

`@SkipFor(value = failureIdentification)`

### Usable through service registration

#### ServiceLogsRunner
This extension can be registered by projects which depend on XTF, for instance by creating a file named 
`org.junit.jupiter.api.extension.Extension` with the following content into the classpath:

```text
cz.xtf.junit5.extensions.ServiceLogsStreamingRunner
```
will allow for the extension to be loaded at runtime. The Service Logs Streaming feature can then be enabled either 
through system properties or by annotating test classes with the `@ServiceLogs` annotation.

##### Enabling via system properties
* `xtf.log.streaming.enabled` - set this property to true in order to enable the feature for all test classes
* `xtf.log.streaming.config` - set this property to define a comma separated list of valid _configuration items_ that will be 
used to provide multiple SLS configuration options, in order to address different test classes in a specific way, e.g.:

  `xtf.log.streaming.config="target=TestClassA,target=TestClassB.*;output=/home/myuser/sls-logs;filter=.*my-app.*"`

where two different _configuration items_ are listed and can be fully described as follows:

1. _Applying only to "TestClassA", will default to System.out for `output`, while the `filter` regex is set to `.*`_

2. _Applying to all test classes that match "TestClassB.*", streaming logs to files in the `home/myuser/sls-logs` 
  directory and monitoring all the resources which name matches the `.*my-app.*` regex_

##### Enabling via annotation
Annotate any test class with the `@ServiceLogs` annotation in order to enable the feature for a given test class.

##### Implementation details
Since both the above mentioned ways to enable the feature can coexist, the extension will collect configurations 
separately, i.e. _annotation based_ and _property based_ configurations.
In case one given test class name matches one of the collected configurations' `target` attribute, then the remaining
configuration attributes (e.g.: `filter`) are used to start a `ServiceLogs` instance for the scenario that is covered 
by the test class.
In case two configurations exist which are matched by one test class name, then the _property based configuration_ is
used.

## Other
Former xPaaS version supported test filtering based on annotations and regular expressions. Use JUnit5 tags and groups for former case and `maven-surefire` version 2.22 plugin for later case.