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

import Globals._

object GDriveSync2 extends App with LazyLogging {
  
  
  if (SYNC_STORE_DIR.exists()) {
    val credential = GoogleOAuth2.authorize
    val drive = new DriveMetaFetcher(credential)
    val root = drive.fetchRoot
    root.sync
  } else {
    logger.error("Destination sync directory does not exists, aborting and clearing metadata.")
  }
  
  // TODO: implement local metastore, local filesystem meta recurse, sync
}