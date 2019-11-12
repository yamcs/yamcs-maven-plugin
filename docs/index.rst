==================
Yamcs Maven Plugin
==================

This is a Maven plugin for developing a Yamcs application.

Yamcs is a Java-based open source mission control framework. Its functionalities can be extended with your own custom code.

Goals
~~~~~

* :doc:`goals/run`
* :doc:`goals/debug`
* :doc:`goals/bundle`


Usage
~~~~~

This plugin expects to find Yamcs configuration in ``${project.basedir}/src/main/yamcs`` in subfolders ``etc`` and ``mdb``.

The pom.xml adds dependencies to the desired Yamcs modules. At least a dependency to yamcs-core is required. yamcs-web is another common dependency that makes Yamcs host a prebuilt copy of the Yamcs web interface:

.. code-block:: xml

    <project>
      ...
      <packaging>jar</packaging>
    
      <properties>
        <!-- Check the latest version at https://yamcs.org -->
        <yamcsVersion>4.10.4</yamcsVersion>
      </properties>
    
      <dependencies>
        <dependency>
          <groupId>org.yamcs</groupId>
          <artifactId>yamcs-core</artifactId>
          <version>${yamcsVersion}</version>
        </dependency>
        <dependency>
          <groupId>org.yamcs</groupId>
          <artifactId>yamcs-web</artifactId>
          <version>${yamcsVersion}</version>
        </dependency>
        ...
      </dependencies>
    
      <build>
        <plugins>
          <plugin>
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>1.1.1</version>
          </plugin>
        </plugins>
      </build>
    
    </project>

To run a Yamcs application:

.. code-block::

    mvn yamcs:run


Examples
~~~~~~~~

* :doc:`examples/plugin`
* :doc:`examples/packaging`
* :doc:`examples/multi`

.. toctree::
    :hidden:
    :maxdepth: 1
    :titlesonly:

    goals/index
    examples/index
