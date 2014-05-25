package org.wololo.gdrivesync2

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory

object Globals {
  val DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".gdrivesync2")
  var SYNC_STORE_DIR = new java.io.File("./gdrive/") 

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val JSON_FACTORY = JacksonFactory.getDefaultInstance
}