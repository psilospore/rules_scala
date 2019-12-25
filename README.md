# WIP Bloop Integration with bazel

First read this: https://jvican.github.io/posts/integrate-bloop-with-bazel-and-pants.html

A very hacky WIP bloop integration with Bazel.
It's pretty messy in here but hopefully it won't be for long.

## What I've done so far

A `.bloop` directory is made in the workspace.
Given we have a project with 4 targets and 3 scala files with the following structure

```
ABC/
    BUILD: containing targets A, B, C, and C_run
    src/
        A.scala
        B.scala (depends on A)
        C.scala (depends on B and has main)
```

and the BUILD file looks like so:

```
load("@rules_scala_annex//rules:scala.bzl", "scala_binary", "scala_library")

scala_binary(
    name = "C_run",
    srcs = glob(["C.scala"]),
    visibility = ["//visibility:public"],
    deps = ["C"]
)

scala_library(
    name = "C",
    srcs = glob(["C.scala"]),
    visibility = ["//visibility:public"],
    deps = ["B"]
)

scala_library(
    name = "B",
    srcs = glob(["B.scala"]),
    visibility = ["//visibility:public"],
    deps = ["A"]
)

scala_library(
    name = "A",
    srcs = glob(["A.scala"]),
    visibility = ["//visibility:public"]
)
```

I want to generate the following:

```
.bloop/
    ABC:A.json
    ABC:B.json
    ABC:C.json
    ABC:C_run.json
    out/
        ABC:A/
            classes/
                A.class
        ABC:B/
            classes/
                B.class
        ABC:C/
            classes/
                C.class
        ABC:C_run/ (Not sure if this will contain classes
```

This is the example project I'm testing it on: https://github.com/psilospore/local_rules_scala

Each of the json files are bloop config files and each target has a config file associated with it.

Example ABC:B.json will look something like this

```json
{
    "version" : "1.1.2",
    "project" : {
        "name" : "ABC:B",
        "directory" : "/Users/syedajafri/dev/bazelExample/ABC",
        "sources" : [
            "/Users/syedajafri/dev/bazelExample/ABC/B.scala"
        ],
        "dependencies" : [
             "ABC:A" 
        ],
        "classpath" : [
             "/Users/syedajafri/dev/bazelExample/.bloop/out/ABC:A/classes"
        ],
        "out" : "/Users/syedajafri/dev/bazelExample/.bloop/out/ABC:B",
        "classesDir" : "/Users/syedajafri/dev/bazelExample/.bloop/out/ABC:B/classes",
        "scala" : { ... }
    }
}
```

You can see B has a dependency on A and the classpath has A's generated class files. `ABC:C.json` would look similar to this.

At this point you can then run `bloop compile ABC:A` or any of the other targets and it would send a BSP request to Bloop I believe.

We could also send a BSP request to compile ourselves


## WIP

I don't know bazel very well so some pointers would be really appreciated.
There's a bunch of hardcoded values and printlns.


I couldn't figure out how to invoke BloopRunner so I put most of my code in ZincRunner.scala.


### TODOs
* Move to BloopRunner and figure out how to invoke it


