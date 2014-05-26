package org.wololo.gdrivesync2

import java.io.FileInputStream

import scala.collection.mutable.ListBuffer

import org.apache.commons.codec.digest.DigestUtils

import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging

object SyncFile {
  def allChildren(children: ListBuffer[SyncFile]) : ListBuffer[SyncFile] = {
	if (children.size > 0) {
	  children ++ allChildren(children.flatMap(child => child.children))
	} else {
	  children
	}
  }
}

class SyncFile(val localFile: java.io.File, var driveFile: File) extends LazyLogging {
  val path = localFile.getPath()
  def localMD5 = DigestUtils.md5Hex(new FileInputStream(localFile))
  def isIdentical = localMD5 == driveFile.getMd5Checksum()
  def isRemoteFolder = driveFile.getMimeType == "application/vnd.google-apps.folder"

  val wasSynced = false
  
  val children = ListBuffer[SyncFile]()
  
  def allChildren = SyncFile.allChildren(children)
  
  def childAtPath(path: java.io.File) = allChildren.find(_.localFile == path)
  
  def sync = {
    logger.debug("All children: " + SyncFile.allChildren(children).size)
    SyncFile.allChildren(children).filter(_.isRemoteFolder).filterNot(_.localFile.exists()).foreach(_.createLocalFolder)
  }
  
  def createLocalFolder = {
    logger.debug("Creating local folder at: " + localFile.getPath())
    localFile.mkdir()
  }
}