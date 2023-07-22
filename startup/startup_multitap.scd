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


	// a volume taper. why not try an s-curve...
	~taper = { arg midi;
		var amp = (midi.linlin(0, 127, 0, pi) - pi).cos + 1 / 2;
		[amp, amp.ampdb].postln;
		amp
	};

	// midi controls
	m = ZSimpleMidiInput.new(connectAll:true);
//	m.verbose = true;

	// these CC numbers are quite arbitrary, please change!
	m.cc(20, { arg ccVal;
		z.setTapLevel(0, ~taper.value(ccVal));
	});

	m.cc(21, { arg ccVal; z.setTapLevel(1, ~taper.value(ccVal)); });

	m.cc(22, { arg ccVal;
		var delayTime = ccVal.linexp(0, 127, 0.1, 4.0);
//		postln("delay time 1: " ++ delayTime);
		z.setTapDelay(0, delayTime);
	});

	m.cc(23, { arg ccVal;
		// FIXME: do something nicer wit this, like just intonation ratios
		var delayTime = ccVal.linexp(0, 127, 0.1, 4.0);
//		postln("delay time 2: " ++ delayTime);
		z.setTapDelay(1, delayTime);
	});

	m.cc(24, { arg ccVal;
		var position= ccVal.linlin(0, 127, -1, 1);
//		postln("pan position 1:" ++ position);
		z.setTapPosition(0, position)
	});

	m.cc(25, { arg ccVal;
		var position= ccVal.linlin(0, 127, -1, 1);
//		postln("pan position 2:" ++ position);
		z.setTapPosition(1, position)
	});

	~ratioSet = [6/5, 5/4, 4/3, 3/2, 8/5, 5/3, 16/9];
	m.cc(1, { arg ccVal;
		var ratioIndex = ccVal.linlin(0, 127, 0, ~ratioSet.size-1);
		var ratio = ~ratioSet[ratioIndex];
		postln("slew ratio : " ++ ratio);
		n.do({ arg i;
			z.synth[\tap][i].set(\delayTimeSlewUp, ratio + 1);
			z.synth[\tap][i].set(\delayTimeSlewDown, ratio - 1);

		});
	});


}.play }

