package code
package snippet

import net.liftweb._
import http._
import common._ 
import util.Helpers._ 
import js._
import JsCmds._ 
import JE._

import code.comet._
import net.liftweb.http.SHtml.ElemAttr

case class Size(w: Int, h: Int)

object GameVariants {
  val sizes = 
    List(("6x7", (1 to 2)), ("8x8", (1 to 3)), ("10x10", (2 to 4)))
  
  val defaultSize = "6x7"
  val defaultRows = 2
  
  def asSize(size :String): Box[Size] = {
    val sizeArray = size.split("x")
    if(sizeArray.length == 2)
      Full(Size(sizeArray(0).toInt, sizeArray(1).toInt))
    else
      Failure("Given size is not valid", Empty, Empty)
  }
  
  def rowsFor(size :String) = sizes.filter(s => size == s._1).head._2.toList
}

class NewGameForm {
  
  import GameVariants._
  
  private var fieldsize = defaultSize
  private var rowAmount = defaultRows.toString
  
  private var dispatchActor = Server.get.open_!.dispatcher

  private def replaceRows(size: String) = {
    val rows = rowsFor(size)
    val first = rows.head
    ReplaceOptions("rows", rows.map(i => (i.toString, i.toString)), Full(first.toString))
  }
  
  private def disabled = if (dispatchActor.gameStarted_?) Some("disabled" -> "true") else None

  def doSelectFieldsize = {
    def process(i: String) = {
      fieldsize = i;
      val size = asSize(i).openOr(Size(6,7))
      dispatchActor ! UpdateFieldsize(size.w, size.h)
      replaceRows(i); 
    }
    SHtml.ajaxSelect(sizes.map(s => (s._1, s._1)),
      Full(fieldsize),
      process(_), List("id" -> "fieldsize") ++ disabled:_*)
  }
    

  def doSelectRows = {
    val rows = rowsFor(fieldsize)
    SHtml.untrustedSelect(rows.map(s => (s.toString, s.toString)), 
      Full(rowAmount), rowAmount = _, List("id" -> "rows") ++ disabled:_*)
  }
  
  def startGame(): JsCmd = {
    for{s <- S.session ?~ "No session active"
        server <- Server.get ?~ "You dont own a server"
        rows <- asInt(rowAmount) ?~ "Rows is not an integer"
        size <- asSize(fieldsize) ?~ "Size is not valid" } {
      s.sendCometActorMessage("NewGame", Empty, DisableControls)
      server.dispatcher ! StartGame(size.w, size.h, rows)
    }
  }
  
  def render = 
    "name=fieldsize" #> doSelectFieldsize &
    "name=rows" #> doSelectRows &
    "type=submit" #> SHtml.ajaxSubmit("Spiel starten", startGame,  
      (if(!dispatchActor.full_? || dispatchActor.gameStarted_?) List("disabled" -> "true") else List()):_*)

}