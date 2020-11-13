workspace(name = "batch")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "0f81174e3d1b892a1342ebc75bb4bbb158ae0efe",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
#gerrit_api()

# Load snapshot Plugin API
gerrit_api(version = "3.4.0-SNAPSHOT")

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()
