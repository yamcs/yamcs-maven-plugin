Multi-Packaging Yamcs
=====================

Multiple Yamcs applications can be packaged from a single Maven project by defining multiple executions of the Yamcs Maven Plugin. Each execution must have a separate execution id. You should also specify different ``classifier`` properties in the configuration block of each execution. The classifier is used in the naming of the generated bundles. Without it, the two executions would overwrite each others outputs.

If you need different configurations of Yamcs for each server, then look into overriding the ``configurationDirectory`` (default is ``src/main/yamcs/``).

.. code-block:: xml

    <project>
      ...
      <artifactId>myproject</artifactId>
      <packaging>jar</packaging>
    
      <properties>
        <yamcsVersion>{{ YAMCS_VERSION }}</yamcsVersion>
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
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>{{ YAMCS_PLUGIN_VERSION }}</version>
            <executions>
              <execution>
                <id>bundle-yamcs1</id>
                <phase>package</phase>
                <goals>
                  <goal>bundle</goal>
                </goals>
                <configuration>
                  <classifier>ops</classifier>
                </configuration>
              </execution>
              <execution>
                <id>bundle-yamcs2</id>
                <phase>package</phase>
                <goals>
                  <goal>bundle</goal>
                </goals>
                <configuration>
                  <classifier>sim</classifier>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
      ...
    </project>

This will generate two bundles:

.. code-block::

    target/
    |-- myproject-1.0.0-SNAPSHOT-ops.tar.gz
    |-- myproject-1.0.0-SNAPSHOT-sim.tar.gz