# OG Docs
[![Build Status](https://api.travis-ci.org/higherkindness/rules_scala.svg?branch=master)](https://travis-ci.org/higherkindness/rules_scala)

`higherkindness/rules_scala` evolved, in part, from the need for Bazel adoption support for large, monorepo Scala projects.
Bazel is wonderful because it makes use of parallelism and caching to vastly improve build times. However, to see these benefits, a project must first be broken down into
tiny packages and make use of fine-grained dependencies. This is not always a realistic short-term goal for large, monorepo Scala projects.

`higherkindness/rules_scala` allows for the optional use of Zinc incremental compilation to provide a stepping stone for these projects as they migrate to Bazel.

`higherkindness/rules_scala` is written with maintainability and accessibility in mind. It aims to facilitate the transition to Bazel, and to satisfy use cases throughout the Scala ecosystem.

## Principles

1. Support the breadth of the Scala ecosystem.
2. Follow Bazel best practices.
3. Be accessible and maintainable.
4. Have high-quality documentation.

If the right design principles are kept, implementing additional features should be simple and straightforward.

## Features

* Simple core API modeled after Bazel's Java APIs
  * [scala_library](docs/stardoc/scala.md#scala_library)
  * [scala_binary](docs/stardoc/scala.md#scala_binary)
  * [scala_test](docs/stardoc/scala.md#scala_test)
  * [scala_import](docs/stardoc/scala.md#scala_import)
  * [scala_repl](docs/stardoc/scala.md#scala_repl)
* [Works with all sbt-compatible test frameworks](docs/scala.md#tests)
* [Advanced Dependency Detection](docs/scala.md#strict--unused-deps)
  * Errors on indirect and unused dependencies
  * Buildozer suggestions for dependency errors
* [Optional Worker strategy](docs/scala.md#workers)
* [Optional Zinc-based stateful incremental compilation](docs/stateful.md#stateful-compilation)
* [Scalafmt](docs/scalafmt.md#scalafmt) integration
* Protobuf support with ScalaPB
  * [scala_proto_library](docs/stardoc/scala_proto.md#scala_proto_library)
  * [scala_proto_toolchain](docs/stardoc/scala_proto.md#scala_proto_toolchain)
* Seamless integration with the [Bazel IntelliJ plugin](https://github.com/bazelbuild/intellij)
* [Customizable rules](docs/newdocs/phases.md#customizing-the-core-rules)
* [Multiple Scala versions in one build](docs/newdocs/scala_versions.md#specifying-the-scala-version-to-use), including Scala 3 (Dotty).
* [Optimal handling of macros and ijars](docs/newdocs/macros.md#macros-and-ijars)
* [Pass flags to Zinc compiler](docs/newdocs/zinc_flags.md)
* Modern implementation using Bazel's most idiomatic APIs

## Usage

WORKSPACE

```python
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

# Load rules scala annex
rules_scala_annex_commit = "0f3fd2734fe1930ed1ca0a3c1876af8f24e94be2"
rules_scala_annex_sha256 = "4b68b573162e38ec5ca792dfd7c326891e9a28e48afdb30d4bb90d0cba7216ab"
http_archive(
    name = "rules_scala_annex",
    sha256 = rules_scala_annex_sha256,
    strip_prefix = "rules_scala-{}".format(rules_scala_annex_commit),
    url = "https://github.com/higherkindness/rules_scala/archive/{}.zip".format(rules_scala_annex_commit),
)

http_archive(
    name = "rules_jvm_external",
    sha256 = "249e8129914be6d987ca57754516be35a14ea866c616041ff0cd32ea94d2f3a1",
    strip_prefix = "rules_jvm_external-2.5",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/2.5.zip",
)

load("@rules_scala_annex//rules/scala:workspace.bzl", "scala_register_toolchains", "scala_repositories")
scala_repositories()
load("@annex//:defs.bzl", annex_pinned_maven_install = "pinned_maven_install")
annex_pinned_maven_install()
scala_register_toolchains()

load("@rules_scala_annex//rules/scalafmt:workspace.bzl", "scalafmt_default_config", "scalafmt_repositories")
scalafmt_repositories()
load("@annex_scalafmt//:defs.bzl", annex_scalafmt_pinned_maven_install = "pinned_maven_install")
annex_scalafmt_pinned_maven_install()
scalafmt_default_config()

load("@rules_scala_annex//rules/scala_proto:workspace.bzl", "scala_proto_register_toolchains", "scala_proto_repositories",)
scala_proto_repositories()
load("@annex_proto//:defs.bzl", annex_proto_pinned_maven_install = "pinned_maven_install")
annex_proto_pinned_maven_install()
scala_proto_register_toolchains()

# Load bazel skylib and google protobuf
git_repository(
    name = "bazel_skylib",
    remote = "https://github.com/bazelbuild/bazel-skylib.git",
    tag = "0.8.0",
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "0963c6ae20340ce41f225a99cacbcba8422cebe4f82937f3d9fa3f5dd7ae7342",
    strip_prefix = "protobuf-9f604ac5043e9ab127b99420e957504f2149adbe",
    urls = ["https://github.com/google/protobuf/archive/9f604ac5043e9ab127b99420e957504f2149adbe.zip"],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")
protobuf_deps()

# Specify the scala compiler we wish to use; in this case, we'll use the default one specified in rules_scala_annex
bind(
    name = "default_scala",
    actual = "@rules_scala_annex//src/main/scala:zinc_2_12_8",
)
```

BUILD

```python
load("@rules_scala_annex//rules:scala.bzl", "scala_library")

scala_library(
  name = "example",
  srcs = glob(["**/*.scala"])
)
```

## Further Documentation

See [contributing guidlines](CONTRIBUTING.md) for help on contributing to this project.

For all rules and attributes, see the [Stardoc](docs/stardoc).
