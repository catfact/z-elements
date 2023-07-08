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
				var writePhase = Phasor.ar(rate:\rate.kr(1), end:bufFrames);
				var oldContent = BufRd.ar(2, buf, writePhase);
				var levelLag = \levelLag.kr(1);
				var preserveLevel = Lag.ar(K2A.ar(\preserveLevel.kr(0)), levelLag);
				var newLevel = Lag.ar(K2A.ar(\newLevel.kr(1)), levelLag);
				Out.kr(\phaseOut.kr, A2K.kr(writePhase));
				BufWr.ar((input * newLevel) + (oldContent * preserveLevel), buf, writePhase);
			}),

			SynthDef.new(\ZGranularHeresy_Grain1, {
				var buf = \buf.ir;
				var envBuf = \envBuf.ir;
				var bufSampleRate = BufSampleRate.ir(buf);
				var startFrame = \startFrame.kr(0);
				var dur = \duration.ir(0.1);
				var endFrame = \endFrame.kr;
				var grainPhase = Line.ar(startFrame, endFrame, dur, doneAction:2);
				var envPhase = Line.ar(0, BufFrames.ir(envBuf), dur);
				var snd = BufRd.ar(2, buf, grainPhase) * BufRd.ar(1, envBuf, envPhase);
				Out.ar(\out.kr(0), Pan2.ar(snd * \level.ir(1), \pan.ir(0)));
			}),

            // add HPF / LPF
			SynthDef.new(\ZGranularHeresy_Grain2, {
				var buf = \buf.ir;
				var envBuf = \envBuf.ir;
				var bufSampleRate = BufSampleRate.ir(buf);
				var startFrame = \startFrame.kr(0);
				var dur = \duration.ir(0.1);
				var endFrame = \endFrame.kr;
				var grainPhase = Line.ar(startFrame, endFrame, dur, doneAction:2);
				var envPhase = Line.ar(0, BufFrames.ir(envBuf), dur);
				var snd = BufRd.ar(2, buf, grainPhase) * BufRd.ar(1, envBuf, envPhase);
				snd = LPF.ar(HPF.ar(snd, \feedbackHpf.kr(100)), \feedbackLpf.kr(8000));
				Out.ar(\out.kr(0), Pan2.ar(snd * \level.ir(1), \pan.ir(0)));
			}),

			// morph between envelope shapes
			SynthDef.new(\ZGranularHeresy_Grain3, {
				var buf = \buf.ir;
				// assume these are same size
				var envBuf1 = \envBuf1.ir;
				var envBuf2 = \envBuf2.ir;
				var bufSampleRate = BufSampleRate.ir(buf);
				var startFrame = \startFrame.kr(0);
				var dur = \duration.ir(0.1);
				var endFrame = \endFrame.kr;
				var grainPhase = Line.ar(startFrame, endFrame, dur, doneAction:2);
				var envPhase = Line.ar(0, BufFrames.ir(envBuf1), dur);
				var env = SelectX.ar(\envShape.kr(0), [
					BufRd.ar(1, envBuf1, envPhase),
					BufRd.ar(1, envBuf2, envPhase)
				]);
				var snd = BufRd.ar(2, buf, grainPhase) * env;
				snd = LPF.ar(HPF.ar(snd, \feedbackHpf.kr(100)), \feedbackLpf.kr(8000));
				Out.ar(\out.kr(0), Pan2.ar(snd * \level.ir(1), \pan.ir(0)));
			}),

			// multiple output busses
			SynthDef.new(\ZGranularHeresy_Grain4, {
				var buf = \buf.ir;
				// assume these are same size
				var envBuf1 = \envBuf1.ir;
				var envBuf2 = \envBuf2.ir;
				var bufSampleRate = BufSampleRate.ir(buf);
				var startFrame = \startFrame.kr(0);
				var dur = \duration.ir(0.1);
				var endFrame = \endFrame.kr;
				var grainPhase = Line.ar(startFrame, endFrame, dur, doneAction:2);
				var envPhase = Line.ar(0, BufFrames.ir(envBuf1), dur);
				var env = SelectX.ar(\envShape.kr(0), [
					BufRd.ar(1, envBuf1, envPhase),
					BufRd.ar(1, envBuf2, envPhase)
				]);
				var snd = BufRd.ar(2, buf, grainPhase) * env;
				snd = LPF.ar(HPF.ar(snd, \feedbackHpf.kr(100)), \feedbackLpf.kr(8000));
				Out.ar(\out.kr(0), Pan2.ar(snd * \level.ir(1), \pan.ir(0)));
				Out.ar(\out2.kr(0), Pan2.ar(snd * \level2.ir(1), \pan2.ir(0)));
			})
		);
	}
}