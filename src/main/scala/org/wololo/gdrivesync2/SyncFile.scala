package org.wololo.gdrivesync2

import com.google.api.services.drive.model.File
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream

class SyncFile(val localFile: java.io.File, val driveFile: File) {
  
  def localMD5 = DigestUtils.md5Hex(new FileInputStream(localFile))
  def isIdentical = localMD5 == driveFile.getMd5Checksum()
  
  
}