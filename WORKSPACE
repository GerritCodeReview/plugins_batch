workspace(name = "batch")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "53cfe52b1b691040fd03dfe025c0a381df48c6ee",
    #local_path = "/home/<user>/projects/bazlets",
)

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Snapshot Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
#    "gerrit_api_maven_local",
#)

# Load release Plugin API
gerrit_api()

load("//tools/bzl:maven_jar.bzl", "GERRIT", "maven_jar")

AUTO_VALUE_VERSION = "1.7"

maven_jar(
    name = "auto-value",
    artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
    sha1 = "fe8387764ed19460eda4f106849c664f51c07121",
)

maven_jar(
    name = "auto-value-annotations",
    artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
    sha1 = "5be124948ebdc7807df68207f35a0f23ce427f29",
)

# Load snapshot Plugin API
#gerrit_api_maven_local()
