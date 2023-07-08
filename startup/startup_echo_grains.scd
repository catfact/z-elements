s = Server.local;
s.options.numInputBusChannels = 4;

~hwInChannels = 1;
~hwOutChannels = 2;
//////
// this is for my streaming setup only!
~hwInChannelOffset = 2;
//////
~hwOutChannelOffset = 0;

s.waitForBoot { Routine {

	ZEcho.defaultMaxDelayTime = 64;
	ZEcho.sendAllSynthDefs(s);
	s.sync;

	// main busses and groups
	c = ZAudioContext.new(s,
		~hwInChannels,
		~hwOutChannels,
		~hwInChannelOffset,
		~hwOutChannelOffset,
	);
	s.sync;

	e = ZEchoDefaultGranular.new(s, [
		\in, c.bus[\hw_in],
		\out, c.bus[\hw_out],
		\level, 1
	], c.group[\process]);
	s.sync;

	e.set(\grainRate, 10);
	e.set(\grainRateNoiseFactor, 0.25);
	e.set(\grainDuration, 1/2);
	e.set(\feedbackLevel, 0.4);

	e.set(\rate, -1);

	{ e.bus[\in].scope; }.defer;
}.play }

