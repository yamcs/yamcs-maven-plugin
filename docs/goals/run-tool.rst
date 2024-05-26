yamcs:run-tool
==============

Runs a Yamcs-related tool as part of a Maven build.

Attributes:

* Requires a Maven project to be executed.
* Requires dependency resolution of artifacts in scope: ``test``.
* Invokes the execution of the lifecycle phase ``process-classes`` prior to executing itself.


.. rubric:: Required Parameters

tool (string)
    Class name of the tool to execute.

    User property is: ``yamcs.tool``


.. rubric:: Optional Parameters

args (list)
    Arguments passed to the Yamcs executable. Add each argument in an <arg> subelement.
        
    User property is: ``yamcs.args``

directory (file)
    The directory where Yamcs is installed.

    Default value is: ``${project.build.directory}/yamcs``

    User property is: ``yamcs.directory``
