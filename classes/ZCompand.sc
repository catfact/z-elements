ZCompand {

	*sendSynthDefs { arg server;


		var compEnvRms = {
			arg input, attack, release, windowSize=0.02;
			var windowFrames = windowSize * SampleRate.ir;
			var env = (RunningSum.ar(input.squared, windowFrames)/windowFrames).sqrt;
			LagUD.ar(env, attack, release);
		};

		var compEnvPeak = {
			arg input, attack, release, window=0.02;
			// expensive, but only happens once (.ir)
			var decay = (-90.dbamp) ** (1/(window*SampleRate.ir));
			var envelope = PeakFollower.ar(input, decay);
			LagUD.ar(envelope, attack, release);
		};

		var compGainHardKnee = {
			arg env, threshDb, slopeAbove, slopeBelow, attack, release;
			var envDb = env.ampdb;
			var deltaDb = envDb - threshDb;
			var newTargetDb = threshDb + (deltaDb * if(envDb > threshDb, slopeAbove, slopeBelow));
			var gainDb = newTargetDb - envDb;
			LagUD.ar(gainDb.dbamp, release, attack);
		};

		var compand = {
			arg input,
			threshold = -12,
			slopeAbove, slopeBelow,
			envAttack=0.01, envRelease=0.1,
			gainAttack=0.01, gainRelease=0.02;

			var inputEnvelope = compEnvPeak.value(input, envAttack, envRelease);
			var gain = compGainHardKnee.value(inputEnvelope, threshold, slopeAbove, slopeBelow, gainAttack, gainRelease);
			input * gain
		};

		SynthDef.new(\ZCompandStereo, {
			var bus = \bus.kr;
			var input = In.ar(bus, 2) * \preLevel.kr(1);
			var output = compand.value(input,
				\threshold.kr(-12),
				\slopeAbove.kr(1/4),
				\slopeBelow.kr(1),
				\envAttack.kr(0.01),
				\envRelease.kr(0.1),
				\gainAttack.kr(0.01),
				\gainRelease.kr(0.1)
			);
			ReplaceOut.ar(bus, output * \postLevel.kr(1));
		}).send(server);
	}
}