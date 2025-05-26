 Original readme available [here](README.md)

## Introduction

With the release of Visual Bukkit 6 (VB6), the need for extensions has significantly diminished. Most functionalities can now be achieved by importing APIs or libraries directly through Maven. However, the bStats extension remains an exception due to its requirement to be shaded—a task not typically handled by standard extensions. Contrary to this belief, I managed to shade bStats using an extension without altering any of Visual Bukkit's core code or involving the main developer. If you're interested in the journey of how this was accomplished or if you're looking to develop extensions for VB6, read on.

## Refactoring the Old Extensions

The legacy extensions were built for VB5, and their code reflects that. Opening these projects in IntelliJ or any modern IDE reveals numerous errors and warnings. This is primarily because the repository isn't specified, leading Maven to search its default repositories and fail to locate Visual Bukkit-related code.

The solution lies in Visual Bukkit's Jitpack page, which provides the necessary Maven dependency and repository information for VB6. Specifically, the build labeled `master-a00f384d81-1` under the builds tab is suitable for developing Visual Bukkit extensions. You can find it [here](https://jitpack.io/#OfficialDonut/VisualBukkit).

In the updated [pom.xml](pom.xml) file, you'll notice that the Vault and Placeholder extensions are commented out. This is intentional, as refactoring them would be redundant—they can be easily replaced by importing through Maven.

Refactoring the [StatConnectBstats](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/StatConnectBstats.java) was straightforward. The new code is cleaner, more readable, and easier to understand than its predecessor. The [BstatsExtension](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java) retained some of the old code, notably the loading of the [Metrics class](bStats/src/main/resources/Metrics.java). The primary change involved making the constructor parameterless and moving the initialization logic to the [open method](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java#L39). These modifications are relatively simple with a basic understanding of Java and Visual Bukkit's architecture.

## The Challenge: Shading bStats into the Plugin's POM

The real challenge was shading bStats into the plugin's `pom.xml`. My initial approach involved making the [BstatsExtension class](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java) extend the [Project class](https://github.com/OfficialDonut/VisualBukkit/blob/master/VB-Application/src/main/java/com/gmail/visualbukkit/project/Project.java) and overriding the [build method](https://github.com/OfficialDonut/VisualBukkit/blob/master/VB-Application/src/main/java/com/gmail/visualbukkit/project/Project.java#L647). However, I quickly realized that `bStatsExtension.build()` is not equivalent to `Project.build()`, leading me to abandon this strategy.

The goal was to intercept the build process and modify the plugin's `pom.xml` to shade bStats before Maven executed the build. Upon examining the [build method](https://github.com/OfficialDonut/VisualBukkit/blob/master/VB-Application/src/main/java/com/gmail/visualbukkit/project/Project.java#L647), I observed the following:

- The build runs on a separate [Thread](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html).
- The `pom.xml` is written before the Maven build is executed.
- The build directory is deleted and regenerated during the process.

With these insights, I devised a strategy to monitor the file system for changes. As soon as the `pom.xml` is created or modified in the project's build directory, my code would:

1. Pause the build thread.
2. Modify the `pom.xml` to include the shaded bStats dependency.
3. Resume the build thread.

This approach had several caveats:

- Initiating a [WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html) to monitor file system changes.
- Watching for the creation and deletion of the build directory.
- Ensuring the `pom.xml` is fully written before attempting modifications.
- Properly enabling and disabling the WatchService at appropriate times.
- Ensuring the Watcher's thread terminates gracefully when the application closes.

These challenges were addressed in the code. Additionally, [Reflection](https://www.oracle.com/technical-resources/articles/java/javareflection.html) was employed to obtain the package name of the current project, as demonstrated in the [reflectPackageName Method](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java#L210).

## Maven Dependencies and Scopes

An important aspect of developing extensions is understanding [Maven's dependency scopes](https://www.baeldung.com/maven-dependency-scopes). In the [pom.xml](pom.xml), all dependencies are set to `provided`. This is because these libraries are already present in Visual Bukkit; we only need them rendered in our IDE for effective development. There's no need to include or compile these libraries, as they are available at runtime. The `compile` scope should only be used if the library isn't present in Visual Bukkit.

## Conclusion

This exploration into shading bStats within Visual Bukkit 6 extensions demonstrates that with a bit of ingenuity and a deep understanding of the build process, it's possible to overcome limitations without altering core code. I hope this serves as a valuable resource for those looking to develop extensions for VB6.

Thank you for reading!
