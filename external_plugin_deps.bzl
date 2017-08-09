load("//tools/bzl:maven_jar.bzl", "maven_jar", "GERRIT", "MAVEN_CENTRAL")

def external_plugin_deps():
  maven_jar(
    name = 'digester3',
    artifact = 'org.apache.commons:commons-digester3:3.2',
    sha1 = 'c3f68c5ff25ec5204470fd8fdf4cb8feff5e8a79',
    exclude = ['META-INF/LICENSE.txt', 'META-INF/NOTICE.txt'],
  )

  maven_jar(
    name = 'beanutils',
    artifact = 'commons-beanutils:commons-beanutils:1.8.3',
    sha1 = '686ef3410bcf4ab8ce7fd0b899e832aaba5facf7',
    exclude = ['META-INF/LICENSE.txt', 'META-INF/NOTICE.txt'],
  )

  maven_jar(
    name = 'logging',
    artifact = 'commons-logging:commons-logging:1.1.1',
    sha1 = '5043bfebc3db072ed80fbd362e7caf00e885d8ae',
  )
