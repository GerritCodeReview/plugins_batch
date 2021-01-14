workspace(name = "batch")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "5703ec25181046b60913d3510a0a5c2f0afa46f8",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

# Load snapshot Plugin API
#gerrit_api(version = "3.3.0-SNAPSHOT")

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()
