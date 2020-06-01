workspace(name = "batch")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "15eae2ee5cd524a204bd62c3d59bfd0ce86916ec",
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

AUTO_VALUE_VERSION = "1.6.5"

maven_jar(
    name = "auto-value",
    artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
    sha1 = "816872c85048f36a67a276ef7a49cc2e4595711c",
)

maven_jar(
    name = "auto-value-annotations",
    artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
    sha1 = "c3dad10377f0e2242c9a4b88e9704eaf79103679",
)

# Load snapshot Plugin API
#gerrit_api_maven_local()
