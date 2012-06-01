if (!Date.now) {
	Date.now = function now() {
		return +(new Date);
	};
}

Notifications = {
	lastWebSocketHeartbeat: Date.now() + 5000,
	lastXHRHeartbeat: Date.now() + 5000,
	websocket: null,
	websocketURL: "",
	xhrURL: "",
	handlers: {},
	
	init: function(websocketURL, xhrURL) {
		Notifications.websocketURL = websocketURL;
		Notifications.xhrURL = xhrURL;
		
    	// Watchdog timer (switches to XHR if WebSockets are unavailable or disconnected)
    	window.setInterval(function(){
    		if (Date.now() - Notifications.lastWebSocketHeartbeat > 5000) {
    			// More than 5 seconds since last heartbeat; switch to XHR
    			if (Notifications.websocket !== null) {
                	Notifications.websocket.close();
                	Notifications.websocket = null;
				}
    			$.getJSON(Notifications.xhrURL, function(data){
    				for (var i = 0; i < data.length; i++) {
    					Notifications.handleNotifications(data[i]);
    				}
    			});
    		}
    		if (Date.now() - Notifications.lastWebSocketHeartbeat > 30000) {
    			Notifications.lastWebSocketHeartbeat = Date.now() - 5000;
    			Notifications.openWebSocket();
    		}
    	}, 1000);
    	
    	// Try WebSockets first
        $(Notifications.openWebSocket);
    	
	},
	
	handleNotifications: function(notification) {
		if (notification.error) {
			if (Notifications.websocket !== null) {
            	Notifications.websocket.close();
            	Notifications.websocket = null;
			}
            return;
            
        } else {
        	if (Notifications.websocket !== null) {
        		Notifications.lastWebSocketHeartbeat = Date.now();
        	} else {
        		Notifications.lastXHRHeartbeat = Date.now();
        	}
        	
        	// TODO: check correct resource, and ignore if it is the wrong resource
        	
        	if (Notifications.handlers[notification.kind]) {
        		for (var i = 0; i < Notifications.handlers[notification.kind].length; i++) {
        			Notifications.handlers[notification.kind][i](notification.data);
        		}
        	}
        }
	},
	
	openWebSocket: function() {
        var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
        if (!WS) return;
        Notifications.websocket = new WS(Notifications.websocketURL);
        Notifications.websocket.onmessage = function(event) {Notifications.handleNotifications(JSON.parse(event.data));};
        Notifications.websocket.onerror = function() {}
        Notifications.websocket.onclose = function() {Notifications.websocket = null;}
	},
	
	listen: function(kind, fn) {
		if (Notifications.handlers[kind])
			Notifications.handlers[kind].push(fn);
		else
			Notifications.handlers[kind] = [fn];
	}
};