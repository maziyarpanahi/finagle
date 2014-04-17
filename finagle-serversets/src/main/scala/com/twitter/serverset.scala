package com.twitter

import com.twitter.app.GlobalFlag
import com.twitter.finagle.serverset2.Zk2Resolver
import com.twitter.finagle.zookeeper.ZkResolver
import com.twitter.finagle.{Addr, NameTree, Namer, Resolver, Path, WeightedInetSocketAddress, Name}
import com.twitter.util.Activity
import java.net.InetSocketAddress

object newZk extends GlobalFlag(
  false, 
  "Use the new ZooKeeper implementation "+
  "for /$/com.twitter.serverset")

/**
 * The serverset namer takes paths of the form
 *
 *    hosts/path...
 *
 * and returns a dynamic name representing its resolution into a tree
 * of /$/inet names.
 */
class serverset extends Namer {
  private[this] val whichZk = if (newZk()) "zk2" else "zk"

  // We have to involve a serverset roundtrip here to return a tree.
  // We risk invalidate an otherwise valid tree when there is a bad
  // serverset on an Alt branch that would never be taken. A
  // potential solution to this conundrum is to introduce some form
  // of lazy evaluation of name trees.
  def lookup(path: Path): Activity[NameTree[Name]] = path match {
    case Path.Utf8(hosts, rest@_*) =>
      val name@Name.Bound(va) = Resolver.eval("%s!%s!/%s".format(whichZk, hosts, rest mkString "/"))

      // We have to bind the name ourselves in order to know whether
      // it resolves negatively.
      Activity(va map {
        case Addr.Bound(_) => Activity.Ok(NameTree.Leaf(name))
        case Addr.Neg => Activity.Ok(NameTree.Neg)
        case Addr.Pending => Activity.Pending
        case Addr.Failed(exc) => Activity.Failed(exc)
      })

    case _ =>
      Activity.exception(new Exception("Invalid com.twitter.namer path "+path.show))
  }
}
