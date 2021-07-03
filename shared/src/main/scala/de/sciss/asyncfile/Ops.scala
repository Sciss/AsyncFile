/*
 *  Ops.scala
 *  (AsyncFile)
 *
 *  Copyright (c) 2020-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.asyncfile

import java.net.URI

object Ops {
  implicit final class URIOps(private val uri: URI) extends AnyVal {
    def path: String = uri.getPath

    def name: String = {
      val p = uri.normalize().getPath
      val j = if (p.endsWith("/")) p.length - 1 else p.length
      val i = p.lastIndexOf('/', j - 1) + 1
      p.substring(i, j)
    }

    def base: String = {
      val n = name
      val k = n.lastIndexOf('.')
      if (k < 0) n else n.substring(0, k)
    }

    def extL: String = {
      val n   = name
      val k   = n.lastIndexOf('.')
      val ext = if (k < 0) "" else n.substring(k + 1)
      // Locale.US -- not available on Scala.js ; rely on user setting JVM's locale appropriately...
      ext.toLowerCase()
    }

    private def parentPath(p: String): String = {
      val j = if (p.endsWith("/")) p.length - 1 else p.length
      val i = p.lastIndexOf('/', j - 1) + 1
      p.substring(0, i)
    }

    def parentOption: Option[URI] = {
      val p   = uri.normalize().getPath
      val pp  = parentPath(p)
      if (pp.isEmpty) None else {
        val scheme  = uri.getScheme
        val host    = uri.getHost
        Some(new URI(scheme, host, pp, null))
      }
    }

    def replaceExt(ext : String): URI = {
      val p       = uri.normalize().getPath
      val isDir   = p.endsWith("/")
      val pp      = parentPath(p)
      val extP    = if (ext.startsWith(".")) ext else "." + ext
      val extD    = if (isDir) extP + "/" else extP
      val nameD   = base + extD
      val pNew    = pp + nameD
      val scheme  = uri.getScheme
      val host    = uri.getHost
      new URI(scheme, host, pNew, null)
    }

    def replaceName(name: String): URI = {
      val p       = uri.normalize().getPath
      val isDir   = p.endsWith("/")
      val pp      = parentPath(p)
      val nameD   = if (isDir) name + "/" else name
      val pNew    = pp + nameD
      val scheme  = uri.getScheme
      val host    = uri.getHost
      new URI(scheme, host, pNew, null)
    }

    def / (sub: String): URI = {
      val parent0 = uri.normalize().getPath
      val parentS = if (parent0.isEmpty || parent0.endsWith("/")) parent0 else s"$parent0/"
      val path    = s"$parentS$sub"
      val scheme  = uri.getScheme
      val host    = uri.getHost
      new URI(scheme, host, path, null)
    }
  }
}
