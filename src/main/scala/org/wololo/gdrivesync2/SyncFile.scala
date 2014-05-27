package org.wololo.gdrivesync2

import java.io.FileInputStream
import scala.collection.mutable.ListBuffer
import org.apache.commons.codec.digest.DigestUtils
import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging
import com.google.api.client.http.FileContent

object SyncFile {
  def allChildren(children: ListBuffer[SyncFile]): ListBuffer[SyncFile] = {
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
  def existsRemotely = driveFile.getId() != null

  val wasSynced = false

  val children = ListBuffer[SyncFile]()

  def allChildren = SyncFile.allChildren(children)

  def childAtPath(path: java.io.File) = allChildren.find(_.localFile == path)

  def sync = {
    logger.info("Begin sync of " + allChildren.size + " remote and/or local items")
    
    logger.info("Creating local folders that only exists remotely")
    allChildren
      .filter(file => file.isRemoteFolder && file.existsRemotely)
      .filterNot(_.localFile.exists())
      .foreach(_.createLocalFolder)
    
    logger.info("Creating remote folders that only exist locally")
    allChildren
      .filter(file => file.localFile.isDirectory && !file.existsRemotely)
      .foreach(_.createRemoteFolder)
    
    logger.info("Upload files that only exist locally")
    allChildren
      .filter(file => file.localFile.exists && !file.localFile.isDirectory && !file.existsRemotely)
      .foreach(_.upload)
      
    logger.info("Update files that are newer locally and not identical")
    allChildren
      .filter(file =>
        file.localFile.exists &&
        !file.localFile.isDirectory &&
        file.existsRemotely &&
        !file.isIdentical &&
        file.localFile.lastModified > file.driveFile.getModifiedDate.getValue)
      .foreach(_.update)
  }

  def createLocalFolder = {
    logger.info("Creating local folder at " + path)
    localFile.mkdir
  }
  
  def createRemoteFolder = {
	logger.info("Creating remote folder for local path " + path)
	GDriveSync2.drive.files.insert(driveFile).execute
  }
  
  def upload = {
    logger.info("Uploading file " + localFile.getPath)
    val mediaContent = new FileContent(driveFile.getMimeType, localFile)
    GDriveSync2.drive.files.insert(driveFile, mediaContent).execute
  }
  
  def update = {
    logger.info("Updating file " + localFile.getPath)
    val mediaContent = new FileContent(driveFile.getMimeType, localFile)
    GDriveSync2.drive.files.update(driveFile.getId, driveFile, mediaContent).execute
  }
}