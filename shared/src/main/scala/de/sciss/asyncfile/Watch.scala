package de.sciss.asyncfile

import java.net.URI

object Watch {
  sealed trait Base

  sealed trait Dir extends Base { def parent: URI; def child: String }
  case class ChildCreated (parent: URI, child: String) extends Dir
  case class ChildDeleted (parent: URI, child: String) extends Dir
  case class ChildModified(parent: URI, child: String) extends Dir

  sealed trait File extends Base { def uri: URI }
  case class Created  (uri: URI) extends File
  case class Deleted  (uri: URI) extends File
  case class Modified (uri: URI) extends File
}
