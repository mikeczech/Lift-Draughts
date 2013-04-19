package code
package snippet

import net.liftweb._ 
import http._ 
import util._ 
import Helpers._
import js._
import JE._
import JsCmds._
import common._

import scala.xml.NodeSeq
import scala.xml.Text

import code.comet.DispatcherActor
import code.lib.GameFactory
import code.model.User
import code.comet._

case class Game(val dispatcher: DispatcherActor, creator: User)

object Server {
  
  private object ServerSession extends SessionVar[Box[Game]](Empty)
  
  private var username = "You"
  
  def get = ServerSession.is
  
  def beforeCreation = {
    var name = ""
    def doCreate(): JsCmd = {
      if(name.length == 0)
        S.error("Name cannot be empty")
      else {
        GameFactory.createDispatcher(name) match {
          case Full(d) if ServerSession.is.isEmpty => {
            for(s <- S.session) {
              val user = new User(username, d.digest)
              ServerSession(Full(Game(d,user)))
              s.sendCometActorMessage("GameActor", Empty, Init(d, user))
            }
          }
          case Failure(msg,_,_) => S.error(msg)
          case _ => S.error("Please close your current game before starting a new one")
        }
      }
    }
    "name=name" #> SHtml.text(name, name = _, "id" -> "server_name") &
    "type=submit" #> SHtml.onSubmitUnit(doCreate)
  }
  
  def doClose() = {
    for(s <- S.session; server <- ServerSession.is) {
      server.dispatcher ! Close
      s.destroySession()
    }
    S.redirectTo("/")
  }  
  
  def render = ServerSession.is match {
    case Full(Game(d,_)) if(d.gameStarted_?) => 
      "#gamecontrol" #> NodeSeq.Empty &
      "#close" #> SHtml.link("/", doClose, Text("Close"), "id" -> "close")
    case Full(_) => 
      "#close" #> SHtml.link("/", doClose, Text("Close"), "id" -> "close")
    case _ => 
      "*" #> <lift:embed what="templates-hidden/creategame" />
  }
  
}