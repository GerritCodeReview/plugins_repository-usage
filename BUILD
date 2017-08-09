load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "repository-usage",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: repository-usage",
        "Gerrit-Module: com.googlesource.gerrit.plugins.repositoryuse.SuperManifestModule",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.repositoryuse.SshModule",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        '@digester3//jar',
        '@beanutils//jar',
        '@logging//jar',
    ],
    provided_deps = [
        '//lib/commons:dbcp',
        '//lib:gson',
    ],
)
