Yamcs Plugin
============

Writing a Yamcs plugin is like writing any other jar. Declare your dependencies to the desired Yamcs artifacts, and define the Java version that you want to comply with. Official Yamcs plugins strive to remain compatible with Java 11 language features for the foreseeable future, but you are free to use more recent Java version in your project if you can.

To prototype your plugin in a local Yamcs application, add the ``yamcs-maven-plugin`` to the plugins section. Once you have specified a valid configuration in ``src/main/yamcs/``, you can get your copy of Yamcs running with:

.. code-block::

    mvn yamcs:run

To package your Yamcs plugin, run ``mvn package``. The resulting jar artifact can be dropped in the ``lib/`` or ``lib/ext/`` folder of any compatible Yamcs server.

Prefer to declare dependencies to the core Yamcs libraries, or other Yamcs plugins at ``provided`` scope. Depending projects may use this information to exclude your plugin from being packaged (assuming it is added to the classpath in another manner).

For optimal integration adding an execution of the :doc:`../goals/detect` mojo as shown below. It will allow Yamcs to find metadata on your plugin and will give your plugin the opportunity to hook into the lifecycle of Yamcs.

.. code-block:: xml

    <project>
      ...
      <packaging>jar</packaging>

      <properties>
        <yamcsVersion>{{ YAMCS_VERSION }}</yamcsVersion>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
      </properties>

      <dependencies>
        <dependency>
          <groupId>org.yamcs</groupId>
          <artifactId>yamcs-core</artifactId>
          <version>${yamcsVersion}</version>
          <scope>provided</scope>
        </dependency>
        <dependency>
          <groupId>org.yamcs</groupId>
          <artifactId>yamcs-web</artifactId>
          <version>${yamcsVersion}</version>
          <scope>provided</scope>
        </dependency>
      </dependencies>

      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.12.1</version>
            <configuration>
              <release>17</release>
            </configuration>
          </plugin>

          <plugin>
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>{{ YAMCS_PLUGIN_VERSION }}</version>
            <executions>
              <execution>
                <goals>
                  <goal>detect</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
      ...
    </project>
