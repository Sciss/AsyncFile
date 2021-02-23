/*
 *  DesktopFileSystemProvider.scala
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

import scala.concurrent.{ExecutionContext, Future}

object DesktopFileSystemProvider extends AsyncFileSystemProvider {
  final val scheme  = "file"
  final val name    = "Desktop File System"

  def obtain()(implicit executionContext: ExecutionContext): Future[AsyncFileSystem] = {
    val fs = new DesktopFileSystem()
    Future.successful(fs)
  }
}
