// a multi-tap delay processor
// mono in, stereo out
ZMultiTap {
	// mono buffer
	classvar bufferDuration = 32.0;
	var <buffer;
	// how many taps
	var <numTaps;
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
			BufWr.ar(input, buf);
			Out.ar(\out.kr, phase);
		}).send(server);

		// read from buffer, read from output phase, output to mono bus
		SynthDef.new(\ZTaps_Read, {
			var buf = \buf.kr;
			var bufFrames = BufFrames.kr(buf);
			var writePhase = In.ar(\writePhaseBus.kr);
			var phaseOffset = \delayTime.kr(1) * SampleRate.ir;
			var readPhase = (writePhase - phaseOffset).wrap(0, bufFrames);
			var output = BufRd.ar(1, buf, readPhase);
			Out.ar(\out.kr, output * \level.kr(1));
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

		bus = Dictionary.new;

		bus[\input] = Bus.audio(s, 1);
		bus[\writePhase] = Bus.audio(s, 1);
		bus[\tap] = Array.fill(numTaps, {
			Bus.audio(s, 1);
		});

		group[\write] = Group.new(context.group[\process]);
		group[\tap] = Group.after(group[\write]);
		group[\pan] = Group.after(group[\tap]);
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
				\out, bus[\tap][i],
				\delayTime, (i+1)*2
			], group[\tap]);
		});

		synth[\pan] = Array.fill(numTaps, { arg i;
			Synth.new(\patch_pan, [
				\in, bus[\tap][i],
				\out, context.bus[\hw_out],
				\pos, i.linlin(0, numTaps-1, -0.8, 0.8)
			], group[\pan]);
		});
	}


}