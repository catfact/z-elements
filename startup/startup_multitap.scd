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

	ZMultTap.sendSynthDefs(s);
	s.sync;

	// create

	//// GUI!
	// orange/blue pallette (tritanopia-friendly)
	~c1 = Color.fromHexString("FFC20A");
	~c2 = Color.fromHexString("0C7BDC");

	g = ZGui.new;

}.play }

