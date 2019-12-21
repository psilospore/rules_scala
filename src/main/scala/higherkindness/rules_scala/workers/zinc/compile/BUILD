load("//rules:scala.bzl", "scala_binary")
load("//rules:scalafmt.bzl", "scala_format_test")

scala_binary(
    name = "compile",
    srcs = glob(["**/*.scala"]),
    main_class = "higherkindness.rules_scala.workers.zinc.compile.ZincRunner",
    scala = "//src/main/scala:bootstrap",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@annex//:com_lihaoyi_sourcecode_2_12",
    ],
    deps = [
        "//src/main/scala/higherkindness/rules_scala/common/worker",
        "//src/main/scala/higherkindness/rules_scala/workers/common",
        "//third_party/bazel/src/java_tools/buildjar/java/com/google/devtools/build/buildjar/jarhelper",
        "@annex//:ch_epfl_scala_bsp4j",
        "@annex//:ch_epfl_scala_bloop_config_2_12",
        "@annex//:ch_epfl_scala_bloop_launcher_core_2_12",
        "@annex//:com_kohlschutter_junixsocket_junixsocket_core",
        "@annex//:org_scala_sbt_test_interface",
        "@annex//:org_scala_sbt_zinc_2_12",
        "@annex//:org_scala_lang_modules_scala_java8_compat_2_12",
        "@annex//:org_eclipse_xtext_org_eclipse_xtext_xbase_lib"
#        "@annex//:com_lihaoyi_ammonite_ops_2_12",
#        "@annex//:com_lihaoyi_upickle_2_12",
#        "@annex//:io_circe_circe_core_2_12",


    ],
)

scala_format_test(
    name = "format",
    srcs = glob(["**/*.scala"]),
)
