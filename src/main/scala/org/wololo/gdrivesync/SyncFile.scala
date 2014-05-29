package org.wololo.gdrivesync

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import scala.collection.mutable.ListBuffer
import org.apache.commons.codec.digest.DigestUtils
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging
import com.google.api.services.drive.model.ParentReference
import scala.collection.JavaConverters._
import java.security.MessageDigest
import java.security.DigestInputStream
import java.io.ObjectInputStream
import org.apache.commons.codec.binary.Hex
import javax.xml.bind.annotation.adapters.HexBinaryAdapter
import java.io.InputStream
import java.io.OutputStream

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
  def isIdentical = localMD5 == driveFile.getMd5Checksum
  def isDirectory = localFile.isDirectory
  def isRemoteFolder = driveFile.getMimeType == "application/vnd.google-apps.folder"
  def exists = localFile.exists
  def existsRemotely = id != null
  def lastModified = localFile.lastModified
  def remoteLastModified = driveFile.getModifiedDate.getValue
  def localIsNewer = lastModified > remoteLastModified
  def mimeType = driveFile.getMimeType
  def downloadUrl = driveFile.getDownloadUrl
  def wasSynced = localMetaStore.contains(path)
  def addSynced = localMetaStore.add(path)
  def removeSynced = localMetaStore.remove(path)

  val buffer = Array.ofDim[Byte](1024 * 8)

  def localMD5 = {
    val fis = new FileInputStream(localFile)
    val md = MessageDigest.getInstance("MD5")

    var read = 0;
    do {
      read = fis.read(buffer)
      if (read > 0) md.update(buffer, 0, read)
    } while (read != -1)
    fis.close

    new HexBinaryAdapter().marshal(md.digest).toLowerCase
  }

  val children = ListBuffer[SyncFile]()

  def allChildren = SyncFile.allChildren(children)

  def childAtPath(path: java.io.File) = allChildren.find(_.localFile == path)

  def sync = {
    logger.info("Begin sync of " + allChildren.size + " remote and/or local items")

    logger.info("Updating metastore for directories that already exists locally and remotely")
    allChildren
      .filter(file => !file.wasSynced && file.isDirectory && file.existsRemotely && file.isRemoteFolder)
      .foreach(file => file.addSynced)

    logger.info("Updating metastore for identical files that already exists locally and remotely")
    allChildren
      .filter(file => !file.wasSynced && !file.isDirectory && file.exists && file.existsRemotely && file.isIdentical)
      .foreach(file => file.addSynced)

    logger.info("Warn about not identical files that already exists locally and remotely and was not previously synced")
    allChildren
      .filter(file => !file.wasSynced && !file.isDirectory && file.exists && file.existsRemotely && !file.isIdentical)
      .foreach(file => {
        logger.warn("WARNING: Ignoring " + file.path + " as it exists locally and remotely is not identical and was not previously synced")
      })

    logger.info("Creating local folders that only exists remotely")
    allChildren
      .filter(file => !file.wasSynced && file.isRemoteFolder && file.existsRemotely && !file.exists)
      .foreach(_.createLocalFolder)

    logger.info("Downloading files that only exists remotely")
    allChildren
      .filter(file => !file.isRemoteFolder &&
        file.existsRemotely &&
        file.driveFile.getDownloadUrl != null &&
        !file.exists &&
        !file.wasSynced)
      .foreach(_.download)

    logger.info("Creating remote folders that only exist locally")
    allChildren
      .filter(file => !file.wasSynced && file.isDirectory && !file.existsRemotely)
      .foreach(_.createRemoteFolder)

    logger.info("Upload files that only exist locally")
    allChildren
      .filter(file => !file.wasSynced && file.exists && !file.isDirectory && !file.existsRemotely)
      .foreach(_.upload)

    logger.info("Update files that are newer locally and not identical")
    allChildren
      .filter(file => file.wasSynced && file.exists &&
        !file.isDirectory &&
        file.existsRemotely &&
        !file.isIdentical &&
        file.localIsNewer)
      .foreach(_.update)

    logger.info("Delete remote files previously synced but no longer existing locally")
    allChildren
      .filter(file =>
        !file.exists && file.existsRemotely && file.wasSynced)
      .foreach(_.deleteRemote)

    logger.info("Delete local files previously synced but no longer existing remotely")
    allChildren
      .filter(file =>
        file.exists && !file.isDirectory && !file.existsRemotely && file.wasSynced)
      .foreach(_.deleteLocal)

    logger.info("Delete local directories previously synced but no longer existing remotely")
    allChildren
      .filter(file =>
        file.isDirectory && !file.existsRemotely && file.wasSynced)
      .foreach(_.deleteLocal)

    logger.info("Sync completed")
  }

  def createLocalFolder = {
    logger.info("Creating local folder at " + path)
    localFile.mkdir
    addSynced
  }

  def createRemoteFolder = {
    logger.info("Creating remote folder for local path " + path)
    driveFile = drive.files.insert(driveFile).execute
    children.foreach(_.driveFile.setParents(List(new ParentReference().setId(driveFile.getId())).asJava))
    addSynced
  }

  def download = {
    logger.info("Downloading file " + path)

    def copyLarge(input: InputStream, output: OutputStream) {
      var n = 0
      while (-1 != { n = input.read(buffer); n }) {
        output.write(buffer, 0, n)
      }
    }

    val is = drive.getRequestFactory.buildGetRequest(new GenericUrl(downloadUrl)).execute.getContent
    copyLarge(is, new FileOutputStream(localFile))
    addSynced
  }

  def mediaContent(length: Long) = new InputStreamContent(mimeType, new BufferedInputStream(new FileInputStream(localFile))).setLength(length)

  def upload = {
    logger.info("Uploading file " + path)
    driveFile = drive.files.insert(driveFile, mediaContent(localFile.length)).execute
    addSynced
  }

  def update = {
    logger.info("Updating file " + path)
    driveFile = drive.files.update(id, driveFile, mediaContent(localFile.length)).execute
  }

  def deleteRemote = {
    logger.info("Deleting remote file " + path)
    drive.files.delete(id).execute
    driveFile.setId(null)
    removeSynced
  }

  def deleteLocal = {
    logger.info("Deleting local file " + path)
    localFile.delete
    removeSynced
  }
}