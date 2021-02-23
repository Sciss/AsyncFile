/*
 *  Exceptions.scala
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

import java.io.IOException
import java.net.URI

final class FileNotFoundException         (uri: URI) extends IOException(s"Not found: $uri")
final class UnsupportedFileSystemException(uri: URI) extends IOException(s"Unsupported file system: $uri")
