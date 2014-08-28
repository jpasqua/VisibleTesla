// SpeedGauge.js


var TWO_PI = 2.0 * Math.PI;
var HALF_PI = 0.5 * Math.PI;
var THREE_HALFS_PI = 1.5 * Math.PI;
var EPS = 0.05;

function logScale(val, max) {
    val = Math.min(val, max);
    return Math.log(val)/Math.log(max);
}

function speedGauge(ctx, w, h, speed, power) {
    w -= 2; h -=2; 
    var centerX = w / 2 + 1;
    var centerY = h / 2 + 1;
    var outerRadius = Math.min(w, h)/2;
    var innerRadius = outerRadius*0.75;
    var end;

    var grd = ctx.createRadialGradient(centerX, centerY, innerRadius, centerX, centerY, outerRadius);
    grd.addColorStop(0.25, '#666');
    grd.addColorStop(0.75, '#ddd');
    grd.addColorStop(1.00, '#666');

    ctx.lineWidth = 2;
    ctx.drawCircle(centerX, centerY, outerRadius, grd, '#555');

    ctx.beginPath(); ctx.moveTo(centerX, 1); ctx.lineTo(centerX, h+1);
    ctx.strokeStyle = "#222"; ctx.lineWidth = 1; ctx.stroke();

    // Show the Speed segment in blue
    var grd = ctx.createRadialGradient(centerX, centerY, innerRadius, centerX, centerY, outerRadius);
    grd.addColorStop(0.25, '#4D4DFF');
    grd.addColorStop(0.75, '#A3C2FF');
    grd.addColorStop(1.00, '#4D4DFF');
    if (speed === 0) { end = HALF_PI; }
    else {
        end = THREE_HALFS_PI * logScale(speed, 120);
        if (end < HALF_PI) end = HALF_PI + EPS;
    }
    ctx.drawArc(HALF_PI, end, false, centerX, centerY, outerRadius, grd);

    var cc, end, fill;
    grd = ctx.createRadialGradient(centerX, centerY, innerRadius, centerX, centerY, outerRadius);
    if (power < 0) {    // Regnerating - green segement
        cc = false;
        grd.addColorStop(0.25, '#006600');
        grd.addColorStop(0.75, '#009900');
        grd.addColorStop(1.00, '#006600');
        fill = grd;
        var scaled = logScale(Math.min(-power, 60), 60);
        end = (0.5 * Math.PI) * scaled;
        if (end < 0) end = EPS;
    } else {    // Consuming power - orange segement
        cc = true;
        grd.addColorStop(0.25, '#FF9900');
        grd.addColorStop(0.75, '#FFD699');
        grd.addColorStop(1.00, '#FF9900');
        fill = grd;
        if (power === 0) { end = 0.0; }
        else {
            end = (2.0 - (0.5 * logScale(Math.min(power, 240), 240))) * Math.PI;
            if (end >= TWO_PI) end = TWO_PI - EPS;
        }
    }
    ctx.drawArc(0, end , cc, centerX, centerY, outerRadius, fill);

    var grd = ctx.buildLGradient(
        [[0.00, '#111'], [0.45, '#555'], [0.50, '#666'], [0.55, '#555'], [1.00, '#111']],
        [centerX-innerRadius, centerY-innerRadius, centerX+innerRadius, centerY+innerRadius]);
    ctx.lineWidth = 1;    
    ctx.drawCircle(centerX, centerY, innerRadius, grd, "#222");

    ctx.font = (innerRadius*0.8) + 'px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = 'white';
    ctx.fillText(speed.toFixed(0).toString(), centerX, centerY-7);
    ctx.fillStyle = power < 0 ? 'lightgreen' : 'orange';
    ctx.font = (innerRadius*0.5) + 'px sans-serif';
    ctx.fillText(Math.abs(power).toFixed(0).toString(), centerX, centerY+(innerRadius*0.5));    
}
