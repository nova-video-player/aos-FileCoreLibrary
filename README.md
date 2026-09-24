### FileCoreLibrary

Please see the entry point repo: https://github.com/archos-sa/aos-AVP

This git repo is part of NOVA opeN sOurce Video plAyer, a video player software for Android. Please see the entry point repo: https://github.com/nova-video-player/aos-AVP

This library provides smb, sftp, document provider, external storage access for file browsing support. 

NOVA is a fork of the open source project Archos Video Player Community Edition available here https://github.c om/archos-sa/aos-AVP

### Testing local jcifs-ng changes

Build the library from the workspace root with JDK 17:

```sh
mvn -f external/jcifs-ng/pom.xml \
  -Dtest=SmbAdaptiveReadTest,SmbTransportCreditsTest,SmbTransportFramingTest \
  -DargLine='--add-opens java.base/java.lang=ALL-UNNAMED' package
```

Then pass the built JAR to your usual Android build, using an absolute path:

```sh
cd Video
./gradlew -PjcifsNgJar=/absolute/path/to/external/jcifs-ng/target/jcifs-ng-2.1.11-SNAPSHOT.jar assembleNoamazonDebug
```

The override replaces the published jcifs-ng dependency and keeps its SLF4J and
BouncyCastle dependencies. Without this property, builds use nova10, which
includes the adaptive-credit fix. The override is only needed when testing
additional unpublished library changes.

The patched library keeps 1 MiB playback reads when 16 credits are available,
reduces regular-file reads to the current credit window, and waits for one credit
when none are available. Device testing should cover large files, seeking,
repeated start/stop, and concurrent metadata access on the affected SMB servers.
