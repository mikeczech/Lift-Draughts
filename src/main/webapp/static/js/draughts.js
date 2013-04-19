var config = {
  default_sizeX : 6,
  default_sizeY : 7,
  fieldsize : 65,
  piecesize : 55,
  canvas_id : 'canvas',
  images_root : "http://localhost:8080/images/"
}

var resources_path = {
  tiles : {
    black : "black_tile.jpg",
    white : "white_tile.jpg",
    bgrey : "grey_black_tile.jpg",
    wgrey : "grey_white_tile.jpg"
  },
  pieces : {
    bdame : "dame_black.png",
    wdame : "dame_white.png",
    black : "piece_black.png",
    white : "piece_white.png"
  }
}

function Piece(normalImg, dameImg, relXPos, relYPos) {
  
  this.relXPos = relXPos;
  this.relYPos = relYPos;
  this.crowned = false;
  this.dragged = false;
  this.x = 0;
  this.y = 0;
  
  this.clone = function() {
    var p = new Piece(normalImg, dameImg, this.relXPos, this.relYPos);
    p.crowned = this.crowned;
    p.dragged = this.dragged;
    return p;
  }
  
  this.drawAt = function(ctx, x, y) {
    if(this.crowned)
      ctx.drawImage(dameImg, x, y);
    else
      ctx.drawImage(normalImg, x, y);
    if(this.dragged) {
      ctx.lineWidth = 5;
      ctx.beginPath();
      ctx.arc(x + config.piecesize / 2, y + config.piecesize / 2, 
        config.piecesize / 2, 0, Math.PI * 2, true);
      ctx.closePath();
      ctx.stroke();
    }
  }
  
  this.draw = function(ctx) {
    this.drawAt(ctx, this.x, this.y);
  }
  
  
}

