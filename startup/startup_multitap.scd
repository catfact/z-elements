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



}.play }

