Packaging Yamcs
===============

Running through Maven is useful for development and for creating prototypes, but it is not recommended for production environments.

This plugin includes a ``bundle`` goal, which supports two packaging approaches:

* Bundle everything (Yamcs + Yamcs Plugins + Your Project) in one single distribution
* Bundle only your project

Bundling everything together is convenient, whereas the split approach allows you to make use of official Yamcs distributions.

In both cases the ``bundle`` goal of the yamcs-maven-plugin binds to the Maven ``package`` lifecycle phase. This makes Maven generate a Yamcs application with the command ``mvn package``.

The resulting artifact can be used as input to platform-specific packaging tools, for example to create an RPM or DEB package.


.. note::

    The ``bundle`` goal supports only a limited set of options. If you require to have more control over the
    layout and contents of your package, use other Maven plugins such as
    `maven-assembly-plugin <https://maven.apache.org/plugins/maven-assembly-plugin/>`_.


All-in-one
----------

This example bundles Yamcs together with your extensions and configurations in one integrated distribution.

.. code-block:: xml

    <project>
      ...
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


Project Only
------------

This example bundles only your extensions and configurations. The generated package can be extracted into an existing Yamcs installation directory.

Set the Maven scope of standard Yamcs dependencies to ``provided``. This way they can be used during compilation, while the ``bundle`` goal will ignore them.

Set also ``includeDefaultWrappers`` to ``false`` to prevent the ``yamcsd`` and ``yamcsadmin`` shell scripts from being added to your package. These are already included in official Yamcs core builds.

.. code-block:: xml
    :emphasize-lines: 14,20,38

    <project>
      ...
      <packaging>jar</packaging>

      <properties>
        <yamcsVersion>{{ YAMCS_VERSION }}</yamcsVersion>
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
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>{{ YAMCS_PLUGIN_VERSION }}</version>
            <executions>
              <execution>
                <id>bundle-yamcs</id>
                <phase>package</phase>
                <goals>
                  <goal>bundle</goal>
                </goals>
                <configuration>
                  <includeDefaultWrappers>false</includeDefaultWrappers>
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


Combination
-----------

.. versionadded:: 1.2.12

What if you want to mark your dependencies as provided, and at the same time also make a bundle with those dependencies included. You can do so by setting the ``scope`` property on the bundle configuration to ``compile``. The default scope if unset, is ``runtime``, which excludes provided dependencies.


.. code-block:: xml
    :emphasize-lines: 14,20,38

    <project>
      ...
      <packaging>jar</packaging>

      <properties>
        <yamcsVersion>{{ YAMCS_VERSION }}</yamcsVersion>
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
            <groupId>org.yamcs</groupId>
            <artifactId>yamcs-maven-plugin</artifactId>
            <version>{{ YAMCS_PLUGIN_VERSION }}</version>
            <executions>
              <execution>
                <id>bundle-yamcs</id>
                <phase>package</phase>
                <goals>
                  <goal>bundle</goal>
                </goals>
                <configuration>
                  <scope>compile</scope>
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
