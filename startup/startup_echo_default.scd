
s = Server.default;
s.options.device = "Steinberg UR-RT2";

/// change this to your actual path
~elementsBasePath = "/Users/emb/code/z-elements";


s.waitForBoot {
	Routine {

		/// there is probably a more elegant way to ensure that all needed synthdefs are sent only once,
		// without writing to disk, as soon as server is booted.. but i haven't thought of it
		ZEchoDefault.sendSynthDefs(s);
		// wait until synthdefs are complete
		s.sync;

		//----------------------------
		/// this thing owns the main input and outputs,
		/// and a basic group order for managing synth execution sequence
		c = ZAudioContext.new(s);
		{ c.bus[\hw_in].scope; }.defer;

		/// AFAIK, supercollider lacks a simple utility to resample buffers in memory
		/// easiest to ensure that file SR matches
		/*
		c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");
		*/

		//----------------------------
		/// this thing will make a "dry" connection from main input to output,
		/// as well as a "wet" connection through its own effect bus
		/// effects can read and write to this bus in-place
		~fx = ZStereoFxLoop.new(c);
		s.sync;
		~fx.setDryLevel(-6.dbamp);
		~fx.setWetLevel(-6.dbamp);
		{ ~fx.fxBus.scope }.defer;


		///----------------------------
		/// simple utility for easily setting responder functions on specific MIDI control changes
		m = ZSimpleMidiControl.new(connectAll:true);
		// uncomment to see raw endpoint, channel, data values
		// m.verbose = true;


		//-----------------------
		/// after waiting a bit, to hear the difference...
		6.wait;
		/// ...create a basic, off the shelf echo effect...
		postln("adding echo");
		~echo = ZEchoDefault.new(c.server, ~fx.fxBus, c.group[\process]);
		/// ... and offset the stereo balance of wet/dry to differentiate them better
		~fx.setDryBalance(-0.8);
		~fx.setWetBalance(0.8);

		//---------------------------------------
		// add some MIDI controls

		m.cc(2, { // "breath control"
			arg value;
			// set the wet/dry mix, naively
			var mix = value.linlin(0, 127, 0, 1);
			var levelDry = 1-mix;
			var levelWet = mix;
			postln("setting wet/dry mix: " ++ levelDry.ampdb ++ "/" ++ levelWet.ampdb ++ " dB");
			~fx.setDryLevel(1-mix);
			~fx.setWetLevel(mix);
		});

		m.cc(7, { // volume
			arg value;
			// set the main volume, naively
			var level = value.linlin(0, 127, 0, 1);
			postln("setting overall FX loop level: " ++ level.ampdb ++ " dB");
			~fx.setMainLevel(level);
		});

		m.cc(10, { // pan
			arg value;
			// set the amount of wet/dry stereo offset
			var offset = value.linlin(0, 127, 0, 1);
			postln("set stereo offset: " ++ offset);
			~fx.setWetBalance(offset);
			~fx.setDryBalance(-1 * offset);
		});


		m.cc(12, { // "effect control 1"
			arg value;
			// change the delay time
			// with our naive "default" echo, this is glitchy!
			// addressing or harnessing this glitchiness will be the main focus of our work
			var time = value.linexp(0, 127, 0.01, ZEchoBase.defaultBufferLength);
			postln("echo time: " ++ time);
			~echo.setDelayTime(time);
		});

		m.cc(13, { // "effect control 2"
			arg value;
			// change the feedback level, naively
			var level = value.linlin(0, 127, 0, 1);
			postln("feedback level: " ++ level.ampdb ++ " dB");
			~echo.setFeedbackLevel(level);
		});


	}.play;
}