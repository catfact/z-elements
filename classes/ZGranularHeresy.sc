ZGranularHeresy : ZEcho {
	classvar <synthDef;

	*initClass {
		synthDef = Array.with(
			SynthDef.new(\ZGranularHeresy_Write, {
				// stereo buffer
				var buf = \buf.kr;
				// stereo input
				var input = In.ar(\input.kr(0), 2);
				var bufFrames = BufFrames.kr(buf);
				var writePhase = Phasor.ar(end:bufFrames);
				Out.kr(\phaseOut.kr, A2K.kr(writePhase));
				BufWr.ar(input, buf, writePhase);
			}),

			SynthDef.new(\ZGranularHeresy_Grain1, {
				var buf = \buf.ir;
				var envBuf = \envBuf.ir;
				var bufSampleRate = BufSampleRate.ir(buf);
				var startFrame = \startFrame.kr(0);
				var dur = \duration.ir(0.1);
				var durFrames = dur * bufSampleRate;
				var grainPhase = Line.ar(startFrame, startFrame + durFrames, dur, doneAction:2);
				var envPhase = Line.ar(0, BufFrames.ir(envBuf), dur);
				var snd = BufRd.ar(2, buf, grainPhase) * BufRd.ar(1, envBuf, envPhase);
				Out.ar(\out.kr(0), Pan2.ar(snd * \level.ir(1), \pan.ir(0)));
			})
		);
	}
}