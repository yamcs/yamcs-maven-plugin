Yamcs Plugin
============

Writing a Yamcs plugin is just like writing any other jar. Declare your dependencies to the desired Yamcs artifacts, and define the Java version that you want to comply with. Official Yamcs plugins strive to remain compatible with Java 8 language features for the foreseeable future, but you are free to use more recent Java version in your project if you can.

To prototype your plugin in a local Yamcs application, add the ``yamcs-maven-plugin`` to the plugins section. Once you have specified a valid configuration in ``src/main/yamcs/``, you can get your copy of Yamcs running with:

.. code-block::

    mvn yamcs:run

To package your Yamcs plugin, simply do ``mvn package``. The resulting jar artifact can be dropped in the ``lib/`` or ``lib/ext/`` folder of any compatible Yamcs server.

.. code-block:: xml

    <project>
      ...
      <packaging>jar</packaging>
    
      <properties>
        <!-- Check the latest version at https://yamcs.org -->
        <yamcsVersion>{{ YAMCS_VERSION }}</yamcsVersion>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
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
      </dependencies>
    
      <build>
        <plugins>
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
              <source>1.8</source>
              <target>1.8</target>
            </configuration>
          </plugin>
    
          <plugin>
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>{{ YAMCS_PLUGIN_VERSION }}</version>
          </plugin>
        </plugins>
      </build>
      ...
    </project>
