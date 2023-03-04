
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

// just a stereo bus and patches
ZStereoAuxLoop {
	var <bus, <synth, context;

	*new { arg context;
		^super.new.init(context)
	}

	init { arg aContext;
		context = aContext;
		bus = Dictionary.newFrom([
			\in, Bus.audio(context.server, 2),
			\out, Bus.audio(context.server, 2)
		]);

		synth = Dictionary.newFrom([
			\in, Synth.new(\patch_stereo, [
				\in, context.bus[\hw_in], \out, bus[\in]
			], context.group[\in]),

			\out, Synth.new(\patch_stereo, [
				\out, context.bus[\hw_out], \in, bus[\out]
			], context.group[\out]),
		]);
	}
}

// base / wrapper class for our effects
// this can be subclassed or simply instanced with a given synthdef
// synthdefs are assumed to operate on a bus in-place  (e.g. using `ReplaceOut`)
ZStereoInsertEffect {
	var <synth, <def, bus;
	*new { arg def, bus, args;
		^super.new.init(def, bus, args)
	}

	init { arg aDef, aBus, target=nil, aArgs=[];
		def = aDef;
		bus = aBus;
		synth = Synth.new(def.asSymbol, [\bus, bus] ++ aArgs, if(target.notNil, {target}, {Server.default}));
	}

}

// simple MIDI input boilerplate
ZMidiInput {

	var <midifunc;
	var <handler;

	*new { arg deviceName;
		^super.new.init(deviceName)
	}

	init { arg aDeviceName;

		MIDIIn.connectAll;

		/// user or subclass should redefine these!
		handler = Dictionary.newFrom([
			\noteon, { arg ... args; (["noteon"] ++ args).postln; },
			\noteoff, { arg ... args; (["noteoff"] ++ args).postln; },
			\cc, { arg ... args; (["cc"] ++ args).postln; },
			\pitchbend, { arg ... args; (["pitchbend"] ++ args).postln; },
		]);

		midifunc = Dictionary.newFrom([
			\noteon, MIDIFunc.noteon({ arg ...args; handler[\noteon].value(args)}, nil),
			\noteoff, MIDIFunc.noteon({ arg ...args; handler[\noteoff].value(args)}, nil),
			\cc, MIDIFunc.noteon({ arg ...args; handler[\cc].value(args)}, nil),
			\pitchbend, MIDIFunc.noteon({ arg ...args; handler[\pitchbend].value(args)}, nil),
		]);
	}

	free {
		midifunc.do({ arg func, i; func.free; });
	}


}
