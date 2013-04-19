package code
package snippet

import net.liftweb._
import http._
import common._ 
import util.Helpers._ 
import js._
import JsCmds._ 
import JE._

import scala.xml.NodeSeq
import scala.xml.Text

import code.model.User
import code.lib.GameFactory._
import code.comet._

class GameAuth(val gameDigest: String) {
  
  private object UserSession extends SessionVar[Box[User]](Empty)
  
  def loggedIn_? = UserSession.is != Empty

  def beforeLogin = {
    
    var username = "nobody"
    
    def doLogin(): JsCmd = {
      if(username.length == 0)
        S.error("Username cannot be empty")
      else {
        (for {
          s <- S.session ?~ "No session during login"
          dispatcher <- dispatcherFor(gameDigest)
        } yield {
          val user = new User(username, gameDigest)
          UserSession(Full(user))
          s.sendCometActorMessage("GameActor", Empty, Init(dispatcher, user))
        }) match {
          case Failure(msg,_,_) => S.error(msg)
          case Empty => S.error("Unknown error")
          case _ => Noop
        }
      }
    }
    
    "name=login" #> SHtml.text(username, username = _, "id" -> "login_name") &
    "type=submit" #> SHtml.onSubmitUnit(doLogin)
  }
  
  def afterLogin = {
    def doLogout(): JsCmd = {
      for(s <- S.session)  {
        s.sendCometActorMessage("GameActor", Empty, Logout)
        s.destroySession()
      }
      S.redirectTo("/")
    }
    if(gameDigest != UserSession.is.open_!.gameId)
      S.redirectTo("/", () => S.error("You are already in a game"))
    else
      "#gamename" #> dispatcherFor(gameDigest).open_!.name &
      "#logout" #> SHtml.link("/", doLogout, Text("Logout"), "id" -> "logout_link")
  }
  
  def render(html: NodeSeq) = 
    if(!loggedIn_?)
      <lift:embed what="templates-hidden/login" />
    else
      <lift:GameAuth.afterLogin>{html}</lift:GameAuth.afterLogin>

}