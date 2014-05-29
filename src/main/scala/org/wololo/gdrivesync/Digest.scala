package org.wololo.gdrivesync

import java.io.File
import java.security.MessageDigest
import javax.xml.bind.annotation.adapters.HexBinaryAdapter
import java.io.FileInputStream

object Digest {
	
  def hexMD5(file: File) = {
    val buffer = Array.ofDim[Byte](1024 * 8)
    val input = new FileInputStream(file)
    val messageDigest = MessageDigest.getInstance("MD5")

    var read = 0;
    do {
      read = input.read(buffer)
      if (read > 0) messageDigest.update(buffer, 0, read)
    } while (read != -1)
    input.close

    new HexBinaryAdapter().marshal(messageDigest.digest).toLowerCase
  }
}