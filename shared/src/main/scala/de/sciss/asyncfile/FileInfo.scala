/*
 *  FileInfo.scala
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

import de.sciss.asyncfile.FileInfo._

object FileInfo {
  final val IS_FILE       = 0x01
  final val IS_DIRECTORY  = 0x02
  final val IS_HIDDEN     = 0x04
  final val CAN_READ      = 0x08
  final val CAN_WRITE     = 0x10
  final val CAN_EXECUTE   = 0x20
}
final case class FileInfo(uri: URI, flags: Int, lastModified: Long, size: Long) {
  def isFile      : Boolean = (flags & IS_FILE      ) != 0
  def isDirectory : Boolean = (flags & IS_DIRECTORY ) != 0
  def isHidden    : Boolean = (flags & IS_HIDDEN    ) != 0
  def canRead     : Boolean = (flags & CAN_READ     ) != 0
  def canWrite    : Boolean = (flags & CAN_WRITE    ) != 0
  def canExecute  : Boolean = (flags & CAN_EXECUTE  ) != 0
}