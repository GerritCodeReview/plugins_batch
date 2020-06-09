load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
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
