
s = Server.default;

s.options.device = "Steinberg UR-RT2";
s.options.numInputBusChannels = 4;

/// change this to your actual path
~elementsBasePath = "/Users/emb/code/z-elements";

~hwInNumChannels = 1;
~hwOutNumChannels = 2;
~hwInChannelOffset = 2; // this is for my streaming setup
~hwOutChannelOffset = 0;

s.waitForBoot {
	Routine {

		/// there is probably a more elegant way to ensure that all needed synthdefs are sent only once,
		// without writing to disk, as soon as server is booted.. but i haven't thought of it
		ZEchoBase.sendSynthDefs(s);
		// wait until synthdefs are complete
		s.sync;

		//----------------------------
		/// this thing owns the main input and outputs,
		/// and a basic group order for managing synth execution sequence
		c = ZAudioContext.new(s,
			~hwInNumChannels, ~hwOutNumChannels,
			~hwInChannelOffset, ~hwOutChannelOffset);

		{ c.bus[\hw_in].scope; }.defer;

		/// AFAIK, supercollider lacks a simple utility to resample buffers in memory
		/// easiest to ensure that file SR matches

		c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");

		~comp = Synth.new(\ZCompand, [

		], c.group[\in], \addAfter);

		//----------------------------
		/// this thing will make a "dry" connection from main input to output,
		/// as well as a "wet" connection through its own effect bus
		/// effects can read and write to this bus in-place
		~fx = ZStereoFxLoop.new(c);
		s.sync;
		~fx.setDryLevel(1);
		~fx.setWetLevel(0);
		{ ~fx.fxBus.scope }.defer;


		///----------------------------
		/// simple utility for easily setting responder functions on specific MIDI control changes
		m = ZSimpleMidiControl.new(connectAll:true);
		// uncomment to see raw endpoint, channel, data values
		// m.verbose = true;

		//---------------------------------------
		// add some MIDI controls

		m.cc(2, { // "breath control"
			arg value;

		});

		m.cc(7, { // volume
			arg value;
		});

		m.cc(10, { // pan
			arg value;
		});


		m.cc(12, { // "effect control 1"
			arg value;
		});

		m.cc(13, { // "effect control 2"
			arg value;
		});





	}.play;
}