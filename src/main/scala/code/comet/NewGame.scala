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

import code.snippet.Server
import scala.xml.Text

case class PlayerArrived(name: String)
case class PlayerLeaved(name: String, color: String)
case class Ready(dispatcher: DispatcherActor)

case class Disable(ids: List[String]) extends JsCmd {
  def toJsCmd = ids.map(s => "jQuery('#"+s+"').attr('disabled', 'true')").reduceLeft(_ + ";" + _) + ";"
}

case class Enable(ids: List[String]) extends JsCmd {
  def toJsCmd = ids.map(s => "jQuery('#"+s+"').removeAttr('disabled')").reduceLeft(_ + ";" + _) + ";"
}

object DisableControls
object EnableControls
object TogglePlayers

class NewGame extends CometActor {
  
  private var registeredWith: Box[DispatcherActor] = Empty
  
  private var startButtonEnabled = false
  private var fieldSizeEnabled = true
  private var rowsEnabled = true
  private var black: Box[String] = Empty
  private var white: Box[String] = Empty
  private val waitingMessage = "waiting for player"
  private var enableToggle = true

  override def lowPriority = {
    
    case Ready(d) => {
      registeredWith = Full(d)
      d ! this
    }
    
    case DisableControls => {
      enableToggle = false
      partialUpdate(Disable(List(
        "fieldsize",
        "rows",
        "start_button"
      )))
      reRender()
    }
    
    case EnableControls => {
      enableToggle = true
      partialUpdate(Enable(List(
        "fieldsize",
        "rows",
        "start_button"
      )))
      reRender()
    }
    
    case TogglePlayers => {
      for(dispatcher <- registeredWith) {
        val tmp = white
        white = black
        black = tmp
        dispatcher ! SetPlayers(white, black)  
        reRender();
      }
    }
    
    case PlayerArrived(n) => {
      for(dispatcher <- registeredWith) {
        val color = if(white.isEmpty) {
          white = Full(n)
          "white"
        } else if(black.isEmpty) {
          black = Full(n)
          "black"
        }
        partialUpdate(
          JsRaw("arrival.playerArrived('%s','%s')".format(n, color)) &
          (if(dispatcher.full_?) Enable(List("start_button")) else Noop)
        )
      }
    }
    
    case PlayerLeaved(n, color) =>{
      for(dispatcher <- registeredWith) {
        if(color == "white") white = Empty
        else black = Empty
        partialUpdate(
          JsRaw("arrival.playerLeaved('%s','%s')".format(n, color)) &
          Disable(List("start_button"))
        ) 
      }
    }
    
    case GameStarted => {
      partialUpdate(JsRaw("arrival.gameStarted()"))
    }

  }
  
  def render = Server.get match {
    case Full(s) => 
      "#gamename" #> s.dispatcher.name &
      "#link" #> "http://%s/play/%s".format("localhost:8080", s.dispatcher.digest) &
      "#white_player *" #> white.openOr(waitingMessage) &
      "#black_player *" #> black.openOr(waitingMessage) &
      "#toggle_button" #> (if(enableToggle) SHtml.a(() => this ! TogglePlayers, Text("Toggle")) else Text(""))
    case _ => Noop
  }
    
  
  
}