Packaging Yamcs
===============

Running through Maven is useful for development and for creating prototypes, but it is not recommended for production environments. Instead bundle Yamcs together with your extensions and configurations in one integrated distribution.

This example binds the ``bundle`` goal of the yamcs-maven-plugin to the Maven ``package`` lifecycle phase. This makes Maven generate a Yamcs application with the command ``mvn package``.

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
      </dependencies>
    
      <build>
        <plugins>
          <plugin>
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>1.1.1</version>
            <executions>
              <execution>
                <id>bundle-yamcs</id>
                <phase>package</phase>
                <goals>
                  <goal>bundle</goal>
                </goals>
                <configuration>
                  <formats>
                    <format>tar.gz</format>
                    <format>zip</format>
                  </formats>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
      ...
    </project>
