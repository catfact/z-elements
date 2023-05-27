ZCompand {
	/*
	*compEnvRms {
	arg input, attack, release, windowSize=0.02;
	var windowFrames = windowSize * SampleRate.ir;
	var env = (RunningSum.ar(input.squared, windowFrames)/windowFrames).sqrt;
	^LagUD.ar(env, attack, release);
	}
	*/
	*compEnvPeak {
		arg input, attack, release, window=0.02;
		// expensive, but only happens once (.ir)
		var decay = (-90.dbamp) ** (1/(window*SampleRate.ir));
		var envelope = PeakFollower.ar(input, decay);
		//decay.poll;
		envelope = LagUD.ar(envelope, attack, release);
		^envelope;
	}

	*compGainHardKnee {
		arg env, threshDb, slopeAbove, slopeBelow, attack, release;
		var envDb = env.ampdb;
		var deltaDb = envDb - threshDb;
		var newTargetDb = threshDb + (deltaDb * if(envDb > threshDb, slopeAbove, slopeBelow));
		var gainDb = newTargetDb - envDb;
		envDb.poll;
		gainDb = LagUD.ar(gainDb.dbamp, release, attack);
		^gainDb
	}

	*compandStereo {
		arg input, // assume input is 2-channel array
		threshold = -12,
		slopeAbove, slopeBelow,
		envAttack=0.01, envRelease=0.1,
		gainAttack=0.01, gainRelease=0.02,
		out;
		var gain;
		var inputEnvelope = max(
			ZCompand.compEnvPeak(input[0], envAttack, envRelease),
			ZCompand.compEnvPeak(input[1], envAttack, envRelease),
		);
		//inputEnvelope.poll;
		gain = ZCompand.compGainHardKnee(inputEnvelope, threshold, slopeAbove, slopeBelow, gainAttack, gainRelease);
		Out.ar(out, [inputEnvelope, gain]);
		^(input * gain)
	}

	*sendSynthDefs { arg server;

		SynthDef.new(\ZCompandStereo, {
			var bus = \bus.kr;
			var input = Sanitize.ar(In.ar(bus, 2) * \preLevel.kr(1));
			var output = ZCompand.compandStereo(input,
				\threshold.kr(-12),
				\slopeAbove.kr(1/50),
				\slopeBelow.kr(1),
				\envAttack.kr(0.01),
				\envRelease.kr(0.2),
				\gainAttack.kr(0.01),
				\gainRelease.kr(0.1),
				\analysisBus.kr
			);
			ReplaceOut.ar(bus, output * \postLevel.kr(1));
		}).send(server);
	}
}