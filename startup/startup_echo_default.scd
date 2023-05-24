
s = Server.default;

~elementsBasePath = "/Users/emb/code/z-elements";

s.waitForBoot {
	Routine {

		/// there is probably a more elegant way to ensure that all needed synthdefs are sent only once,
		// without writing to disk, as soon as server is booted.. but i haven't thought of it
		ZEchoDefault.sendSynthDefs(s);
		// wait until synthdefs are complete
		s.sync;

		/// this thing owns the main input and outputs,
		/// and a basic group order for managing synth execution sequence
		c = ZAudioContext.new(s);

		/// AFAIK, supercollider lacks a simple utility to resample buffers in memory
		/// easiest to ensure that file SR matches
		c.playTestInputLoop(~elementsBasePath ++ "/resources/dont_explain_short_48k.wav");

		/// this thing will make a "dry" connection from main input to output,
		/// as well as a "wet" connection through its own effect bus
		/// effects can read and write to this bus in-place
		~fx = ZStereoFxLoop.new(c);
		s.sync;
		~fx.setDryLevel(-6.dbamp);
		~fx.setWetLevel(-6.dbamp);

		/// after waiting a bit, to hear the difference...
		6.wait;
		/// ...create a basic, off the shelf echo effect...
		postln("adding echo");
		~echo = ZEchoDefault.new(c.server, ~fx.fxBus, c.group[\process]);
		/// ... and balance wet/dry a bit differently to differentiate them better
		~fx.setDryBalance(-0.8);
		~fx.setWetBalance(0.8);
	}.play;
}