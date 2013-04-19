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

import scala.xml.NodeSeq
import scala.util.Random
import scala.collection.mutable.ListBuffer

import code.model.User
import code.snippet.GameAuth

import de.upb.swt.swtpra2011.player.model._
import de.upb.swt.swtpra2011.common._
import java.beans.{PropertyChangeListener, PropertyChangeEvent}

trait GameListener extends PropertyChangeListener {
  def propertyChange(evt :PropertyChangeEvent) = evt.getPropertyName() match {
    case Game.PROPERTY_GAMESTARTED => {
      val board = evt.getNewValue().asInstanceOf[Board]
      gameStarted(board.getWidth(), board.getHeight(), board.getRows())
    }
    case Game.PROPERTY_STEP_PERFORMED =>
      stepPerformed(evt.getNewValue().asInstanceOf[IStep])
  }
  def gameStarted(sizeX : Int, sizeY : Int, rows : Int) : Unit
  def stepPerformed(step : IStep) : Unit
}

object Logout

case class Init(d: DispatcherActor, user: User)

class GameActor extends CometActor with Logger with GameListener {
  
  private val game = new Game(this)
  
  private var invitedPlayers: List[PlayerData] = List()
  
  private var registeredWith: Box[DispatcherActor] = Empty
  
  private val stepParamsBuffer = new ListBuffer[String]
  
  implicit def positionToJs(pos : IPosition) : JsRaw = 
    JsRaw("new Position(%d,%d)".format(pos.getX(),  pos.getY()))
  
  appendJsonHandler {
    /* Finalize step */
    case JsonCmd("step", _, p : Map[String, Map[String, Double]], _) if game.getBoard().isTurn() => {
      val srcPos = new Position(p("source")("x").toInt, p("source")("y").toInt)
      val tarPos = new Position(p("target")("x").toInt, p("target")("y").toInt)
      game.getBoard().finalizeStep(new Step(srcPos, tarPos), false)
    }
  }

  override def lowPriority = {
     
     /* Registers user to comet actor */
    case Init(actor, user) if !actor.contains(this) => {
      for{port <- actor !! RegisterCometActor(this)} {
        game.getActivePlayer().setName(user.name)
        info("Access server %s using port %s".format(actor.url, port.asInstanceOf[Int]))
        game.login(actor.url, port.asInstanceOf[Int], actor.id, true)
        registeredWith = Full(actor)
        this ! actor.fieldsize // Sync fieldsize
      }
    }
   
    case Fieldsize(w,h) =>
      partialUpdate(JsRaw("game.setup({width: %s, height: %s})".format(w, h)))

    case Logout => {
      for(dispatcher <- registeredWith) {
        info("Player " + game.getActivePlayer().getName() + " leaved the game")
        game.logout()
        dispatcher ! UnregisterCometActor(this)
        registeredWith = Empty 
      }
    }
    
  }
  
  def gameStarted(sizeX : Int, sizeY : Int, rows : Int) =
    partialUpdate(JsRaw("game.gameStarted(%d,%d,%d)".format(sizeX, sizeY, rows)))

  def stepPerformed(step : IStep) = {
    val params : String = 
      List(
        ("source",  Option(step.getSource())),
        ("target",  Option(step.getTarget())),
        ("capture", Option(step.getCapturePosition())),
        ("update",  Option(step.getUpdatePosition()))
      )
      .collect { case (s, Some(pos)) => s + ":" + pos.toJsCmd }
      .reduceLeft((p,n) => p + ", " + n)
    stepParamsBuffer += params
    partialUpdate(JsRaw("game.stepPerformed({" + params + "})"))
  }
  
  
  def render = {
    val jsonCmd = jsonCall("step", JsRaw("{source:{x:src.x, y:src.y}, target:{x:tar.x, y:tar.y} }"))
    JsRaw("var initStepsArray = [];") &
    JsRaw("var initGameParams = {};") &
    OnLoad(
      (for(d <- registeredWith if d.gameStarted_?) yield JsRaw("initGameParams = {w:%d,h:%d,r:%d}".format(d.fieldsize.w, d.fieldsize.h, d.rows)).cmd).openOr(JsRaw("").cmd) &
      seqJsToJs(stepParamsBuffer.toList.map( s => JsRaw("initStepsArray.push({" + s + "})").cmd)) &
      JsRaw("init();")) &
    JsRaw("function sendStep(src, tar) {" + jsonCmd.toJsCmd + "}")
  }
    
  
}
