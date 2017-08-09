load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "repository-usage",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: repository-usage",
        "Gerrit-Module: com.googlesource.gerrit.plugins.repositoryuse.SuperManifestModule",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.repositoryuse.SshModule",
    ],
    provided_deps = [
        "//lib/commons:dbcp",
        "//lib:gson",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        "@beanutils//jar",
        "@digester3//jar",
        "@logging//jar",
    ],
)
