



package org.wololo.gdrivesync2

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.http.HttpTransport
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import java.util.Collections
import com.google.api.services.drive.DriveScopes
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import java.io.InputStreamReader
import com.google.api.services.drive.Drive
import java.util.ArrayList
import com.google.api.services.drive.model.File

object GDriveSync2 extends App {
 val  DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".gdrivesync2")
  
  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val JSON_FACTORY = JacksonFactory.getDefaultInstance
  val dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
  
  val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY , new InputStreamReader(GDriveSync2.getClass().getResourceAsStream("/client_secrets.json")))
  val flow = new GoogleAuthorizationCodeFlow.Builder(
        httpTransport, JSON_FACTORY, clientSecrets,
        Collections.singleton(DriveScopes.DRIVE)).setDataStoreFactory(
        dataStoreFactory).build();
  
   val credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user")
   
   val builder = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
   builder.setApplicationName("GDriveSync");
   val drive = builder.build()
   
   
   val request = drive.files().list()
   val result = new ArrayList[File]()
   
   do {
     val files = request.execute()
     result.addAll(files.getItems())
   } while (request.getPageToken() != null && request.getPageToken().length() > 0)

   println(result.get(0).getTitle())
}