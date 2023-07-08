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

	~period = 1/200;
	~delay = 1;
	~duration = ~period * 2;

	/*
	ZGranularHeresy.synthDef.do({ arg def;
		def.name.postln;
		def.allControlNames.do({ arg ctl; ctl.name.postln; });
	});
	*/
	// record synth
	~write = Synth.new(\ZGranularHeresy_Write, [
		\input, c.bus[\hw_in],
		\buf, ~buf.bufnum,
		\phaseOut, ~writePhaseBus.index
	],target:c.group[\in] );

	// envelope buffer
	~envSignal = Signal.hammingWindow(512);
	// Signal.inspect
	// ~envSignal.plot
	~envBuf = Buffer.loadCollection(s, ~envSignal);

	~latency = 0.1;

	s.sync;
	{ ~envBuf.plot }.defer;
	// grain scheduler
	r = Routine { inf.do {
		var writePhase = ~writePhaseBus.getSynchronous;
		var durFrames = ~duration * s.sampleRate;
		var grainPhase = (writePhase - (~delay * s.sampleRate)).wrap(0, ~buf.numFrames - (durFrames+1));
		//[writePhase, grainPhase].postln;
		// send the message to the server with a timestamp and latency
		// server will handle the message when its clock advances to the precise latency requested
		// this eliminates jitter from OSC transport

		s.makeBundle(~latency, {
			Synth.new(\ZGranularHeresy_Grain1, [
				\buf, ~buf.bufnum,
				\envBuf, ~envBuf.bufnum,
				\duration, ~duration,
				\startFrame, grainPhase,
				\out, c.bus[\hw_out],
				\pan, 0.5.rand2
			], target:c.group[\process], addAction:\addToTail);
		});
		~period.wait;
	} }.play;

}.play }

