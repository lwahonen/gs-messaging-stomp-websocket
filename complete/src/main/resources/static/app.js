var stompClient = null;

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#conversation").show();
    }
    else {
        $("#conversation").hide();
    }
    $("#greetings").html("");
}

function connect() {
    var name=$("#name").val();

    stompClient = new StompJs.Client({
        brokerURL: "notused",
        debug: function (str) {
            console.log(str);
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000
    });

    stompClient.webSocketFactory = function () {
        // Note that the URL is different from the WebSocket URL
        return new SockJS("/fallback");
    };

    stompClient.onConnect = function (frame) {
        // Do something, all subscribes must be done is this callback
        // This is needed because this will be executed after a (re)connect
        setConnected(true);
        console.log('Connected: ' + frame);
        var destination = '/topic/' + name;
        var managed=stompClient.subscribe(destination, function (greeting) {
            showGreeting(JSON.parse(greeting.body).content);
        });
        console.log("Connection to "+managed+" with destination "+destination);
    };

    stompClient.onStompError = function (frame) {
        // Will be invoked in case of error encountered at Broker
        // Bad login/passcode typically will cause an error
        // Complaint brokers will set `message` header with a brief message. Body may contain details.
        // Compliant brokers will terminate the connection after any error
        console.log('Broker reported error: ' + frame.headers['message']);
        console.log('Additional details: ' + frame.body);
    };

    stompClient.activate();
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    console.log("Disconnected");
}

function sendName() {
    var name=$("#name").val();
    var message = $("#message").val();
    var target = "/stompbroker/"+name;
    stompClient.publish({destination: target, body: message});
}

function showGreeting(message) {
    $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $( "#connect" ).click(function() { connect(); });
    $( "#disconnect" ).click(function() { disconnect(); });
    $( "#send" ).click(function() { sendName(); });
});

