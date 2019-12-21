package higherkindness.rules_scala
package workers.zinc.compile

import workers.common.AnnexLogger
import workers.common.AnnexScalaInstance
import workers.common.CommonArguments
import workers.common.FileUtil
import workers.common.LoggedReporter
import common.worker.WorkerMain
import com.google.devtools.build.buildjar.jarhelper.JarCreator
import java.io.{File, InputStream, PrintWriter}
import java.nio.file.{FileSystems, Files, NoSuchFileException, Path, Paths}
import java.util.concurrent.Executors
import java.util.{Optional, Properties, List => JList}

import bloop.bloopgun.core.Shell
import bloop.config.Config.Scala
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.impl.{Arguments => Arg}
import net.sourceforge.argparse4j.inf.Namespace
import sbt.internal.inc.classpath.ClassLoaderCache

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

import xsbti.compile.CompilerCache

import ch.epfl.scala.bsp4j._
import bloop.config.ConfigEncoderDecoders._
import bloop.config.{Config => BloopConfig}
import bloop.launcher.LauncherStatus.SuccessfulRun
import bloop.launcher.{Launcher => BloopLauncher}
import bloop.launcher.bsp.BspBridge
import org.eclipse.lsp4j.jsonrpc.{Launcher => LspLauncher}

import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Can't figure out how to invoke BloopRunner so I'm just gonna rip this up and replace it then figure that part out later
 * `bazel run //hello:hello_run --worker_extra_flag=ScalaCompile=--persistence_dir=.bazel-zinc`
 */
object ZincRunner extends WorkerMain[Namespace] {

  trait BloopServer extends BuildServer with ScalaBuildServer

  //At the moment just print results
  val printClient = new BuildClient {
    override def onBuildShowMessage(params: ShowMessageParams): Unit = println("onBuildShowMessage", params)

    override def onBuildLogMessage(params: LogMessageParams): Unit = println("onBuildLogMessage", params)

    override def onBuildTaskStart(params: TaskStartParams): Unit = println("onBuildTaskStart", params)

    override def onBuildTaskProgress(params: TaskProgressParams): Unit = println("onBuildTaskProgress", params)

    //TODO handle this probably contains class files and I might write it to .bloop/out/classes/ then other bazel targets can point to this if this is a dep
    override def onBuildTaskFinish(params: TaskFinishParams): Unit = {
      println("onBuildTaskFinish", params)
    }

    override def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = println("onBuildPublishDiagnostics", params)

    override def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = println("onBuildTargetDidChange", params)
  }


  private[this] val classloaderCache = new ClassLoaderCache(null)

  private[this] val compilerCache = CompilerCache.fresh

  // prevents GC of the soft reference in classloaderCache
  private[this] var lastCompiler: AnyRef = null

  private[this] def labelToPath(label: String) = Paths.get(label.replaceAll("^/+", "").replaceAll(raw"[^\w/]", "/"))

  private[this] var bloopServer: BloopServer = null

  protected[this] def init(args: Option[Array[String]]) = {
    val parser = ArgumentParsers.newFor("zinc-worker").addHelp(true).build
    parser.addArgument("--persistence_dir", /* deprecated */ "--persistenceDir").metavar("path")
    parser.addArgument("--use_persistence").`type`(Arg.booleanType)
    // deprecated
    parser.addArgument("--max_errors")

    val emptyInputStream = new InputStream() {
      override def read(): Int = -1
    }

    val dir = Files.createTempDirectory(s"bsp-launcher")
    val bspBridge = new BspBridge(
      emptyInputStream,
      System.out,
      Promise[Unit](),
      System.out,
      Shell.default,
      dir
    )

    //TODO move to init we get a work request in sequence A, B, C, C_run
    BloopLauncher.connectToBloopBspServer("1.1.2", false, bspBridge, List()) match {
      case Right(Right(Some(socket))) => {
        val es = Executors.newCachedThreadPool()

        val launcher = new LspLauncher.Builder[BloopServer]()
          .setRemoteInterface(classOf[BloopServer])
          .setExecutorService(es)
          .setInput(socket.getInputStream)
          .setOutput(socket.getOutputStream)
          .setLocalService(printClient)
          .create()

        launcher.startListening()
        bloopServer = launcher.getRemoteProxy

        printClient.onConnectWithServer(bloopServer)

        println("attempting build initialize")

        val initBuildParams = new InitializeBuildParams(
          "bsp",
          "1.3.4",
          "2.0",
          s"file:///Users/syedajafri/dev/bazelExample", //TODO don't hardcode
          new BuildClientCapabilities(List("scala").asJava)
        )

        bloopServer.buildInitialize(initBuildParams).toScala.foreach(initializeResults => {
          println(s"initialized: Results $initializeResults")
          bloopServer.onBuildInitialized()
        })
      }
    }
    Thread.sleep(1000)

    parser.parseArgsOrFail(args.getOrElse(Array.empty))

  }

