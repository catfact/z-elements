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

	/// the Server!
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
		SynthDef.new(\patch_xfade, {
			var snd = SelectX.ar(\pos.kr(0), In.ar(\in.kr, 2));
			Out.ar(\out.kr(0), snd * \level.kr(1, patchLevelLagTime));
		}).send(server);

		SynthDef.new(\patch_mono, {
			Out.ar(\out.kr(0), In.ar(\in.kr, 1) * \level.kr(1, patchLevelLagTime));
		}).send(server);

		SynthDef.new(\patch_stereo, {
			Out.ar(\out.kr,
				In.ar(\in.kr, 2) * \level.kr(1, patchLevelLagTime));
		}).send(server);

		SynthDef.new(\patch_pan, {
			var snd = In.ar(\in.kr, 1) * \level.kr(1, patchLevelLagTime);
			Out.ar(\out.kr, Pan2.ar(snd, \pos.kr(0, patchLevelLagTime)));
		}).send(server);

		SynthDef.new(\adc_mono, {
			Out.ar(\out.kr, SoundIn.ar(\in.kr(0)) * \level.kr(1, \levelLag.kr(patchLevelLagTime)));
		}).send(server);

		SynthDef.new(\adc_stereo, {
			var in = \in.kr(0);
			Out.ar(\out.kr, SoundIn.ar([in, in+1]) * \level.kr(1, patchLevelLagTime));
		}).send(server);

		SynthDef.new(\adc_pan, {
			var snd = SoundIn.ar(\in.kr(0), 1) * \level.kr(1, \levelLag.kr(patchLevelLagTime));
			Out.ar(\out.kr, Pan2.ar(snd, \pos.kr(0, patchLevelLagTime)));
		}).send(server);

		server.sync;

		/// groups
		group = Dictionary.new;
		// group to hold input patches
		group[\in] = Group.new(server);
		// group to hold processing synths
		group[\process] = Group.after(group[\in]);
		// group to hold output patches
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
