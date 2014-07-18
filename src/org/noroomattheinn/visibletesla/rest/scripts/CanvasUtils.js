// CanvasUtils.js

/** 
 * Draws a rounded rectangle using the current state of the canvas.  
 * @param {Number} x The top left x coordinate 
 * @param {Number} y The top left y coordinate  
 * @param {Number} width The width of the rectangle  
 * @param {Number} height The height of the rectangle 
 * @param {Object} radius All corner radii. Defaults to 0,0,0,0; 
 * @param {Object} The fillStyle. If falsy, then don't fill 
 * @param {Object} The strokeStyle. If falsy, then don't stroke
 */
CanvasRenderingContext2D.prototype.roundRect = function (x, y, width, height, radius, fill, stroke) {
    var cornerRadius = { upperLeft: 0, upperRight: 0, lowerLeft: 0, lowerRight: 0 };
    if (typeof radius === "object") {
        for (var side in radius) {
            cornerRadius[side] = radius[side];
        }
    }

    this.beginPath();
    this.moveTo(x + cornerRadius.upperLeft, y);
    this.lineTo(x + width - cornerRadius.upperRight, y);
    this.quadraticCurveTo(x + width, y, x + width, y + cornerRadius.upperRight);
    this.lineTo(x + width, y + height - cornerRadius.lowerRight);
    this.quadraticCurveTo(x + width, y + height, x + width - cornerRadius.lowerRight, y + height);
    this.lineTo(x + cornerRadius.lowerLeft, y + height);
    this.quadraticCurveTo(x, y + height, x, y + height - cornerRadius.lowerLeft);
    this.lineTo(x, y + cornerRadius.upperLeft);
    this.quadraticCurveTo(x, y, x + cornerRadius.upperLeft, y);
    this.closePath();
    this.fillStroke(fill, stroke);
} 

CanvasRenderingContext2D.prototype.buildLGradient = function(stops, region) {
    var grd = this.createLinearGradient(region[0], region[1], region[2], region[3]);
    var nStops = stops.length;
    if (typeof stops[0][1] === 'string') {
        for (var i = 0; i < nStops; i++) {
            grd.addColorStop(stops[i][0], stops[i][1]);
        }
    } else {    // It's a 3 element array of RGB color values (0-255)
        for (var i = 0; i < nStops; i++) {
            var rgb = 'rgb(' + stops[i][1].join(',') + ')';
            grd.addColorStop(stops[i][0], rgb);
        }
    }
    return grd;
}

CanvasRenderingContext2D.prototype.fillStroke = function(fill, stroke) {
    if (fill != undefined) {
        this.fillStyle = fill;
        this.fill();
    }
    if (stroke != undefined) {
        this.strokeStyle = stroke;
        this.stroke();
    }
}

CanvasRenderingContext2D.prototype.drawArc = function(start, end, cc, x, y, r, fill, stroke) {
    this.beginPath();
    this.moveTo(x, y);
    this.arc(x, y, r, start, end, cc);
    this.closePath();
    this.fillStroke(fill, stroke);
}

CanvasRenderingContext2D.prototype.drawCircle = function(x, y, r, fill, stroke) {
    this.beginPath();
    this.arc(x, y, r, 0, 2 * Math.PI, false);
    this.fillStroke(fill, stroke);
}