  protected[this] def work(worker: Namespace, args: Array[String]) = {
    val parser = ArgumentParsers.newFor("zinc").addHelp(true).defaultFormatWidth(80).fromFilePrefix("@").build()
    CommonArguments.add(parser)
    val namespace = parser.parseArgsOrFail(args)

    val logger = new AnnexLogger(namespace.getString("log_level"))

    logger.error(() => s"hii: $worker args ${args.toList}")

    //TODO figure out how to pass workspace dir from bazel
    val workspaceDir = namespace.get[File]("workspace_dir").toPath.toAbsolutePath
    val projectDir = Paths.get(namespace.get[File]("build_file_path")
      .toPath.toAbsolutePath
      .toString.replace("BUILD", ""))

    val label = namespace.getString("label")
    val srcs = namespace.getList[File]("sources").asScala.toList.map(_.toPath)

    println(workspaceDir, label, srcs)


    //TODO could do query to find deps? Actually maybe that is passed in with depset?
    //Make sure there is an open BSP connection with the Bloop server. Otherwise use Bloop Launcher

    val projectName = label.replaceAll("^/+", "")

    val bloopDir = workspaceDir.resolve(".bloop").toAbsolutePath
    val bloopOutDir = bloopDir.resolve("out").toAbsolutePath
    val projectOutDir = bloopOutDir.resolve(projectName).toAbsolutePath
    val projectClassesDir = projectOutDir.resolve("classes").toAbsolutePath
    Files.createDirectories(projectClassesDir)


    val bloopConfigPath = bloopDir.resolve(s"$projectName.json")

    println("analysis", namespace.getList[JList[String]]("analysis"))
    val scalaJars = namespace.getList[File]("compiler_classpath").asScala.map(_.toPath.toAbsolutePath).toList
    val bloopConfig = BloopConfig.File(
      version = BloopConfig.File.LatestVersion,
      project = BloopConfig.Project(
        name = projectName,
        directory = projectDir,
        sources = srcs,
        dependencies = List(), //Similar logic as in ZincRunner I think
        classpath = scalaJars, //TODO Add classpath of deps. need to filter this but how do I know?
        out = projectOutDir,
        classesDir = projectClassesDir,
        resources = None,
        `scala` = Some(Scala("org.scala-lang", "scala-compiler", "2.12.18", List(), scalaJars, None, None)),
        java = None,
        sbt = None,
        test = None,
        platform = None,
        resolution = None
      )
    )

    println(bloop.config.toStr(bloopConfig))
    println(bloopConfigPath.toAbsolutePath)

    Files.write(bloopConfigPath, bloop.config.toStr(bloopConfig).getBytes)

    Thread.sleep(1000)

    val buildTargetId = List(new BuildTargetIdentifier(s"file:///Users/syedajafri/dev/bazelExample?id=$projectName"))
    val compileParams = new CompileParams(buildTargetId.asJava)

    bloopServer.buildTargetCompile(compileParams).toScala.onComplete(cr => println(s"Compiled $projectName! $cr")) //TODO data is null here

  }

}