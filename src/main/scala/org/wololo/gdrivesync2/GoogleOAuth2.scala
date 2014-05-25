package org.wololo.gdrivesync2

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.mutable.ListBuffer

import Globals._
import com.google.api.client.util.store.FileDataStoreFactory
import java.io.InputStreamReader
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.services.drive.DriveScopes
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.typesafe.scalalogging.slf4j.LazyLogging
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.services.drive.DriveScopes

object GoogleOAuth2 extends LazyLogging {
	def authorize = {
	  val dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

	  val clientSecretsStream = GDriveSync2.getClass.getResourceAsStream("/client_secrets.json")
	  val clientSecrets = GoogleClientSecrets
	  	.load(JSON_FACTORY, new InputStreamReader(clientSecretsStream))
	  
	  val flow = new GoogleAuthorizationCodeFlow
	  	.Builder(httpTransport, JSON_FACTORY, clientSecrets, List(DriveScopes.DRIVE))
	  	.setDataStoreFactory(dataStoreFactory)
	  	.build
	
	  val credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
	  	.authorize("user")
	  	
	  logger.debug("Authorized with Google Drive: " + credential.getAccessToken())
	  
	  credential
	}
}