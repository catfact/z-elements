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


	~bufdur = 8;
	~buf = Buffer.alloc(s, ~bufdur * s.sampleRate, 2);
	~writePhaseBus = Bus.control(s, 1);

	~period = 1/4;
	~overlap = 3;
	~delay = 1;

	~grainInBus = Bus.audio(s, 2);
	~grainOutBus = Bus.audio(s, 2);
	~feedbackBus = Bus.audio(s, 2);

	~grainInput = {
		var hwIn = In.ar(c.bus[\hw_in], 2);
		var feedbackIn = InFeedback.ar(~feedbackBus.index, 2);
		feedbackIn = LPF.ar(HPF.ar(feedbackIn, \feedbackHpf.kr(100)), \feedbackLpf.kr(8000));
		Out.ar(~grainInBus, hwIn + (\feedbackLevel.kr(0) * feedbackIn));
	}.play(target:c.group[\in]);

	~grainOutput = {
		Out.ar(c.bus[\hw_out], In.ar(~grainOutBus.index, 2));
	}.play(target:c.group[\out], addAction:\addBefore);

	s.sync;

	// record synth
	~write = Synth.new(\ZGranularHeresy_Write, [
		\input, ~grainInBus.index,
		\buf, ~buf.bufnum,
		\phaseOut, ~writePhaseBus.index
	], target:~grainInput, addAction:\addAfter );

	// envelope buffer
	~envSignal = Signal.hammingWindow(512);
	// Signal.inspect
	// ~envSignal.plot
	~envBuf = Buffer.loadCollection(s, ~envSignal);

	~latency = 0.1;
	~rate = 1;

	~rateMulPat = Pseq([1, 2, -1, 3/2], inf).asStream;
	~lpfMulPat = Pseq([1, 2, 3/2, 6/5], inf).asStream;
	~hpfMulPat = Pseq([1, 2, 5/4], inf).asStream;
	~periodMulPat = Pseq([1, 2, 3/2, 3/4], inf).asStream;
	~panPat = Pseq(Array.series(5, -0.8, 0.4), inf).asStream;
	~fbLevelPat = Pseq([0, 0, 1, 0, 0, 1], inf).asStream;

	~lpfBase = 2000;
	~hpfBase = 100;

	s.sync;

	~grainInput.set(\feedbackLevel, -8.dbamp);

	//{ ~envBuf.plot }.defer;

	/// grain position LFO
	~grainPosRate = 1;
	~grainStartFrame = 0;

	// grain scheduler
	r = Routine { inf.do {
		var writePhase, durFrames, startFrame, endFrame;
		var rate = 1; // = ~rate * ~rateMulPat.next;
		var lpf = (~lpfBase * ~lpfMulPat.next).min(14000);
		var hpf = (~hpfBase * ~hpfMulPat.next).min(1000);
		var period = ~period * ~periodMulPat.next;
		var duration = period * ~overlap;
		var pan = ~panPat.next;
		var feedbackPan = pan * -1;
		var feedbackLevel = 0; // = ~fbLevelPat.next;
		var writePhaseEnd;
		var willCross;
		var analysis, pitch;
		var cond;
		var halfoctave, octave;

		cond = Condition.new;
		cond.test = false;
		~analysis_bus.getn(6, { arg vals;
			analysis = Dictionary.newFrom([
				\amp, vals[0].ampdb.max(-80),
				\pitch, vals[2].cpsmidi.max(0)
			]);
			cond.test = true;
			cond.signal;
		});
		cond.wait;
		analysis.postln;

		halfoctave = ((analysis[\pitch] - 60) / 3).round;
		octave = halfoctave * 2;
		octave.postln;
		rate = if(halfoctave % 2 > 0, { 1.5}, {1}) * (2 ** octave);
		feedbackLevel = analysis[\amp].linlin(-80, -20, -16, -4).dbamp;

		[rate, feedbackLevel].postln;

		// [rate, lpf, hpf, period].postln;

		// FIXME: `getSynchronous` is adding unknown and implicit timing jitter
		// to our grainscheduling routine
		writePhase = ~writePhaseBus.getSynchronous;

		durFrames = duration * s.sampleRate * rate;

		startFrame = ~grainStartFrame;

		//startFrame = (writePhase - (~delay * s.sampleRate)).wrap(0, ~buf.numFrames - (durFrames+1).max(0));
		endFrame = startFrame + durFrames;

		// we want to check if the grain will cross the write phasor
	/*	writePhaseEnd = (writePhase + (duration * s.sampleRate)).wrap(0, ~buf.numFrames);
		if (writePhaseEnd < writePhase, {
			// write phase will have wrapped, need to check two regions of write phase
			// - write phase region 1:
			//   -- will grain wrap?
			// - yes: check two regions playback against two regions write
			// - no: check one region read vs 2 regions write
			//
		}, {
			// write phase will not wrap
			//...
		});
*/

		/*
		If rate < 1:
		  If(r < w) { r = r + bufsize};
		  If(w + durframes > r + (rate * durframes)) then boom;
        If rate > 1:
          If(w < r) { w = w + bufsize};
          If( r + (rate * durframes) > w + durframes) then boom
		*/

		if(willCross, {
			// silence the grain
			// or move it
		});

		~grainStartFrame = ~grainStartFrame + (~grainPosRate * period * s.sampleRate);

		//[writePhase, grainPhase].postln;
		// send the message to the server with a timestamp and latency
		// server will handle the message when its clock advances to the precise latency requested
		// this eliminates jitter from OSC transport

		/*
		things to try:
		- grain pos independent of write head
		- ducking / modulating position if grain will cross write head
		- saturation
		- interleaving history
		- modulating write / preserve level
		- weird envelope shapes: interpolate between buffers
		- analysis-driven grain generation
		*/


		s.makeBundle(~latency, {
			Synth.new(\ZGranularHeresy_Grain4, [
				\buf, ~buf.bufnum,
				\envBuf1, ~envBuf.bufnum,
				\envBuf2, ~envBuf.bufnum,
				\duration, duration,
				\endFrame, endFrame,
				\startFrame, startFrame,
				\out, ~grainOutBus.index,
				\pan, pan,
				\lpf, lpf,
				\hpf, hpf,
				\out2, ~feedbackBus.index,
				\level2, feedbackLevel,
				\pan2, feedbackPan
			], target:c.group[\process], addAction:\addToTail);
		});
		period.wait;
	} }.play;

	// analysis module
	~analysis_bus = Bus.control(s, 6);

	~analysis = Synth.new(\ZGranularHeresy_Analyzer, [
		\in, c.bus[\hw_in],
		\out, ~analysis_bus.index
	], c.group[\in]);

	// {
	// 	w = Window("z-granular-heresy", Rect(100, 200, 400, 520), false);
	// 	ZGranularHeresy_HistoryPlot.maxTime = 8;
	// 	v = ZGranularHeresy_HistoryPlot.new(~analysis_bus, w.view, Rect(0, 0, 400, 520));
	//
	// 	w.front;
	// }.defer;

}.play }

/*
~feedbackBus.scope;
*/