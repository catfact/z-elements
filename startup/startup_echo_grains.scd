s = Server.local;

s.waitForBoot { Routine {

	ZEcho.defaultMaxDelayTime = 64;
	ZEcho.sendAllSynthDefs(s);
	s.sync;

	// main busses and groups
	c = ZAudioContext.new(s, 1, 2, 0, 0);
	s.sync;

	e = ZEchoDefaultGranular.new(s, [
		\in, c.bus[\hw_in],
		\out, c.bus[\hw_out],
		\level, 1
	], c.group[\process]);
	s.sync;

	e.set(\grainRate, 10);
	e.set(\grainRateNoiseFactor, 0.25);
	e.set(\grainDuration, 1/5);
	e.set(\feedbackLevel, 0.4);

	// dry patch
	d = Synth.new(\patch_stereo, [
		\in, c.bus[\hw_in],
		\out, c.bus[\hw_out],
		\level, 1
	], c.group[\process]);

	s.sync;
	d.set(\level, 0.5);

	// changes to hear the difference
	4.wait;
	e.set(\delayTime, 3);
	4.wait;
	e.set(\rate, 5/4);
	4.wait;
	e.set(\rate, 3/2);
	4.wait;
	e.set(\rate, -1);

}.play }