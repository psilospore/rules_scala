package higherkindness.rules_scala
package workers.bloop.compile

import workers.common.{AnnexLogger, CommonArguments}
import common.worker.WorkerMain
import bloop.Bloop
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.inf.Namespace

object BloopRunner extends WorkerMain[Namespace] {
  override def init(args: Option[Array[String]]): Unit = {
    val parser = ArgumentParsers.newFor("bloop-worker").addHelp(true).build
    parser.addArgument("--someBoolFlag").`type`(Arg.booleanType)
    parser.parseArgsOrFail(args.getOrElse(Array.empty))
  }
  override def work(worker: Namespace, args: Array[String]): Unit = {
    val parser = ArgumentParsers.newFor("bloop").addHelp(true).defaultFormatWidth(80).fromFilePrefix("@").build()
    val logger = new AnnexLogger(namespace.getString("log_level"))
    CommonArguments.add(parser)
    val namespace = parser.parseArgsOrFail(args)

    Bloop
  }
}
