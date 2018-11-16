load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "batch",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: batch",
        "Gerrit-ApiVersion: 2.15",
        "Implementation-Title: Batch Plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/batch",
        "Gerrit-Module: com.googlesource.gerrit.plugins.batch.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.batch.ssh.SshModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "//gerrit-util-cli:cli",
    ],
)
