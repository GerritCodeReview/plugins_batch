load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
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
