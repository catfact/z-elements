
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
		ZCompand.sendSynthDefs(s);

		// wait until synthdefs are complete
		s.sync;

		//----------------------------
		/// this thing owns the main input and outputs,
		/// and a basic group order for managing synth execution sequence
		c = ZAudioContext.new(s,
			~hwInNumChannels, ~hwOutNumChannels,
			~hwInChannelOffset, ~hwOutChannelOffset);

		/// AFAIK, supercollider lacks a simple utility to resample buffers in memory
		/// easiest to ensure that file SR matches

		//c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");

		// expander only
		~comp.set(\slopeBelow, 100);
		~comp.set(\slopeAbove, 1);
		/// analysis envelope:
		~comp.set(\envAttack, 0.02);
		~comp.set(\envRelease, 0.05);
		/// for the gain:
		~comp.set(\gainAttack, 2);
		~comp.set(\gainRelease, 2);

		//----------------------------
		/// this thing will make a "dry" connection from main input to output,
		/// as well as a "wet" connection through its own effect bus
		/// effects can read and write to this bus in-place
		~fx = ZStereoFxLoop.new(c);
		s.sync;
		~fx.setDryLevel(1);
		~fx.setWetLevel(0);
		//{ ~fx.fxBus.scope }.defer;


		///----------------------------
		/// simple utility for easily setting responder functions on specific MIDI control changes
		m = ZSimpleMidiControl.new(connectAll:true);
		// uncomment to see raw endpoint, channel, data values
		// m.verbose = true;

		//-----------------------
		/// create an echo effect with feedback companding
		postln("adding echo");
		~echo = ZEchoFeedbackCompand.new(c.server, ~fx.fxBus, c.group[\process]);
		~echo.setDelayTime(4);
		~echo.setFeedbackLevel(0.8);

//		{ ~echo.feedbackBus.scope }.defer;
		{ ~echo.analysisBus.scope }.defer;

		/// ... and offset the stereo balance of wet/dry to differentiate them better
		~fx.setDryBalance(-0.5);
		~fx.setWetBalance(0.5);

		~fx.setDryLevel(0.5);
		~fx.setWetLevel(0.5);

		//---------------------------------------
		// add some MIDI controls

		m.cc(2, { // "breath control"
			arg value;
			var threshold = value.linlin(0, 127, -60, 0);
			postln("threshold: " ++ threshold ++ " dB");
			~echo.setCompandControl(\threshold, threshold);
		});

		m.cc(7, { // volume
			arg value;
			var attack = value.linexp(0, 127, 0.02, 4);
			postln("attack: " ++ attack );
			~echo.setCompandControl(\attack, attack);
		});

		m.cc(10, { // pan
			arg value;
			var release = value.linexp(0, 127, 0.02, 4);
			postln("release: " ++ release );
			~echo.setCompandControl(\release, release);
		});

		m.cc(12, { // "effect control 1"
			arg value;
		});

		m.cc(13, { // "effect control 2"
			arg value;
		});

	}.play;
}