include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//lib/maven.defs')

DEPS = [
  ':digester3',
  ':beanutils',
  ':logging',
]
PROVIDED_DEPS = [
  '//lib/commons:dbcp',
  '//lib:gson',
]
TEST_DEPS = GERRIT_PLUGIN_API + [
  ':repository-usage__plugin',
  '//lib:junit',
  '//lib:truth',
]

gerrit_plugin(
  name = 'repository-usage',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: repository-usage',
    'Gerrit-Module: com.googlesource.gerrit.plugins.repositoryuse.Module',
    'Gerrit-SshModule: com.googlesource.gerrit.plugins.repositoryuse.SshModule',
  ],
  deps = DEPS,
  provided_deps = PROVIDED_DEPS,
)

java_library(
  name = 'classpath',
  deps = [':repository-usage__plugin'],
)

java_test(
  name = 'repository-usage_tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['repository-usage'],
  source_under_test = [':repository-usage__plugin'],
  deps = TEST_DEPS,
)

maven_jar(
  name = 'digester3',
  id = 'org.apache.commons:commons-digester3:3.2',
  sha1 = 'c3f68c5ff25ec5204470fd8fdf4cb8feff5e8a79',
  license = 'Apache2.0',
  exclude = ['META-INF/LICENSE.txt', 'META-INF/NOTICE.txt'],
)

maven_jar(
  name = 'beanutils',
  id = 'commons-beanutils:commons-beanutils:1.8.3',
  sha1 = '686ef3410bcf4ab8ce7fd0b899e832aaba5facf7',
  license = 'Apache2.0',
  exclude = ['META-INF/LICENSE.txt', 'META-INF/NOTICE.txt'],
)

maven_jar(
  name = 'logging',
  id = 'commons-logging:commons-logging:1.1.1',
  sha1 = '5043bfebc3db072ed80fbd362e7caf00e885d8ae',
  license = 'Apache2.0',
)

