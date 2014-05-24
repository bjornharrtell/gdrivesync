package org.wololo.gdrivesync2

import java.io.InputStreamReader

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.mutable.ListBuffer

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.typesafe.scalalogging.slf4j.LazyLogging

object GDriveSync2 extends App with LazyLogging {
  val DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".gdrivesync2")

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val JSON_FACTORY = JacksonFactory.getDefaultInstance
  val dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

  val clientSecretsStream = GDriveSync2.getClass.getResourceAsStream("/client_secrets.json")
  val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(clientSecretsStream))
  val flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, List(DriveScopes.DRIVE))
  	.setDataStoreFactory(dataStoreFactory)
  	.build

  val credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
  	.authorize("user")
  logger.debug("Authorized with Google Drive: " + credential.getAccessToken())

  val builder = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
  builder.setApplicationName("GDriveSync")
  val drive = builder.build

  val driveRootId = drive.about.get.execute.getRootFolderId
  val driveRoot = new File
  driveRoot.setId(driveRootId)
  val root = new SyncFile("", driveRoot)
  logger.debug("Found root: " + driveRootId)

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

  var multipleParents = result filter {_.getParents.length > 1 }
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

  // TODO: implement local metastore, local filesystem meta recurse, sync
}