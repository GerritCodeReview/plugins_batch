workspace(name = "batch")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "efb3ad71f38e515eb1805ff457a077264b9f3dcc",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

load("//:external_plugin_deps.bzl", "external_plugin_deps")

external_plugin_deps()
