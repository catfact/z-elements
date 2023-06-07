/// ZAudioContext
// component holding audio boilerplate.
// its main functions are to provide:
// - a stereo audio I/O environment, even with mono input devices
// - some very basic and common synthdefs
// - basic structure for node execution order
ZAudioContext {
	// lag time for level controls in patch synths.
	// note that this only takes effect at class initialization time (effectively constant)
	classvar <patchLevelLagTime = 0.05;

	// the Server!
	var <server;

	/// input and output channel
	var <hwInNumChannels;
	var <hwOutNumChannels;
	var <hwInChannelOffset;
	var <hwOutChannelOffset;

	/// dictionaries
	var <group;
	var <bus;
	var <patch;

	// test input loop
	var <testInputSynth;
	var <testInputBuffer;

	/// NB: must be constructed within a Thread! (or subclass like Routine, Task)
	*new { arg server,
		hwInNumChannels=1,
		hwOutNumChannels=2,
		hwInChannelOffset=0,
		hwOutChannelOffset=0;

		^super.newCopyArgs(server, hwInNumChannels, hwOutNumChannels, hwInChannelOffset, hwOutChannelOffset).init;
	}

	init {

		/// synthdefs
		SynthDef.new(\patch_mono,{
			Out.ar(\out.kr(0), In.ar(\in.kr, 1) * \level.kr(1, \levelLag.kr(patchLevelLagTime)));
		}).send(server);

		SynthDef.new(\patch_stereo,{
			var levelLag = \levelLag.kr(patchLevelLagTime);
			Out.ar(\out.kr,
				In.ar(\in.kr, 2) * \level.kr(1, levelLag));
		}).send(server);

		SynthDef.new(\patch_pan,{
			var snd = In.ar(\in.kr, 1) * \level.kr(1, \levelLag.kr(patchLevelLagTime));
			Out.ar(\out.kr, Pan2.ar(snd, \pan.kr(0, \panLag.kr(patchLevelLagTime))));
		}).send(server);

		SynthDef.new(\adc_mono, {
			Out.ar(\out.kr, SoundIn.ar(\in.kr(0)) * \level.kr(1, \levelLag.kr(patchLevelLagTime)));
		}).send(server);

		SynthDef.new(\adc_stereo, {
			var levelLag = \levelLag.kr(patchLevelLagTime);
			var in = \in.kr(0);
			Out.ar(\out.kr, SoundIn.ar([in, in+1]) * \level.kr(1, levelLag));
		}).send(server);

		SynthDef.new(\adc_pan, {
			var snd = SoundIn.ar(\in.kr(0), 1) * \level.kr(1, \levelLag.kr(patchLevelLagTime));
			Out.ar(\out.kr, Pan2.ar(snd, \pan.kr(0, \panLag.kr(patchLevelLagTime))));
		}).send(server);

		server.sync;

		/// groups
		group = Dictionary.new;
		// group to hold (internal) input patches
		group[\in] = Group.new(server);
		// group to hold processing synths
		group[\process] = Group.after(group[\in]);
		// group to hold (internal) output patches
		group[\out] = Group.after(group[\out]);

		/// busses
		bus = Dictionary.new;
		bus[\hw_in] = Bus.audio(server, 2);
		bus[\hw_out] = Bus.audio(server, 2);

		/// I/O patch synths
		patch = Dictionary.new;
		patch[\hw_in] = if (hwInNumChannels > 1, {
			postln("patching stereo HW input to main input bus");
			Synth.new(\adc_stereo, [
				\in, hwInChannelOffset, \out, bus[\hw_in].index,
			], group[\in], \addBefore);
		}, {
			postln("panning mono HW input to main input bus");
			Synth.new(\adc_pan, [
				\in, hwInChannelOffset, \out, bus[\hw_in].index
			], group[\in], \addBefore);
		});

		patch[\hw_out] = Synth.new(\patch_stereo, [
			\in, bus[\hw_out].index, \out, hwOutChannelOffset
		], group[\out], \addAfter);
	}

	// play a soundfile to the hardware input bus, allocating needed resources
	playTestInputLoop { arg soundFilePath;
		Routine {
			if (testInputBuffer.notNil, {
				testInputBuffer.free;
			});
			if (testInputSynth.notNil, {
				testInputSynth.free;
			});
			server.sync;
			testInputBuffer = Buffer.read(server, soundFilePath);
			server.sync;
			testInputSynth = {
				Out.ar(bus[\hw_in], \level.kr(1.0) *
					if (testInputBuffer.numChannels < 2, {
						Pan2.ar(PlayBuf.ar(1, testInputBuffer, loop:1), 0)
					}, {
						PlayBuf.ar(2, testInputBuffer, loop:1)
					})
				)
			}.play(group[\in])
		}.play;
	}

	// if playing a soundfile to HW input bus, stop and free resources
	stopTestInputLoop {
		if (testInputSynth.notNil, {
			testInputSynth.free;
		});
		if (testInputBuffer.notNil, {
			testInputBuffer.free;
		});
	}
}

