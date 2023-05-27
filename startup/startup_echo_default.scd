
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
		~fx.setDryLevel(-6.dbamp);
		~fx.setWetLevel(-6.dbamp);
		{ ~fx.fxBus.scope }.defer;


		///----------------------------
		/// simple utility for easily setting responder functions on specific MIDI control changes
		m = ZSimpleMidiControl.new(connectAll:true);
		// uncomment to see raw endpoint, channel, data values
		// m.verbose = true;


		//-----------------------
		/// create a basic, off the shelf echo effect...
		postln("adding echo");
		~echo = ZEchoSmooth.new(c.server, ~fx.fxBus, c.group[\process]);
		/// ... and offset the stereo balance of wet/dry to differentiate them better
		// ~fx.setDryBalance(-0.8);
		// ~fx.setWetBalance(0.8);
		~fx.setDryBalance(0);
		~fx.setWetBalance(0);
		~fx.setDryLevel(1);
		~fx.setWetLevel(0);

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
			var lagTime =  value.linexp(0, 127, 0.05, 2);
			postln("lag time: " ++ lagTime);
			~echo.setSynthControl(\delayTimeLag, lagTime);
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


		~lastNote = nil;
		~numNotesHeld = 0;
		~lastNoteonTime = SystemClock.seconds;
		~ratios = [ 1, 16/15, 9/8, 6/5, 5/4, 4/3, 729/512, 3/2, 8/5, 10/6, 16/9, 15/8];

		m.verbose = true;
		m.noteon {
			arg num, vel;
			if (~lastNote.notNil && (~lastNote != num), {
				var interval = (num - ~lastNote).abs;
				var rat = ~ratios[interval % 12] * (2 ** (interval / 12).floor);
				[ interval, rat].postln;
				~echo.setSynthControl(\delayTimeSlewUp, rat + 1);
				~echo.setSynthControl(\delayTimeSlewDown, rat - 1);
			});
			if (~numNotesHeld == 0, {
				var now = SystemClock.seconds;
				var deltaTime = now - ~lastNoteonTime;
				~baseTime = deltaTime;
				~lastNoteonTime = now;
			});
			~numNotesHeld = ~numNotesHeld + 1;
			~lastNote = num;
		};

		m.noteoff {
			~numNotesHeld = ~numNotesHeld - 1;
		};

		~baseTime = 1.0;


		~timeSequence = Routine {
			var ratios = [1, 3/2, 7/8];
			var state = 0;
			var time;
			inf.do {
				state = (state + 1).wrap(0, ratios.size-1);
				time = ratios[state] * 	~baseTime;

				~echo.setDelayTime(time);
				(ratios[(state + 1).wrap(0, ratios.size-1)] * ~baseTime * 0.5).min(0.05).wait;
				//0.2.wait;
			}
		}.play;



	}.play;
}