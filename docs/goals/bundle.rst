yamcs:bundle
============

Bundle a Yamcs application into a single archive file.

Attributes:

* Requires a Maven project to be executed.
* Requires dependency resolution of artifacts in scope: ``compile+runtime``.
* Invokes the execution of the lifecycle phase ``package``.


.. rubric:: Optional Parameters

attach (boolean)
    Controls whether this mojo attaches the resulting bundle to the Maven project.

    Default value is: ``true``

    User property is: ``yamcs.attach``

classifier (string)
    Classifier to add to the generated bundle.

    Default value is: ``bundle``

configurationDirectory (file)
    The directory that contains Yamcs configuration files. By convention this contains subfolders named ``etc`` and ``mdb``.

    Relative paths in yaml configuration files are resolved from this directory.

    Default value is: ``${basedir}/src/main/yamcs``

    User property is: ``yamcs.configurationDirectory``

includeDefaultWrappers (boolean)
    Whether ``yamcs`` and ``yamcsadmin`` wrapper scripts should be included in the bundle.

    Default value is: ``true``

    User property is: ``yamcs.includeDefaultWrappers``

includeConfiguration (boolean)
    Whether this module's configuration directory (default location: ``src/main/yamcs``) should be included in the bundle

    Default value is: ``true``

    User property is: ``yamcs.includeConfiguration``

useDefaultExcludes (boolean)
    Set whether the default excludes are being applied.

    Default value is: ``true``.

includes (list)
    Set a string of patterns, which included files should match. Add each argument in an <include> subelement.

excludes (list)
    Set a string of patterns, which excluded files should match. Add each argument in an <exclude> subelement.

    For example:

    .. code-block:: xml

      <excludes>
        <exclude>**/__pycache__/**</exclude>
      </excludes>

formats (list)
    Specifies the formats of the bundle. Multiple formats can be supplied. Each format is specified by supplying one of the following values in a <format> subelement:

    * zip - Creates a ZIP file format
    * tar - Creates a TAR format
    * tar.gz or tgz - Creates a gzip'd TAR format
    * tar.bz2 or tbz2 - Creates a bzip'd TAR format
    * tar.snappy - Creates a snappy'd TAR format
    * tar.xz or txz - Creates a xz'd TAR format
    * tar.zst or tzst - Creates a zst'd TAR format

    If unspecified the behavior is equivalent to:

    .. code-block:: xml

      <formats>
        <format>tar.gz</format>
      </formats>

skip (boolean)
    Skip execution

    Default value is: ``false``

    User property is: ``yamcs.skip``
