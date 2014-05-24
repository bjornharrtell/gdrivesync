package org.wololo.gdrivesync2

import com.google.api.services.drive.model.File
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import scala.collection.mutable.ListBuffer

class SyncFile(var path: String, val driveFile: File) {

  val localFile = new java.io.File(path)

  def localMD5 = DigestUtils.md5Hex(new FileInputStream(localFile))
  def isIdentical = localMD5 == driveFile.getMd5Checksum()

  val children = ListBuffer[SyncFile]()
}