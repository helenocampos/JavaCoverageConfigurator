# JavaCoverageConfigurator

Handy app for configuring a Maven Java project to use Jacoco and generate test coverage reports. 

Useful for empirical studies scripts to automate Java projects test coverage extraction.

It is able to handle multi module Maven projects as well. 

What it does:

- It modifies the pom.xml of the project passed as argument, adding the Jacoco dependency.
- If the project is multi module, in addition to adding the Jacoco dependency, it creates an aggregator module to allow the coverage report for all your modules. This strategy is [recommended](https://github.com/jacoco/jacoco/wiki/MavenMultiModule#strategy-module-with-dependencies) by Jacoco developers.

How to use: 

You have two options for using this project, building from source or downloading a released .jar.

## Build from source

If you want to build the project yourself, you can clone the repository:
```bash
$ git clone https://github.com/helenocampos/JavaCoverageConfigurator
```

Build the jar file using Maven:
```bash
$ mvn install
```

The jar cov_configurator.jar will be generated in the target folder.


## Using the app

After obtaining the cov_configurator.jar, you can execute it passing the path to the project you want to configure:

```bash
$ java -jar cov_configurator.jar path/to/project/folder
```

## Generating coverage reports

After configuring the desired project (by executing the app), you can generate the coverage report by executing the project tests 

```bash
$ mvn test jacoco:report
```


If you want the aggregate report (only if the desired project is multi module), you must execute the following command on the parent project:

```bash
$ mvn test jacoco:report-aggregate
```


Be aware that by executing the .jar, your pom.xml may be modified. If your project is multi module, an aggregator module will be created within your project and your pom.xml will be modified.

