# AsyncFile

[![Build Status](https://travis-ci.org/Sciss/AsyncFile.svg?branch=main)](https://travis-ci.org/Sciss/AsyncFile)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/asyncfile_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/asyncfile_2.13)

## statement

AsyncFile is a Scala library to read and write files asynchronously with a common API both for the JVM and for
JavaScript (Scala.js). It is (C)opyright 2020 by Hanns Holger Rutz. All rights reserved.
This project is released under
the [GNU Affero General Public License](https://git.iem.at/sciss/AsyncFile/raw/main/LICENSE) v3+ and comes
with absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`.

__This project is still in experimental state!__

## requirements / installation

This project builds with sbt against Scala 2.13, 2.12, Dotty (JVM) and Scala 2.13 (JS).

To use the library in your project:

    "de.sciss" %% "asyncfile" % v

The current version `v` is `"0.1.2"`

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

## getting started

A good way to understand the library is to look at [AudioFile](https://github.com/Sciss/AudioFile/) 
which is another library that uses AsyncFile.

The idea is to use `java.net.URI` as the common path representation, and to support the `file` scheme on the JVM,
mapping to `java.nio.channels.AsynchronousFileChannel`, while introducing a new scheme `idb` for JS which is
a virtual file system backed by IndexedDB, thus supporting client-side Scala.js in the browser. New file systems
can be registered using `AsyncFile.addFileSystem`.

You obtain a file system for scheme by calling `AsyncFile.getFileSystemProvider(scheme)` where is `"file"` or `"idb"`,
for example. It is possible to register other file system providers. Once you have the file system, you can 
use—in a platform neutral way—`fs.openRead(uri)` and `fs.openWrite(uri)` to gain access to a file to be read or 
written.
 
You can also directly call `DesktopFile.openRead(file)` and `DesktopFile.openWrite(file)` on the desktop, and
in the browser, you can directly use `IndexedDBFile.openRead(uri)` and `IndexedDBFile.openWrite(uri)`.

## limitations

- `IndexedDBFileSystem` currently does not implement directory functionality, thus `.listDir`, `.mkDirs` etc.
  do not work and return failed futures.
- `IndexedDBFileSystem` has a fixed block size and does not yet use caching. Performance improvements are to be
  expected in future versions with larger block sizes and in-memory caching in place.
  
