package org.wololo.gdrivesync2

import java.io.FileInputStream
import scala.annotation.migration
import scala.collection.mutable.ListBuffer
import org.apache.commons.codec.digest.DigestUtils
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging
import com.google.api.client.http.GenericUrl
import java.io.BufferedInputStream
import java.io.FileOutputStream
import scala.io.Source
import java.nio.file.Files
import org.apache.commons.io.IOUtils
import com.google.api.client.http.InputStreamContent

object SyncFile {
  def allChildren(children: ListBuffer[SyncFile]): ListBuffer[SyncFile] = {
    if (children.size > 0) {
      children ++ allChildren(children.flatMap(child => child.children))
    } else {
      children
    }
  }
}

class SyncFile(val localFile: java.io.File, var driveFile: File, implicit val drive: Drive, implicit val localMetaStore: LocalMetaStore) extends LazyLogging {
  def id = driveFile.getId
  val path = localFile.getPath
  def localMD5 = DigestUtils.md5Hex(new FileInputStream(localFile))
  def isIdentical = localMD5 == driveFile.getMd5Checksum
  def isRemoteFolder = driveFile.getMimeType == "application/vnd.google-apps.folder"
  def existsRemotely = id != null

  val wasSynced = false

  val children = ListBuffer[SyncFile]()

  def allChildren = SyncFile.allChildren(children)

  def childAtPath(path: java.io.File) = allChildren.find(_.localFile == path)

  def sync = {
    logger.info("Begin sync of " + allChildren.size + " remote and/or local items")

    logger.info("Updating metastore for directories that already exists locally and remotely")
    allChildren
      .filter(file => !localMetaStore.contains(file.path) && file.localFile.isDirectory && file.existsRemotely && file.isRemoteFolder)
      .foreach(file => localMetaStore.add(file.path))
      
    logger.info("Updating metastore for identical files that already exists locally and remotely")
    allChildren
      .filter(file => !localMetaStore.contains(file.path) && !file.localFile.isDirectory && file.localFile.exists && file.existsRemotely && file.isIdentical)
      .foreach(file => localMetaStore.add(file.path))
      
    logger.info("Warn about not identical files that already exists locally and remotely and was not previously synced")
    allChildren
      .filter(file => !localMetaStore.contains(file.path) && !file.localFile.isDirectory && file.localFile.exists && file.existsRemotely && !file.isIdentical)
      .foreach(file => {
        logger.warn("WARNING: " + file.path + " exists locally and remotely, is not identical and was not previously synced. This file will be ignored.")
      })
      
    logger.info("Checkfor  metastore with existing directories")
    allChildren
      .filter(file => !localMetaStore.contains(file.path) && !file.localFile.isDirectory && file.localFile.exists && file.existsRemotely && file.isIdentical)
      .foreach(file => localMetaStore.add(file.path))
      
    logger.info("Updating metastore with existing directories")
    allChildren
      .filter(file => !localMetaStore.contains(file.path) && file.localFile.isDirectory)
    
    logger.info("Creating local folders that only exists remotely")
    allChildren
      .filter(file => file.isRemoteFolder && file.existsRemotely && !file.localFile.exists)
      .foreach(_.createLocalFolder)

    logger.info("Downloading files that only exists remotely")
    allChildren
      .filter(file => !file.isRemoteFolder &&
        file.existsRemotely &&
        file.driveFile.getDownloadUrl != null &&
        !file.localFile.exists &&
        !localMetaStore.contains(file.path))
      .foreach(_.download)

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
      .filter(file => file.localFile.exists &&
        !file.localFile.isDirectory &&
        file.existsRemotely &&
        !file.isIdentical &&
        file.localFile.lastModified > file.driveFile.getModifiedDate.getValue)
      .foreach(_.update)

    logger.info("Delete remote files previously synced but no longer existing locally")
    allChildren
      .filter(file =>
        !file.localFile.exists && file.existsRemotely && localMetaStore.contains(file.path))
      .foreach(file => {
        logger.debug("File " + file.path + " was previously synced but do no longer exist locally")
      })

    logger.info("Delete local files previously synced but no longer existing remotely")
    allChildren
      .filter(file =>
        file.localFile.exists && !file.existsRemotely && localMetaStore.contains(file.path))
      .foreach(_.deleteLocal)
      
    logger.info("Sync completed")
  }

  def createLocalFolder = {
    logger.info("Creating local folder at " + path)
    localFile.mkdir
    localMetaStore.add(path)
  }

  def createRemoteFolder = {
    logger.info("Creating remote folder for local path " + path)
    drive.files.insert(driveFile).execute
    localMetaStore.add(path)
  }

  def download = {
    logger.info("Downloading file " + path)
    val is = drive.getRequestFactory.buildGetRequest(new GenericUrl(driveFile.getDownloadUrl)).execute.getContent
    IOUtils.copy(is, new FileOutputStream(localFile))
    localMetaStore.add(path)
  }

  def upload = {
    logger.info("Uploading file " + path)
    //val mediaContent = new FileContent(driveFile.getMimeType, localFile)
    val mediaContent = new InputStreamContent(driveFile.getMimeType, new BufferedInputStream(new FileInputStream(localFile)))
    mediaContent.setLength(localFile.length)
    driveFile = drive.files.insert(driveFile, mediaContent).execute
    localMetaStore.add(path)
  }

  def update = {
    logger.info("Updating file " + path)
    //val mediaContent = new FileContent(driveFile.getMimeType, localFile)
    val mediaContent = new InputStreamContent(driveFile.getMimeType, new BufferedInputStream(new FileInputStream(localFile)))
    mediaContent.setLength(localFile.length)
    driveFile = drive.files.update(id, driveFile, mediaContent).execute
  }

  def deleteRemote = {
    logger.info("Deleting remote file " + path)
    drive.files.delete(id).execute
    localMetaStore.remove(path)
  }
  
  def deleteLocal = {
    logger.info("Deleting local file " + path)
    localFile.delete
    localMetaStore.remove(path)
  }
}