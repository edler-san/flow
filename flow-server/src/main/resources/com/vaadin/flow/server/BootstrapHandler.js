(function() {
	var apps = {};
	var widgetsets = {};
		
		
	var log;
	if (typeof console === undefined || !window.location.search.match(/[&?]debug(&|$)/)) {
		/* If no console.log present, just use a no-op */
		log = function() {};
	} else if (typeof console.log === "function") {
		/* If it's a function, use it with apply */
		log = function() {
			console.log.apply(console, arguments);
		};
	} else {
		/* In IE, its a native function for which apply is not defined, but it works
		 without a proper 'this' reference */
		log = console.log;
	}
	
	var isWidgetsetLoaded = function(widgetset) {
		var className = widgetset.replace(/\./g, "_");
		return (typeof window[className]) != "undefined";
	};
	
	var loadWidgetset = function(url, widgetset) {
		if (widgetsets[widgetset]) {
			return;
		}
		log("load widgetset", url, widgetset);
		setTimeout(function() {
			if (!isWidgetsetLoaded(widgetset)) {
				alert("Failed to load the widgetset: " + url);
			}
		}, 15000);
	
		var scriptTag = document.createElement('script');
		scriptTag.setAttribute('type', 'text/javascript');
		scriptTag.setAttribute('src', url);
		document.getElementsByTagName('head')[0].appendChild(scriptTag);
		
		widgetsets[widgetset] = {
			pendingApps: []
		};
	};
	
	var isInitializedInDom = function(appId) {
		var appDiv = document.getElementById(appId);
		if (!appDiv) {
			return false;
		}
		for ( var i = 0; i < appDiv.childElementCount; i++) {
			var className = appDiv.childNodes[i].className;
			/* If the app div contains a child with the class
			"v-app-loading" we have only received the HTML
			but not yet started the widget set
			(UIConnector removes the v-app-loading div). */
			if (className && className.indexOf("v-app-loading") != -1) {
				return false;
			}
		}
		return true;
	};
	
	/* 
	 * Needed for Testbench compatibility, but prevents any Vaadin 7 app from
	 * bootstrapping unless the legacy vaadinBootstrap.js file is loaded before
	 * this script.
	 */
    window.Vaadin = window.Vaadin || {};

	window.Vaadin.Flow = window.Vaadin.Flow || {
        clients: {},

		initApplication: function(appId, config) {
			var testbenchId = appId.replace(/-\d+$/, '');
			
			if (apps[appId]) {
				if (window.Vaadin && window.Vaadin.Flow && window.Vaadin.Flow.clients && window.Vaadin.Flow.clients[testbenchId] && window.Vaadin.Flow.clients[testbenchId].initializing) {
					throw "Application " + appId + " is already being initialized";
				}
				if (isInitializedInDom(appId)) {
					throw "Application " + appId + " already initialized";
				}
			}
	
			log("init application", appId, config);
			
			window.Vaadin.Flow.clients[testbenchId] = {
					isActive: function() {
						return true;
					},
					initializing: true
			};
			
			var getConfig = function(name) {
				var value = config[name];
				return value;
			};
			
			/* Export public data */
			var app = {
				getConfig: getConfig
			};
			apps[appId] = app;
			
			if (!window.name) {
				window.name =  appId + '-' + Math.random();
			}
	
			var widgetset = "client";
			widgetsets[widgetset] = {
					pendingApps: []
				};
			if (widgetsets[widgetset].callback) {
				log("Starting from bootstrap", appId);
				widgetsets[widgetset].callback(appId);
			}  else {
				log("Setting pending startup", appId);
				widgetsets[widgetset].pendingApps.push(appId);
			}
	
			return app;
		},
		getAppIds: function() {
			var ids = [ ];
			for (var id in apps) {
				if (apps.hasOwnProperty(id)) {
					ids.push(id);
				}
			}
			return ids;
		},
		getApp: function(appId) {
			return apps[appId];
		},
		registerWidgetset: function(widgetset, callback) {
			log("Widgetset registered", widgetset);
			var ws = widgetsets[widgetset];
			if (ws && ws.pendingApps) {
				ws.callback = callback;
				for(var i = 0; i < ws.pendingApps.length; i++) {
					var appId = ws.pendingApps[i];
					log("Starting from register widgetset", appId);
					callback(appId);
				}
				ws.pendingApps = null;
			}
		},
        getBrowserDetailsParameters: function() {
            var params = {  };

            /* Screen height and width */
            params['v-sh'] = window.screen.height;
            params['v-sw'] = window.screen.width;

            /* Current time */
            var date = new Date();
            params['v-curdate'] = date.getTime();

            /* Current timezone offset (including DST shift) */
            var tzo1 = date.getTimezoneOffset();

            /* Compare the current tz offset with the first offset from the end
            of the year that differs --- if less that, we are in DST, otherwise
            we are in normal time */
            var dstDiff = 0;
            var rawTzo = tzo1;
            for(var m = 12; m > 0; m--) {
                date.setUTCMonth(m);
                var tzo2 = date.getTimezoneOffset();
                if (tzo1 != tzo2) {
                    dstDiff = (tzo1 > tzo2 ? tzo1 - tzo2 : tzo2 - tzo1);
                    rawTzo = (tzo1 > tzo2 ? tzo1 : tzo2);
                    break;
               }
            }

            /* Time zone offset */
            params['v-tzo'] = tzo1;

            /* DST difference */
            params['v-dstd'] = dstDiff;

            /* Time zone offset without DST */
            params['v-rtzo'] = rawTzo;

            /* DST in effect? */
            params['v-dston'] = (tzo1 != rawTzo);

            /* Time zone id (if available) */
            try {
                params['v-tzid'] = Intl.DateTimeFormat().resolvedOptions().timeZone;
            } catch (err) {
                params['v-tzid'] = '';
            }

            /* Window name */
            if (window.name) {
                params['v-wn'] = window.name;
            }

            /* Detect touch device support */
            var supportsTouch = false;
            try {
                document.createEvent("TouchEvent");
                supportsTouch = true;
            } catch (e) {
                /* Chrome and IE10 touch detection */
                supportsTouch = 'ontouchstart' in window
                   || (typeof navigator.msMaxTouchPoints !== 'undefined');
            }
            params['v-td'] = false;

            /* Stringify each value (they are parsed on the server side) */
            Object.keys(params).forEach(function(key) {
                var value = params[key];
                if (typeof value !== 'undefined') {
                    params[key] = value.toString();
                }
            });
            return params;
        }
	};
	
	log('Flow bootstrap loaded');
	
	{{GWT_STAT_EVENTS}}
	
	var uidl = {{INITIAL_UIDL}};
	var config = {{CONFIG_JSON}};
	config.uidl = uidl;

    window.Vaadin.Flow.initApplication("{{APP_ID}}", config);
})();
