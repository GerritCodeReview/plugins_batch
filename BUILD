load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

java_plugin(
    name = "auto-annotation-plugin",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "@auto-value-annotations//jar",
        "@auto-value//jar",
    ],
)

java_library(
    name = "auto-value",
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-value-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto-value//jar"],
)

java_library(
    name = "auto-value-annotations",
    exported_plugins = [
        ":auto-annotation-plugin",
        ":auto-value-plugin",
    ],
    visibility = ["//visibility:public"],
    exports = ["@auto-value-annotations//jar"],
)

gerrit_plugin(
    name = "batch",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: batch",
        "Gerrit-ApiVersion: 3.4.0-SNAPSHOT",
        "Implementation-Title: Batch Plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/batch",
        "Gerrit-Module: com.googlesource.gerrit.plugins.batch.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.batch.ssh.SshModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":auto-value",
        ":auto-value-annotations"
    ],
)
