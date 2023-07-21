s = Server.local;
s.options.numInputBusChannels = 4;

~hwInChannels = 1;
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

	ZMultiTap.sendSynthDefs(s);
	s.sync;

	n = 2;
	z = ZMultiTap.new(n, c);


	// a basic volume taper function
	~taper_break = -80.dbamp;
	~taper = { arg midi;
		if (midi < 47, {
			midi.linlin(0, ~taper_break)
		}, {
			midi.linlin(47, 127, -80, 0).dbamp
		});
	};

	// midi controls
	m = ZSimpleMidiInput.new(connectAll:true);
	m.verbose = true;

	m.cc(20, { arg ccVal; z.setTapLevel(0, ~taper.value(ccVal)); });
	m.cc(21, { arg ccVal; z.setTapLevel(1, ~taper.value(ccVal)); });

	m.cc(22, { arg ccVal; z.setTapDelay(0, ccVal.linexp(0, 127, 0.1, 4.0)) });
	m.cc(23, { arg ccVal; z.setTapDelay(1, ccVal.linexp(0, 127, 0.1, 4.0)) });

	m.cc(24, { arg ccVal; z.setTapPosition(0, ccVal.linlin(0, 127, -1, 1)) });
	m.cc(25, { arg ccVal; z.setTapPosition(1, ccVal.linlin(0, 127, -1, 1)) });


}.play }