function Board(sizeX, sizeY, res) {
  var fields = [];
  var pieces = [];
  var _self = this;
  var offset = (config.fieldsize - config.piecesize) / 2;
  
  this.width = sizeX;
  this.height = sizeY;
  this.dragging = false;
  
  function Field(normalImg, greyImg, xpos, ypos) {
    this.draw = function(ctx) {
      ctx.drawImage(normalImg, xpos, ypos, config.fieldsize, config.fieldsize); 
    }
  }
  
  function modifyPieceAt(relPos, call) {
    for(var i = 0; i < pieces.length; i++) {
       if(pieces[i].relXPos == relPos.x && pieces[i].relYPos == relPos.y) {
         return call(pieces[i], i);
       }
    }
  }
  
  function initialize() {
    var xpos = 0;
    var ypos = 0;
    /* Initialize fields */
    for(var i = 0; i < _self.height; i++) {
      for(var j = 0; j < _self.width; j++) {
        if(i%2 == 0) {
          if(j%2 == 0)
            if(_self.height % 2 != 0)
              fields.push(new Field(res["tiles"]["black"], res["tiles"]["bgrey"], xpos, ypos));
            else
              fields.push(new Field(res["tiles"]["white"], res["tiles"]["wgrey"], xpos, ypos));
          else
            if(_self.height % 2 != 0)
              fields.push(new Field(res["tiles"]["white"], res["tiles"]["wgrey"], xpos, ypos));
            else
              fields.push(new Field(res["tiles"]["black"], res["tiles"]["bgrey"], xpos, ypos));
        } else {
          if(j%2 == 0)
            if(_self.height % 2 != 0)
              fields.push(new Field(res["tiles"]["white"], res["tiles"]["wgrey"], xpos, ypos));
            else
              fields.push(new Field(res["tiles"]["black"], res["tiles"]["bgrey"], xpos, ypos));
          else
            if(_self.height % 2 != 0)
              fields.push(new Field(res["tiles"]["black"], res["tiles"]["bgrey"], xpos, ypos));
            else
              fields.push(new Field(res["tiles"]["white"], res["tiles"]["wgrey"], xpos, ypos));
        }
        xpos += config.fieldsize;
      }
      xpos = 0;
      ypos += config.fieldsize;
    }
  }
  
  initialize();
  
  return {
    
    beginDrag : function(offsetX, offsetY) {
      var relX = Math.floor((offsetX - offset) / config.fieldsize);
      var relY = Math.ceil((offset -offsetY) / config.fieldsize) + _self.height - 1;
      modifyPieceAt(new Position(relX, relY), function(piece, i) {
        piece.dragged = true;
        _self.draggedPiece = piece;
        _self.pseudoPiece = piece.clone();
        _self.pseudoPiece.dragged = false;
        _self.pseudoPiece.x = config.fieldsize * piece.relXPos + offset;
        _self.pseudoPiece.y = (((_self.height - 1)  - piece.relYPos)  * config.fieldsize) + offset;
      });
      _self.dragging = true;
    },
    
    doDrag : function(offsetX, offsetY) {
      if(typeof _self.pseudoPiece != 'undefined') {
        _self.pseudoPiece.x = offsetX - config.piecesize / 2;
        _self.pseudoPiece.y = offsetY - config.piecesize / 2;
      }
    },
    
    endDrag : function(offsetX, offsetY) {
      if(typeof _self.draggedPiece != 'undefined') {
        _self.draggedPiece.dragged = false;
        var relX = Math.floor((offsetX - offset) / config.fieldsize);
        var relY = Math.ceil((offset -offsetY) / config.fieldsize) + _self.height - 1;
        var oldRelX = _self.draggedPiece.relXPos;
        var oldRelY = _self.draggedPiece.relYPos;
        if(relX != oldRelX || relY != oldRelY)
          sendStep(new Position(oldRelX, oldRelY), new Position(relX, relY));
        delete _self.draggedPiece;
        delete _self.pseudoPiece;
        
      }
      _self.dragging = false;
    },
    
    movePiece : function(oldRelPos, newRelPos) {
      modifyPieceAt(oldRelPos, function(piece, i) {
        piece.relXPos = newRelPos.x;
        piece.relYPos = newRelPos.y;
      });
    },
    
    crownPiece : function(relPos) {
      modifyPieceAt(relPos, function(piece, i) {
        piece.crowned = true;
      });
    },
    
    removePiece : function(relPos) {
      modifyPieceAt(relPos, function(piece, i) {
        pieces.splice(i, 1);
      });
    },
    
    draw : function(ctx) {
      for(var i = 0; i < fields.length; i++)
        fields[i].draw(ctx);
      for(var i = 0; i < pieces.length; i++)
        pieces[i].drawAt(ctx, 
          config.fieldsize * pieces[i].relXPos + offset, 
          (((_self.height - 1)  - pieces[i].relYPos)  * config.fieldsize) + offset);
      if(typeof _self.pseudoPiece != 'undefined')
        _self.pseudoPiece.draw(ctx);
    },
    
    pieceList : pieces,
    
    clear : function() {
      pieces.length = 0;
      return this;
    },
    
    resize : function(sizeX, sizeY) {
      _self.width = sizeX;
      _self.height = sizeY;
      var canvas = document.getElementById(config.canvas_id);
      canvas.width = sizeX * config.fieldsize;
      canvas.height = sizeY * config.fieldsize;
      $('#game')
        .width(canvas.width)
        .height(canvas.height)
        .css({
          'margin-left' : "-" + Math.floor(canvas.width / 2) + "px",
          'margin-top' : "-" + Math.floor(canvas.height / 2) + "px"
        });
      fields.length = 0;
      initialize();
      return this;
    },
    
    isDragging: function() { return _self.dragging;  },
    getWidth : function() { return _self.width; },  
    getHeight : function() { return _self.height; }
  }
}

