//
// CarView.js
//

var images = {};
var ctx;

function carView(theCtx, carDetails, carState) {
    ctx = theCtx;
    loadImages(chooseAppropriateImage(carDetails), drawCar);
}

function drawCar() {
    var bodyX = 0;
    var bodyY = 45;

    // Right Doors
    if (carState.rf === "open") ctx.drawImage(images.rfOpen, bodyX + 180, bodyY - 40);
    if (carState.rr === "open") ctx.drawImage(images.rrOpen, bodyX + 278, bodyY - 25);

    // Body
    ctx.drawImage(images.body, bodyX, bodyY);

    // Seats
    if (carDetails.seats === "gray" || carDetails.seats === "white") ctx.drawImage(images.seatsGray, bodyX + 0, bodyY - 1);
    else if (carDetails.seats === "tan") ctx.drawImage(images.seatsTan, bodyX + 0, bodyY - 1);
    // else they're black which is the default

    // Left Doors
    if (carState.lf === "open") ctx.drawImage(images.lfOpen, bodyX + 128, bodyY + 107);
    else ctx.drawImage(images.lfClosed, bodyX + 152, bodyY + 59);
    if (carState.lr === "open") ctx.drawImage(images.lrOpen, bodyX + 245, bodyY + 69);
    else ctx.drawImage(images.lrClosed, bodyX + 274, bodyY + 58);

    // Roof
    if (carDetails.hasPano) {
        if (carState.panoPct > 0 && carState.panoPct < 75) ctx.drawImage(images.panoVented, bodyX + 220, bodyY + 0);
        else if (carState.panoPct >= 75) ctx.drawImage(images.panoOpen, bodyX + 220, bodyY + 0);
        else ctx.drawImage(images.panoClosed, bodyX + 220, bodyY + 0);
        ctx.font = '.9em arial,sans-serif';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = 'white';
        ctx.fillText(carState.panoPct + "%", bodyX + 272, bodyY + 22);
    } else ctx.drawImage(images.solidRoof, bodyX + 220, bodyY + 0);

    // Trunks
    if (carState.ft === "open") ctx.drawImage(images.ftOpen, bodyX + 8, bodyY - 34);
    else ctx.drawImage(images.ftClosed, bodyX + 1, bodyY + 36);
    if (carState.rt === "open") ctx.drawImage(images.rtOpen, bodyX + 381, bodyY - 44);
    else ctx.drawImage(images.rtClosed, bodyX + 380, bodyY + 9);
    if (carDetails.hasSpoiler) {
        if (carState.rt === "open") ctx.drawImage(images.spoilerOpen, bodyX + 472, bodyY - 42);
        else ctx.drawImage(images.spoilerClosed, bodyX + 470, bodyY + 38);
    }

    // Charging
    if (carState.chargePort === "open") ctx.drawImage(images.chargePortOpen, bodyX + 480, bodyY + 113);
    else ctx.drawImage(images.chargePortClosed, bodyX + 471, bodyY + 112);
    if (carState.charging) {
        ctx.drawImage(images.chargePortOn, bodyX + 472, bodyY + 113);
        ctx.drawImage(images.chargeCable, bodyX + 440, bodyY + 116);
    }

    // Wheels
    if (carDetails.wheels === "silver19") {
        ctx.drawImage(images.silver19Front, bodyX + 45, bodyY + 135);
        ctx.drawImage(images.silver19Rear, bodyX + 369, bodyY + 135);
    } else if (carDetails.wheels === "gray21") {
        ctx.drawImage(images.gray21Front, bodyX + 45, bodyY + 135);
        ctx.drawImage(images.gray21Rear, bodyX + 369, bodyY + 135);
    } else if (carDetails.wheels === "aero") {
        ctx.drawImage(images.aeroFront, bodyX + 45, bodyY + 135);
        ctx.drawImage(images.aeroRear, bodyX + 369, bodyY + 135);
    } else if (carDetails.wheels === "cyclone") {
        ctx.drawImage(images.cycloneFront, bodyX + 45, bodyY + 135);
        ctx.drawImage(images.cycloneRear, bodyX + 369, bodyY + 135);
    } // else use the default - Silver 21

    // Emblem
    var emblem;
    if (carDetails.model === "s60") {
        emblem = images.s60;
    } else if (carDetails.model === "s85") {
        emblem = images.s85;
    } else if (carDetails.model === "p85") {
        emblem = images.p85;
    } else {
        emblem = images.p85Plus;
    }
    ctx.save();
        ctx.shadowColor = "rgba( 0, 0, 0, 0.3 )";
        ctx.shadowOffsetX = ctx.shadowOffsetY = 3; ctx.shadowBlur = 3;
        ctx.drawImage(
            emblem, bodyX + 375, bodyY + 225,
            emblem.width * 0.75, emblem.height * 0.75);
    ctx.restore();

    // Lock Status
    var lock = carState.locked ? images.locked : images.unlocked;
    ctx.drawImage(
        lock, bodyX + 63, bodyY + 220, lock.width * 0.5, lock.height * 0.5);
}

