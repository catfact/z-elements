c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");

c.testInputSynth.run(false);
c.testInputSynth.run(true);

{
	Slew.ar(LFPulse.ar(100), 200, 200)
}.plot(0.1)
]


~ratios = [ 1, 16/15, 9/8, 6/5, 5/4, 4/3, 3/2, 8/5, 10/6, 16/9, 15/8];

Routine { ~ratios.do { arg rat;
	{ EnvGen.ar(Env.perc, doneAction:2) * Pulse.ar(220 * rat) }.play;
	0.5.wait;
} }.play;


////

~compEnvRms = {
	arg input, attack, release, windowSize=0.02;
	var windowFrames = windowSize * SampleRate.ir;
	var env = (RunningSum.ar(input.squared, windowFrames)/windowFrames).sqrt;
	LagUD.ar(env, attack, release);
};

~compEnvPeak = {
	arg input, attack, release, window=0.02;
	// expensive, but only happens once (.ir)
	var decay = (-90.dbamp) ** (1/(window*SampleRate.ir));
	var envelope = PeakFollower.ar(input, decay);
	LagUD.ar(envelope, attack, release);
};

~compGainHardKnee = {
	arg env, threshDb, slopeAbove, slopeBelow, attack, release;
	var envDb = env.ampdb;
	var deltaDb = envDb - threshDb;
	var newTargetDb = threshDb + (deltaDb * if(envDb > threshDb, slopeAbove, slopeBelow));
	var gainDb = newTargetDb - envDb;
	LagUD.ar(gainDb.dbamp, release, attack);
};

~compand = {
	arg input,
	threshold = -12,
	slopeAbove, slopeBelow,
	envAttack=0.01, envRelease=0.1,
	gainAttack=0.01, gainRelease=0.02;

	var inputEnvelope = ~compEnvPeak.value(input, envAttack, envRelease);
	var gain = ~compGainHardKnee.value(inputEnvelope, threshold, slopeAbove, slopeBelow, gainAttack, gainRelease);
	input * gain
};