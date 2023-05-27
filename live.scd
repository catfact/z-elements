c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");

c.testInputSynth.run(false);
c.testInputSynth.run(true);


~timeSequence = Routine {
	var times = [1, 2];
	var state = 0;
	inf.do {
		state = (state + 1).wrap(0, 1);
		times[state].postln;
		~echo.setDelayTime(times[state]);
		0.8.wait;
	}
}.play;

{
	Slew.ar(LFPulse.ar(100), 200, 200)
}.plot(0.1)