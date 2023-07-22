// a multi-tap delay processor
// mono in, stereo out
ZMultiTap {
	// buffer duration for all instances
	classvar <>bufferDuration = 32.0;
	classvar <>shapeBufferSize = 2048;
	classvar <>numChebyBufs= 9;

	// how many taps
	var <numTaps;

	// mono buffer
	var <buffer;
	// shape buffers
	var <shapeBufferList;

	// each of these is a Dictionary collecting like objects
	var <synth;
	var <group;
	var <bus;

	*sendSynthDefs {
		arg server;

		// read from mono bus, write to buffer, output phase
		SynthDef.new(\ZTaps_Write, {
			var buf = \buf.kr;
			var bufFrames = BufFrames.kr(buf);
			var phase = Phasor.ar(end:bufFrames);
			var input = In.ar(\in.kr);
			BufWr.ar(input, buf, phase);
			Out.ar(\out.kr, phase);
		}).send(server);

		// read from buffer, read from output phase, output to mono bus
		SynthDef.new(\ZTaps_Read, {
			var delayTime, phaseOffset, readPhase, output;
			var buf = \buf.kr;

			var delayTimeSlewUp = \delayTimeSlewUp.kr(2);
			var delayTimeSlewDown = \delayTimeSlewDown.kr(1);
			var inWritePhase = \inWritePhase.kr;
			var bufFrames = BufFrames.kr(buf);
			var writePhase = In.ar(inWritePhase);
			delayTime = Lag.ar(K2A.ar(\delayTime.kr(1)), 1);
			delayTime = Slew.ar(delayTime, delayTimeSlewUp, delayTimeSlewDown);
			phaseOffset = delayTime * SampleRate.ir;
			readPhase = (writePhase - phaseOffset).wrap(0, bufFrames);
			output = BufRd.ar(1, buf, readPhase, interpolation:4);
			Out.ar(\out.kr, output * \level.kr(0));
		}).send(server);

		SynthDef.new(\ZTaps_Filter, {
			var shapeParamLag = \shapeParamLag.kr(0.1);
			var shapeSelect = Lag.ar(K2A.ar(\shapeSelect.kr(0)), shapeParamLag);
			var shapeFocus = Lag.ar(K2A.ar(\shapeFocus.kr(1)), shapeParamLag);
			var bus = \bus.kr(0);
			var input = In.ar(bus) * \inputGain.kr(1);
			var shapeChannels = Array.fill(numChebyBufs, {
				arg order;
				var cutoff = (order + 1.125).reciprocal * (SampleRate.ir / 2);
				var filtered = LPF.ar(LPF.ar(LPF.ar(input, cutoff), cutoff), cutoff);
				/// TODO: balance each channel with a more wideband saturator (also BL'd)
				Shaper.ar(\buf.kr + order, filtered)

			});
			var output = SelectXFocus.ar(shapeSelect, shapeChannels, shapeFocus);
			output = LeakDC.ar(output);
			ReplaceOut.ar(bus, output);
		}).send(server);
	}

	*new {
		arg numTaps, context;
		^super.newCopyArgs(numTaps).init(context);
	}

	init {
		arg context; // a ZAudioContext
		var s = context.server;

		buffer = Buffer.alloc(s, s.sampleRate * bufferDuration, 1);

		////
		/// FIXME: should allocate adjacent buffers explicitly, then fill them,
		/// so we know FOR SURE they have sequential bufnums
		shapeBufferList = List.new;
		numChebyBufs.do({ arg n;
			var buf =Buffer.alloc(s, shapeBufferSize, 1, {
				arg buf;
				var partials = Array.fill(n+1, { arg i;
					if(i == n, {1}, {0})
				});
				postln("partials for order " ++ n ++ ": " ++ partials);
				buf.chebyMsg(partials);
			});
			s.sync;
			shapeBufferList.add(buf);
		});

		// NB: must be initialized inside a Thread/Task/Routine
		s.sync;

		bus = Dictionary.new;
		group = Dictionary.new;
		synth = Dictionary.new;

		bus[\input] = Bus.audio(s, 1);
		bus[\writePhase] = Bus.audio(s, 1);
		bus[\tap] = Array.fill(numTaps, {
			Bus.audio(s, 1);
		});

		group[\write] = Group.new(context.group[\process]);
		group[\tap] = Group.after(group[\write]);
		group[\filter] = Group.after(group[\tap]);
		group[\pan] = Group.after(group[\filter]);
		group[\output] = Group.after(group[\pan]);

		synth[\input] = Synth.new(\patch_xfade, [
			\in, context.bus[\hw_in],
			\out, bus[\input]
		], group[\write], \addBefore);

		synth[\write] = Synth.new(\ZTaps_Write, [
			\buf, buffer,
			\in, bus[\input],
			\out, bus[\writePhase]
		], group[\write]);

		synth[\tap] = Array.fill(numTaps, { arg i;
			Synth.new(\ZTaps_Read, [
				\buf, buffer,
				\inWritePhase, bus[\writePhase],
				\out, bus[\tap][i],
				\delayTime, (i+1)*2
			], group[\tap]);
		});

		synth[\filter] = Array.fill(numTaps, { arg i;
			Synth.new(\ZTaps_Filter, [
				\buf, shapeBufferList[0],
				\bus, bus[\tap][i],
			], group[\filter]);
		});

		synth[\pan] = Array.fill(numTaps, { arg i;
			Synth.new(\patch_pan, [
				\in, bus[\tap][i],
				\out, context.bus[\hw_out],
				\pos, i.linlin(0, numTaps-1, -0.8, 0.8)
			], group[\pan]);
		});
	}

	setTapLevel { arg index, level;
		synth[\tap][index].set(\level, level);
	}

	setTapDelay { arg index, seconds;
		synth[\tap][index].set(\delayTime, seconds);
	}

	setTapPosition { arg index, position;
		synth[\pan][index].set(\pos, position);
	}

	setShapeSelect{ arg index, value;
		synth[\filter][index].set(\shapeSelect, value);
	}

	setShapeFocus{ arg index, value;
		synth[\filter][index].set(\shapeFocus, value);
	}

	// position in [-1, 1]
	setInputBalance { arg position;
		synth[\input].set(\pos, position.linlin(-1, 1, 0, 0.5));
	}

}