package code
package lib

import net.liftweb._
import actor._
import http._
import common._

import code.comet.DispatcherActor
import code.comet.NewGame
import code.comet.Ready

object GameFactory extends Logger {
  
  private var listeners: Map[String, DispatcherActor] = Map()
  
  def servers = listeners.toList.map(v => v._1)
  
  def dispatcherFor(digest: String) = listeners.get(digest) match {
    /* Return Failure if game is already full */
    case Some(s) if s.full_? => Failure("Game is full", Empty, Empty)
    
    /* Return requested DispatcherActor */
    case Some(s) => Full(s)

    /* Foo */
    case _ => Failure("Game %s does not exist".format(digest))
  }
  
  def createDispatcher(name: String) = synchronized {
    import java.security.MessageDigest
    val digest = MessageDigest.getInstance("MD5").digest(name.getBytes)
      .map(0xFF & _).map { "%02x".format(_) }.mkString
    if(listeners.contains(digest))
      Failure("Game %s already exists".format(name),Empty,Empty)
    else {
      for (
        s <- S.session ?~ "Dispatcher has to be created during a session"
      ) yield {
        info("create DispatcherActor for game " + name)
        val ret = new DispatcherActor(name, digest)
        s.sendCometActorMessage("NewGame", Empty, Ready(ret))
        listeners += digest -> ret
        ret
      }
    }
  }
  
  def deleteDispatcher(digest :String) = synchronized {
    listeners -= digest
  }
  
}