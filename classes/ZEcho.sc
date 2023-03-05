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

	/// constructor
	// NB: assumed to be run inside a Routine/Task/Thread!
	// subclasses could override, but likely don't need to
	init { arg aServer, aTargetNode, aSynthArgs, aMaxDelayTime=nil;
		var synthArgs, synthDef;

		server = aServer;
		maxDelayTime = if(aMaxDelayTime.notNil, { aMaxDelayTime}, { defaultMaxDelayTime });

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


	//-----------------------------------------
	/// instance methods
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


///------------------------------
/// a naive starting place

ZEchoNaive : ZEcho
{
	classvar <synthDef;

	*initClass {
		synthDef = SynthDef.new(\ZEchoNaive, {
			arg buf, in, out, level=1, time=1, feedback=0;

			var input, output;
			var phaseRd, phaseWr, phaseOffset;

			input = In.ar(in, 2);

			phaseRd = Phasor.ar(end: BufFrames.ir(buf));
			phaseOffset = time * SampleRate.ir;
			phaseWr = phaseRd + phaseOffset;

			output = BufRd.ar(2, buf, phaseRd);
			input = input + (feedback * output);
			BufWr.ar(input, buf, phaseWr);

			Out.ar(out, output * level);
		});
	}
}



ZEchoNaiveMod : ZEcho
{
	classvar <synthDef;

	*initClass {
		synthDef = SynthDef.new(\ZEchoNaive, {
			arg buf, in, out, level=1, time=1, feedback=0, modTime=1;

			var input, output;
			var phaseRd, phaseWr, phaseOffset, bufFrames;

			bufFrames = BufFrames.ir(buf);

			input = In.ar(in, 2);

			phaseWr = Phasor.ar(end: BufFrames.ir(buf));
			phaseOffset = time * SampleRate.ir;
			phaseOffset = Lag.ar(K2A.ar(phaseOffset), modTime);
			phaseOffset = phaseOffset.min(bufFrames);
			phaseRd = phaseWr + bufFrames - phaseOffset;

			output = BufRd.ar(2, buf, phaseRd);
			input = input + (feedback * output);
			BufWr.ar(input, buf, phaseWr);

			Out.ar(out, output * level);
		});
	}
}


ZEchoSlewMod : ZEcho
{
	classvar <synthDef;

	*initClass {
		synthDef = SynthDef.new(\ZEchoSlewMod, {
			arg buf, in, out, level=1, time=1, feedback=0, lagTime=0.25, slewRateLimitUp=2, slewRateLimitDown=3;

			var input, output;
			var phaseRd, phaseWr, phaseOffset, bufFrames;


			bufFrames = BufFrames.ir(buf);

			input = In.ar(in, 2);

			phaseWr = Phasor.ar(end: BufFrames.ir(buf));
			phaseOffset = K2A.ar(time * SampleRate.ir);

			phaseOffset = Slew.ar(phaseOffset, slewRateLimitUp*SampleRate.ir, slewRateLimitDown*SampleRate.ir);
			phaseOffset = Lag.ar(phaseOffset, lagTime);
			phaseOffset = phaseOffset.min(bufFrames);
			phaseRd = phaseWr + bufFrames - phaseOffset;

			output = BufRd.ar(2, buf, phaseRd);
			input = input + (feedback * output);
			BufWr.ar(input, buf, phaseWr);

			Out.ar(out, output * level);
		});
	}
}


