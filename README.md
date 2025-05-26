# VisualBukkitExtensions
Extensions for Visual Bukkit

# Rework
Hi everyone, since the Visual Bukkit 6 update, extensions are mostly redundant as you can just import the API or library thorugh maven. An exception to this is the bStats extension because it had to be shaded, which is not typically what an extension could do? or so you thought. Well, I managed to do so using an extension without modifying **any** of Visual Bukkit's core code (and disturbing the main developer). If you are interested in reading this tale of how everything was put togather and made to work or you are just someone willing to make extensions for VB6, read along.

## The refactoring
Now, to begin with, the old extensions were based on VB5 and so was their code. As you open the project in IntelliJ or any IDE, you will instantly see errors and warnings. This is because the repository is **not** mentioned so maven would look into its default repository and would not be able to find any code regarding Visual Bukkit. There is a solution to this and that is Visual Bukkit's Jitpack page which has the maven dependency and repository information for visual bukkit 6, specifically [here](https://jitpack.io/#OfficialDonut/VisualBukkit) and under the builds tab, the build labelled `master-a00f384d81-1` is the one which can be used to make Visual Bukkit extensions. If you look at the updated [pom.xml](pom.xml) file, you will notice that the vault extension and placeholder extension are commented out. This is done as refactoring them would be redundant, i.e. they can easily be replaced by importing through maven.

The [StatConnectBstats](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/StatConnectBstats.java) was an easy thing to refactor, in fact the new code is a lot more cleaner, readable and understandable than the old one. The [BstatsExtension](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java) was able to carry on some of the old code, notably the loading of [Metrics class](bStats/src/main/resources/Metrics.java). The only change was that the constructor for the class was made empty (and parameterless) and the initialization logic was moved to the [open method](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java#L39). These things are pretty easy to do with a little understanding of java and how Visual Bukkit works.

## The challenge
Alright, so now comes the hard part which is shading bStats into plugin's pom file. I first tried making [BstatsExtension class](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java) extend the [Project class](https://github.com/OfficialDonut/VisualBukkit/blob/master/VB-Application/src/main/java/com/gmail/visualbukkit/project/Project.java) and then overriding the [build method](https://github.com/OfficialDonut/VisualBukkit/blob/master/VB-Application/src/main/java/com/gmail/visualbukkit/project/Project.java#L647) but I quickly realised `bStatsExtension.build() != Project.build()` and scraped the idea but you might ask what I wanted to achieve with this? Well, if overriding the build method and calling the modified build method was possible, I could rewrite the contents of Plugin's pom.xml file to shade bstats before the maven's build was run.

So what must be done now? I thought... I figured out a way. If you carefully go through the [build method](https://github.com/OfficialDonut/VisualBukkit/blob/master/VB-Application/src/main/java/com/gmail/visualbukkit/project/Project.java#L647), you will notice some things such as:
- The build is run on a seperate [Thread](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html)
- The Pom file is written before the maven build is executed
- The build directory is first deleted completely and then regenerated

With these things in mind, I figured out how I would shade bStats into the pom file. All I had to do was watch for changes in the file system, and as soon as a pom file is created or modified in the project's build directory, my code would put the `Build Thread` in sleep mode, shade the pom file, and then wake up the `Build Thread` again. This had some ceveats though, such as:
- Starting our [WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html)
- Watching for creation and deletion of the `Build directory`
- Waiting for the pom file to be fully written so that we don't edit it before everything is generated
- Making sure our [WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html) is enabled and disabled at the right time
- Making sure our Watcher's Thread is stopped when the application closes

These ceveats and a few more were all covered up if you looks at the code. Additionally [Reflection](https://www.oracle.com/technical-resources/articles/java/javareflection.html) was used to obtain the package name of the current project, see [reflectPackageName Method](bStats/src/main/java/com/gmail/visualbukkit/extensions/bstats/BstatsExtension.java#L210).

## Maven dependancies
Another interesting thing is the scopes used in [pom.xml](pom.xml). When developing extensions it is useful to have knowledge about [Maven's dependency scopes](https://www.baeldung.com/maven-dependency-scopes). All of the scopes are set to provided because we know that those libraries are already present in VisualBukkit, it is just that we need those libraries to be *rendered* in our IDE so that we can effectively code and build our extension. We do not need to include or compile the libraries because they are already there in VisualBukkit, i.e. they are `provided` (at runtime). We only need to use the compile scope if the library is not present in VisualBukkit.

# Thanks for reading
That's it for this explanation on Visual Bukkit 6 extensions using an example, hope you learned a lot!
