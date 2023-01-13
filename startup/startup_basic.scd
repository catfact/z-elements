
s = Server.default;

~elementsBasePath = "/Users/emb/code/z-elements";

s.waitForBoot {
	Routine {
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
		~fx.setDryLevel(1);
		~fx.setWetLevel(-6.dbamp);

		// we should see and hear stereo input
		{ c.bus[\hw_in].scope; }.defer;
	}.play;
}