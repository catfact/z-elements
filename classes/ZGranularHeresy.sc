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
			}),

			SynthDef.new(\ZGranularHeresy_Analyzer, {
				var input = In.ar(\in.kr(0));
				// lots of defaults to override in all these...
				var amplitude = Amplitude.kr(input);
				var pitch = Pitch.kr(input);
				var fft = FFT(LocalBuf(2048), input);
				var centroid = SpecCentroid.kr(fft);
				var flatness = SpecFlatness.kr(fft);
				var pcile = SpecPcile.kr(fft, \pcile.kr(0.9));
				var zerox = A2K.kr(ZeroCrossing.ar(input));
				Out.kr(\out.kr, [
					amplitude, flatness, pitch, centroid, pcile, zerox
				]);
			});
		);
	}
}

ZGranularHeresy_HistoryPlot {
	classvar <paramCount = 6;
	classvar <>historyCount = 128;
	classvar <>maxTime = 32;

	var <analysisBus;
	var <viewParent;
	var <bounds;
	var <view;
	var <data;
	var <frameInterval;
	var <tickRoutine;

	*new { arg analysisBus, viewParent, bounds;
		^super.newCopyArgs(analysisBus, viewParent, bounds).init;
	}

	init {
		"param count: ".postln;
		paramCount.postln;
		bounds.postln;
		data = Array.fill(paramCount, { LinkedList.new(historyCount) });

		view = Array.fill(paramCount, {
			arg i;
			var b, h;
			b = bounds.copy;
			b.postln;
			h = bounds.height / paramCount;
			//			h.postln;
			b.height = h;
			b.top = bounds.top + (h * i);
			"input bounds: ".postln;
			bounds.postln;

			"view bounds: ".postln;
			b.postln;
			MultiSliderView.new(viewParent, b)
			.elasticMode_(1)
			.gap_(0)
			.thumbSize_(0)
			.drawRects_(false)
			.drawLines_(true)
			.isFilled_(true)
			.showIndex_(false)
			.strokeColor_(Color.white)
			.fillColor_(Color.gray)
			.backColor_(Color.black)
		});

		frameInterval = 1 / 15;

		tickRoutine = Routine {
			var displayValue = Array.newClear(paramCount);
			var cond = Condition.new;
			inf.do {
				// fetch all bus values;
				cond.test = false;
				analysisBus.getn(6, { arg values;
					values.postln;
					[0, 1].do({ arg idx;
						displayValue[idx] = values[idx];
					});
					[2, 3, 4, 5].do({ arg idx;
//						[idx, values[idx]].postln;
//						displayValue[idx] = values[idx].cpsmidi.linlin(20, 120);
					});

					cond.test = true;
					cond.signal;
				});
				cond.wait;
				cond.test = false;
				// update the history and the views
				paramCount.do({ arg i;
					while ({data[i].size >= historyCount}, {
						data[i].popFirst;
					});
					data[i].add(displayValue[i]);
					//[i, data[i]].postln;
					// { view[i].value = data[i].asArray; }.defer;
				});
				{ viewParent.refresh; }.defer;
				frameInterval.wait;
			}
		}.play;
	}
}