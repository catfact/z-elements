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
	// a synth
	var <echoSynth;

	*new { arg aServer, aBufferLength;
		^super.new.initBase(aServer, aBufferLength);
	}

	*sendSynthDefs {
		ZEchoBase.subclasses.do({arg class;
			class.postln;
			class.sendSynthDefs;
		});

	}

	// we don't call this `init` so that subclasses won't override
	initBase {
		arg aServer, aBufferLength;
		var bufferLength = if(aBufferLength.isNil, { defaultBufferLength }, { aBufferLength });

		server = if(aServer.isNil, { Server.default }, { aServer });
		buffer = Buffer.alloc(server, bufferLength * server.sampleRate, 2);

		/*		server.sync;
		buffer.zero;
		server.sync;*/

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

	setSynthControl { arg key, value;
		echoSynth.set(key, value);
	}


}
//
// ZEchoDefault : ZEchoBase {
//
// 	*new {
// 		arg aServer, aFxBus, aTarget, aAddAction, aBufferLength;
// 		^super.new(aServer, aBufferLength).init(
// 			aFxBus,
// 			if(aTarget.isNil, {aServer}, {aTarget}),
// 			if(aAddAction.isNil, {\addToTail}, {aAddAction})
// 		);
// 	}
//
// 	*sendSynthDefs { arg server;
// 		SynthDef.new(\ZEchoDefault, {
// 			var fxBus = \fxBus.kr;
// 			var input = In.ar(fxBus, 2);
// 			var levelLag = \levelLag.kr(0.05);
// 			var feedback = InFeedback.ar(\feedbackBus.kr, 2) * \feedbackLevel.kr(0, levelLag);
// 			var delayTime, delayed;
// 			delayTime = K2A.ar(\delayTime.kr(0.2));
// 			delayTime = Slew.ar(delayTime, \delayTimeSlewUp.kr(4), \delayTimeSlewDown.kr(4));
// 			delayTime = Lag.ar(delayTime, \delayTimeLag.kr(0.1));
// 			delayed = BufDelayC.ar(\buffer.ir, input + feedback, delayTime);
// 			ReplaceOut.ar(fxBus, delayed * \level.kr(1, levelLag));
// 			Out.ar(\feedbackBus.kr, delayed);
// 		}).send(server);
// 	}
//
// 	init { arg aFxBus, aTarget, aAddAction;
// 		fxBus = aFxBus;
// 		server.sync;
// 		echoSynth = Synth.new(\ZEchoDefault, [
// 			\buffer, buffer,
// 			\fxBus, fxBus,
// 			\feedbackBus, this.feedbackBus,
// 		], aTarget, aAddAction);
// 	}
//
// 	setDelayTime { arg aTime;
// 		echoSynth.set(\delayTime, aTime);
// 	}
//
// 	setFeedbackLevel { arg aLevel;
// 		echoSynth.set(\feedbackLevel, aLevel);
// 	}
//
// 	setOutputLevel { arg aLevel;
// 		echoSynth.set(\level, aLevel);
// 	}
//
// }



ZEchoSmooth : ZEchoBase {

	*new {
		arg aServer, aFxBus, aTarget, aAddAction, aBufferLength;
		^super.new(aServer, aBufferLength).init(
			aFxBus,
			if(aTarget.isNil, {aServer}, {aTarget}),
			if(aAddAction.isNil, {\addToTail}, {aAddAction})
		);
	}

	*sendSynthDefs { arg server;
		SynthDef.new(\ZEchoSmooth, {
			var fxBus = \fxBus.kr;
			var input = In.ar(fxBus, 2);
			var levelLag = \levelLag.kr(0.05);
			var feedback = InFeedback.ar(\feedbackBus.kr, 2) * \feedbackLevel.kr(0, levelLag);
			var delayTime, delayed;
			var phaseWrite, phaseRead;
			var phaseOffset; // in samples
			var buffer = \buffer.ir;
			var bufferFrames = BufFrames.kr(buffer);

			delayTime = K2A.ar(\delayTime.kr(0.2));

			delayTime = Slew.ar(delayTime, \delayTimeSlewUp.kr(2.5), \delayTimeSlewDown.kr(0.5));
			delayTime = Lag.ar(delayTime, \delayTimeLag.kr(0.05));

			input = input + feedback;
			delayed = BufDelayC.ar(buffer, input, delayTime);

			ReplaceOut.ar(fxBus, delayed * \level.kr(1, levelLag));
			Out.ar(\feedbackBus.kr, delayed);
		}).send(server);
	}

	init { arg aFxBus, aTarget, aAddAction;
		fxBus = aFxBus;
		server.sync;
		echoSynth = Synth.new(\ZEchoSmooth, [
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


ZEchoFeedbackCompand : ZEchoBase {

	var silenceSynth;
	var compandSynth;
	var <analysisBus;

	*new {
		arg aServer, aFxBus, aTarget, aAddAction, aBufferLength;
		^super.new(aServer, aBufferLength).init(
			aFxBus,
			if(aTarget.isNil, {aServer}, {aTarget}),
			if(aAddAction.isNil, {\addToTail}, {aAddAction})
		);
	}

	*sendSynthDefs { arg server;
		SynthDef.new(\ZEchoSmooth, {
			var fxBus = \fxBus.kr;
			var input = In.ar(fxBus, 2);
			var levelLag = \levelLag.kr(0.05);

			var feedback = InFeedback.ar(\feedbackBus.kr, 2) * \feedbackLevel.kr(0, levelLag);
			var delayTime, delayed;
			var phaseWrite, phaseRead;
			var phaseOffset; // in samples
			var buffer = \buffer.ir;
			var bufferFrames = BufFrames.kr(buffer);

			delayTime = K2A.ar(\delayTime.kr(0.2));
			delayTime = Slew.ar(delayTime, \delayTimeSlewUp.kr(2.5), \delayTimeSlewDown.kr(0.5));
			delayTime = Lag.ar(delayTime, \delayTimeLag.kr(0.05));

			input = input + feedback;

			delayed = Sanitize.ar(BufDelayC.ar(buffer, input, delayTime));

			ReplaceOut.ar(fxBus, delayed * \level.kr(1, levelLag));
			//Out.ar(\feedbackBus.kr, Sanitize.ar(delayed));
			Out.ar(\feedbackBus.kr, delayed);
		}).send(server);
	}

	init { arg aFxBus, aTarget, aAddAction;
		fxBus = aFxBus;
		analysisBus = Bus.audio(server, 2);
		server.sync;

		echoSynth = Synth.new(\ZEchoSmooth, [
			\buffer, buffer,
			\fxBus, fxBus,
			\feedbackBus, feedbackBus,
		], aTarget, aAddAction);

		/// NB: assumes that `ZCompand` has had synthdefs sent!
		compandSynth = Synth.new(\ZCompandStereo, [
			\bus, fxBus,
			\threshold, -12,
			\slopeAbove, 1,
			\slopeBelow, 100,
			\envAttack, 0.01,
			\envRelease, 0.2,
			\gainAttack, 2,
			\gainRelease, 2,
			\analysisBus, analysisBus
		], echoSynth, \addBefore);
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

	setCompandControl { arg key, value;
		compandSynth.set(key, value);
	}

}