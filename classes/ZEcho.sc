ZEchoBase {

	classvar <defaultBufferLength = 8.0;

	// we need a server
	var <server;
	// we need an audio buffer
	var <buffer;
	// we operate as an insert effect, reading and writing to a single bus in place
	var <fxBus;
	// manage our own bus for feedback
	var <feedbackBus;
	var <echoSynth;

	*new { arg aServer, aBufferLength;
		^super.new.initBase(aServer, aBufferLength);
	}

	*sendSynthDefs {
		ZEchoBase.subclasses.do({arg class; class.postln; });
	}

	// we don't call this `init` so that subclasses won't override
	initBase {
		arg aServer, aBufferLength;
		var bufferLength = if(aBufferLength.isNil, { defaultBufferLength }, { aBufferLength });

		server = if(aServer.isNil, { Server.default }, { aServer });
		buffer = Buffer.alloc(server, bufferLength * server.sampleRate, 2);
		feedbackBus = Bus.audio(server, 2);
	}

	// subclasses should override
	setDelayTime { arg aTime;
		postln("ZEchoBase setDelayTime: " ++ aTime);
	}

		// subclasses should override
	setFeedbackLevel { arg aLevel;
		postln("ZEchoBase setFeedbackLevel: " ++ aLevel);
	}

}

ZEchoDefault : ZEchoBase {

	*new {
		arg aServer, aFxBus, aTarget, aAddAction, aBufferLength;
		^super.new(aServer, aBufferLength).init(
			aFxBus,
			if(aTarget.isNil, {aServer}, {aTarget}),
			if(aAddAction.isNil, {\addToTail}, {aAddAction})
		);
	}

	*sendSynthDefs { arg server;
		SynthDef.new(\ZEchoDefault, {
			var fxBus = \fxBus.kr;
			var input = In.ar(fxBus, 2);
			var levelLag = \levelLag.kr(0.05);
			var feedback = InFeedback.ar(\feedbackBus.kr, 2) * \feedbackLevel.kr(0, levelLag);
			var delayTime = \delayTime.kr(0.2);
			var delayed = BufDelayC.ar(\buffer.ir, input + feedback, delayTime);
			ReplaceOut.ar(fxBus, delayed * \level.kr(1, levelLag));
			Out.ar(\feedbackBus.kr, delayed);
		}).send(server);
	}

	init { arg aFxBus, aTarget, aAddAction;
		fxBus = aFxBus;
		server.sync;
		echoSynth = Synth.new(\ZEchoDefault, [
			\buffer, buffer,
			\fxBus, fxBus,
			\feedbackBus, this.feedbackBus,
		], aTarget, aAddAction);
	}

	setDelayTime { arg aTime;
		echoSynth.set(\delayTime, aTime);
	}

	setFeedbackLevel { arg aLevel;
		echoSynth.set(\feedbackLevel, aLevel);
	}


	setOutputLevel { arg aLevel;
		echoSynth.set(\level, aLevel);
	}

}