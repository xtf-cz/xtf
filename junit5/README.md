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

##### ConfigRecorder
Records specified images url. Set `xtf.junit.used_image` property with images id split by ',' character. Particular image url will be then stored in `used-images.properties` file in project root.

##### ProjectCreator
Creates project before test executions on OpenShift if doesn't exist. Use `xtf.junit.clean_openshift` property to delete after all test have been executed. It also checks property `xtf.openshift.pullsecret` to create pull secret in the new project.

##### TestExecutionLogger
Logs individual test executions into the console.

##### TestExecutionReporter
Reports test execution on the fly into one `log/junit-report.xml` file. 

## Extensions
Extensions enable better test management. Usable directly on classes and methods.

##### @CleanBeforeAll/@CleanBeforeEach
Cleans namespace specified by `xtf.openshift.namespace` property. Either before all tests or each test execution.

##### @SinceVersion
Marks that test for particular feature is available from specfied tag. Compares specified image version tag for particular image repo with one that is expected. Executed if tag is at least one that is expected.

`@SinceVersion(image = imageId, name = imageRepo, since = "1.5")`

##### @SkipFor
Skips test if image repo matches the name in annotation.

`@SkipFor(image = imageId, name = imageRepo)`

##### @KnownIssue
Marks test as skipped rather then failed in case that thrown exception's message contains failureIdentification String.

`@SkipFor(value = failureIdentification)`

## Other
Former xPaaS version supported test filtering based on annotations and regular expressions. Use JUnit5 tags and groups for former case and `maven-surefire` version 2.22 plugin for later case.