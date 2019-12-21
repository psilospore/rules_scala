Don't read this it's my train of thought

There's some scripts in scripts/
like gen-deps

//All packages
bazel query '...' --output package

//Show all deps and maybe other things?
bazel query '@annex//:*'

Looks like monix is available so maybe use that
and cats (not as direct deps probably)


Seems that this is the main thing

/Users/syedajafri/dev/rules_scala/rules/scala.bzl

See phases.md too

Read about Bazel providers I can follow make_scala_binary -> 

rules/provider.bzl ScalaRulePhase = provider(
                       doc = "A Scala compiler plugin",
                       fields = {
                           "phases": "the phases to add",
                       },
                   )
                   
/Users/syedajafri/dev/rules_scala/rules/private/phases/
has all the phases like binary launcher


which does the following


get inputs from ctx.files.data
ctx probably just holds a bunch of stuff that it's aware of???

main_class from ctx.attr.main_class
So I guess that's an attribute passed in

launcher uses the singlejar utility from bazel

It combines multiple jars into a single jar for self contained code

looks like these phases just return stuff to execute and that happens in run phases

disliking the lack of types

Idea replace ZincRunner with Bloop stuff since I have no idea how to invoke it


Alright so I ripped out Zinc runner and am going to replace it with bloop if I run it like 
```
bazel run //hello:hello_run --worker_extra_flag=ScalaCompile=--persistence_dir=.bazel-zinc
```

I see my label
 --label=//hello:hello_run
That might be the thing I want for mapping to a BSP request
I also see  hello/src/hello.scala

TODO How would I walk down the tree of dependencies?
Might be in bazel-out/darwin-fastbuild/bin/hello/hello_run/deps_used.txt so cat that and see what it says
Probably add a dependency on another library first. Following the A B C pattern


Task for now generate config for A and send BSP request to compile A

according to commponArgs help("Analysis, given as: label apis relations [jar ...]")


pass stuff from bazel land from phase_zinc_compile.bzl


So if I have a C and C_run and C_run depends on C then I would still want 2 bloop configs one for C and one for C_run
So the target to bloop project mapping holds


Since A is the lowest on the rung it seems like it's called first


To debug from intelliJ I can hardcode some arguments like so

```
--compiler_bridge
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/external/rules_scala_annex/src/main/scala/compiler_bridge_2_12_8.jar
--compiler_classpath
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/external/annex/v1/http/central.maven.org/maven2/org/scala-lang/scala-compiler/2.12.8/scala-compiler-2.12.8.jar
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/external/annex/v1/http/central.maven.org/maven2/org/scala-lang/modules/scala-xml_2.12/1.2.0/scala-xml_2.12-1.2.0.jar
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/external/annex/v1/http/central.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/external/annex/v1/http/central.maven.org/maven2/org/scala-lang/scala-reflect/2.12.8/scala-reflect-2.12.8.jar
--classpath
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/external/annex/v1/http/central.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar
--java_compiler_option=-source
--java_compiler_option=8
--java_compiler_option=-target
--java_compiler_option=8
--java_compiler_option=-XDskipDuplicateBridges=true
--java_compiler_option=-g
--java_compiler_option=-parameters
--label=//ABC:A
--main_manifest
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A.jar.mains.txt
--output_apis
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/apis.gz
--output_infos
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/infos.gz
--output_jar
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/classes.jar
--output_relations
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/relations.gz
--output_setup
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/setup.gz
--output_stamps
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/stamps.gz
--output_used
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/deps_used.txt
--tmp
/Users/syedajafri/dev/bazelExample/bazel-out/darwin-fastbuild/bin/ABC/A/tmp
--workspace_dir
/Users/syedajafri/dev/bazelExample/
--
/Users/syedajafri/dev/bazelExample/ABC/A.scala
```

