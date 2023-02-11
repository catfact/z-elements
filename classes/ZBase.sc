/// ZStereoAuxLoop
/// a stereo effect loop, into which effects may be inserted
ZStereoAuxLoop {
	var <server;
	var <parent;
	var <bus;

	*new {
		arg server, parent=nil;
		^super.new.init(server, parent);
	}

	init { arg aServer, aParent;
		server = aServer;
		parent = aParent.isNil.if({server}, {aParent});

		bus = Dictionary.new;
		bus[\in] = Bus.audio(server, 2);
		bus[\out] = Bus.audio(server, 2);
	}
}


ZInsertEffect {
	var <bus;
	var <synth;
}

/// ZAudioContext
/// component holding audio boilerplate
ZAudioContext {
	var <server;

	var <>hwInNumChannels=1;
	var <>hwOutNumChannels=2;
	var <>hwInChannel=0;
	var <>hwOutChannel=0;

	/// dictionaries
	var <group;
	var <bus;
	var <patch;

	/// NB: must be constructed within a Thread! (or subclass like Routine, Task)
	*new { arg aServer;
		^super.new.init(aServer)
	}

	init {
		arg aServer=nil;
		server = if (aServer.isNil, { Server.default}, {aServer});

		SynthDef.new(\patch_mono,{ arg in, out=0, level=1;
			Out.ar(out, In.ar(in, 1)  *level);
		}).send(server);

		SynthDef.new(\patch_stereo,{ arg in, out=0, level=1;
			Out.ar(out, In.ar(in, 2)  *level);
		}).send(server);

		SynthDef.new(\patch_pan,{ arg in, out=0, level=1, pan=0;
			Out.ar(out, Pan2.ar(In.ar(in, 1)  *level, pan));
		}).send(server);

		SynthDef.new(\adc_mono, { arg in, out=0, level=1;
			Out.ar(out, SoundIn.ar(in, 1) * level);
		}).send(server);

		SynthDef.new(\adc_stereo, { arg in, out=0, level=1;
			Out.ar(out, SoundIn.ar(in, 2) * level);
		}).send(server);

		SynthDef.new(\adc_pan, { arg in, out=0, level=1, pan=0;
			Out.ar(out, Pan2.ar(SoundIn.ar(in, 1) * level, pan));
		}).send(server);

		server.sync;

		group = Dictionary.new;
		// group to hold (internal) input patches
		group[\in] = Group.new(server);
		// group to hold processing synths
		group[\process] = Group.after(group[\in]);
		// group to hold (internal) output patches
		group[\out] = Group.after(group[\out]);

		bus = Dictionary.new;
		bus[\hw_in] = Bus.audio(server, 2);
		bus[\hw_out] = Bus.audio(server, 2);

		// patch hardware I/O to busses
		patch = Dictionary.new;

		patch[\hw_in] = if (hwInNumChannels > 1, {
			postln("patching stereo HW input to main input bus");
			Synth.new(\adc_stereo, [
				\in, hwInChannel, \out, bus[\hw_in].index,
			], group[\in], \addBefore);
		}, {
			postln("panning mono HW input to main input bus");
			Synth.new(\adc_pan, [
				\in, hwInChannel, \out, bus[\hw_in].index

			], group[\in], \addBefore);
		});

		patch[\hw_out] = Synth.new(\patch_stereo, [
			\in, bus[\hw_out].index, \out, hwOutChannel
		], group[\out], \addAfter);

	}
}
