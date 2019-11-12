yamcs:debug
===========

Runs Yamcs in debug mode as part of a Maven build.

Attributes:

* Requires a Maven project to be executed.
* Requires dependency resolution of artifacts in scope: ``test``.
* Invokes the execution of the lifecycle phase ``process-classes`` prior to executing itself.


.. rubric:: Optional Parameters

args (list)
    Arguments passed to the Yamcs executable. Add each argument in an <arg> subelement.
        
    User property is: ``yamcs.args``

configurationDirectory (file)
    The directory that contains Yamcs configuration files. By convention this contains subfolders named ``etc`` and ``mdb``.

    Relative paths in yaml configuration files are resolved from this directory.

    Default value is: ``${basedir}/src/main/yamcs``

    User property is: ``yamcs.configurationDirectory``

directory (file)
  The directory to create the runtime Yamcs server configuration under.

  Default value is: ``${project.build.directory}/yamcs``

  User property is: ``yamcs.directory``

jvmArgs (list)
    JVM Arguments passed to the forked JVM that runs Yamcs. Add each argument in a <jvmArg> subelement.

    User property is: ``yamcs.jvmArgs``

jvmDebugPort (int)
    Port for debugging

    Default value is: ``7896``

    User property is: ``yamcs.jvm.debug.port``

jvmDebugSuspend (boolean)
    Suspend when debugging

    User property is: ``yamcs.jvm.debug.suspend``

skip (boolean)
    Skip execution

    Default value is: ``false``

    User property is: ``yamcs.skip``

stopTimeout (long)
    Time in milliseconds that a graceful stop of Yamcs is allowed to take. When this time has passed, Yamcs is stopped forcefully. A value < 0 causes the stop to be done async from the Maven JVM.

    User property is: ``yamcs.stopTimeout``
