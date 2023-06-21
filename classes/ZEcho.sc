// base class for stereo echo effects
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

	*sendAllSynthDefs { arg aServer;
		ZEcho.allSubclasses.do({ arg class;
			var def = class.synthDef;
			def.class.postln;
			switch(def.class,
				{SynthDef}, {
					def.send(aServer);
					postln("sent synthdef: " ++ def.name);
				},
				{Array}, {
					postln("this is an array of synthdefs (?)");
					def.do({ arg theDef;
						postln(theDef);
						theDef.send(aServer);
						postln("sent synthdef: " ++ theDef.name);

					});
				},
				{Nil}, {}
			);
		});
	}


	// NB: assumed to be run inside a Routine/Task/Thread!
	// subclasses could override, but likely don't need to
	/// NB: param order was switched to match `Synth.new`
	init { arg aServer, aSynthArgs, aTargetNode, aMaxDelayTime;
		server = aServer;
		maxDelayTime = aMaxDelayTime;

		bus = Dictionary.new;
		bus[\in] = Bus.audio(server, 2);
		bus[\out] = Bus.audio(server, 2);

		this.initBuf;
		this.initSynth(aSynthArgs, aTargetNode);
	}

	// making more fine-grained initializers so that subclasses can override
	initBuf {
		buf = Buffer.alloc(server, server.sampleRate * maxDelayTime, 2);
		server.sync;
	}

	initSynth {
		arg aSynthArgs, aTargetNode;
		var synthArgs = [\in, bus[\in].index, \out, bus[\out].index, \buf, buf.bufnum];
		if (aSynthArgs.notNil, { synthArgs = synthArgs ++ aSynthArgs });
		synth = Synth.new(this.class.synthDef.name, synthArgs, aTargetNode);
	}

	// set a synth parameter
	set { arg key, value;
		synth.set(key, value);
	}

	setDelayTime { arg value; this.set(\delayTime, value); }
	setFeedbacKLevel { arg value; this.set(\feedbackLevel, value); }
}


//--------------------------------------------------------
//----- ZEcho subclasses

///------------------------------
/// "off the shelf"
ZEchoDefault : ZEcho {
	classvar <synthDef;

	*initClass {
		synthDef = SynthDef.new(\ZEchoDefault, {
			Out.ar(\out.kr(0), BufDelayC.ar(\buf.kr, In.ar(\in.ar, 2), \delayTime.kr(1)) * \level.kr(1));
		});
	}
}

///------------------------------
/// simplest granular
ZEchoDefaultGranular : ZEcho {
	classvar <synthDef;


	*initClass {
		synthDef = SynthDef.new(\ZEchoDefaultGranular, {
			// grain ugens only take mono buffers!
			// more elegant workarounds exist, but this came to mind first
			var buf1 = \buf1.kr;
			var buf2 = \buf2.kr;

			// assuming buffers have same frame count
			var bufFrames = BufFrames.kr(buf1);
			// write head position, in frames
			var writePhase = Phasor.ar(end:bufFrames);

			// stereo input
			var input = In.ar(\in.kr, 2);
			// read head follows behind write head by this number of frames
			var offsetFrames = \delayTime.kr(1) * SampleRate.ir;
			var readPhase = (writePhase - offsetFrames);
			// grain ugens take a normalized position into the buffer (!)
			var readPos = (readPhase / bufFrames).wrap(0, 1);

			// rate at which to produce grains
			var grainRate = \grainRate.kr(10);
			// add some noise to the rate
			var grainNoiseRatio = 1 + (\grainRateNoiseFactor.kr(0.01) * LFNoise0.kr(grainRate));
			// pulse sequence to trigger grains
			var grainTrigger = LFPulse.ar(grainRate * grainNoiseRatio);

			// the grains! array of 2x mono signals
			var grains = [
				BufGrain.ar(
					grainTrigger,
					\grainDuration.kr(0.2),
					\buf1.kr,
					\rate.kr(1),
					readPos,       // normalized
					4              // interpolation
				),
				BufGrain.ar(
					grainTrigger,
					\grainDuration.kr(0.2),
					\buf1.kr,
					\rate.kr(1),
					readPos,
					4
				),
			];

			// add feedback
			// beware! overlapping grains adds gain before feedback
			var feedback = grains * \feedbackLevel.kr(0);
			var inputMix = input + feedback;

			// write to buffer
			BufWr.ar(inputMix[0], buf1, writePhase);
			BufWr.ar(inputMix[1], buf2, writePhase);

			Out.ar(\out.kr(0), grains * \level.kr(1));
		});
	}

	initBuf {
		buf =  Array.fill(2, {Buffer.alloc(server, server.sampleRate * maxDelayTime, 1)});
		server.sync;
	}

	initSynth {
		arg aSynthArgs, aTargetNode;
		var synthArgs = [\in, bus[\in].index, \out, bus[\out].index,
			\buf1, buf[0].bufnum, \buf2, buf[1].bufnum];
		if (aSynthArgs.notNil, { synthArgs = synthArgs ++ aSynthArgs });
		synth = Synth.new(this.class.synthDef.name, synthArgs, aTargetNode);
	}
}
