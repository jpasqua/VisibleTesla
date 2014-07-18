// BatteryGauge.js

var redStops = [
	[0.00, [160,0,0]], [0.02, [150,0,0]], [0.04, [179,0,0]], [0.12, [221,0,0]],
	[0.16, [239,0,0]], [0.21, [251,0,0]], [0.31, [239,0,0]], [0.39, [197,0,0]],
	[0.44, [168,0,0]], [0.47, [154,0,0]], [0.51, [149,0,0]], [0.57, [154,0,0]],
	[0.68, [175,0,0]], [0.74, [180,0,0]], [0.80, [176,0,0]], [0.86, [165,0,0]],
	[0.92, [147,0,0]], [0.97, [120,0,0]], [1.00, [106,0,0]]
];
var greenStops = [
	[0.00, [0,160,0]], [0.02, [0,150,0]], [0.04, [0,179,0]], [0.12, [0,221,0]], 
	[0.16, [0,239,0]], [0.21, [0,251,0]], [0.31, [0,239,0]], [0.39, [0,197,0]], 
	[0.44, [0,168,0]], [0.47, [0,154,0]], [0.51, [0,149,0]], [0.57, [0,154,0]], 
	[0.68, [0,175,0]], [0.74, [0,180,0]], [0.80, [0,176,0]], [0.86, [0,165,0]], 
	[0.92, [0,147,0]], [0.97, [0,120,0]], [1.00, [0,106,0]]
];
var yellowStops = [
	[0.00, [255,160,0]], [0.02, [255,150,0]], [0.04, [255,179,0]], [0.12, [255,221,0]], 
	[0.16, [255,239,0]], [0.21, [255,251,0]], [0.31, [255,239,0]], [0.39, [255,197,0]], 
	[0.44, [255,168,0]], [0.47, [255,154,0]], [0.51, [255,149,0]], [0.57, [255,154,0]], 
	[0.68, [255,175,0]], [0.74, [255,180,0]], [0.80, [255,176,0]], [0.86, [255,165,0]], 
	[0.92, [255,147,0]], [0.97, [255,120,0]], [1.00, [255,106,0]]
];
var bkgStops = [
	[0.00, [202,202,202]], [0.01, [202,202,202]], [0.04, [212,212,212]], [0.10, [226,226,226]], 
	[0.15, [235,235,235]], [0.21, [242,242,242]], [0.27, [247,247,247]], [0.30, [248,248,248]], 
	[0.31, [240,240,240]], [0.55, [240,240,240]], [0.69, [237,237,237]], [0.77, [232,232,232]], 
	[0.83, [226,226,226]], [0.86, [220,220,220]], [0.92, [202,202,202]], [0.99, [173,173,173]], 
	[1.00, [165,165,165]]
];

var batteryBase = [
	[0.00, [153,153,153]], [0.05, [178,178,178]], [0.09, [199,199,199]], [0.10, [199,199,199]], 
	[0.17, [235,235,235]], [0.21, [249,249,249]], [0.25, [254,254,254]], [0.28, [249,249,249]], 
	[0.31, [237,237,237]], [0.42, [174,174,174]], [0.43, [166,166,166]], [0.45, [161,161,161]], 
	[0.46, [154,154,154]], [0.50, [150,150,150]], [0.54, [152,152,152]], [0.56, [154,154,154]], 
	[0.69, [175,175,175]], [0.75, [180,180,180]], [0.78, [178,178,178]], [0.84, [170,170,170]], 
	[0.88, [160,160,160]], [0.92, [147,147,147]], [0.96, [127,127,127]], [1.00, [ 97, 97, 97]]	
];

function chooseStops(pct) {
	if (pct <= 33) return redStops;
	if (pct <= 66) return yellowStops;
	return greenStops;
}

function batteryGauge(ctx, w, h, pct, charging) {
	var BAT_H = h;
	var BAT_INT_W = w/(1 + 0.08 * 3);
	var BASE_W = BAT_INT_W * 0.08;
	var TOP_W = BASE_W;
	var CATHODE_W = BASE_W;
	var CATHODE_H = (2.0/5.0) * BAT_H;
	var CATHODE_Y = (BAT_H-CATHODE_H)/2-1;

	function drawChargingSymbol(ctx) {
		var plug = new Image();
		plug.src = "http://visibletesla.com/Documentation/images/Battery/Solid/Plug.png";
		plug.onload = function() {
			var scale = 0.8;
			var computedWidth = scale * BAT_INT_W;
			var computedHeight = (computedWidth / this.width) * this.height;
			if (computedHeight > scale * BAT_H) {
				computedHeight = scale * BAT_H;
				computedWidth = (computedHeight / this.height) * this.width;
			}
			var x = (BAT_INT_W - computedWidth)/2 + BASE_W;
			var y = (BAT_H - computedHeight)/2;
			ctx.drawImage(plug, x, y, computedWidth, computedHeight);
		}
	}

	// Draw the battery left to right
	var curX = 0;
	var gradient = ctx.buildLGradient(batteryBase, [0,0,0,BAT_H]);

	ctx.roundRect(curX, 0, BASE_W, BAT_H, {upperLeft:4,lowerLeft:4}, gradient);
	curX += BASE_W;

	ctx.fillStyle = ctx.buildLGradient(bkgStops, [0,0,0,BAT_H]);
	ctx.fillRect(curX,0,BAT_INT_W,BAT_H);

	var stops = chooseStops(pct);
	ctx.fillStyle = ctx.buildLGradient(stops, [0,0,0,BAT_H]);
	ctx.fillRect(curX, 0, (pct/100.0)*BAT_INT_W, BAT_H);
	curX += BAT_INT_W;
	if (charging) drawChargingSymbol(ctx);

	ctx.roundRect(curX, 0, TOP_W,BAT_H, {upperRight:4,lowerRight:4}, gradient);
	curX += TOP_W;

	gradient = ctx.buildLGradient(batteryBase,[0,CATHODE_Y,0,CATHODE_Y+CATHODE_H]);
	ctx.roundRect(curX, CATHODE_Y, CATHODE_W, CATHODE_H, {upperRight:2,lowerRight:2}, gradient);
	curX += CATHODE_W;

//      ctx.font='1em arial,sans-serif';
//      ctx.textAlign = 'left';
//      ctx.textBaseline = 'middle';
//      ctx.fillStyle = 'black';
//      ctx.fillText(" " + pct + "%", curX, BAT_H/2);
}