function chooseAppropriateImage(details) {
    var resourceRoot = "/TeslaResources/";
    var rootWithColor = resourceRoot + "COLOR_" + details.color + "/";
    var imageURLs = {
        // Body
        body: rootWithColor + "body@2x.png",

        // Roof
        solidRoof: rootWithColor + "roof@2x.png",
        panoClosed: resourceRoot + "sunroof_closed@2x.png",
        panoOpen: resourceRoot + "sunroof_open@2x.png",
        panoVented: resourceRoot + "sunroof_vent@2x.png",

        // Doors
        lfOpen: rootWithColor + "left_front_open@2x.png",
        lfClosed: rootWithColor + "left_front_closed@2x.png",
        rfOpen: rootWithColor + "right_front_open@2x.png",
        lrOpen: rootWithColor + "left_rear_open@2x.png",
        lrClosed: rootWithColor + "left_rear_closed@2x.png",
        rrOpen: rootWithColor + "right_rear_open@2x.png",

        // Trunks
        ftOpen: rootWithColor + "frunk_open@2x.png",
        ftClosed: rootWithColor + "frunk_closed@2x.png",
        rtOpen: rootWithColor + "trunk_open@2x.png",
        rtClosed: rootWithColor + "trunk_closed@2x.png",
        spoilerOpen: resourceRoot + "spoiler_open@2x.png",
        spoilerClosed: resourceRoot + "spoiler_closed@2x.png",

        // Charging
        chargePortOpen: resourceRoot + "charge_port_open@2x.png",
        chargePortClosed: resourceRoot + "charge_port_closed@2x.png",
        chargePortOn: resourceRoot + "charge_port_on@2x.png",
        chargeCable: resourceRoot + "charge_cable_long@2x.png",

        // Seats
        seatsTan: resourceRoot + "seats_tan.png",
        seatsGray: resourceRoot + "seats_gray.png",

        // Wheels
        silver19Front: resourceRoot + "wheel_front_19@2x.png",
        silver19Rear: resourceRoot + "wheel_rear_19@2x.png",
        gray21Front: resourceRoot + "wheel_front_21_dark@2x.png",
        gray21Rear: resourceRoot + "wheel_rear_21_dark@2x.png",
        aeroFront: resourceRoot + "wheel_front_aero@2x.png",
        aeroRear: resourceRoot + "wheel_rear_aero@2x.png",
        cycloneFront: resourceRoot + "wheel_front_turbine@2x.png",
        cycloneRear: resourceRoot + "wheel_rear_turbine@2x.png",

        // Emblems
        s60: resourceRoot + "Emblems/60.png",
        s85: resourceRoot + "Emblems/85.png",
        p85: resourceRoot + "Emblems/P85.png",
        p85Plus: resourceRoot + "Emblems/P85+.png",

        // Lock
        locked: resourceRoot + "06_controls_lock@2x.png",
        unlocked: resourceRoot + "06_controls_unlock@2x.png"
    };
    return imageURLs;
}

function loadImages(sources, callback) {
    var loadedImages = 0;
    var numImages = Object.keys(sources).length;

    for (var src in sources) {
        images[src] = new Image();
        images[src].onload = function() {
            if (++loadedImages >= numImages) {
                callback();
            }
        };
        images[src].src = sources[src];
    }
}