ZEcho {

	var <server;
	var <synth;
	var <bus;
	var <buf;
	var <maxDelayTime;

	classvar <>defaultMaxDelayTime;

	*initClass {
		defaultMaxDelayTime = 8;
	}

	*new {
		arg server, targetNode, synthArgs, maxDelayTime;
		if (maxDelayTime.isNil, { maxDelayTime = defaultMaxDelayTime; });
		^super.new.init(server, targetNode, synthArgs, maxDelayTime);
	}

	*addAllSynthDefs { arg aServer;
		ZEcho.allSubclasses.do({ arg class;
			postln("adding synthdef for ZEcho subclass: " ++ class.asString);
			class.synthDef.send(aServer);
		});
	}


	// NB: assumed to be run inside a Routine/Task/Thread!
	// subclasses could override, but likely don't need to
	init { arg aServer, aTargetNode, aSynthArgs, aMaxDelayTime;
		var synthArgs, synthDef;

		server = aServer;
		maxDelayTime = aMaxDelayTime;

		bus = Dictionary.new;
		bus[\in] = Bus.audio(server, 2);
		bus[\out] = Bus.audio(server, 2);

		buf = Buffer.alloc(server, server.sampleRate * maxDelayTime, 2);

		server.sync;

		synthArgs = [\in, bus[\in].index, \out, bus[\out].index, \buf, buf.bufnum];
		if (aSynthArgs.notNil, { synthArgs = synthArgs ++ aSynthArgs });
		synthDef = this.class.synthDef;
		synth = Synth.new(synthDef.name, synthArgs, aTargetNode);
	}

	setDelayTime { arg time;
		this.setSynthParam(\time, time);
	}

	setFeedbackLevel { arg level;
		this.setSynthParam(\feedback, level);
	}

	setSynthParam { arg key, value;
		synth.set(key, value);
	}
}
