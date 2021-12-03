from xml.etree import ElementTree as ET

# Read the latest Yamcs versions from the Maven pom.xml
tree = ET.ElementTree()
tree.parse("../pom.xml")
plugin_version_el = tree.getroot().find("{http://maven.apache.org/POM/4.0.0}version")
properties_el = tree.getroot().find("{http://maven.apache.org/POM/4.0.0}properties")
yamcs_version_el = properties_el.find("{http://maven.apache.org/POM/4.0.0}yamcsVersion")

# Configure Sphinx
project = "yamcs-maven-plugin"
copyright = "2019, Space Applications Services"
author = "Yamcs Team"
version = plugin_version_el.text  # The short X.Y version
release = version  # The full version, including alpha/beta/rc tags
extensions = ["sphinxcontrib.fulltoc"]
source_suffix = ".rst"
language = None
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
pygments_style = "sphinx"

latex_elements = {
    "papersize": "a4paper",
    "figure_align": "htbp",
    "extraclassoptions": "openany",
}

latex_documents = [
    (
        "index",
        "yamcs-maven-plugin.tex",
        "Yamcs Maven Plugin",
        "Space Applications Services",
        "manual",
    ),
]

# Brute-force replacer for substituting properties even in source code blocks
def handlebar_replacer(app, docname, source):
    result = source[0]
    result = result.replace("{{ YAMCS_PLUGIN_VERSION }}", plugin_version_el.text)
    result = result.replace("{{ YAMCS_VERSION }}", yamcs_version_el.text)
    source[0] = result


def setup(app):
    app.connect("source-read", handlebar_replacer)
