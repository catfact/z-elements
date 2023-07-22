// power series expansion of ideal clipping function with given polynomial order
// (after RBJ)
// https://dsp.stackexchange.com/questions/36202/monotonic-symmetrical-soft-clipping-polynomial
// matlab:
// n = linspace(0, N, N+1);
// a = (-1).^n ./ (factorial(N-n) .* factorial(n) .* (2*n + 1));
// a = a/sum(a)
// y = x .* polyval(fliplr(a), x.^2);

// compute polynomial softclipper coefficients
~polysoftclip_coef = {
	arg o; // the final order is actually (o*2)+1
	var a = Array.fill(o+1, { arg n;
		(-1 ** n) / ((o-n).factorial * n.factorial * (2*n + 1));
	});
	a.normalizeSum
};


// evaluate the RBJ softclip polynomial with given order, returning a buffer
~poly_softclip_buffer = {
	arg o, n, gain=2;
	var c = ~polysoftclip_coef.value(o);
	var xx = Array.interpolation(n, -1*gain, gain);
	var yy = xx.collect({ arg x;
		var y = 0;
		x = x.min(1).max(-1);
		(o+1).do({ arg i;
			y = y + (c[i] * (x ** (i*2)));
		});
		y * x
	});
	var buf = Buffer.alloc(s, n*2, 1);
	{ yy.plot }.defer;
	s.sync;
	buf.sendCollection(Signal.newFrom(yy).asWavetableNoWrap);
	s.sync;
	buf
};


s.waitForBoot {
	Routine {
		var buf;
		var bus;
		var wave, lpf, clip;
		var output;

		// general waveshaper using a buffer as a lookup table
		SynthDef.new(\bufShaper, {
			var input = In.ar(\in.kr(0)) * \inputGain.kr(1);
			var output = Shaper.ar(\buf.kr, input) * \outputGain.kr(1);
			Out.ar(\out.kr(0), output);
		}).send(s);

		// tangent: load up our precalculated brickwall LPF coefficients
		// (see `elliptic_brickwall_lpf.m`)
		~here = PathName(Document.current.path).pathOnly;
		~brickwall_coeff = (this.executeFile(~here ++ "brickwall_lpf_coeff.scd")).value;

		// construct a dictionary of brickwall synthdefs,
		// indexed by order of distortion products (division of nyquist)
		~brickwall_def = ~brickwall_coeff.keys.collect({ arg ratio;
			var data = ~brickwall_coeff[ratio];
			[ratio, data].postln;
			SynthDef.new(("brickwall_lpf_"++ratio).asSymbol, {
				var coeffs = data.c;
				var gain = data.g;
				var x = In.ar(\in.kr(0));
				// all the filters are 8th-order, so 4x SOS in series
				4.do({ arg i;
					var c = coeffs[i];
					x = SOS.ar(x, c[0], c[1], c[2], -1 * c[4], -1 * c[5]);
				});
				Out.ar(\out.kr(0), x * gain);
			}).send(s);
		});

		~poly_order = 3;
		~lpf_order = ~poly_order * 2 + 1;

		postln("polynomial N = " ++ ~poly_order);
		postln("lowpass order = " ++ ~lpf_order);
		postln("brickwall cutoff = " ++ (s.sampleRate / (~lpf_order * 2)) ++ " Hz");

		buf = ~poly_softclip_buffer.value(~poly_order, 2048, 4.dbamp);
		{ buf.plot; }.defer;

		bus = Dictionary.new;
		bus[\wave] = Bus.audio(s, 1);
		bus[\lpf] = Bus.audio(s, 1);
		bus[\clip] = Bus.audio(s, 1);

		wave = {
			Out.ar(bus[\wave], Pulse.ar(\freq.kr(220)) * \level.kr(0.5))
		}.play(s);
		s.sync;

		if (true, {
			lpf = Synth.new(("brickwall_lpf_" ++ ~lpf_order).asSymbol, [
				\in, bus[\wave], \out, bus[\lpf]
			], wave, \addAfter);
		}, {
			// test: bypass
			lpf = {
				Out.ar(bus[\lpf], In.ar(bus[\wave]));
			}.play(s, addAction:\addToTail);
		});
		s.sync;

		clip = Synth.new(\bufShaper, [
			\in, bus[\lpf], \out, bus[\clip],
			\buf, buf
		], lpf, \addAfter);
		s.sync;

		output = {
			Out.ar(0, In.ar([bus[\wave].index, bus[\clip].index]) * \level.kr(0.1))
		}.play(target:s, addAction:\addToTail);

		// { FreqScope.new(400, 200, bus[\wave].index, server: s); }.defer;
		{ FreqScope.new(400, 200, bus[\clip].index, server: s); }.defer;
		// {  bus[\wave].scope }.defer;
		{  bus[\clip].scope }.defer;



	}.play;
}