/// ZStereoFxLoop
// given a ZAudioContext,
// this maintains an effected bus (`fx`) between the hardware ins and outs,
// and controls and a dry/wet balance in addition to overall level
// synths or classes can treat the fx bus as an insert using `ReplaceOut`
ZStereoFxLoop {
	var <context; // an instance of ZAudioContext
	var <fxBus;
	var <patch;

	var <dryLevel, <wetLevel, <mainLevel=1;

	*new {
		arg context;
		^super.newCopyArgs(context).init;
	}

	init {

		fxBus = Bus.audio(context.server, 2);

		patch = Dictionary.new;

		patch[\in] = Synth.new(\patch_stereo, [
			\in, context.bus[\hw_in], \out, fxBus
		], context.group[\in], \addAfter);

		patch[\dry] = Synth.new(\patch_stereo, [
			\in, context.bus[\hw_in], \out, context.bus[\hw_out],
		], context.group[\out], \addBefore);

		patch[\wet] = Synth.new(\patch_stereo, [
			\in, fxBus, \out, context.bus[\hw_out],
		], context.group[\out], \addBefore);
	}


	/// set the wet/dry levels directly
	setWetLevel { arg level;
		wetLevel = level;
		patch[\wet].set(\level, wetLevel * mainLevel);
	}

	setDryLevel { arg level;
		dryLevel = level;
		patch[\dry].set(\level, dryLevel * mainLevel);
	}

	setMainLevel { arg level;
		mainLevel = level;
		patch[\wet].set(\level, wetLevel * mainLevel);
		patch[\dry].set(\level, dryLevel * mainLevel);
	}

	*setBalance { arg position, equalPower=true;
		var l, r;
		if (equalPower, {
			position = position.linlin(-1, 1, 0, pi/2);
			l = cos(position);
			r = sin(position);
		}, {
			l = position.linlin(-1, 1, 1, 0);
			r = position.linlin(-1, 1, 0, 1);
		});
		^[l, r]
	}

	/// set the stereo balance of wet / dry signal
	// position in [-1, 1]
	setDryBalance { arg position, equalPower=true;
		var lr = ZStereoFxLoop.setBalance(position, equalPower);
		patch[\dry].set(\leftLevel, lr[0], \rightLevel, lr[1]);
	}

	setWetBalance { arg position, equalPower=true;
		var lr = ZStereoFxLoop.setBalance(position, equalPower);
		patch[\wet].set(\leftLevel, lr[0], \rightLevel, lr[1]);
	}
}


/// connect to a single MIDI input device and provide glue functions
ZSimpleMidiControl {
	var <port, <dev;
	var <ccFunc; // array of functions
	var <noteOnFunc;
	var <noteOffFunc;

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
				if (ccFunc[num].notNil, {
					ccFunc[num].value(val);
				});
			});
		});

		MIDIIn.addFuncTo(\noteOn, { arg uid, chan, num, vel;
			if (channel.isNil || (chan == channel), {
				if (verbose, { [uid, chan, num, vel].postln; });
				if (noteOnFunc.notNil, { noteOnFunc.value(num, vel); });
			});
		});


		MIDIIn.addFuncTo(\noteOff, { arg uid, chan, num, vel;
			if (channel.isNil || (chan == channel), {
				if (verbose, { [uid, chan, num, vel].postln; });
				if (noteOffFunc.notNil, { noteOffFunc.value(num, vel); });
			});
		});
	}

	// set a handler for a given CC numnber
	cc { arg num, func;
		ccFunc[num] = func;
	}

	noteon { arg func;
		noteOnFunc = func;
	}

	noteoff { arg func;
		noteOffFunc = func;
	}
}


