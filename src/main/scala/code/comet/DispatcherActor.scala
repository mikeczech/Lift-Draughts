package code
package comet

import net.liftweb._ 
import http._ 
import actor._
import util._ 
import Helpers._
import js._
import JE._
import JsCmds._
import common._
import code.lib.GameFactory

import scala.collection.mutable.Map

import de.upb.swt.swtpra2011.server.ServerFactory
import de.upb.swt.swtpra2011.server.control.GameControl
import de.upb.swt.swtpra2011.server.model._
import de.upb.swt.swtpra2011.common.IServer
import java.beans.{PropertyChangeListener, PropertyChangeEvent}

case class Fieldsize(val w : Int, val h : Int) {
  override def toString() = w + "x" + h
}

case class RegisterCometActor(actor: CometActor)
case class UnregisterCometActor(actor: CometActor)
case class UpdateFieldsize(w : Int, h : Int)
case class StartGame(w: Int, h: Int, rows: Int)
case class SetPlayers(whitePlayer: Box[String], blackPlayer: Box[String])

case class PlayerData(name: String, val instance: Player)

trait SignInListener extends PropertyChangeListener {
  def propertyChange(evt :PropertyChangeEvent) = {
    evt.getPropertyName() match {
      case ListOfPlayers.PROPERTY_PLAYER_ADD =>
        addPlayer(evt.getNewValue().asInstanceOf[Player])
      case ListOfPlayers.PROPERTY_PLAYER_REMOVE =>
        removePlayer(evt.getNewValue().asInstanceOf[Player])
    }
  }
  def addPlayer(p: Player): Unit
  def removePlayer(p: Player): Unit
}

case class PlayerAdded(pdata: List[PlayerData])
case class PlayerRemoved(pdata: List[PlayerData])
object GameStarted
object Close

class DispatcherActor(val name: String, val digest: String) 
extends LiftActor with Logger with SignInListener {
  
  private val clientControl = ServerFactory.createServer(this)
  private var gameControl: Box[GameControl] = Empty 
  
  private var cometActorsToUpdate: List[CometActor] = List()  
  private var invitedPlayers: Map[String, Player] = Map()
  private var registeredWith: Box[NewGame] = Empty
  
  private var _fieldsize = Fieldsize(6,7)
  private var _rows = 1
  private var gameStarted = false
  private var black: Box[Player] = Empty
  private var white: Box[Player] = Empty

  val url = "127.0.0.1"
  val id = "de.upb.swt.swtpra2011.common.IServer"
  
  implicit def toPlayerData(p: Player) = PlayerData(p.getIPlayer().getName(), p)
  
  def contains(cactor: CometActor) = cometActorsToUpdate.contains(cactor)
  
  def full_? = invitedPlayers.size == 2
  
  def gameStarted_? = gameStarted
  
  def fieldsize = _fieldsize
  
  def rows = _rows
  
  def addPlayer(p :Player) = synchronized {
    invitedPlayers += p.getIPlayer().getName() -> p
    if(white.isEmpty) white = Full(p)
    else if(black.isEmpty) black = Full(p)
    for(a <- registeredWith) a ! PlayerArrived(p.name)
  }
  
  def removePlayer(p :Player) = synchronized {
    invitedPlayers.remove(p.getIPlayer().getName())
    val color = if(Full(p) == white) { 
      white = Empty
      "white"
    } else {
      black = Empty
      "black"
    }
    for(a <- registeredWith) a ! PlayerLeaved(p.name, color)
  }

  override def messageHandler = {
    
    case newGameActor: NewGame => 
      registeredWith = Full(newGameActor)
    
    case RegisterCometActor(actor) => {
      if(!cometActorsToUpdate.contains(actor)) {
        info("We are adding actor %s to the list".format(actor))
        cometActorsToUpdate = actor :: cometActorsToUpdate
        reply(clientControl.getPort())
      } else {
        info("The list so far is %s".format(cometActorsToUpdate))
        reply(Empty)
      }
    }
    
    case UnregisterCometActor(actor) => {
      info("Removing actor %s from list".format(actor))
      cometActorsToUpdate = cometActorsToUpdate diff List(actor)
    }
    
    case SetPlayers(whitePlayer, blackPlayer) => {
      white = for(p <- whitePlayer; w <- invitedPlayers.get(p)) yield w
      black = for(p <- blackPlayer; b <- invitedPlayers.get(p)) yield b
    }
    
    case StartGame(width, height, rows) if full_? && !gameStarted => {
      for(w <- white; b <- black; gc <- registeredWith) {
        val gctrl = new GameControl(width, height, rows, clientControl)
        gctrl.startGame(w, b, -1, -1)
        gameControl = Full(gctrl)
        _rows = rows
        gameStarted = true
        gc ! GameStarted
        info("Game %s has been started".format(name))
      }
    }
         
    case UpdateFieldsize(w,h) if !gameStarted => {
      _fieldsize = Fieldsize(w,h)
      info("Size of the field in game %s has been set to %s".format(name, _fieldsize))
      cometActorsToUpdate.foreach(_ ! _fieldsize)
    }
    
    case Close => {
      info("Closing rmi server...")
      ServerFactory.closeServer(clientControl)
      GameFactory.deleteDispatcher(digest)
    }
  }
  
}