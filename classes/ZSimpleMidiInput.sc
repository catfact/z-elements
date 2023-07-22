/// connect to a single MIDI input device and provide glue functions
ZSimpleMidiInput {
	var <port, <dev;
	var <ccVal;
	var <ccFunc; // array of functions

	var <>verbose = false;
	var <>channel = nil;

	*new { arg deviceName=nil, portName=nil, connectAll=false;
		^super.new.init(deviceName, portName, connectAll);
	}

	init {
		arg deviceName, portName, connectAll;
		MIDIClient.init;

		if (connectAll, {
			MIDIIn.connectAll;
		}, {
			var endpoint = MIDIIn.findPort(deviceName, portName);
			if (endpoint.isNil, {
				postln ("ERROR: couldn't open device and port: " ++ deviceName ++ ", " ++ portName);
			});
		});

		ccFunc = Array.newClear(128);

		MIDIIn.addFuncTo(\control, { arg uid, chan, num, val;
			if (channel.isNil || (chan == channel), {
				if (verbose, { [uid, chan, num, val].postln; });
				if (ccFunc[num].notNil, { ccFunc[num].value(val); });
			});
		});
	}

	// set a handler for a given CC numnber
	cc { arg num, func;
		ccFunc[num] = func;
	}
}