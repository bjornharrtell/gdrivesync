package org.wololo.gdrivesync2

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable.ListBuffer

import com.google.api.client.auth.oauth2.Credential
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging

import Globals.JSON_FACTORY
import Globals.httpTransport

class DriveMetaFetcher(credential: Credential) extends LazyLogging {
  
  val builder = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
  builder.setApplicationName("GDriveSync")
  val drive = builder.build
  
  def fetchRoot = {
    val driveRootId = drive.about.get.execute.getRootFolderId
    logger.debug("Found root: " + driveRootId)
    val driveRoot = new File
    driveRoot.setId(driveRootId)
    var root = new SyncFile("", driveRoot)
    fetchChildren(root)
  }

  def fetchChildren(root: SyncFile) = {
    val request = drive.files.list
    request.setMaxResults(200)
    val result = ListBuffer[File]()
    do {
      val files = request.execute
      val items = files.getItems
      logger.debug("Fetched items: " + items.length)
      result ++= items
      request.setPageToken(files.getNextPageToken)
    } while (request.getPageToken != null && request.getPageToken.length > 0)

    logger.debug("Fetched " + result.size + " from Google Drive API")

    var notOwned = result filter { _.getOwners.toList exists { !_.getIsAuthenticatedUser } }
    logger.debug("Found " + notOwned.size + " items not owned by you")
    logger.debug("Ignoring items not owned by you")
    result --= notOwned

    var trashed = result filter { _.getExplicitlyTrashed() != null }
    logger.debug("Found " + trashed.size + " trashed items")
    logger.debug("Ignoring trashed items")
    result --= trashed

    var multipleParents = result filter { _.getParents.length > 1 }
    logger.debug("Found " + multipleParents.size + " items with multiple parents")
    logger.debug("Ignoring items with multiple parents")
    result --= multipleParents

    var noParents = result filter { _.getParents.length == 0 }
    logger.debug(noParents.size + " items with no parents")
    logger.debug("Ignoring items with no parents")
    result --= noParents

    def findChildren(folder: SyncFile) {
      logger.debug("Searching for children path: " + folder.path)
      folder.children ++= result.filter(_.getParents().get(0).getId() == folder.driveFile.getId()).map(file => {
        val isFolder = file.getMimeType == "application/vnd.google-apps.folder"
        val path = folder.path + file.getTitle + (if (isFolder) "/" else "")
        logger.debug("Found child with path: " + path)
        val syncFile = new SyncFile(path, file)
        if (isFolder) findChildren(syncFile)
        syncFile
      })
    }
    findChildren(root)

    root
  }
}