s = Server.local;
s.options.numInputBusChannels = 4;

~hwInChannels = 2;
~hwOutChannels = 2;
~hwInChannelOffset = 0;
~hwOutChannelOffset = 0;

s.waitForBoot { Routine {

	// main busses and groups
	c = ZAudioContext.new(s,
		~hwInChannels,
		~hwOutChannels,
		~hwInChannelOffset,
		~hwOutChannelOffset,
	);
	s.sync;

	ZMultiTap.bufferDuration = 64;
	ZMultiTap.sendSynthDefs(s);
	s.sync;

	n = 2;
	z = ZMultiTap.new(n, c);
	s.sync;

	~show_phase = {
		var writePhase = In.ar(z.bus[\writePhase]);
		writePhase.poll;
		Silent.ar
	}.play(s, addAction:\addToTail);
	~show_phase.free;

	// a volume taper. why not try an s-curve...
	~taper = { arg midi;
		var amp = (midi.linlin(0, 127, 0, pi) - pi).cos + 1 / 2;
		[amp, amp.ampdb].postln;
		amp
	};

	// midi controls
	m = ZSimpleMidiInput.new(connectAll:true);
	m.verbose = false;

	// these CC numbers are quite arbitrary, please change!
	m.cc(20, { arg ccVal, chan;
		if(chan == 0, {
			// first channel
			z.setTapLevel(0, ~taper.value(ccVal));
		}, {
			// do somethin else
		});
	});
	m.cc(21, { arg ccVal; z.setTapLevel(1, ~taper.value(ccVal)); });

	m.cc(22, { arg ccVal;
		var delayTime = ccVal.linexp(0, 127, 0.1, 4.0);
		postln("delay time 1: " ++ delayTime);
		z.setTapDelay(0, delayTime);
	});

	m.cc(23, { arg ccVal;
		// FIXME: do something nicer wit this, like just intonation ratios
		var delayTime = ccVal.linexp(0, 127, 0.1, 4.0);
		postln("delay time 2: " ++ delayTime);
		z.setTapDelay(1, delayTime);
	});

	m.cc(24, { arg ccVal;
		var position= ccVal.linlin(0, 127, -1, 1);
		postln("pan position 1:" ++ position);
		z.setTapPosition(0, position)
	});

	m.cc(25, { arg ccVal;
		var position= ccVal.linlin(0, 127, -1, 1);
		postln("pan position 2:" ++ position);
		z.setTapPosition(1, position)
	});


}.play }