function SimpleResourceLoader() {
  var resources = {};
  var left = 0;
  var self = this;
  
  function onImageLoad() {
    left--; 
    if(typeof self.onProgress != "undefined")
      self.onProgress(left);
    if(left == 0) {
      if(typeof self.onReady != "undefined")
        self.onReady(resources);
    }
  }
  
  this.loadAll = function() {
    for (var key in resources_path) {
      var obj = resources_path[key];
      resources[key] = {};
      for (var prop in obj) {
        var img = new Image();
        img.src = config.images_root + "/" + obj[prop];
        img.onload = onImageLoad;
        left++;
        resources[key][prop] = img;
      }
    }
  }
}

function LoadingBar(resLoader, ctx) {
  resLoader.onProgress = function(left) {
    // Does nothing at the moment
  }
}

function Position(x,y) {
  this.x = x;
  this.y = y;
}

function Game(res, ctx) {

  var board = new Board(config.default_sizeX, config.default_sizeY, res);
  var that = this;
  
  function refresh() {
    board.draw(ctx);
  }
  refresh();
  
  var getElementPosition = function (theElement){
    var posX = 0;
    var posY = 0;
    while(theElement != null){
      posX += theElement.offsetLeft;
      posY += theElement.offsetTop;
      theElement = theElement.offsetParent;
    }
     return {x:posX,y: posY};
  };
  
  
  $('#' + config.canvas_id).mousedown(function(e) {
    board.beginDrag(e.pageX - getElementPosition(this).x, e.pageY - getElementPosition(this).y);
    refresh();
  });
  
  $('#' + config.canvas_id).mousemove(function(e) {
    if(board.isDragging()) {
      board.doDrag(e.pageX - getElementPosition(this).x, e.pageY - getElementPosition(this).y);
      refresh();  
    }
  });
  
  $('#' + config.canvas_id).mouseup(function(e) {
    board.endDrag(e.pageX - getElementPosition(this).x, e.pageY - getElementPosition(this).y);
    refresh();
  });
  
  function initialize(width, height, rows) {
    for(var x = 0; x < width; x++) {
      for (var y = 0; y < rows; y++){
        if ((x%2 + y%2)%2 == 0)
          board.pieceList.push(new Piece(res["pieces"]["black"], res["pieces"]["bdame"], x, y));
        if ((x%2 + (height - 1 - y)%2)%2 == 0)
          board.pieceList.push(new Piece(res["pieces"]["white"], res["pieces"]["wdame"], x, height -1 - y));
      }
    }
  }

  this.setup = function(data) {
    if(typeof data.width != 'undefined' && typeof data.height != 'undefined') {
      board.clear().resize(data.width, data.height);
    }
    if(typeof data.rows != 'undefined') {
      board.clear();
      initialize(board.getWidth(), board.getHeight(), data.rows);
    }
    refresh();
  }
  
  this.gameStarted = function(sizeX, sizeY, r) {
    that.setup({width: sizeX, height: sizeY, rows: r})
    refresh();
  }
  
  this.stepPerformed = function(params) {
    if(typeof params.source != 'undefined' && typeof params.target != 'undefined')
      board.movePiece(params.source, params.target);
    if(typeof params.update != 'undefined')
      board.crownPiece(params.update);
    if(typeof params.capture != 'undefined')
      board.removePiece(params.capture);
    refresh();
  }
  
}

function init() {
  var ctx = $(config.canvas_id)[0].getContext("2d");
  var resLoader = new SimpleResourceLoader();
  
  var canvas = document.getElementById(config.canvas_id);
  canvas.width = config.default_sizeX * config.fieldsize;
  canvas.height = config.default_sizeY * config.fieldsize;
  
  resLoader.onReady = function(resources) {
    game = new Game(resources, ctx); // Make game global
    if(typeof initGameParams.w != 'undefined') {
      game.gameStarted(initGameParams.w, initGameParams.h, initGameParams.r);
    }
    for(var s in initStepsArray)
      game.stepPerformed(initStepsArray[s]);
  }
  var loadingBar = new LoadingBar(resLoader, ctx);
  resLoader.loadAll();
}
