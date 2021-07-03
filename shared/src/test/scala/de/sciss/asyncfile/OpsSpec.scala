package de.sciss.asyncfile

import java.net.URI

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OpsSpec extends AnyFlatSpec with Matchers {
  "URI ops" should "work as expected" in {
    val uriDir = new URI("file:/home/pi/")
    import Ops._
    assert(uriDir.path === "/home/pi/")
    assert(uriDir.base === "pi")
    assert(uriDir.name === "pi")
    assert(uriDir.extL === "")
    val parentOpt = uriDir.parentOption
    assert(parentOpt  === Some(new URI("file:/home/")))
    val parent = parentOpt.get
    val parentParentOpt = parent.parentOption
    assert(parentParentOpt === Some(new URI("file:/")))
    val parentParent = parentParentOpt.get
    val parentParentParentOpt = parentParent.parentOption
    assert(parentParentParentOpt === None)
    assert(uriDir.replaceName("foo") === new URI("file:/home/foo/"))
    val child1 = uriDir / "foo.mllt"
    val child2 = uriDir / "foo.mllt/"
    assert(child1 === new URI("file:/home/pi/foo.mllt"))
    assert(child2 === new URI("file:/home/pi/foo.mllt/"))
    val repl1 = child1.replaceExt("JPG")
    assert(repl1 === new URI("file:/home/pi/foo.JPG"))
    assert(repl1.extL === "jpg")
    val repl2 = child2.replaceExt("JPG")
    assert(repl2 === new URI("file:/home/pi/foo.JPG/"))
    assert(repl2.extL === "jpg")
    // issue #1
    val child = uriDir / "tmp[1]"
    assert (child.parentOption === Some(uriDir))
    assert (child.base === "tmp[1]")
  }
}
