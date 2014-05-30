package org.wololo.gdrivesync

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.ListBuffer

import com.google.api.client.googleapis.media.MediaHttpDownloader
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.ParentReference
import com.typesafe.scalalogging.slf4j.LazyLogging

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
  def localMD5 = Digest.hexMD5(localFile)

  val children = ListBuffer[SyncFile]()
  def allChildren = SyncFile.allChildren(children)
  def childAtPath(path: java.io.File) = allChildren.find(_.localFile == path)

  def isIdentical = localMD5 == driveFile.getMd5Checksum
  def isDirectory = localFile.isDirectory
  def isRemoteFolder = driveFile.getMimeType == "application/vnd.google-apps.folder"
  def exists = localFile.exists
  def existsRemotely = id != null
  def lastModified = localFile.lastModified
  def remoteLastModified = driveFile.getModifiedDate.getValue
  def localIsNewer = lastModified > remoteLastModified
  def remoteIsNewer = lastModified < remoteLastModified
  def mimeType = driveFile.getMimeType
  def downloadUrl = driveFile.getDownloadUrl
  def canBeDownloaded = downloadUrl != null

  def wasSynced = localMetaStore.contains(path)
  def addSynced = localMetaStore.add(path)
  def removeSynced = localMetaStore.remove(path)

  def unsyncedIdenticalDir = !wasSynced && isDirectory && existsRemotely && isRemoteFolder
  def unsyncedIdenticalFile = !wasSynced && !isDirectory && exists && existsRemotely && isIdentical
  def unsyncedNotIdentical = !wasSynced && !isDirectory && exists && existsRemotely && isIdentical
  def unsyncedRemoteDir = !wasSynced && isRemoteFolder && existsRemotely && !exists
  def unsyncedRemoteFile = !wasSynced && !isRemoteFolder && existsRemotely && canBeDownloaded && !exists
  def unsyncedLocalDir = !wasSynced && isDirectory && !existsRemotely
  def unsyncedLocalFile = !wasSynced && exists && !isDirectory && !existsRemotely
  def syncedRemoteNewerFile = wasSynced && !isRemoteFolder && existsRemotely && canBeDownloaded && exists && !isDirectory && remoteIsNewer && !isIdentical
  def syncedLocalNewer = wasSynced && exists && !isDirectory && existsRemotely && !isIdentical && localIsNewer
  def syncedOnlyRemoteFile = wasSynced && !exists && existsRemotely  && !isRemoteFolder
  def syncedOnlyRemoteDir = wasSynced && !exists && existsRemotely && isRemoteFolder
  def syncedOnlyLocalFile = wasSynced && exists && !isDirectory && !existsRemotely
  def syncedOnlyLocalDir = wasSynced && isDirectory && !existsRemotely

  def warnIgnored = logger.warn("WARNING: Ignoring " + path + " as it exists locally and remotely is not identical and was not previously synced")

  def sync = {
    logger.info("Begin sync of " + allChildren.size + " remote and/or local items")

    logger.info("Determine directories that already exists locally and remotely")
    allChildren.filter(_.unsyncedIdenticalDir).foreach(file => file.addSynced)

    logger.info("Determine identical files that already exists locally and remotely")
    allChildren.filter(_.unsyncedIdenticalFile).foreach(_.addSynced)

    logger.info("Determine not identical files that already exists locally and remotely and was not previously synced")
    allChildren.filter(_.unsyncedNotIdentical).foreach(_.warnIgnored)

    logger.info("Creating local folders that only exists remotely")
    allChildren.filter(_.unsyncedRemoteDir).foreach(_.createLocalFolder)

    logger.info("Determine unsynced remote files")
    allChildren.filter(_.unsyncedRemoteFile).foreach(_.download)

    logger.info("Determine newer remote files")
    allChildren.filter(_.syncedRemoteNewerFile).foreach(_.download)

    logger.info("Determine unsynced local folders")
    allChildren.filter(_.unsyncedLocalDir).foreach(_.createRemoteFolder)

    logger.info("Determine unsynced local files")
    allChildren.filter(_.unsyncedLocalFile).foreach(_.upload)

    logger.info("Determine newer local files")
    allChildren.filter(_.syncedLocalNewer).foreach(_.update)

    // NOTE: do not delete folders first, as they could contain unsyncable stuff
    logger.info("Determine files to be remotely deleted")
    allChildren.filter(_.syncedOnlyRemoteFile).foreach(_.deleteRemote)
    
    logger.info("Determine folders to be remotely deleted")
    allChildren.filter(_.syncedOnlyRemoteDir).foreach(_.deleteRemote)

    logger.info("Determine files to be locally deleted")
    allChildren.filter(_.syncedOnlyLocalFile).foreach(_.deleteLocal)

    logger.info("Determine folders to be locally deleted")
    allChildren.filter(_.syncedOnlyLocalDir).foreach(_.deleteLocal)

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
    if (exists) {
      localFile.delete
    }
    val requestFactory = drive.getRequestFactory
    val downloader = new MediaHttpDownloader(requestFactory.getTransport, requestFactory.getInitializer)
    downloader.setProgressListener(new MediaHttpDownloaderProgressListener() {
      def progressChanged(downloader: MediaHttpDownloader) {
        logger.info("Downloaded " + math.round(downloader.getProgress * 100) + "%")
      }
    })
    downloader.download(new GenericUrl(downloadUrl), new FileOutputStream(localFile))
    localFile.setLastModified(remoteLastModified)
    addSynced
  }

  def mediaContent(length: Long) = new InputStreamContent(mimeType, new BufferedInputStream(new FileInputStream(localFile))).setLength(length)

  def upload = {
    logger.info("Uploading file " + path)
    var request = drive.files.insert(driveFile, mediaContent(localFile.length))
    request.getMediaHttpUploader.setProgressListener(new MediaHttpUploaderProgressListener() {
      def progressChanged(uploader: MediaHttpUploader) {
        if (uploader.getUploadState != UploadState.INITIATION_STARTED) {
          logger.info("Uploaded " + math.round(uploader.getProgress * 100) + "%")
        }
      }
    })
    driveFile = request.execute
    addSynced
  }

  def update = {
    logger.info("Updating file " + path)
    driveFile = drive.files.update(id, driveFile, mediaContent(localFile.length)).execute
  }

  def deleteRemote = {
    def delete = {
      drive.files.delete(id).execute
      driveFile.setId(null)
      removeSynced
    }
    if (isRemoteFolder) {
      // NOTE: make sure to check if folder is empty before deleting it
      if (new DriveMetaFetcher().fetchChildren(this).length == 0) {
        logger.info("Deleting remote dir " + path)
        delete
      } else {
        logger.warn("Ignoring non-empty remote dir " + path)
      }
    } else {
      logger.info("Deleting remote file " + path)
      delete
    }
  }

  def deleteLocal = {
    logger.info("Deleting local file " + path)
    localFile.delete
    removeSynced
  }
}